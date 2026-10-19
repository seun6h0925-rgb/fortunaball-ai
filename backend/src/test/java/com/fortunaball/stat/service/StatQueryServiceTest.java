package com.fortunaball.stat.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.PatternCode;
import com.fortunaball.stat.domain.SnapshotStatus;
import com.fortunaball.stat.domain.StatPatternCard;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.dto.PatternStatResponse;
import com.fortunaball.stat.repository.StatPatternCardRepository;
import com.fortunaball.stat.repository.StatRoundSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatQueryServiceTest {

    private RoundPolicy roundPolicy;
    private StatRoundSnapshotRepository snapshotRepo;
    private StatPatternCardRepository cardRepo;
    private StatQueryService service;

    @BeforeEach
    void setUp() {
        roundPolicy = mock(RoundPolicy.class);
        snapshotRepo = mock(StatRoundSnapshotRepository.class);
        cardRepo = mock(StatPatternCardRepository.class);
        service = new StatQueryService(roundPolicy, snapshotRepo, cardRepo);
        when(roundPolicy.targetRound()).thenReturn(1247);
    }

    @Test
    void 기간을_안_보내면_100이다() {
        assertEquals(100, StatQueryService.parsePeriod(null));
        assertEquals(100, StatQueryService.parsePeriod(" "));
    }

    @Test
    void 허용된_기간만_통과한다() {
        assertEquals(20, StatQueryService.parsePeriod("20"));
        assertEquals(50, StatQueryService.parsePeriod("50"));
        assertEquals(100, StatQueryService.parsePeriod("100"));
    }

    @Test
    void 허용되지_않은_기간은_입력_오류다() {
        for (String bad : new String[]{"10", "30", "0", "-1", "abc", "20.5"}) {
            BusinessException e = assertThrows(BusinessException.class, () -> StatQueryService.parsePeriod(bad));
            assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
        }
    }

    @Test
    void 통계가_아직_없으면_준비중_안내_오류다() {
        when(snapshotRepo.findByTargetRoundNo(1247)).thenReturn(Optional.empty());
        BusinessException e = assertThrows(BusinessException.class, () -> service.getPatterns(100));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    void 대상_회차_통계_상태를_돌려준다() {
        StatRoundSnapshot snap = StatRoundSnapshot.prepare(1247, 1246, LocalDateTime.of(2026, 10, 19, 10, 0));
        snap.markWaiting();
        when(snapshotRepo.findByTargetRoundNo(1247)).thenReturn(Optional.of(snap));
        assertEquals(SnapshotStatus.WAITING, service.statusOf(1247));
    }

    @Test
    void 통계가_없어도_상태_조회는_사전_분석으로_안전하게_돌려준다() {
        when(snapshotRepo.findByTargetRoundNo(1247)).thenReturn(Optional.empty());
        assertEquals(SnapshotStatus.PREPARING, service.statusOf(1247));
    }

    @Test
    void 패턴_코드_순서대로_카드_6장과_상태를_돌려준다() {
        StatRoundSnapshot snap = StatRoundSnapshot.prepare(1247, 1246, LocalDateTime.of(2026, 10, 19, 10, 0));
        when(snapshotRepo.findByTargetRoundNo(1247)).thenReturn(Optional.of(snap));
        // 일부러 거꾸로 섞어서 돌려준다
        List<StatPatternCard> cards = new ArrayList<>();
        for (PatternCode code : PatternCode.values()) {
            cards.add(new StatPatternCard(null, code, "설명", 100, 50, "{}"));
        }
        Collections.reverse(cards);
        when(cardRepo.findBySnapshotIdAndPeriodRounds(any(), eq(100))).thenReturn(cards);

        PatternStatResponse res = service.getPatterns(100);

        assertEquals(1247, res.targetRound());
        assertEquals(1246, res.basisRound());
        assertEquals(SnapshotStatus.PREPARING.name(), res.status());
        assertEquals("사전 분석", res.statusLabel());
        assertEquals(6, res.patterns().size());
        assertEquals(PatternCode.HOT_NUMBER.name(), res.patterns().get(0).code());
        assertEquals(PatternCode.ALL_SECTION_SPREAD.name(), res.patterns().get(5).code());
        assertEquals(50, res.patterns().get(0).hitCount());
    }
}
