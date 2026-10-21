package com.fortunaball.admin.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fortunaball.admin.auth.config.AdminProperties;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Ticker;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminLoginThrottleTest {

    private AtomicLong now;
    private AdminLoginThrottle throttle;

    @BeforeEach
    void setUp() {
        now = new AtomicLong();
        Ticker ticker = now::get;
        throttle = new AdminLoginThrottle(new AdminProperties("", "", 3, 15), ticker);
    }

    @Test
    void 한도_전까지는_통과한다() {
        throttle.recordFailure("ip");
        throttle.recordFailure("ip");

        assertDoesNotThrow(() -> throttle.checkAllowed("ip"));
    }

    @Test
    void 한도에_닿으면_429() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("ip");
        }

        BusinessException e = assertThrows(BusinessException.class, () -> throttle.checkAllowed("ip"));
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, e.getErrorCode());
    }

    @Test
    void 정해진_시간이_지나면_다시_시도할_수_있다() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("ip");
        }
        now.addAndGet(TimeUnit.MINUTES.toNanos(16));

        assertDoesNotThrow(() -> throttle.checkAllowed("ip"));
    }

    @Test
    void 성공하면_횟수를_지운다() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("ip");
        }
        throttle.clear("ip");

        assertDoesNotThrow(() -> throttle.checkAllowed("ip"));
    }

    @Test
    void 접속지마다_따로_센다() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("ip-a");
        }

        assertDoesNotThrow(() -> throttle.checkAllowed("ip-b"));
    }
}
