package com.fortunaball.ai.usage;

import com.fortunaball.ai.AiTestFixtures;
import com.fortunaball.ai.client.AiProperties;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.security.RequestRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AI 한도 검사 순서와 판정 확인. (DB 는 가짜, 1분 제한기는 진짜를 쓴다)
 * 핵심: 서비스 한도에 걸린 요청은 사용자 무료 횟수를 쓰지 않고, 날짜가 바뀌면 사용자 횟수가 다시 시작한다.
 */
@ExtendWith(MockitoExtension.class)
class AiUsageGuardTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final AiFeature FEATURE = AiFeature.DREAM;

    @Mock
    private AiUsageLogRepository repository;
    @Mock
    private AiUsageRecorder recorder;

    private TestClock clock;

    @BeforeEach
    void setUp() {
        clock = new TestClock(ZonedDateTime.of(2026, 10, 20, 11, 0, 0, 0, SEOUL));
    }

    private AiUsageGuard guard(AiProperties props) {
        return new AiUsageGuard(props, new RequestRateLimiter(), repository, recorder, clock);
    }

    private static ErrorCode codeOf(Runnable action) {
        return assertThrows(BusinessException.class, action::run).getErrorCode();
    }

    @Test
    void 키가_없으면_AI_001이고_DB를_보지_않는다() {
        AiUsageGuard guard = guard(AiTestFixtures.props(""));

        assertEquals(ErrorCode.AI_NOT_CONFIGURED, codeOf(() -> guard.check(FEATURE, "guest-1", "1.1.1.1")));

        verify(recorder).recordBlocked(FEATURE, AiUsageGuard.CODE_NOT_CONFIGURED);
        verifyNoInteractions(repository);
    }

    @Test
    void 한도_안이면_통과한다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k"));

        assertDoesNotThrow(() -> guard.check(FEATURE, "guest-1", "1.1.1.1"));

        verify(recorder, never()).recordBlocked(any(), any());
    }

    @Test
    void 사용자_하루_무료_횟수를_넘으면_AI_004이고_차단_기록을_남긴다() {
        // 사용자 하루 2회, 1분 한도는 넉넉하게
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 2, 20, 100));

        guard.check(FEATURE, "guest-1", "1.1.1.1");
        guard.check(FEATURE, "guest-1", "1.1.1.1");
        assertEquals(ErrorCode.AI_USER_LIMIT, codeOf(() -> guard.check(FEATURE, "guest-1", "1.1.1.1")));

        verify(recorder).recordBlocked(FEATURE, AiUsageGuard.CODE_USER_LIMIT);
    }

    @Test
    void 다른_사용자는_따로_센다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 1, 20, 100));

        guard.check(FEATURE, "guest-1", "1.1.1.1");

        assertDoesNotThrow(() -> guard.check(FEATURE, "guest-2", "2.2.2.2"));
    }

    @Test
    void guest_token을_바꿔도_접속지_하루_상한에_걸린다() {
        // 접속지 하루 2회. 매번 다른 guest_token 으로 우회를 시도한다.
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 5, 2, 100));

        guard.check(FEATURE, "guest-a", "9.9.9.9");
        guard.check(FEATURE, "guest-b", "9.9.9.9");

        assertEquals(ErrorCode.AI_USER_LIMIT, codeOf(() -> guard.check(FEATURE, "guest-c", "9.9.9.9")));
    }

    @Test
    void 날짜가_바뀌면_사용자_횟수가_다시_시작한다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 1, 20, 100));

        guard.check(FEATURE, "guest-1", "1.1.1.1");
        assertEquals(ErrorCode.AI_USER_LIMIT, codeOf(() -> guard.check(FEATURE, "guest-1", "1.1.1.1")));

        clock.set(ZonedDateTime.of(2026, 10, 21, 0, 0, 1, 0, SEOUL));   // 서울 기준 다음 날

        assertDoesNotThrow(() -> guard.check(FEATURE, "guest-1", "1.1.1.1"));
    }

    @Test
    void 서비스_하루_호출_상한에_도달하면_AI_002이다() {
        when(repository.countCallsSince(any())).thenReturn(300L);
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 5, 20, 100));

        assertEquals(ErrorCode.AI_DAILY_LIMIT, codeOf(() -> guard.check(FEATURE, "guest-1", "1.1.1.1")));

        verify(recorder).recordBlocked(FEATURE, AiUsageGuard.CODE_DAILY_LIMIT);
    }

    @Test
    void 서비스_한도에_걸린_요청은_사용자_횟수를_쓰지_않는다() {
        // 사용자 하루 1회. 서비스 한도에 걸린 뒤 한도가 풀리면 그 사용자는 아직 1회를 쓸 수 있어야 한다.
        when(repository.countCallsSince(any())).thenReturn(300L, 0L);
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 1, 20, 100));

        assertEquals(ErrorCode.AI_DAILY_LIMIT, codeOf(() -> guard.check(FEATURE, "guest-1", "1.1.1.1")));

        assertDoesNotThrow(() -> guard.check(FEATURE, "guest-1", "1.1.1.1"));
    }

    @Test
    void 월_비용_상한에_도달하면_AI_003이다() {
        when(repository.sumCostSince(any())).thenReturn(new BigDecimal("10.000000"));
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 5, 20, 100));

        assertEquals(ErrorCode.AI_MONTHLY_COST_LIMIT, codeOf(() -> guard.check(FEATURE, "guest-1", "1.1.1.1")));

        verify(recorder).recordBlocked(FEATURE, AiUsageGuard.CODE_MONTHLY_COST_LIMIT);
    }

    @Test
    void 월_비용이_상한_바로_아래면_통과한다() {
        when(repository.sumCostSince(any())).thenReturn(new BigDecimal("9.999999"));
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 5, 20, 100));

        assertDoesNotThrow(() -> guard.check(FEATURE, "guest-1", "1.1.1.1"));
    }

    @Test
    void 하루는_서울_0시부터_이번_달은_1일_0시부터_센다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k"));

        guard.check(FEATURE, "guest-1", "1.1.1.1");

        verify(repository).countCallsSince(LocalDateTime.of(2026, 10, 20, 0, 0));
        verify(repository).sumCostSince(LocalDateTime.of(2026, 10, 1, 0, 0));
    }

    @Test
    void 월_합계_기록이_비어_있어도_통과한다() {
        when(repository.sumCostSince(any())).thenReturn(null);
        AiUsageGuard guard = guard(AiTestFixtures.props("k"));

        assertDoesNotThrow(() -> guard.check(FEATURE, "guest-1", "1.1.1.1"));
    }

    @Test
    void 일분_연타는_COMMON_007이고_AI_기록은_남기지_않는다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 100, 100, 2));

        guard.check(FEATURE, "guest-1", "1.1.1.1");
        guard.check(FEATURE, "guest-1", "1.1.1.1");

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, codeOf(() -> guard.check(FEATURE, "guest-1", "1.1.1.1")));
        verify(recorder, never()).recordBlocked(any(), any());
    }

    @Test
    void 관리자는_모든_한도와_횟수_제한을_받지_않는다() {
        // 사용자 1회·접속지 1회·분당 1회·서비스 하루 1회·월 $1 로 아주 빡빡하게 두고 20번을 불러도 막히지 않아야 한다.
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 1, "1", 1, 1, 1));

        for (int i = 0; i < 20; i++) {
            guard.check(FEATURE, "guest-admin", "1.1.1.1", true);
        }

        verify(recorder, never()).recordBlocked(any(), any());
        verifyNoInteractions(repository);   // 관리자는 DB 집계도 보지 않는다
    }

    @Test
    void 관리자도_키가_없으면_AI_001이다() {
        AiUsageGuard guard = guard(AiTestFixtures.props(""));

        assertEquals(ErrorCode.AI_NOT_CONFIGURED, codeOf(() -> guard.check(FEATURE, "guest-admin", "1.1.1.1", true)));
    }

    @Test
    void 관리자_요청은_일반_사용자_횟수를_쓰지_않는다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 1, 20, 100));

        guard.check(FEATURE, "guest-1", "1.1.1.1", true);   // 관리자 요청
        guard.check(FEATURE, "guest-1", "1.1.1.1", true);

        // 같은 guest_token 이라도 일반 사용자로는 아직 1회를 쓸 수 있다
        assertDoesNotThrow(() -> guard.check(FEATURE, "guest-1", "1.1.1.1", false));
    }

    @Test
    void 오류_상세에_사용자_식별값이_들어가지_않는다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 1, 20, 100));
        guard.check(FEATURE, "guest-secret-token-0001", "7.7.7.7");

        BusinessException e = assertThrows(BusinessException.class,
                () -> guard.check(FEATURE, "guest-secret-token-0001", "7.7.7.7"));

        assertFalse(e.getDetail().contains("guest-secret-token-0001"));
        assertFalse(e.getDetail().contains("7.7.7.7"));
        assertTrue(e.getDetail().contains("guestCount=2/1"));
    }

    /** 시간을 직접 움직일 수 있는 시계 (테스트용) */
    private static final class TestClock extends Clock {
        private Instant now;

        TestClock(ZonedDateTime start) {
            this.now = start.toInstant();
        }

        void set(ZonedDateTime time) {
            this.now = time.toInstant();
        }

        @Override
        public ZoneId getZone() {
            return SEOUL;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
