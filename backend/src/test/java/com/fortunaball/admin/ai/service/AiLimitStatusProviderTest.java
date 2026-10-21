package com.fortunaball.admin.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fortunaball.admin.ai.dto.AiLimitStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AiLimitStatusProviderTest {

    @Test
    void 사용률을_소수_첫째_자리까지_계산한다() {
        AiLimitStatus status = AiLimitStatusProvider.build(300, 45, new BigDecimal("10"), new BigDecimal("2.5"));

        assertEquals(15.0, status.dailyUsagePercent());
        assertEquals(25.0, status.monthUsagePercent());
        assertEquals(45, status.todayCalls());
    }

    @Test
    void 한도가_0이면_사용률은_0으로_보여_준다() {
        AiLimitStatus status = AiLimitStatusProvider.build(0, 10, BigDecimal.ZERO, new BigDecimal("1"));

        assertEquals(0.0, status.dailyUsagePercent());
        assertEquals(0.0, status.monthUsagePercent());
    }

    @Test
    void 한도를_넘으면_100이_넘게_보여_준다() {
        AiLimitStatus status = AiLimitStatusProvider.build(100, 150, new BigDecimal("10"), new BigDecimal("12.34"));

        assertEquals(150.0, status.dailyUsagePercent());
        assertEquals(123.4, status.monthUsagePercent());
    }
}
