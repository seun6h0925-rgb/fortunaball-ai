package com.fortunaball.record.dto;

import java.time.LocalDateTime;

/**
 * 관리자 인증 목록의 한 줄 (API-28).
 *
 * @param roundNo  용지(발급) 회차
 * @param bestRank 이 발급 건의 가장 높은 당첨 등수. 아직 검증 결과가 없으면 null
 * @param photoUrl 사진을 볼 주소 (API-62). 관리자만 열 수 있다.
 */
public record AdminVerificationItem(Long id, String issueCode, int roundNo, Integer bestRank, String comment,
                                    String reviewStatus, boolean publicVisible, String adminComment,
                                    LocalDateTime createdAt, LocalDateTime reviewedAt, String photoUrl) {
}
