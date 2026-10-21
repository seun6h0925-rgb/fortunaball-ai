package com.fortunaball.admin.ai.dto;

/** 키워드 입력. matchType 을 안 보내면 CONTAINS, active 를 안 보내면 true. */
public record AiPolicyKeywordInput(String keyword, String matchType, Boolean active) {
}
