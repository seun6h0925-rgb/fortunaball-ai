package com.fortunaball.record.dto;

/**
 * 익명 당첨 인증 등록 결과 (API-16).
 *
 * @param verificationId 인증 번호
 * @param reviewStatus   검수 상태. 등록 직후에는 항상 PENDING(검수 대기)
 * @param message        화면에 보여줄 안내 문구
 */
public record ProofRegisterResponse(Long verificationId, String reviewStatus, String message) {
}
