package com.fortunaball.generate.service;

import com.fortunaball.generate.domain.Combo;

import java.util.List;

/**
 * 조합의 점수를 매긴다. 점수 = 기본 100 + (중심 범위에 든 지표 수 × 5) - 쏠림 감점.
 *
 * 쏠림 감점 (사람들이 흔히 고르는 모양이라 "서비스 전체에서 같은 조합이 몰리는" 것을 줄이기 위한 것이다.
 * 당첨 확률과는 무관하다)
 *  - 생일 번호만(전부 1~31)           : -30
 *  - 연속 번호가 3개 이상 이어짐       : -25
 *  - 같은 간격으로 4개 이상 이어짐(등차) : -40
 */
public final class ComboScorer {

    private ComboScorer() {
    }

    public static int score(Combo combo, DrawProfile profile) {
        return GenerationRule.BASE_SCORE
                + profile.coreHits(combo) * GenerationRule.BONUS_PER_CORE_METRIC
                - penalty(combo);
    }

    /** 쏠림 감점의 합 (0 이면 쏠림 없음) */
    public static int penalty(Combo combo) {
        List<Integer> n = combo.numbers();
        int penalty = 0;
        if (n.get(5) <= GenerationRule.BIRTHDAY_MAX_NUMBER) {
            penalty += GenerationRule.PENALTY_BIRTHDAY;
        }
        if (hasRunOfThree(n)) {
            penalty += GenerationRule.PENALTY_RUN_OF_THREE;
        }
        if (hasArithmeticRun(n)) {
            penalty += GenerationRule.PENALTY_ARITHMETIC;
        }
        return penalty;
    }

    /** 1 씩 커지는 번호가 3개 이상 이어지는지 (예: 7, 8, 9) */
    static boolean hasRunOfThree(List<Integer> n) {
        for (int i = 0; i + 2 < n.size(); i++) {
            if (n.get(i + 1) - n.get(i) == 1 && n.get(i + 2) - n.get(i + 1) == 1) {
                return true;
            }
        }
        return false;
    }

    /** 같은 간격으로 4개가 이어지는지 (예: 5, 10, 15, 20) */
    static boolean hasArithmeticRun(List<Integer> n) {
        for (int i = 0; i + 3 < n.size(); i++) {
            int gap = n.get(i + 1) - n.get(i);
            if (n.get(i + 2) - n.get(i + 1) == gap && n.get(i + 3) - n.get(i + 2) == gap) {
                return true;
            }
        }
        return false;
    }
}
