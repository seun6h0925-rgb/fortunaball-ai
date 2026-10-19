package com.fortunaball.payout.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.domain.DrawPrize;
import com.fortunaball.draw.repository.DrawPrizeRepository;
import com.fortunaball.payout.dto.PayoutCalculateRequest;
import com.fortunaball.payout.dto.PayoutCalculateResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DB 없이 가짜 저장소(Mockito)로 세금 계산 흐름을 확인한다. */
class PayoutServiceTest {

    private final DrawPrizeRepository prizeRepo = mock(DrawPrizeRepository.class);
    private final PayoutService service = new PayoutService(prizeRepo);

    private void stubPrizes() {
        when(prizeRepo.findByRoundNoOrderByWinningRankAsc(1246)).thenReturn(List.of(
                new DrawPrize(1246, 1, 2_000_000_000L, 5),
                new DrawPrize(1246, 3, 1_500_000L, 2000),
                new DrawPrize(1246, 5, 5_000L, 100000)));
    }

    @Test
    void 일등은_세금이_계산된다() {
        stubPrizes();
        PayoutCalculateResponse r = service.calculate(new PayoutCalculateRequest(1246, 1));
        assertEquals("1등", r.rankLabel());
        assertEquals(5, r.winnerCount());
        assertEquals(2_000_000_000L, r.prizeAmount());
        assertEquals(627_000_000L, r.totalTax());
        assertEquals(1_373_000_000L, r.netAmount());
        assertFalse(r.taxFree());
        assertEquals("2026-10-19", r.policyDate());
    }

    @Test
    void 삼등_오등은_비과세() {
        stubPrizes();
        PayoutCalculateResponse third = service.calculate(new PayoutCalculateRequest(1246, 3));
        assertTrue(third.taxFree());
        assertEquals(1_500_000L, third.netAmount());
        assertEquals(5_000L, service.calculate(new PayoutCalculateRequest(1246, 5)).netAmount());
    }

    @Test
    void 그_회차에_그_등수_당첨금이_없으면_404() {
        stubPrizes();                                                        // 1246회차에는 2등 데이터가 없다
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.calculate(new PayoutCalculateRequest(1246, 2))).getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.calculate(new PayoutCalculateRequest(9999, 1))).getErrorCode());
    }

    @Test
    void 회차나_등수가_이상하면_400이고_조회하지_않는다() {
        PayoutCalculateRequest[] bad = {
                null, new PayoutCalculateRequest(null, 1), new PayoutCalculateRequest(0, 1),
                new PayoutCalculateRequest(1246, null), new PayoutCalculateRequest(1246, 0), new PayoutCalculateRequest(1246, 6)};
        for (PayoutCalculateRequest request : bad) {
            assertEquals(ErrorCode.INVALID_INPUT,
                    assertThrows(BusinessException.class, () -> service.calculate(request)).getErrorCode());
        }
        verifyNoInteractions(prizeRepo);
    }
}
