package com.fortunaball.stat.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class StatPeriodTest {

    @Test
    void 비우면_100회다() {
        assertEquals(100, StatPeriod.parse(null).rounds());
        assertEquals(100, StatPeriod.parse("  ").rounds());
    }

    @Test
    void 허용된_값만_받는다() {
        assertEquals(10, StatPeriod.parse("10").rounds());
        assertEquals(50, StatPeriod.parse(" 50 ").rounds());
        assertEquals("100", StatPeriod.parse("100").label());
    }

    @Test
    void ALL은_대소문자_상관없이_전체다() {
        assertTrue(StatPeriod.parse("all").isAll());
        assertTrue(StatPeriod.parse("ALL").isAll());
        assertEquals("ALL", StatPeriod.parse("All").label());
    }

    @Test
    void 허용되지_않는_값은_거절한다() {
        for (String bad : new String[] {"20", "0", "-1", "abc", "10.5", "1000"}) {
            BusinessException e = assertThrows(BusinessException.class, () -> StatPeriod.parse(bad), bad);
            assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
        }
    }
}
