package com.fortunaball.stat.service;

import com.fortunaball.stat.domain.PatternCode;
import com.fortunaball.stat.dto.DrawNumbers;
import com.fortunaball.stat.dto.PatternResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 패턴 기준과 계산 시험 (스프링·DB 없이 빠르게 실행됨) */
class PatternCalculatorTest {

    private final PatternCalculator calculator = new PatternCalculator();

    private static List<Integer> nums(Integer... n) {
        return List.of(n);
    }

    private static int hits(List<PatternResult> results, PatternCode code) {
        return results.stream().filter(r -> r.code() == code).findFirst().orElseThrow().hitCount();
    }

    // ---- 기준 경계값 ----

    @Test
    void 밸런스_믹스는_홀수_2에서_4개() {
        assertFalse(PatternRule.isBalanceMix(nums(1, 2, 4, 6, 8, 10)));   // 홀수 1개
        assertTrue(PatternRule.isBalanceMix(nums(1, 3, 4, 6, 8, 10)));    // 2개
        assertTrue(PatternRule.isBalanceMix(nums(1, 3, 5, 7, 8, 10)));    // 4개
        assertFalse(PatternRule.isBalanceMix(nums(1, 3, 5, 7, 9, 10)));   // 5개
    }

    @Test
    void 하이로우_믹스는_1에서_22가_2에서_4개() {
        assertFalse(PatternRule.isHighLowMix(nums(1, 30, 31, 32, 33, 34)));   // 낮은 번호 1개
        assertTrue(PatternRule.isHighLowMix(nums(1, 22, 31, 32, 33, 34)));    // 2개 (22 포함)
        assertTrue(PatternRule.isHighLowMix(nums(1, 2, 3, 22, 33, 34)));      // 4개
        assertFalse(PatternRule.isHighLowMix(nums(1, 2, 3, 4, 5, 34)));       // 5개
    }

    @Test
    void 센터_존은_16에서_30이_4개_이상() {
        assertTrue(PatternRule.isCenterZone(nums(16, 17, 18, 30, 1, 2)));     // 4개 (16, 30 경계 포함)
        assertFalse(PatternRule.isCenterZone(nums(16, 17, 18, 31, 1, 2)));    // 31 은 밖이라 3개
        assertFalse(PatternRule.isCenterZone(nums(15, 17, 18, 19, 1, 2)));    // 15 는 밖이라 3개
    }

    @Test
    void 올_구간_스프레드는_5개_구간_중_4구간_이상() {
        assertTrue(PatternRule.isAllSectionSpread(nums(1, 10, 20, 30, 40, 41)));   // 5구간
        assertTrue(PatternRule.isAllSectionSpread(nums(1, 10, 20, 30, 31, 32)));   // 4구간
        assertFalse(PatternRule.isAllSectionSpread(nums(1, 10, 20, 21, 22, 23)));  // 3구간
        assertTrue(PatternRule.isAllSectionSpread(nums(9, 19, 29, 39, 45, 1)));    // 구간 끝 번호 경계
    }

    // ---- 계산 ----

    @Test
    void 같은_번호만_나온_20회는_패턴별로_정해진_횟수가_나온다() {
        List<DrawNumbers> history = new ArrayList<>();
        for (int r = 1; r <= 20; r++) {
            history.add(new DrawNumbers(r, nums(1, 2, 3, 4, 5, 6)));
        }
        List<PatternResult> results = calculator.calculate(history, 20);

        assertEquals(6, results.size());
        assertEquals(20, hits(results, PatternCode.HOT_NUMBER));          // 핫 번호 = 1~6 → 6개 모두 포함
        assertEquals(0, hits(results, PatternCode.WINTER_SLEEP));         // 앞 20회가 없어 세지 않음
        assertEquals(20, hits(results, PatternCode.BALANCE_MIX));         // 홀수 3개
        assertEquals(0, hits(results, PatternCode.HIGH_LOW_MIX));         // 낮은 번호 6개
        assertEquals(0, hits(results, PatternCode.CENTER_ZONE));
        assertEquals(0, hits(results, PatternCode.ALL_SECTION_SPREAD));
        assertEquals(20, results.get(0).windowSize());
    }

    @Test
    void 겨울잠_번호는_직전_20회에_없던_번호가_있을_때만_센다() {
        List<DrawNumbers> history = new ArrayList<>();
        for (int r = 1; r <= 20; r++) {
            history.add(new DrawNumbers(r, nums(1, 2, 3, 4, 5, 6)));
        }
        history.add(new DrawNumbers(21, nums(1, 2, 3, 4, 5, 7)));   // 7 은 앞 20회에 없음
        List<PatternResult> results = calculator.calculate(history, 20);

        // 창은 2~21회. 앞 20회가 모두 있는 회차는 21회뿐이고, 거기서 7 이 해당
        assertEquals(1, hits(results, PatternCode.WINTER_SLEEP));
    }

    @Test
    void 창은_가장_최근_period_개_회차다() {
        List<DrawNumbers> history = new ArrayList<>();
        for (int r = 1; r <= 30; r++) {
            // 앞 10회는 센터 존에 해당, 나머지 20회는 해당하지 않음
            history.add(new DrawNumbers(r, r <= 10 ? nums(16, 17, 18, 19, 1, 2) : nums(1, 2, 3, 4, 5, 6)));
        }
        List<PatternResult> results = calculator.calculate(history, 20);
        assertEquals(0, hits(results, PatternCode.CENTER_ZONE));   // 최근 20회(11~30회)에는 없음
    }

    @Test
    void 저장된_회차가_기간보다_적으면_있는_만큼만_센다() {
        List<DrawNumbers> history = List.of(
                new DrawNumbers(1, nums(1, 2, 3, 4, 5, 6)),
                new DrawNumbers(2, nums(1, 2, 3, 4, 5, 6)));
        List<PatternResult> results = calculator.calculate(history, 100);
        assertEquals(100, results.get(0).period());
        assertEquals(2, results.get(0).windowSize());
    }

    @Test
    void 허용하지_않는_기간은_실패한다() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of(), 30));
    }

    @Test
    void 당첨번호가_6개가_아니면_실패한다() {
        assertThrows(IllegalArgumentException.class, () -> new DrawNumbers(1, nums(1, 2, 3)));
    }
}
