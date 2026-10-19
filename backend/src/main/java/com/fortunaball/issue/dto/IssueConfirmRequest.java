package com.fortunaball.issue.dto;

/**
 * 발급 확정 요청 (POST /api/issues 의 본문)
 *
 * @param confirmToken 번호 생성(API-03) 응답으로 받은 임시 확정 토큰
 */
public record IssueConfirmRequest(String confirmToken) {
}
