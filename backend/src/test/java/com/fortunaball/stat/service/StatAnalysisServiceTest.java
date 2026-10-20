package com.fortunaball.stat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.SnapshotStatus;
import com.fortunaball.stat.domain.StatPeriod;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.dto.NumberStatResponse;
import com.fortunaball.stat.dto.StatSummaryResponse;
import com.fortunaball.stat.repository.StatRoundSnapshotRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

/** DB 없이 가짜로 통계 요약·번호별 통계의 입력 검사와 구간 결정을 확인한다. (회차 1~5가 저장돼 있다고 가정) */
class StatAnalysisServiceTest {

    private static final int LATEST = 5;

    private final DrawRoundRepository drawRepo = mock(DrawRoundRepository.class);
    private final RoundPolicy roundPolicy = mock(RoundPolicy.class);
    private final StatRoundSnapshotRepository snapshotRepo = mock(StatRoundSnapshotRepository.class);
    private StatAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new StatAnalysisService(drawRepo, roundPolicy, snapshotRepo);
        List<DrawRound> rounds = new ArrayList<>();
        for (int r = 1; r <= LATEST; r++) {
            rounds.add(new DrawRound(r, LocalDate.of(2026, 1, r), List.of(r, r + 6, r + 12, r + 18, r + 24, r + 30), 45, "TEST"));
        }
        when(drawRepo.findTopByOrderByRoundNoDesc()).thenReturn(Optional.of(rounds.get(LATEST - 1)));
        when(drawRepo.findByRoundNoLessThanEqualOrderByRoundNoDesc(anyInt(), any(Pageable.class))).thenAnswer(inv -> {
            int basis = inv.getArgument(0);
            List<DrawRound> desc = new ArrayList<>(rounds.stream().filter(x -> x.getRoundNo() <= basis).toList());
            Collections.reverse(desc);
            return desc;
        });
        when(roundPolicy.targetRound()).thenReturn(LATEST + 1);
    }

    private static BusinessException reject(Runnable r) {
        return assertThrows(BusinessException.class, r::run);
    }

    // ---------- API-09 ----------

    @Test
    void 요약은_최신_회차_기준이고_상태는_스냅샷을_따른다() {
        StatRoundSnapshot snap = mock(StatRoundSnapshot.class);
        when(snap.getStatus()).thenReturn(SnapshotStatus.PREPARING);
        when(snapshotRepo.findByTargetRoundNo(LATEST + 1)).thenReturn(Optional.of(snap));

        StatSummaryResponse r = service.summary(StatPeriod.ALL, null);

        assertEquals(LATEST, r.basisRound());
        assertEquals(5, r.roundCount());
        assertEquals("PREPARING", r.status());
        assertEquals("사전 분석", r.statusLabel());
    }

    @Test
    void 스냅샷이_없으면_상태는_null이다() {
        when(snapshotRepo.findByTargetRoundNo(anyInt())).thenReturn(Optional.empty());

        StatSummaryResponse r = service.summary(new StatPeriod(10), null);

        assertEquals(null, r.status());
    }

    @Test
    void 지난_회차를_기준으로_보면_확정이고_그_회차까지만_계산한다() {
        StatSummaryResponse r = service.summary(StatPeriod.ALL, 3);

        assertEquals(3, r.basisRound());
        assertEquals(3, r.roundCount());
        assertEquals("CONFIRMED", r.status());
        verifyNoInteractions(snapshotRepo);
    }

    @Test
    void 기준_회차가_범위를_벗어나면_거절한다() {
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.summary(StatPeriod.ALL, 0)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.summary(StatPeriod.ALL, LATEST + 1)).getErrorCode());
    }

    @Test
    void 회차_데이터가_없으면_503() {
        when(drawRepo.findTopByOrderByRoundNoDesc()).thenReturn(Optional.empty());

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, reject(() -> service.summary(StatPeriod.ALL, null)).getErrorCode());
    }

    // ---------- API-10 ----------

    @Test
    void 번호별은_기간으로_구간을_정한다() {
        NumberStatResponse r = service.numbers(new StatPeriod(10), null, null);   // 10회지만 이력이 5회뿐

        assertEquals(1, r.fromRound());
        assertEquals(5, r.toRound());
        assertEquals(5, r.roundCount());
        assertEquals(45, r.items().size());
    }

    @Test
    void 번호별_기간이_이력보다_짧으면_최근_회차만_센다() {
        // 3번 회차 번호: 3, 9, 15, 21, 27, 33 / 5회차: 5, 11, 17, 23, 29, 35
        NumberStatResponse r = service.numbers(new StatPeriod(10), 2, 4);

        assertEquals(2, r.fromRound());
        assertEquals(4, r.toRound());
        assertEquals(3, r.roundCount());
        assertEquals(0, r.items().get(0).count());              // 번호 1은 1회차에만 나왔다 → 범위 밖
        assertEquals(1, r.items().get(2).count());              // 번호 3은 3회차
        assertEquals(1, r.items().get(2).absentRounds());       // toRound(4) - 3
    }

    @Test
    void 번호별_회차_범위는_둘_다_줘야_한다() {
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.numbers(StatPeriod.ALL, 1, null)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.numbers(StatPeriod.ALL, null, 3)).getErrorCode());
    }

    @Test
    void 번호별_잘못된_범위는_거절한다() {
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.numbers(StatPeriod.ALL, 4, 2)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.numbers(StatPeriod.ALL, 0, 2)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.numbers(StatPeriod.ALL, 1, LATEST + 1)).getErrorCode());
    }

    @Test
    void 번호별_전체는_첫_회차부터다() {
        NumberStatResponse r = service.numbers(StatPeriod.ALL, null, null);

        assertEquals(1, r.fromRound());
        assertEquals(5, r.roundCount());
    }
}
