package com.fortunaball.verify.domain;

/**
 * 검증 한 건의 처리 결과. DB winning_verification.result_status 값과 같다.
 *
 * ⚠ issue.domain.VerificationStatus(내역 화면용 PENDING/VERIFIED/ERROR "발급 건 전체 상태")와는 다른 것이다.
 *   - 이 enum: 게임 한 줄을 검증한 결과 (VERIFIED = 계산 완료, ERROR = 계산 실패)
 *   - VerificationStatus: 발급 한 건 안의 여러 게임을 모아서 보여주는 상태
 */
public enum VerificationResultStatus {
    VERIFIED,
    ERROR
}
