package com.fortunaball.admin.record.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 우리 서비스 당첨 기록 한 건 (API-26). recordId 는 winning_verification.id 이며, 공개 여부 변경(API-27)에 그대로 쓴다.
 * 발급코드는 관리자만 보는 화면이라 그대로 보여 준다.
 */
public record AdminWinningRecordItem(Long recordId, int roundNo, int winningRank, int matchCount,
                                     boolean bonusMatched, boolean publicVisible, String issueCode,
                                     List<Integer> numbers, LocalDateTime verifiedAt) {
}
