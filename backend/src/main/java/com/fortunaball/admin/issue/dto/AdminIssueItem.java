package com.fortunaball.admin.issue.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 번호 발급 기록 한 건 (API-24).
 * guestToken 은 앞 6글자만 보여 준다. (검색할 때는 전체 값을 보내야 한다)
 * verificationStatus: PENDING(검증 대기) / VERIFIED(검증 완료) / ERROR(검증 실패)
 */
public record AdminIssueItem(Long issueId, String issueCode, String guestToken, int targetRoundNo, int gameCount,
                             String issueType, String verificationStatus, String verificationLabel,
                             LocalDateTime createdAt, List<List<Integer>> games) {
}
