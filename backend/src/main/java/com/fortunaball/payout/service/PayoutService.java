package com.fortunaball.payout.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.domain.DrawPrize;
import com.fortunaball.draw.repository.DrawPrizeRepository;
import com.fortunaball.payout.domain.PayoutTaxCalculator;
import com.fortunaball.payout.domain.TaxResult;
import com.fortunaball.payout.dto.PayoutCalculateRequest;
import com.fortunaball.payout.dto.PayoutCalculateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 당첨금 세금 계산 (API-15). 읽기 전용이라 DB 를 바꾸지 않는다.
 * 📚 배운 내용: @Service, @Transactional(readOnly = true)
 *
 * 흐름: 입력 검사(회차·등수) → draw_prize 에서 그 회차 그 등수의 1인 당첨금 조회 → 세금 계산(PayoutTaxCalculator)
 *
 * ⚠ 금액·등수가 없거나 이상하면 계산하지 않고 오류로 돌려준다. (추측해서 계산하지 않는다)
 *    - 회차/등수 값이 잘못됨 → 400
 *    - 그 회차의 당첨금 데이터가 없음 (아직 추첨 전이거나 수집 전) → 404
 */
@Service
public class PayoutService {

    private static final String NOTICE = "참고용 계산이에요. 실제 수령액은 지급 기관의 기준에 따라 다를 수 있어요.";

    private final DrawPrizeRepository prizeRepo;

    public PayoutService(DrawPrizeRepository prizeRepo) {
        this.prizeRepo = prizeRepo;
    }

    @Transactional(readOnly = true)
    public PayoutCalculateResponse calculate(PayoutCalculateRequest request) {
        if (request == null || request.roundNo() == null || request.roundNo() < 1) {
            throw invalid("회차 번호를 확인해 주세요.", "roundNo=" + (request == null ? null : request.roundNo()));
        }
        if (request.rank() == null || request.rank() < 1 || request.rank() > 5) {
            throw invalid("등수는 1등부터 5등까지 선택할 수 있어요.", "rank=" + request.rank());
        }

        int round = request.roundNo();
        int rank = request.rank();
        DrawPrize prize = prizeRepo.findByRoundNoOrderByWinningRankAsc(round).stream()
                .filter(p -> p.getWinningRank() == rank)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "해당 회차의 당첨금 정보를 찾을 수 없어요.",
                        "draw_prize 없음: round=" + round + ", rank=" + rank));

        TaxResult tax = PayoutTaxCalculator.calculate(prize.getPrizeAmount());
        return new PayoutCalculateResponse(round, rank, rank + "등", prize.getWinnerCount(),
                tax.prizeAmount(), tax.taxFree(), tax.incomeTax(), tax.localTax(), tax.totalTax(), tax.netAmount(),
                tax.rateLabel(), PayoutTaxCalculator.POLICY_CHECKED_DATE,
                PayoutTaxCalculator.POLICY_SOURCE_NAME, PayoutTaxCalculator.POLICY_SOURCE_URL, NOTICE);
    }

    private static BusinessException invalid(String userMessage, String detail) {
        return new BusinessException(ErrorCode.INVALID_INPUT, userMessage, detail);
    }
}
