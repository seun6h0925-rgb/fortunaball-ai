package com.fortunaball.record.dto;

import java.time.LocalDateTime;

/**
 * 당첨 기록 한 건 (API-17). 사진·발급코드·발급 번호는 내려주지 않는다. (공개용)
 *
 * @param recordId 당첨 기록 번호(winning_verification.id)
 * @param comment  승인된 인증의 사용자 한마디. 없으면 null
 */
public record WinningRecordItem(Long recordId, int roundNo, int rank, int matchCount, boolean bonusMatched,
                                String comment, LocalDateTime verifiedAt) {
}
