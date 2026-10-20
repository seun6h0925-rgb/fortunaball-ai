package com.fortunaball.ai.usage;

import com.fortunaball.ai.AiTestFixtures;
import com.fortunaball.ai.client.AiProperties;
import com.fortunaball.ai.client.AiRole;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 비용 추정 공식 확인: (입력 × 입력가격 + 출력 × 출력가격) ÷ 100만, 소수 6자리 */
class AiCostCalculatorTest {

    @Test
    void 입력_출력_토큰으로_비용을_계산한다() {
        AiCostCalculator calc = new AiCostCalculator(AiTestFixtures.props("k"));
        // 입력 1000 × 0.10 + 출력 200 × 0.50 = 100 + 100 = 200 → 200 / 1,000,000 = 0.000200
        assertEquals(new BigDecimal("0.000200"), calc.estimate(AiRole.ANSWER, 1000, 200));
    }

    @Test
    void 토큰이_0이면_비용도_0이다() {
        AiCostCalculator calc = new AiCostCalculator(AiTestFixtures.props("k"));
        assertEquals(new BigDecimal("0.000000"), calc.estimate(AiRole.CLASSIFIER, 0, 0));
    }

    @Test
    void 음수_토큰은_0으로_본다() {
        AiCostCalculator calc = new AiCostCalculator(AiTestFixtures.props("k"));
        assertEquals(new BigDecimal("0.000000"), calc.estimate(AiRole.ANSWER, -5, -5));
    }

    @Test
    void 분류용과_답변용은_각자_가격표를_쓴다() {
        AiProperties props = new AiProperties("k", "u", "m1", "m2", 20, 300, new BigDecimal("10"), 5, 20, 5,
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("10"), new BigDecimal("20"));
        AiCostCalculator calc = new AiCostCalculator(props);
        // 분류: 1,000,000 × 1 + 1,000,000 × 2 = 3,000,000 / 1,000,000 = 3
        assertEquals(new BigDecimal("3.000000"), calc.estimate(AiRole.CLASSIFIER, 1_000_000, 1_000_000));
        // 답변: 1,000,000 × 10 + 1,000,000 × 20 = 30
        assertEquals(new BigDecimal("30.000000"), calc.estimate(AiRole.ANSWER, 1_000_000, 1_000_000));
    }

    @Test
    void 소수_여섯자리까지_반올림한다() {
        AiCostCalculator calc = new AiCostCalculator(AiTestFixtures.props("k"));
        // 입력 1 × 0.10 = 0.1 → 0.1 / 1,000,000 = 0.0000001 → 6자리 반올림 0.000000
        assertEquals(new BigDecimal("0.000000"), calc.estimate(AiRole.ANSWER, 1, 0));
        // 입력 5 × 0.10 = 0.5 → 0.0000005 → 반올림(HALF_UP) 0.000001
        assertEquals(new BigDecimal("0.000001"), calc.estimate(AiRole.ANSWER, 5, 0));
    }
}
