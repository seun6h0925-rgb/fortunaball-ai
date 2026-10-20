package com.fortunaball.record.domain;

/**
 * 익명 당첨 인증의 검수 상태. anonymous_verification.review_status 값이 그대로 저장된다.
 *
 * ⚠ 반려(REJECTED) 건만 "다시 등록할 수 있다". 나머지(대기·승인·숨김)는 발급코드/용지를 계속 점유한다.
 *    이 규칙은 DB 의 보조 칸(active_issue_id, active_ticket_hash)이 지킨다. (docs/db/schema.sql 참고)
 */
public enum ReviewStatus {
    PENDING,    // 검수 대기 (등록 직후)
    APPROVED,   // 승인
    REJECTED,   // 반려 (같은 발급코드·용지로 다시 등록 가능)
    HIDDEN      // 승인했다가 숨김
}
