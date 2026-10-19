package com.fortunaball.verify.domain;

import java.util.List;

/**
 * 당첨 등수 판정 규칙. DB·스프링 없이 숫자만 비교하는 순수 계산 클래스라서 테스트하기 쉽다.
 *
 * 등수 기준 (동행복권 규칙)
 *   1등: 6개 일치
 *   2등: 5개 일치 + 보너스 일치
 *   3등: 5개 일치
 *   4등: 4개 일치
 *   5등: 3개 일치
 *   그 외: 낙첨 (rank = null)
 *
 * 주의: 보너스 번호는 "5개 맞힌 경우의 2등/3등 구분"에만 쓴다.
 *       (보너스가 맞아도 4개 이하면 등수에 영향 없음)
 */
public final class WinningRankRule {

    private WinningRankRule() {
    }

    /**
     * @param issued  발급받은 번호 6개
     * @param winning 당첨번호 6개 (보너스 제외)
     * @param bonus   보너스 번호
     */
    public static MatchResult evaluate(List<Integer> issued, List<Integer> winning, int bonus) {
        if (issued == null || winning == null || issued.size() != 6 || winning.size() != 6) {
            throw new IllegalArgumentException("번호는 6개씩이어야 합니다.");
        }
        int matchCount = (int) issued.stream().filter(winning::contains).count();
        boolean bonusMatched = issued.contains(bonus);
        return new MatchResult(matchCount, bonusMatched, rankOf(matchCount, bonusMatched));
    }

    private static Integer rankOf(int matchCount, boolean bonusMatched) {
        return switch (matchCount) {
            case 6 -> 1;
            case 5 -> bonusMatched ? 2 : 3;
            case 4 -> 4;
            case 3 -> 5;
            default -> null;
        };
    }
}
