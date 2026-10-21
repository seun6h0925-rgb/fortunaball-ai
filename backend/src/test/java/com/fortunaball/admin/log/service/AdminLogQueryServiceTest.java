package com.fortunaball.admin.log.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdminLogQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 15, 0);

    @Test
    void 오래_전에_시작해서_안_끝난_실행은_중단으로_본다() {
        assertTrue(AdminLogQueryService.isInterrupted("RUNNING", NOW.minusHours(3), NOW.minusHours(1)));
    }

    @Test
    void 방금_시작한_실행이나_끝난_실행은_중단이_아니다() {
        assertFalse(AdminLogQueryService.isInterrupted("RUNNING", NOW.minusMinutes(5), NOW.minusHours(1)));
        assertFalse(AdminLogQueryService.isInterrupted("SUCCESS", NOW.minusHours(5), NOW.minusHours(1)));
        assertFalse(AdminLogQueryService.isInterrupted("RUNNING", null, NOW.minusHours(1)));
    }
}
