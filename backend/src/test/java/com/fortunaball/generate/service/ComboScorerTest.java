package com.fortunaball.generate.service;

import com.fortunaball.generate.domain.Combo;
import com.fortunaball.stat.dto.DrawNumbers;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboScorerTest {

    static List<DrawNumbers> randomHistory(int size, long seed) {
        Random r = new Random(seed);
        List<DrawNumbers> list = new ArrayList<>();
        for (int round = 1; round <= size; round++) {
            List<Integer> nums = new ArrayList<>();
            while (nums.size() < 6) {
                int n = 1 + r.nextInt(45);
                if (!nums.contains(n)) {
                    nums.add(n);
                }
            }
            nums.sort(null);
            list.add(new DrawNumbers(round, nums));
        }
        return list;
    }

    @Test
    void 생일번호만_있으면_감점() {
        assertEquals(GenerationRule.PENALTY_BIRTHDAY, ComboScorer.penalty(Combo.of(List.of(2, 9, 14, 20, 27, 31))));
        assertEquals(0, ComboScorer.penalty(Combo.of(List.of(2, 9, 14, 20, 27, 32))));
    }

    @Test
    void 연속번호_3개_이상이면_감점() {
        assertTrue(ComboScorer.hasRunOfThree(List.of(7, 8, 9, 20, 30, 40)));
        assertEquals(false, ComboScorer.hasRunOfThree(List.of(7, 8, 10, 11, 30, 40)));   // 2개씩 두 쌍은 해당 없음
    }

    @Test
    void 등차수열_4개_이상이면_감점() {
        assertTrue(ComboScorer.hasArithmeticRun(List.of(5, 10, 15, 20, 33, 41)));
        assertEquals(false, ComboScorer.hasArithmeticRun(List.of(5, 10, 15, 21, 33, 41)));
        // 5,10,15,20,25,30 : 등차 + 생일번호
        assertEquals(GenerationRule.PENALTY_ARITHMETIC + GenerationRule.PENALTY_BIRTHDAY,
                ComboScorer.penalty(Combo.of(List.of(5, 10, 15, 20, 25, 30))));
    }

    @Test
    void 분포는_실제_회차의_대부분을_통과시킨다() {
        List<DrawNumbers> history = randomHistory(100, 7);
        DrawProfile profile = DrawProfile.from(history);
        long pass = history.stream().filter(d -> profile.accepts(Combo.of(d.numbers()))).count();
        // 통과 범위가 5%~95% 이므로 지표 6개를 함께 보더라도 실제 회차의 상당수가 통과해야 한다
        assertTrue(pass >= 60, "통과한 회차 수: " + pass);
        int[] sumBand = profile.bandOf(DrawProfile.Metric.SUM);
        assertTrue(sumBand[0] < sumBand[1]);
    }

    @Test
    void 점수는_감점이_없는_쪽이_높다() {
        DrawProfile profile = DrawProfile.from(randomHistory(100, 7));
        Combo skew = Combo.of(List.of(5, 10, 15, 20, 25, 30));
        Combo normal = Combo.of(List.of(4, 13, 22, 29, 36, 44));
        assertTrue(ComboScorer.score(normal, profile) > ComboScorer.score(skew, profile));
    }
}
