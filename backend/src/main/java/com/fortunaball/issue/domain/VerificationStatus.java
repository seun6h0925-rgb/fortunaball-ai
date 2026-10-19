package com.fortunaball.issue.domain;

/**
 * 발급 건의 당첨 검증 상태 (화면 표시용). DB 에 저장하지 않고 winning_verification 행 수로 계산한다.
 *  - PENDING  : 검증 대기 (추첨 전이거나 아직 검증하지 않음. 검증 결과 행이 하나도 없을 때도 포함)
 *  - VERIFIED : 검증 완료 (모든 게임의 검증 결과가 있고 오류가 없음)
 *  - ERROR    : 검증 실패 (게임 중 하나라도 result_status = ERROR)
 */
public enum VerificationStatus {
    PENDING("검증 대기"),
    VERIFIED("검증 완료"),
    ERROR("검증 실패");

    private final String label;

    VerificationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * @param totalGames    발급 건의 전체 게임 수
     * @param verifiedGames 검증 완료(VERIFIED) 게임 수
     * @param errorGames    검증 오류(ERROR) 게임 수
     */
    public static VerificationStatus of(long totalGames, long verifiedGames, long errorGames) {
        if (errorGames > 0) {
            return ERROR;
        }
        if (totalGames > 0 && verifiedGames == totalGames) {
            return VERIFIED;
        }
        return PENDING;
    }
}
