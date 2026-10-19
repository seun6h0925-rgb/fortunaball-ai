package com.fortunaball.verify.domain;

/**
 * 번호 한 게임의 당첨 비교 결과.
 *
 * @param matchCount   당첨번호 6개 중 맞힌 개수 (0~6)
 * @param bonusMatched 보너스 번호 일치 여부 (의미가 있는 건 5개를 맞힌 경우뿐이다)
 * @param rank         등수 1~5, 낙첨이면 null  (DB winning_verification.winning_rank 와 같은 규칙)
 */
public record MatchResult(int matchCount, boolean bonusMatched, Integer rank) {

    /** 5등 안에 들었는지 (낙첨이면 false) */
    public boolean isWinning() {
        return rank != null;
    }
}
