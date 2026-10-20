package com.fortunaball.record.dto;

import com.fortunaball.record.domain.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자 검수 요청 (API-29).
 *
 * @param reviewStatus 바꿀 상태: APPROVED(승인) / REJECTED(반려) / HIDDEN(숨김). PENDING 은 허용하지 않는다.
 * @param adminComment 관리자 코멘트. 반려·숨김일 때는 사유로 필수, 승인일 때는 선택. 500자까지.
 */
public record VerificationReviewRequest(
        @NotNull ReviewStatus reviewStatus,
        @Size(max = 500) String adminComment) {
}
