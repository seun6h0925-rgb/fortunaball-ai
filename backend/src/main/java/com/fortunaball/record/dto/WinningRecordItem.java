package com.fortunaball.record.dto;

import java.time.LocalDateTime;

/**
 * 당첨 기록 한 건 (API-17, 공개용).
 * 발급코드는 그대로, 게스트는 앞 4자리만 보여 준다. 발급 시각은 초 단위까지 그대로 내려준다.
 * (번호 자체와 인증 사진은 여기서 내려주지 않는다. 번호는 발급코드로 조회한다.)
 *
 * @param recordId     당첨 기록 번호(winning_verification.id)
 * @param issueCode    발급코드 (개인정보가 아니라서 가리지 않는다)
 * @param guestMasked  발급한 게스트. 예: guest3f2a****
 * @param issuedAt     번호를 발급(확정)한 시각 (number_issue.created_at)
 * @param comment      승인된 인증의 사용자 한마디. 없으면 null
 * @param verifiedAt   당첨 검증이 끝난 시각
 */
public record WinningRecordItem(Long recordId, int roundNo, int rank, int matchCount, boolean bonusMatched,
                                String issueCode, String guestMasked, LocalDateTime issuedAt,
                                String comment, LocalDateTime verifiedAt) {
}
