package com.fortunaball.admin.action.dto;

/**
 * 당첨 검증 재실행 결과 (API-25).
 * status: DONE / NOTHING_TO_DO(판정할 게임 없음) / ALREADY_RUNNING(이미 실행 중이라 건너뜀)
 */
public record VerifyResult(String status, int verified, int failed, String message) {
}
