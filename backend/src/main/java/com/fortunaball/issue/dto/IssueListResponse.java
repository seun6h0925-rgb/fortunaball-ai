package com.fortunaball.issue.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이 기기의 발급 내역 목록 (API-42). 최신 발급이 먼저 나온다.
 *
 * @param items      이번 쪽의 발급 건들 (번호 내용은 상세 조회에서 본다)
 * @param page       현재 쪽 (0부터)
 * @param size       한 쪽에 담긴 최대 개수
 * @param totalCount 전체 발급 건수
 * @param totalPages 전체 쪽 수
 */
public record IssueListResponse(
        List<Item> items,
        int page,
        int size,
        long totalCount,
        int totalPages) {

    public record Item(
            String issueCode,
            int targetRound,
            int gameCount,
            LocalDateTime issuedAt,
            String patternCode,
            String patternName,
            String verificationStatus,
            String verificationLabel) {
    }
}
