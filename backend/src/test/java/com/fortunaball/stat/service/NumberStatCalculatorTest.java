package com.fortunaball.stat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fortunaball.stat.dto.DrawNumbers;
import com.fortunaball.stat.service.NumberStatCalculator.NumberStat;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 번호별 출현 횟수·미출현 기간 계산 확인. 손으로 센 값과 비교한다. */
class NumberStatCalculatorTest {

    private static final List<DrawNumbers> HISTORY = List.of(
            new DrawNumbers(1, List.of(1, 2, 3, 4, 5, 6)),
            new DrawNumbers(2, List.of(10, 20, 30, 40, 41, 42)),
            new DrawNumbers(3, List.of(7, 14, 21, 28, 35, 42)),
            new DrawNumbers(4, List.of(2, 13, 22, 33, 43, 45)));

    private static NumberStat of(List<NumberStat> list, int number) {
        return list.get(number - 1);
    }

    @Test
    void 항상_번호_1부터_45까지_45개를_준다() {
        List<NumberStat> r = NumberStatCalculator.calculate(HISTORY, 1, 4);
        assertEquals(45, r.size());
        assertEquals(1, r.get(0).number());
        assertEquals(45, r.get(44).number());
    }

    @Test
    void 구간_안의_출현_횟수만_센다() {
        List<NumberStat> r = NumberStatCalculator.calculate(HISTORY, 3, 4);   // 3~4회차만

        assertEquals(1, of(r, 2).count());     // 4회차에 출현 (1회차 출현은 구간 밖)
        assertEquals(0, of(r, 1).count());     // 1회차에만 나왔으므로 구간 안에서는 0
        assertEquals(1, of(r, 42).count());    // 2·3회차 출현 중 3회차만 구간 안
    }

    @Test
    void 미출현_기간은_구간이_아니라_전체_이력으로_계산한다() {
        List<NumberStat> r = NumberStatCalculator.calculate(HISTORY, 3, 4);

        assertEquals(0, of(r, 2).absentRounds());       // 기준 회차(4)에 출현
        assertEquals(4, of(r, 2).lastRound());
        assertEquals(3, of(r, 1).absentRounds());       // 1회차가 마지막 → 4 - 1
        assertEquals(1, of(r, 1).lastRound());
        assertEquals(1, of(r, 42).absentRounds());      // 마지막 출현 3회차
    }

    @Test
    void 한번도_안_나온_번호는_이력_회차수만큼_안_나온_것으로_본다() {
        NumberStat n8 = of(NumberStatCalculator.calculate(HISTORY, 1, 4), 8);

        assertNull(n8.lastRound());
        assertEquals(4, n8.absentRounds());
        assertEquals(0, n8.count());
    }

    @Test
    void 기준_회차보다_뒤_회차는_무시한다() {
        List<NumberStat> r = NumberStatCalculator.calculate(HISTORY, 1, 3);   // 4회차는 아직 없는 셈

        assertEquals(1, of(r, 2).count());         // 4회차의 2번은 세지 않는다
        assertEquals(1, of(r, 2).lastRound());
        assertEquals(2, of(r, 2).absentRounds());  // 3 - 1
        assertNull(of(r, 45).lastRound());         // 45는 4회차에만 나왔다
    }
}
