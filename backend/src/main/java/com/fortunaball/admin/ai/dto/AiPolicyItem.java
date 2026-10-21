package com.fortunaball.admin.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자용 AI 입력 정책 한 건 (API-54, 55, 56).
 * featureScope: ANALYST / DREAM / ALL, actionType: FIXED_RESPONSE / AI_REVIEW. 키워드는 사용 중지된 것도 모두 담는다.
 */
public record AiPolicyItem(long id, String policyCode, String policyName, String featureScope, String policyType,
                           String actionType, int priority, String responseMessage, boolean active,
                           List<AiPolicyKeywordItem> keywords, LocalDateTime updatedAt) {
}
