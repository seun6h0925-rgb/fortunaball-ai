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
    void 처음에는_무료_횟수가_모두_남아_있다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k"));

        AiUsageSnapshot snapshot = guard.snapshot("guest-1", "1.1.1.1", false);

        assertFalse(snapshot.unlimited());
        assertEquals(5, snapshot.dailyFreeLimit());
        assertEquals(0, snapshot.usedToday());
        assertEquals(5, snapshot.remainingToday());
        assertEquals(LocalDateTime.of(2026, 10, 21, 0, 0), snapshot.resetAt());   // 다음 날 서울 0시
    }

    @Test
    void 쓴_만큼_남은_횟수가_줄고_조회는_횟수를_쓰지_않는다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k"));
        guard.check(FEATURE, "guest-1", "1.1.1.1");
        guard.check(FEATURE, "guest-1", "1.1.1.1");

        AiUsageSnapshot first = guard.snapshot("guest-1", "1.1.1.1", false);
        AiUsageSnapshot second = guard.snapshot("guest-1", "1.1.1.1", false);   // 몇 번을 조회해도 그대로

        assertEquals(2, first.usedToday());
        assertEquals(3, first.remainingToday());
        assertEquals(3, second.remainingToday());
    }

    @Test
    void 한도를_다_쓰면_남은_횟수는_0이고_쓴_횟수는_한도까지만_보인다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 2, 20, 100));
        guard.check(FEATURE, "guest-1", "1.1.1.1");
        guard.check(FEATURE, "guest-1", "1.1.1.1");
        assertEquals(ErrorCode.AI_USER_LIMIT, codeOf(() -> guard.check(FEATURE, "guest-1", "1.1.1.1")));   // 막힌 시도(3번째)

        AiUsageSnapshot snapshot = guard.snapshot("guest-1", "1.1.1.1", false);

        assertEquals(0, snapshot.remainingToday());
        assertEquals(2, snapshot.usedToday());   // 3 이 아니라 한도(2)까지만
    }

    @Test
    void 접속지_한도가_먼저_바닥나면_그_기준으로_남은_횟수를_보여_준다() {
        // 사용자 한도 5 인데 접속지 한도는 2. 같은 접속지의 다른 사람이 2번 쓴 상태.
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 5, 2, 100));
        guard.check(FEATURE, "guest-a", "9.9.9.9");
        guard.check(FEATURE, "guest-b", "9.9.9.9");

        AiUsageSnapshot snapshot = guard.snapshot("guest-c", "9.9.9.9", false);   // guest-c 는 한 번도 안 썼다

        assertEquals(0, snapshot.usedToday());
        assertEquals(0, snapshot.remainingToday());   // 접속지 한도(2)가 바닥나서 사실대로 0
    }

    @Test
    void 날짜가_바뀌면_남은_횟수가_다시_채워진다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k"));
        guard.check(FEATURE, "guest-1", "1.1.1.1");
        assertEquals(4, guard.snapshot("guest-1", "1.1.1.1", false).remainingToday());

        clock.set(ZonedDateTime.of(2026, 10, 21, 0, 0, 1, 0, SEOUL));

        assertEquals(5, guard.snapshot("guest-1", "1.1.1.1", false).remainingToday());
    }

    @Test
    void 관리자는_제한_없음으로_알려_준다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k"));

        assertTrue(guard.snapshot("guest-1", "1.1.1.1", true).unlimited());
    }

    @Test
    void 실패해서_돌려주면_무료_횟수가_다시_생긴다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 1, 20, 100));
        AiPermit permit = guard.check(FEATURE, "guest-1", "1.1.1.1");
        assertEquals(0, guard.snapshot("guest-1", "1.1.1.1", false).remainingToday());

        guard.refund(permit);

        assertEquals(1, guard.snapshot("guest-1", "1.1.1.1", false).remainingToday());
        assertDoesNotThrow(() -> guard.check(FEATURE, "guest-1", "1.1.1.1"));   // 다시 쓸 수 있다
    }

    @Test
    void 한_영수증으로는_한_번만_돌려준다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k"));
        guard.check(FEATURE, "guest-1", "1.1.1.1");                          // 1회 사용
        AiPermit permit = guard.check(FEATURE, "guest-1", "1.1.1.1");        // 2회 사용
        assertEquals(3, guard.snapshot("guest-1", "1.1.1.1", false).remainingToday());

        guard.refund(permit);
        guard.refund(permit);   // 두 번 불러도
        guard.refund(permit);

        assertEquals(4, guard.snapshot("guest-1", "1.1.1.1", false).remainingToday());   // 1만 돌아온다
    }

    @Test
    void 관리자_영수증과_null은_돌려줄_것이_없다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k"));
        guard.check(FEATURE, "guest-1", "1.1.1.1");

        guard.refund(guard.check(FEATURE, "guest-1", "1.1.1.1", true));   // 관리자 영수증
        guard.refund(AiPermit.none());
        guard.refund(null);

        assertEquals(4, guard.snapshot("guest-1", "1.1.1.1", false).remainingToday());   // 변화 없음
    }

    @Test
    void 자정을_넘겨_실패해도_차감한_날짜의_횟수에서_돌려준다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k"));
        AiPermit permit = guard.check(FEATURE, "guest-1", "1.1.1.1");   // 10/20 에 사용
        clock.set(ZonedDateTime.of(2026, 10, 21, 0, 0, 5, 0, SEOUL));    // 호출이 끝나기 전에 자정이 지남
        guard.check(FEATURE, "guest-1", "1.1.1.1");                      // 10/21 에 1회 사용

        guard.refund(permit);   // 10/20 의 횟수만 돌려준다

        assertEquals(4, guard.snapshot("guest-1", "1.1.1.1", false).remainingToday());   // 10/21 은 그대로
    }

    @Test
    void 막힌_시도는_횟수를_쓰지_않아서_환불하면_정확히_돌아온다() {
        AiUsageGuard guard = guard(AiTestFixtures.props("k", 300, "10", 2, 20, 100));
        AiPermit first = guard.check(FEATURE, "guest-1", "1.1.1.1");
        guard.check(FEATURE, "guest-1", "1.1.1.1");
        // 한도(2)를 넘긴 시도가 여러 번 막힌다
        assertEquals(ErrorCode.AI_USER_LIMIT, codeOf(() -> guard.check(FEATURE, "guest-1", "1.1.1.1")));
        assertEquals(ErrorCode.AI_USER_LIMIT, codeOf(() -> guard.check(FEATURE, "guest-1", "1.1.1.1")));
        assertEquals(0, guard.snapshot("guest-1", "1.1.1.1", false).remainingToday());

        guard.refund(first);   // 첫 호출이 실패했다면

        // 막힌 시도가 카운터를 부풀리지 않았으므로 정확히 1회가 돌아온다 (2가 아니라 1)
        assertEquals(1, guard.snapshot("guest-1", "1.1.1.1", false).remainingToday());
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
