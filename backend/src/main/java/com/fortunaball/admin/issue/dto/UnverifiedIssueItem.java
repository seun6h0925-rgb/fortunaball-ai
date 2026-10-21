package com.fortunaball.admin.issue.dto;

import java.time.LocalDateTime;

/**
 * 검증이 끝나지 않았거나 실패한 발급 건 (API-48).
 * status: PENDING(검증 대기) / ERROR(검증 실패). reason 은 화면에 그대로 보여 줄 설명이다.
 */
public record UnverifiedIssueItem(Long issueId, String issueCode, int targetRoundNo, int gameCount,
                                  long verifiedGames, long errorGames, String status, String statusLabel,
                                  String reason, LocalDateTime createdAt) {
}
