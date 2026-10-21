package com.fortunaball.admin.action.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.admin.action.dto.JobRunResult;
import com.fortunaball.admin.action.dto.RecalculateResult;
import com.fortunaball.admin.action.dto.ResyncResult;
import com.fortunaball.admin.action.dto.VerifyResult;
import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.draw.collector.DrawCollector;
import com.fortunaball.draw.domain.DrawPrize;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.dto.DrawApiResponse;
import com.fortunaball.draw.repository.DrawPrizeRepository;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.draw.service.DrawCacheEvictor;
import com.fortunaball.draw.service.DrawCollectService;
import com.fortunaball.draw.service.SchedulerLogRecorder;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.service.StatSnapshotService;
import com.fortunaball.stat.service.StatSnapshotTrigger;
import com.fortunaball.verify.service.VerificationBatchService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class AdminOperationServiceTest {

    private final DrawCollector collector = mock(DrawCollector.class);
    private final DrawRoundRepository roundRepo = mock(DrawRoundRepository.class);
    private final DrawPrizeRepository prizeRepo = mock(DrawPrizeRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final DrawCacheEvictor evictor = mock(DrawCacheEvictor.class);
    private final DrawCollectService collectService = mock(DrawCollectService.class);
    private final VerificationBatchService verifyBatch = mock(VerificationBatchService.class);
    private final StatSnapshotService statService = mock(StatSnapshotService.class);
    private final StatSnapshotTrigger statTrigger = mock(StatSnapshotTrigger.class);
    private final SchedulerLogRecorder logRecorder = mock(SchedulerLogRecorder.class);
    private final AdminActionRecorder recorder = mock(AdminActionRecorder.class);

    private final AdminOperationService service = new AdminOperationService(collector, roundRepo, prizeRepo, tx,
            evictor, collectService, verifyBatch, statService, statTrigger, logRecorder, recorder);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L, "admin", "ADMIN"), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        // 트랜잭션 템플릿은 받은 코드를 그대로 한 번 실행한다
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static DrawRound round(int no, List<Integer> nums, int bonus) {
        return new DrawRound(no, LocalDate.of(2026, 10, 17), nums, bonus, "DHLOTTERY_API");
    }

    private static List<DrawPrize> fivePrizes(int no, long firstAmount) {
        return List.of(new DrawPrize(no, 1, firstAmount, 10), new DrawPrize(no, 2, 5_000_000L, 20),
                new DrawPrize(no, 3, 1_500_000L, 30), new DrawPrize(no, 4, 50_000L, 40), new DrawPrize(no, 5, 5_000L, 50));
    }

    private void official(int no, DrawRound fresh, List<DrawPrize> prizes, boolean aggregated) {
        DrawApiResponse.Item item = mock(DrawApiResponse.Item.class);
        when(item.isPrizeAggregated()).thenReturn(aggregated);
        when(item.toDrawRound(anyString())).thenReturn(fresh);
        when(item.toDrawPrizes()).thenReturn(prizes);
        when(collector.fetch(no)).thenReturn(Optional.of(item));
    }

    // ---------------------------------------------------------------- API-23

    @Test
    void resync_저장돼_있지_않던_회차는_새로_저장한다() {
        official(1250, round(1250, List.of(1, 2, 3, 4, 5, 6), 7), fivePrizes(1250, 2_000_000_000L), true);
        when(roundRepo.findByRoundNo(1250)).thenReturn(Optional.empty());
        when(roundRepo.save(any(DrawRound.class))).thenAnswer(inv -> inv.getArgument(0));

        ResyncResult r = service.resync(1250);

        assertEquals("CREATED", r.result());
        assertEquals(5, r.prizes().size());
        assertTrue(r.prizeCheckPassed());
        verify(prizeRepo).saveAll(any());
        verify(evictor).evictRound(1250);
        verify(statTrigger).refresh(contains("1250"));
        verify(recorder).record(eq(7L), eq("RESYNC_DRAW"), eq("draw_round"), any(), contains("CREATED"));
    }

    @Test
    void resync_공식_값과_같으면_바꾸지_않고_캐시도_안_비운다() {
        DrawRound existing = round(1250, List.of(1, 2, 3, 4, 5, 6), 7);
        official(1250, round(1250, List.of(1, 2, 3, 4, 5, 6), 7), fivePrizes(1250, 2_000_000_000L), true);
        when(roundRepo.findByRoundNo(1250)).thenReturn(Optional.of(existing));
        when(prizeRepo.findByRoundNoOrderByWinningRankAsc(1250)).thenReturn(fivePrizes(1250, 2_000_000_000L));

        ResyncResult r = service.resync(1250);

        assertEquals("UNCHANGED", r.result());
        verify(evictor, never()).evictRound(anyInt());
        verify(statService, never()).recalculateAffectedBy(anyInt(), any());
    }

    @Test
    void resync_값이_다르면_공식_값으로_덮어쓰고_전후를_기록한다() {
        DrawRound existing = round(1250, List.of(1, 2, 3, 4, 5, 6), 7);
        official(1250, round(1250, List.of(1, 2, 3, 4, 5, 8), 7), fivePrizes(1250, 2_100_000_000L), true);
        when(roundRepo.findByRoundNo(1250)).thenReturn(Optional.of(existing));
        when(prizeRepo.findByRoundNoOrderByWinningRankAsc(1250)).thenReturn(fivePrizes(1250, 2_000_000_000L));
        when(statService.recalculateAffectedBy(anyInt(), any())).thenReturn(2);

        ResyncResult r = service.resync(1250);

        assertEquals("UPDATED", r.result());
        assertTrue(r.changes().contains("당첨번호"));
        assertTrue(r.changes().contains("1등 당첨금"));
        assertEquals(List.of(1, 2, 3, 4, 5, 8), existing.getNumbers());
        verify(evictor).evictRound(1250);
        verify(statService).recalculateAffectedBy(eq(1250), any());
        verify(recorder).record(eq(7L), eq("RESYNC_DRAW"), eq("draw_round"), any(), contains("UPDATED"));
    }

    @Test
    void resync_공식_사이트에_없으면_NOT_FOUND() {
        when(collector.fetch(2000)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.resync(2000));
        verify(roundRepo, never()).save(any());
    }

    @Test
    void resync_2_5등_집계_전이면_저장하지_않는다() {
        official(1251, round(1251, List.of(1, 2, 3, 4, 5, 6), 7), fivePrizes(1251, 1L), false);
        assertThrows(BusinessException.class, () -> service.resync(1251));
        verify(roundRepo, never()).save(any());
        verify(recorder, never()).record(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void resync_1_5등_중_빠진_등수가_있으면_저장하지_않는다() {
        List<DrawPrize> four = List.of(new DrawPrize(1250, 1, 1L, 1), new DrawPrize(1250, 2, 1L, 1),
                new DrawPrize(1250, 3, 1L, 1), new DrawPrize(1250, 4, 1L, 1));
        official(1250, round(1250, List.of(1, 2, 3, 4, 5, 6), 7), four, true);
        assertThrows(BusinessException.class, () -> service.resync(1250));
        verify(roundRepo, never()).save(any());
    }

    @Test
    void resync_회차_번호가_범위를_벗어나면_거절() {
        assertThrows(BusinessException.class, () -> service.resync(0));
        assertThrows(BusinessException.class, () -> service.resync(5001));
    }

    @Test
    void checkPrizes_중복_등수를_잡아낸다() {
        List<DrawPrize> dup = List.of(new DrawPrize(1, 1, 1L, 1), new DrawPrize(1, 1, 1L, 1));
        assertNotNull(AdminOperationService.checkPrizes(dup));
        assertNull(AdminOperationService.checkPrizes(fivePrizes(1, 1L)));
    }

    // ---------------------------------------------------------------- API-25

    @Test
    void verify_판정할_게임이_없으면_아무것도_안_한다() {
        when(verifyBatch.hasPending()).thenReturn(false);
        VerifyResult r = service.verify();
        assertEquals("NOTHING_TO_DO", r.status());
        verify(verifyBatch, never()).verifyAll();
        verify(recorder).record(eq(7L), eq("RUN_VERIFY"), eq("winning_verification"), any(), anyString());
    }

    @Test
    void verify_판정하고_실행기록을_남긴다() {
        when(verifyBatch.hasPending()).thenReturn(true);
        when(logRecorder.start(AdminOperationService.LOG_VERIFY, null)).thenReturn(5L);
        when(verifyBatch.verifyAll()).thenReturn(new VerificationBatchService.Result(12, 0));

        VerifyResult r = service.verify();

        assertEquals("DONE", r.status());
        assertEquals(12, r.verified());
        verify(logRecorder).success(5L, 12);
    }

    @Test
    void verify_실패한_게임이_있으면_FAILED로_기록한다() {
        when(verifyBatch.hasPending()).thenReturn(true);
        when(logRecorder.start(anyString(), any())).thenReturn(6L);
        when(verifyBatch.verifyAll()).thenReturn(new VerificationBatchService.Result(3, 2));

        VerifyResult r = service.verify();

        assertEquals(2, r.failed());
        verify(logRecorder).failed(eq(6L), eq(3), contains("2"));
    }

    // ---------------------------------------------------------------- API-40

    @Test
    void runJob_알_수_없는_작업_이름은_거절() {
        assertThrows(BusinessException.class, () -> service.runJob("HACK"));
        assertThrows(BusinessException.class, () -> service.runJob(null));
    }

    @Test
    void runJob_통계_맞춤_성공() {
        when(logRecorder.start(AdminOperationService.LOG_STAT, null)).thenReturn(9L);
        when(statTrigger.refresh(anyString())).thenReturn(true);

        JobRunResult r = service.runJob("stat_snapshot");   // 소문자도 허용

        assertEquals("SUCCESS", r.status());
        verify(logRecorder).success(9L, 0);
        verify(recorder).record(eq(7L), eq("RUN_JOB"), eq("scheduler_log"), any(), contains("STAT_SNAPSHOT"));
    }

    @Test
    void runJob_통계_맞춤_실패() {
        when(logRecorder.start(anyString(), any())).thenReturn(9L);
        when(statTrigger.refresh(anyString())).thenReturn(false);
        assertEquals("FAILED", service.runJob("STAT_SNAPSHOT").status());
        verify(logRecorder).failed(eq(9L), eq(0), anyString());
    }

    @Test
    void runJob_검증배치는_할_일이_없으면_NOTHING_TO_DO() {
        when(verifyBatch.hasPending()).thenReturn(false);
        assertEquals("NOTHING_TO_DO", service.runJob("VERIFY_BATCH").status());
    }

    // ---------------------------------------------------------------- API-47

    @Test
    void recalculate_통계가_없으면_NOT_FOUND() {
        when(statService.recalculate(eq(1300), any())).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.recalculate(1300));
        verify(recorder, never()).record(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void recalculate_성공하면_기록을_남긴다() {
        StatRoundSnapshot snap = StatRoundSnapshot.prepare(1251, 1250, LocalDateTime.of(2026, 10, 21, 15, 0));
        when(statService.recalculate(eq(1251), any())).thenReturn(Optional.of(snap));

        RecalculateResult r = service.recalculate(1251);

        assertEquals(1251, r.roundNo());
        assertEquals(1250, r.basisRoundNo());
        assertEquals("PREPARING", r.statStatus());
        verify(recorder).record(eq(7L), eq("RECALCULATE_STAT"), eq("stat_round_snapshot"), any(), contains("1251"));
    }

    @Test
    void 로그인_없이는_모두_UNAUTHORIZED() {
        SecurityContextHolder.clearContext();
        assertThrows(BusinessException.class, () -> service.resync(1250));
        assertThrows(BusinessException.class, () -> service.verify());
        assertThrows(BusinessException.class, () -> service.runJob("DRAW_COLLECT"));
        assertThrows(BusinessException.class, () -> service.recalculate(1250));
    }
}
