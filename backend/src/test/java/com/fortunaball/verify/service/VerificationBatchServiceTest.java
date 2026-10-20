package com.fortunaball.verify.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.verify.repository.UnverifiedGameRepository;
import com.fortunaball.verify.repository.UnverifiedGameRepository.UnverifiedGame;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** DB 없이 가짜(Mockito)로 당첨 검증 배치의 판정·저장·오류 처리를 확인한다. */
class VerificationBatchServiceTest {

    private UnverifiedGameRepository targetRepo;
    private DrawRoundRepository drawRepo;
    private WinningVerificationRepository verificationRepo;
    private TransactionTemplate tx;
    private VerificationBatchService service;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        targetRepo = mock(UnverifiedGameRepository.class);
        drawRepo = mock(DrawRoundRepository.class);
        verificationRepo = mock(WinningVerificationRepository.class);
        tx = mock(TransactionTemplate.class);
        // 트랜잭션 템플릿은 받은 코드를 그대로 실행하는 가짜로 둔다
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback) inv.getArgument(0)).doInTransaction(null));
        service = new VerificationBatchService(targetRepo, drawRepo, verificationRepo, tx);

        DrawRound draw = mock(DrawRound.class);
        when(draw.getRoundNo()).thenReturn(1247);
        when(draw.getNumbers()).thenReturn(List.of(1, 2, 3, 4, 5, 6));
        when(draw.getBonusNumber()).thenReturn(7);
        when(drawRepo.findByRoundNo(1247)).thenReturn(Optional.of(draw));
    }

    /** 가짜 게임 한 줄. (Mockito 가짜를 when(...) 안에서 또 만들면 설정이 꼬이므로 직접 구현한다) */
    private static UnverifiedGame game(long id, int round, int... n) {
        return new UnverifiedGame() {
            public Long getId() { return id; }
            public Integer getRoundNo() { return round; }
            public Integer getNumber1() { return n[0]; }
            public Integer getNumber2() { return n[1]; }
            public Integer getNumber3() { return n[2]; }
            public Integer getNumber4() { return n[3]; }
            public Integer getNumber5() { return n[4]; }
            public Integer getNumber6() { return n[5]; }
        };
    }

    @Test
    void 판정할_게임이_없으면_아무것도_하지_않는다() {
        when(targetRepo.findUnverified(anyInt())).thenReturn(List.of());
        assertFalse(service.hasPending());
        assertEquals(new VerificationBatchService.Result(0, 0), service.verifyAll());
        verify(verificationRepo, never()).insertIfAbsent(anyLong(), anyInt(), anyInt(), anyBoolean(), any(), anyString());
    }

    @Test
    void 판정할_게임이_있으면_hasPending_이_true() {
        when(targetRepo.findUnverified(1)).thenReturn(List.of(game(1, 1247, 1, 2, 3, 4, 5, 6)));
        assertTrue(service.hasPending());
    }

    @Test
    void 일등과_낙첨을_각각_판정해서_저장한다() {
        when(targetRepo.findUnverified(anyInt())).thenReturn(
                List.of(game(10, 1247, 1, 2, 3, 4, 5, 6), game(11, 1247, 10, 20, 30, 40, 41, 42)), List.of());

        VerificationBatchService.Result result = service.verifyAll();

        assertEquals(new VerificationBatchService.Result(2, 0), result);
        verify(verificationRepo).insertIfAbsent(10L, 1247, 6, false, 1, "VERIFIED");
        verify(verificationRepo).insertIfAbsent(eq(11L), eq(1247), eq(0), eq(false), isNull(), eq("VERIFIED"));
    }

    @Test
    void 이등은_보너스까지_맞아야_한다() {
        when(targetRepo.findUnverified(anyInt())).thenReturn(List.of(game(12, 1247, 1, 2, 3, 4, 5, 7)), List.of());
        service.verifyAll();
        verify(verificationRepo).insertIfAbsent(12L, 1247, 5, true, 2, "VERIFIED");
    }

    @Test
    void 한_게임이_실패해도_나머지는_계속_처리한다() {
        when(targetRepo.findUnverified(anyInt())).thenReturn(
                List.of(game(20, 1247, 1, 2, 3, 4, 5, 6), game(21, 1247, 1, 2, 3, 4, 5, 6)), List.of());
        doThrow(new RuntimeException("DB 오류")).when(verificationRepo)
                .insertIfAbsent(eq(20L), anyInt(), anyInt(), anyBoolean(), any(), anyString());

        VerificationBatchService.Result result = service.verifyAll();

        assertEquals(new VerificationBatchService.Result(1, 1), result);
        verify(verificationRepo).insertIfAbsent(21L, 1247, 6, false, 1, "VERIFIED");
    }

    @Test
    void 추첨_결과를_못_찾으면_그_게임은_실패로_센다() {
        when(drawRepo.findByRoundNo(9999)).thenReturn(Optional.empty());
        when(targetRepo.findUnverified(anyInt())).thenReturn(
                List.of(game(30, 9999, 1, 2, 3, 4, 5, 6), game(31, 1247, 1, 2, 3, 4, 5, 6)), List.of());

        VerificationBatchService.Result result = service.verifyAll();

        assertEquals(new VerificationBatchService.Result(1, 1), result);
    }

    @Test
    void 한_묶음이_전부_실패하면_무한_반복하지_않고_멈춘다() {
        when(drawRepo.findByRoundNo(9999)).thenReturn(Optional.empty());
        when(targetRepo.findUnverified(anyInt())).thenReturn(List.of(game(40, 9999, 1, 2, 3, 4, 5, 6)));   // 계속 같은 게임

        VerificationBatchService.Result result = service.verifyAll();

        assertEquals(new VerificationBatchService.Result(0, 1), result);
        verify(targetRepo, times(1)).findUnverified(VerificationBatchService.BATCH_SIZE);
    }

    @Test
    void 같은_회차는_한_번만_읽는다() {
        when(targetRepo.findUnverified(anyInt())).thenReturn(
                List.of(game(50, 1247, 1, 2, 3, 4, 5, 6), game(51, 1247, 7, 8, 9, 10, 11, 12), game(52, 1247, 1, 2, 3, 40, 41, 42)),
                List.of());
        service.verifyAll();
        verify(drawRepo, times(1)).findByRoundNo(1247);
    }
}
