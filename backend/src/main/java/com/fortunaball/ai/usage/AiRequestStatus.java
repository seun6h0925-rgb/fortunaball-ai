package com.fortunaball.ai.usage;

/**
 * AI 요청 처리 결과. DB ai_usage_log.request_status 값과 같은 이름이다. (CHECK 제약)
 */
public enum AiRequestStatus {
    /** AI 호출 성공 */
    SUCCESS,
    /** AI 를 불렀지만 실패 (시간 초과, 오류 응답 등) */
    FAILED,
    /** AI 를 부르지 않고 막음 (정책 고정 응답, 한도·횟수 초과) */
    BLOCKED
}
