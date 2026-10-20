package com.fortunaball.record.dto;

import java.time.LocalDateTime;

/** 검수 처리 결과 (API-29). */
public record VerificationReviewResponse(Long verificationId, String reviewStatus, boolean publicVisible,
                                         LocalDateTime reviewedAt) {
}
