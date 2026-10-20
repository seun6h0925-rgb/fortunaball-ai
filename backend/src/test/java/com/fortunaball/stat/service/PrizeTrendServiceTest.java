package com.fortunaball.stat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.domain.DrawPrize;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawPrizeRepository;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.stat.dto.PrizeTrendResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DB 없이 가짜로 당첨금 추이의 범위 결정과 평균 계산을 확인한다. */
class PrizeTrendServiceTest {

    private final DrawRoundRepository drawRepo = mock(DrawRoundRepository.class);
    private final DrawPrizeRepository prizeRepo = mock(DrawPrizeRepository.class);
    private PrizeTrendService service;

    @BeforeEach
    void setUp() {
        service = new PrizeTrendService(drawRepo, prizeRepo);
    }

    private static DrawRound round(int no) {
        return new DrawRound(no, LocalDate.of(2026, 1, 1).plusWeeks(no), List.of(1, 2, 3, 4, 5, 6), 7, "TEST");
    }

    private static BusinessException reject(Runnable r) {
        return assertThrows(BusinessException.class, r::run);
    }

    @Test
    void 범위를_안_주면_최근_20회다() {
        when(drawRepo.findTopByOrderByRoundNoDesc()).thenReturn(Optional.of(round(30)));
        when(drawRepo.findByRoundNoBetweenOrderByRoundNoAsc(11, 30)).thenReturn(List.of());
        when(prizeRepo.findByRoundNoBetweenOrderByRoundNoAscWinningRankAsc(11, 30)).thenReturn(List.of());

        PrizeTrendResponse r = service.prizes(null, null);

        assertEquals(11, r.fromRound());
        assertEquals(30, r.toRound());
        verify(drawRepo).findByRoundNoBetweenOrderByRoundNoAsc(11, 30);
    }

    @Test
    void 평균은_당첨금이_있는_회차만으로_계산한다() {
        when(drawRepo.findByRoundNoBetweenOrderByRoundNoAsc(1, 3)).thenReturn(List.of(round(1), round(2), round(3)));
        when(prizeRepo.findByRoundNoBetweenOrderByRoundNoAscWinningRankAsc(1, 3)).thenReturn(List.of(
                new DrawPrize(1, 1, 2_000_000_000L, 10),
                new DrawPrize(1, 2, 50_000_000L, 4),
                new DrawPrize(2, 1, 1_000_000_000L, 5)));       // 3회차는 당첨금 정보 없음

        PrizeTrendResponse r = service.prizes(1, 3);

        assertEquals(3, r.roundCount());
        assertEquals(2, r.rounds().get(0).prizes().size());
        assertEquals(1, r.rounds().get(1).prizes().size());
        assertTrue(r.rounds().get(2).prizes().isEmpty());       // 정보 없는 회차도 줄은 나온다
        assertEquals(2, r.averages().size());                    // 1·2등만 값이 있다
        PrizeTrendResponse.RankAverage first = r.averages().get(0);
        assertEquals(1, first.rank());
        assertEquals(7.5, first.averageWinnerCount());           // (10+5)/2
        assertEquals(1_500_000_000L, first.averagePrizeAmount());
        assertEquals(4.0, r.averages().get(1).averageWinnerCount());
    }

    @Test
    void 회차_범위는_둘_다_줘야_한다() {
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.prizes(1, null)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.prizes(null, 5)).getErrorCode());
    }

    @Test
    void 잘못된_범위는_거절한다() {
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.prizes(5, 3)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.prizes(0, 3)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.prizes(1, 201)).getErrorCode());   // 201회
    }

    @Test
    void 정확히_200회까지는_허용한다() {
        when(drawRepo.findByRoundNoBetweenOrderByRoundNoAsc(1, 200)).thenReturn(List.of());
        when(prizeRepo.findByRoundNoBetweenOrderByRoundNoAscWinningRankAsc(1, 200)).thenReturn(List.of());

        assertEquals(200, service.prizes(1, 200).toRound());
    }

    @Test
    void 회차_데이터가_없으면_503() {
        when(drawRepo.findTopByOrderByRoundNoDesc()).thenReturn(Optional.empty());

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, reject(() -> service.prizes(null, null)).getErrorCode());
    }
}
