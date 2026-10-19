package com.fortunaball.issue.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 발급 내역 상세 (API-05)
 *
 * @param issueCode          발급 코드
 * @param targetRound        대상 회차
 * @param issuedAt           발급 시각 (한국 시간)
 * @param gameCount          게임 수
 * @param patternCode        생성 때 고른 패턴 (없으면 null)
 * @param patternName        패턴 한글 이름 (없으면 null)
 * @param games              발급 번호들 (게임 순서대로, 각각 오름차순 6개)
 * @param verificationStatus PENDING / VERIFIED / ERROR (추첨 전에는 PENDING)
 * @param verificationLabel  "검증 대기" / "검증 완료" / "검증 실패"
 */
public record IssueDetailResponse(
        String issueCode,
        int targetRound,
        LocalDateTime issuedAt,
        int gameCount,
        String patternCode,
        String patternName,
        List<Game> games,
        String verificationStatus,
        String verificationLabel) {

    /** gameOrder 는 1부터 시작 */
    public record Game(int gameOrder, List<Integer> numbers) {
    }
}
