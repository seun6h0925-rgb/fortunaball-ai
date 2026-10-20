package com.fortunaball.stat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.stat.domain.StatPeriod;
import com.fortunaball.stat.dto.DrawNumbers;
import com.fortunaball.stat.dto.StatSummaryResponse;
import com.fortunaball.stat.dto.StatSummaryResponse.Band;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 통계 요약 계산 확인. 3~4회차짜리 작은 이력으로 만들어 손으로 계산한 값과 비교한다.
 *   1회차: 1 2 3 4 5 6        (홀3 저6 합21  연속쌍5 AC0 소수3[2,3,5])
 *   2회차: 10 20 30 40 41 42  (홀1 저2 합183 연속쌍2 AC6 소수1[41])
 *   3회차: 7 14 21 28 35 42   (홀3 저3 합147 연속쌍0 AC0 소수1[7])
 */
class SummaryCalculatorTest {

    private static final DrawNumbers R1 = new DrawNumbers(1, List.of(1, 2, 3, 4, 5, 6));
    private static final DrawNumbers R2 = new DrawNumbers(2, List.of(10, 20, 30, 40, 41, 42));
    private static final DrawNumbers R3 = new DrawNumbers(3, List.of(7, 14, 21, 28, 35, 42));
    private static final DrawNumbers R4 = new DrawNumbers(4, List.of(2, 13, 22, 33, 43, 45));

    private static StatSummaryResponse all3() {
        return SummaryCalculator.summarize(List.of(R1, R2, R3), StatPeriod.ALL, "CONFIRMED", "확정");
    }

    private static int count(List<Band> bands, String label) {
        return bands.stream().filter(b -> b.label().equals(label)).findFirst().orElseThrow().count();
    }

    @Test
    void 기본_정보와_구간_회차() {
        StatSummaryResponse r = all3();

        assertEquals(3, r.basisRound());
        assertEquals(3, r.toRound());
        assertEquals(1, r.fromRound());
        assertEquals(3, r.roundCount());
        assertEquals("ALL", r.period());
        assertEquals("CONFIRMED", r.status());
    }

    @Test
    void 홀짝은_번호_개수_비율과_조합별_회차수를_준다() {
        StatSummaryResponse.Split s = all3().oddEven();

        assertEquals(7, s.firstTotal());          // 홀수 3+1+3
        assertEquals(11, s.secondTotal());
        assertEquals(38.9, s.firstPercent());     // 7/18
        assertEquals(61.1, s.secondPercent());
        assertEquals(2, s.combos().get("3:3"));   // 1·3회차
        assertEquals(1, s.combos().get("1:5"));   // 2회차
        assertEquals(0, s.combos().get("6:0"));
        assertEquals(List.of("6:0", "5:1", "4:2", "3:3", "2:4", "1:5", "0:6"), List.copyOf(s.combos().keySet()));
    }

    @Test
    void 저고는_22이하가_저다() {
        StatSummaryResponse.Split s = all3().highLow();

        assertEquals(11, s.firstTotal());         // 저 6+2+3
        assertEquals(7, s.secondTotal());
        assertEquals(1, s.combos().get("6:0"));
        assertEquals(1, s.combos().get("2:4"));
        assertEquals(1, s.combos().get("3:3"));
    }

    @Test
    void 합계는_평균_최소_최대_구간을_준다() {
        StatSummaryResponse.Sum sum = all3().sum();

        assertEquals(117.0, sum.average());       // (21+183+147)/3
        assertEquals(21, sum.min());
        assertEquals(183, sum.max());
        assertEquals(6, sum.bands().size());
        assertEquals(1, count(sum.bands(), "~99"));
        assertEquals(0, count(sum.bands(), "100~119"));
        assertEquals(1, count(sum.bands(), "140~159"));
        assertEquals(1, count(sum.bands(), "180~"));
        assertEquals(3, sum.series().size());
        assertEquals(147, sum.series().get(2).sum());
    }

    @Test
    void 합계_구간_경계값이_올바른_구간에_들어간다() {
        // 합계 99 / 100 / 119 / 120 / 179 / 180
        List<DrawNumbers> h = List.of(
                new DrawNumbers(1, List.of(1, 2, 3, 4, 44, 45)),        // 99
                new DrawNumbers(2, List.of(1, 2, 3, 5, 44, 45)),        // 100
                new DrawNumbers(3, List.of(15, 16, 17, 18, 19, 34)),    // 119
                new DrawNumbers(4, List.of(15, 16, 17, 18, 19, 35)),    // 120
                new DrawNumbers(5, List.of(1, 4, 42, 43, 44, 45)),      // 179
                new DrawNumbers(6, List.of(1, 5, 42, 43, 44, 45)));     // 180

        List<Band> bands = SummaryCalculator.summarize(h, StatPeriod.ALL, null, null).sum().bands();

        assertEquals(1, count(bands, "~99"));
        assertEquals(2, count(bands, "100~119"));
        assertEquals(1, count(bands, "120~139"));
        assertEquals(0, count(bands, "140~159"));
        assertEquals(1, count(bands, "160~179"));
        assertEquals(1, count(bands, "180~"));
    }

    @Test
    void 구간별_번호_개수() {
        List<Band> s = all3().sections();

        assertEquals(List.of("1~10", "11~20", "21~30", "31~40", "41~45"), s.stream().map(Band::label).toList());
        assertEquals(8, count(s, "1~10"));
        assertEquals(2, count(s, "11~20"));
        assertEquals(3, count(s, "21~30"));
        assertEquals(2, count(s, "31~40"));
        assertEquals(3, count(s, "41~45"));
    }

