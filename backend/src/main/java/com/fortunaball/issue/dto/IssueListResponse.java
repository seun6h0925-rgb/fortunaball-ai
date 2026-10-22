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
 *
 * Item.firstGame : 그 발급 건의 첫 번째 게임 번호(오름차순 6개). 목록 카드에 번호 한 줄을 보여 주기 위한 값이다.
 *                  전체 게임은 상세 조회(API-05)에서 본다. (목록에서 건마다 상세를 부르면 조회 횟수 제한에 걸리기 때문)
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
            String verificationLabel,
            List<Integer> firstGame) {
    }
}
