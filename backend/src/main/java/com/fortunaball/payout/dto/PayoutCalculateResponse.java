package com.fortunaball.payout.dto;

/**
 * 세금 계산 결과 (API-15). 금액은 모두 원 단위.
 *
 * @param roundNo        회차
 * @param rank           등수
 * @param rankLabel      "1등"~"5등"
 * @param winnerCount    그 등수 당첨자 수
 * @param prizeAmount    1인 세전 당첨금
 * @param taxFree        비과세 여부
 * @param incomeTax      소득세
 * @param localIncomeTax 지방소득세
 * @param totalTax       세금 합계
 * @param netAmount      세후 실수령액
 * @param rateLabel      적용 세율 문구
 * @param policyDate     세금 기준 확인일 (계산 기준일)
 * @param sourceName     기준 출처 이름
 * @param sourceUrl      공식 확인 링크
 * @param notice         참고용 안내 문구
 */
public record PayoutCalculateResponse(
        int roundNo,
        int rank,
        String rankLabel,
        int winnerCount,
        long prizeAmount,
        boolean taxFree,
        long incomeTax,
        long localIncomeTax,
        long totalTax,
        long netAmount,
        String rateLabel,
        String policyDate,
        String sourceName,
        String sourceUrl,
        String notice) {
}
