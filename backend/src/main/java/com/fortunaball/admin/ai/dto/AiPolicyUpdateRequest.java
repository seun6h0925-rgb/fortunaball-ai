package com.fortunaball.admin.ai.dto;

import java.util.List;

/**
 * AI 입력 정책 수정 요청 (API-56). 보내지 않은(null) 항목은 바꾸지 않는다.
 * keywords 를 보내면 "이 목록이 전부"가 된다. (빠진 키워드는 삭제, 새 키워드는 추가, 같은 키워드는 일치 방식·사용 여부만 수정)
 */
public record AiPolicyUpdateRequest(String policyName, Integer priority, String responseMessage,
                                    List<AiPolicyKeywordInput> keywords, Boolean active) {
}
