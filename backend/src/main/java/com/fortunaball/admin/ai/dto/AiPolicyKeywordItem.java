package com.fortunaball.admin.ai.dto;

/** 정책에 붙은 키워드 한 개. matchType 은 EXACT / CONTAINS. */
public record AiPolicyKeywordItem(long id, String keyword, String matchType, boolean active) {
}
