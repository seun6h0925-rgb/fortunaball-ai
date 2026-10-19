package com.fortunaball.payout.domain;

/**
 * 세금 계산 결과 (금액은 모두 원 단위, 소수점 없음)
 *
 * @param prizeAmount 세전 당첨금
 * @param taxFree     비과세 여부 (비과세면 세금 0)
 * @param incomeTax   소득세
 * @param localTax    지방소득세 (소득세의 10%)
 * @param netAmount   세후 실수령액 (당첨금 - 소득세 - 지방소득세)
 * @param rateLabel   화면 표시용 적용 세율 문구
 */
public record TaxResult(long prizeAmount, boolean taxFree, long incomeTax, long localTax, long netAmount, String rateLabel) {

    public long totalTax() {
        return incomeTax + localTax;
    }
}
