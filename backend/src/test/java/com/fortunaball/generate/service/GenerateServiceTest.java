package com.fortunaball.generate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.generate.dto.GenerateRequest;
import com.fortunaball.generate.dto.GenerateResponse;
import com.fortunaball.issue.repository.ComboIssueCountRepository;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.PatternCode;
import com.fortunaball.stat.domain.StatPatternCard;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.repository.StatPatternCardRepository;
import com.fortunaball.stat.repository.StatRoundSnapshotRepository;
import com.fortunaball.stat.service.PatternRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DB 없이 가짜 저장소(Mockito)로 번호 생성 서비스의 흐름을 확인한다. */
class GenerateServiceTest {

    private static final int TARGET = 1247;
    private static final int BASIS = 1246;
    private static final GuestToken GUEST = GuestToken.of("guest-aaaaaaaaaaaaaaaa");

    private final RoundPolicy roundPolicy = mock(RoundPolicy.class);
    private final StatRoundSnapshotRepository snapshotRepo = mock(StatRoundSnapshotRepository.class);
    private final StatPatternCardRepository cardRepo = mock(StatPatternCardRepository.class);
    private final DrawRoundRepository drawRepo = mock(DrawRoundRepository.class);
    private final ComboIssueCountRepository comboRepo = mock(ComboIssueCountRepository.class);
    private final GenerationProperties props = new GenerationProperties(30, 50, 3);
    private final GenerationStore store = new GenerationStore(props);
    private GenerateService service;

    @BeforeEach
    void setUp() {
        service = new GenerateService(roundPolicy, snapshotRepo, cardRepo, drawRepo, comboRepo,
                new NumberGenerator(), store, props, new ObjectMapper());

        when(roundPolicy.targetRound(any(ZonedDateTime.class))).thenReturn(TARGET);
        when(roundPolicy.drawDate(any(ZonedDateTime.class))).thenReturn(LocalDate.of(2026, 10, 24));
        when(snapshotRepo.findByTargetRoundNo(TARGET))
                .thenReturn(Optional.of(StatRoundSnapshot.prepare(TARGET, BASIS, LocalDateTime.now())));
        when(drawRepo.findByRoundNoLessThanEqualOrderByRoundNoDesc(anyInt(), any(Pageable.class))).thenReturn(history());
        when(comboRepo.findKeysAtLimit(anyInt(), anyInt())).thenReturn(List.of());
    }

    private static List<DrawRound> history() {
        Random r = new Random(5);
        List<DrawRound> list = new ArrayList<>();
        for (int round = BASIS; round > BASIS - 100; round--) {
            List<Integer> nums = new ArrayList<>();
            while (nums.size() < 7) {
                int n = 1 + r.nextInt(45);
                if (!nums.contains(n)) {
                    nums.add(n);
                }
            }
            list.add(new DrawRound(round, LocalDate.of(2026, 1, 1), nums.subList(0, 6), nums.get(6), "TEST"));
        }
        return list;
    }

    private void cards(PatternCode code, String json) {
        when(cardRepo.findBySnapshotIdAndPeriodRounds(any(), anyInt())).thenReturn(
                List.of(new StatPatternCard(1L, code, code.displayName(), 100, 10, json)));
    }

    private static BusinessException error(Runnable r) {
        return assertThrows(BusinessException.class, r::run);
    }

    @Test
    void 패턴_없이_만들면_응답에_회차와_토큰이_담긴다() {
        GenerateResponse res = service.generate(GUEST, new GenerateRequest(5, null));
        assertEquals(5, res.games().size());
        assertEquals(TARGET, res.targetRound());
        assertEquals(BASIS, res.basisRound());
        assertEquals(LocalDate.of(2026, 10, 24), res.drawDate());
        assertEquals(30, res.ttlMinutes());
        assertNull(res.patternCode());
        assertNotNull(res.confirmToken());
        assertTrue(store.find(res.confirmToken(), GUEST.value()).isPresent());   // 서버에 보관됨
        res.games().forEach(g -> assertEquals(6, g.size()));
    }

    @Test
    void 핫넘버_패턴은_카드의_번호를_3개_이상_포함한다() {
        cards(PatternCode.HOT_NUMBER, "{\"hotNumbers\":[1,2,3,4,5,6,7,8,9,10],\"minMatch\":3}");
        GenerateResponse res = service.generate(GUEST, new GenerateRequest(20, "HOT_NUMBER"));
        assertEquals("HOT_NUMBER", res.patternCode());
        assertNotNull(res.patternName());
        for (List<Integer> g : res.games()) {
            assertTrue(g.stream().filter(n -> n <= 10).count() >= PatternRule.HOT_MIN_MATCH, "핫넘버 부족: " + g);
        }
    }

    @Test
    void 이미_3번_찬_조합_목록을_이번_대상_회차와_설정값으로_조회한다() {
        service.generate(GUEST, new GenerateRequest(3, null));
        verify(comboRepo).findKeysAtLimit(TARGET, 3);
    }

    @Test
    void 게임_수가_범위를_벗어나거나_비어_있으면_입력_오류() {
        for (Integer bad : new Integer[]{null, 0, -1, 51}) {
            BusinessException e = error(() -> service.generate(GUEST, new GenerateRequest(bad, null)));
            assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode(), "gameCount=" + bad);
        }
        service.generate(GUEST, new GenerateRequest(1, null));    // 경계값 통과
        service.generate(GUEST, new GenerateRequest(50, null));
    }

    @Test
    void 모르는_패턴_코드는_입력_오류() {
        BusinessException e = error(() -> service.generate(GUEST, new GenerateRequest(3, "LUCKY_SEVEN")));
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void 스냅샷이_없으면_통계_준비중_오류() {
        when(snapshotRepo.findByTargetRoundNo(TARGET)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, error(() -> service.generate(GUEST, new GenerateRequest(3, null))).getErrorCode());
    }

    @Test
    void 겨울잠_번호가_없으면_패턴을_완화하지_않고_충돌_오류() {
        cards(PatternCode.WINTER_SLEEP, "{\"sleepNumbers\":[],\"gapRounds\":20,\"minCount\":1}");
        BusinessException e = error(() -> service.generate(GUEST, new GenerateRequest(3, "WINTER_SLEEP")));
        assertEquals(ErrorCode.CONFLICT, e.getErrorCode());
    }

    @Test
    void 다시_생성하면_이전_토큰은_쓸_수_없다() {
        GenerateResponse first = service.generate(GUEST, new GenerateRequest(3, null));
        GenerateResponse second = service.generate(GUEST, new GenerateRequest(3, null));
        assertFalse(store.find(first.confirmToken(), GUEST.value()).isPresent());
        assertTrue(store.find(second.confirmToken(), GUEST.value()).isPresent());
    }
}
