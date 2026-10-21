package com.fortunaball.admin.ai.dto;

import java.util.List;

/** AI 입력 정책 등록 요청 (API-55). 정책 코드·기능 범위·유형·처리 방식은 등록 때만 정하고 이후 바꿀 수 없다. */
public record AiPolicyCreateRequest(String policyCode, String policyName, String featureScope, String policyType,
                                    String actionType, Integer priority, String responseMessage,
                                    List<AiPolicyKeywordInput> keywords) {
}