    @Test
    void 연속수는_이웃한_쌍_개수로_센다() {
        StatSummaryResponse.Consecutive c = all3().consecutive();

        assertEquals(2, c.drawsWithConsecutive());     // 1·2회차
        assertEquals(66.7, c.percent());
        assertEquals(Map.of("0", 1, "1", 0, "2", 1, "3+", 1), c.pairDistribution());   // 3회차 0쌍, 2회차 2쌍, 1회차 5쌍
    }

    @Test
    void 끝수_분포() {
        List<Band> d = all3().lastDigits();

        assertEquals(10, d.size());
        assertEquals(4, count(d, "0"));
        assertEquals(3, count(d, "1"));
        assertEquals(3, count(d, "2"));
        assertEquals(0, count(d, "9"));
        assertEquals(18, d.stream().mapToInt(Band::count).sum());
    }

    @Test
    void AC값은_서로_다른_차이의_개수에서_5를_뺀다() {
        assertEquals(0, SummaryCalculator.acValue(R1));   // 차이 {1,2,3,4,5}
        assertEquals(6, SummaryCalculator.acValue(R2));   // 차이 11종
        assertEquals(0, SummaryCalculator.acValue(R3));   // 7의 배수 5종
        StatSummaryResponse.Ac ac = all3().ac();
        assertEquals(2.0, ac.average());
        assertEquals(11, ac.distribution().size());
        assertEquals(2, count(ac.distribution(), "0"));
        assertEquals(1, count(ac.distribution(), "6"));
    }

    @Test
    void 소수_개수_분포() {
        StatSummaryResponse.Prime p = all3().prime();

        assertEquals(3, SummaryCalculator.primeCount(R1));
        assertEquals(1.7, p.average());               // (3+1+1)/3
        assertEquals(2, count(p.distribution(), "1"));
        assertEquals(1, count(p.distribution(), "3"));
        assertEquals(14, p.primeNumbers().size());
    }

    @Test
    void 핫넘버는_횟수_내림차순_같으면_번호_오름차순이다_전체는_증감이_없다() {
        List<StatSummaryResponse.HotNumber> hot = all3().hotNumbers();

        assertEquals(5, hot.size());
        assertEquals(42, hot.get(0).number());     // 2·3회차에 나온 유일한 번호
        assertEquals(2, hot.get(0).count());
        assertEquals(List.of(1, 2, 3, 4), hot.subList(1, 5).stream().map(StatSummaryResponse.HotNumber::number).toList());
        assertTrue(hot.stream().allMatch(h -> h.change() == null));
    }

    @Test
    void 핫넘버_증감은_바로_앞_같은_길이_구간과_비교한다() {
        // 최근 2회(3·4회차) vs 그 앞 2회(1·2회차)
        StatSummaryResponse r = SummaryCalculator.summarize(List.of(R1, R2, R3, R4), new StatPeriod(2), null, null);

        List<StatSummaryResponse.HotNumber> hot = r.hotNumbers();
        assertEquals(2, r.roundCount());
        assertEquals(3, r.fromRound());
        assertEquals(2, hot.get(0).number());
        assertEquals(0, hot.get(0).change());      // 2번: 앞 구간 1회 → 이번 1회
        assertEquals(7, hot.get(1).number());
        assertEquals(1, hot.get(1).change());      // 7번: 앞 구간 0회 → 이번 1회
    }

    @Test
    void 앞_구간이_모자라면_증감은_null이다() {
        StatSummaryResponse r = SummaryCalculator.summarize(List.of(R1, R2, R3), new StatPeriod(2), null, null);

        assertTrue(r.hotNumbers().stream().allMatch(h -> h.change() == null));
    }

    @Test
    void 이력이_구간보다_짧으면_있는_만큼만_계산한다() {
        StatSummaryResponse r = SummaryCalculator.summarize(List.of(R1, R2, R3), new StatPeriod(100), null, null);

        assertEquals(3, r.roundCount());
        assertEquals(1, r.fromRound());
    }

    @Test
    void 장기_미출현은_전체_이력_기준이다() {
        StatSummaryResponse.Absence a = all3().absence();

        assertEquals(3, a.maxAbsent());               // 한 번도 안 나온 번호 = 이력 회차수
        assertEquals(7.5, a.averageInterval());
        assertEquals(0, a.over10Count());
        assertEquals(8, a.numbers().size());
        assertEquals(8, a.numbers().get(0).number());    // 안 나온 번호 중 가장 작은 번호
        assertNull(a.numbers().get(0).lastRound());
    }

    @Test
    void 최근_회차는_최신순_최대_5개이고_상세값을_준다() {
        List<StatSummaryResponse.RoundDetail> recent = all3().recentRounds();

        assertEquals(List.of(3, 2, 1), recent.stream().map(StatSummaryResponse.RoundDetail::roundNo).toList());
        StatSummaryResponse.RoundDetail d = recent.get(0);   // 3회차
        assertEquals(3, d.odd());
        assertEquals(3, d.even());
        assertEquals(3, d.low());
        assertEquals(3, d.high());
        assertEquals(147, d.sum());
        assertEquals(List.of(1, 1, 2, 1, 1), d.sectionCounts());
        assertEquals(0, d.consecutivePairs());
        assertEquals(0, d.ac());
        assertEquals(1, d.primeCount());
    }

    @Test
    void 이력이_비어_있으면_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> SummaryCalculator.summarize(List.of(), StatPeriod.ALL, null, null));
    }
}
