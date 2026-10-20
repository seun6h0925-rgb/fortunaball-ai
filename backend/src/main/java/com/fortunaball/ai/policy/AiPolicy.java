package com.fortunaball.ai.policy;

import java.util.List;

/**
 * AI 예외처리 정책 1건 (ai_input_policy + 연결된 ai_policy_keyword).
 *
 *  code      정책 코드 (DESPERATE_REQUEST, GUARANTEE_REQUEST, PURCHASE_PUSH, LOCATION_REQUIRED, OFF_TOPIC_MAP, DREAM_OFF_TOPIC 등)
 *  type      정책 분류 (policy_type)
 *  scope     적용 기능 (ALL / ANALYST / DREAM)
 *  priority  작을수록 먼저 검사한다
 *  message   걸렸을 때 사용자에게 보여 줄 고정 응답
 *  keywords  공백 없이 저장된 키워드
 */
public record AiPolicy(String code, String type, String scope, int priority, String message, List<String> keywords) {
}
