package com.fortunaball.payout.dto;

/**
 * 세금 계산 요청 (API-15)
 * 숫자가 아닌 값이나 빠진 값도 우리 오류 형식(400)으로 돌려주려고 Integer 로 받아 서비스에서 직접 검사한다.
 *
 * @param roundNo 회차 번호
 * @param rank    등수 (1~5)
 */
public record PayoutCalculateRequest(Integer roundNo, Integer rank) {
}
