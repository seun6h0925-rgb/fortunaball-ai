package com.fortunaball.issue.dto;

/**
 * 발급 내역 지우기 응답 (API-67)
 *
 * @param issueCode 이 기기 목록에서 지운 발급 코드 (기록 자체는 서버에 남아 있다)
 */
public record IssueHideResponse(String issueCode) {
}
