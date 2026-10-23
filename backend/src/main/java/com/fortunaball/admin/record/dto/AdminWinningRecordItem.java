package com.fortunaball.admin.record.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 우리 서비스 당첨 기록 한 건 (API-26). recordId 는 winning_verification.id 이며, 공개 여부 변경(API-27)에 그대로 쓴다.
 * 발급코드·게스트 전체 값·발급 시각은 관리자만 보는 화면이라 그대로 보여 준다. (누구 건인지 확실히 구분하려는 것)
 */
public record AdminWinningRecordItem(Long recordId, int roundNo, int winningRank, int matchCount,
                                     boolean bonusMatched, boolean publicVisible, String issueCode,
                                     String guestToken, LocalDateTime issuedAt,
                                     List<Integer> numbers, LocalDateTime verifiedAt) {
}
