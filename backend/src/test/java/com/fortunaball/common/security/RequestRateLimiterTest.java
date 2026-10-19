package com.fortunaball.common.security;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestRateLimiterTest {

    private final AtomicLong nanos = new AtomicLong();
    private RequestRateLimiter limiter;

    @BeforeEach
    void setUp() {
        Ticker ticker = nanos::get;
        limiter = new RequestRateLimiter(ticker);
    }

    private void passSeconds(long seconds) {
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(seconds));
    }

    @Test
    void 한도까지는_통과하고_넘으면_429() {
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> limiter.check("issue-lookup", "1.1.1.1", 3));
        }
        BusinessException e = assertThrows(BusinessException.class, () -> limiter.check("issue-lookup", "1.1.1.1", 3));
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, e.getErrorCode());
    }

    @Test
    void 접속지와_용도가_다르면_따로_센다() {
        limiter.check("issue-lookup", "1.1.1.1", 1);
        assertDoesNotThrow(() -> limiter.check("issue-lookup", "2.2.2.2", 1));   // 다른 접속지
        assertDoesNotThrow(() -> limiter.check("other", "1.1.1.1", 1));          // 다른 용도
        assertThrows(BusinessException.class, () -> limiter.check("issue-lookup", "1.1.1.1", 1));
    }

    @Test
    void 구간이_지나면_다시_처음부터_센다() {
        limiter.check("issue-lookup", "1.1.1.1", 1);
        assertThrows(BusinessException.class, () -> limiter.check("issue-lookup", "1.1.1.1", 1));
        passSeconds(59);
        assertThrows(BusinessException.class, () -> limiter.check("issue-lookup", "1.1.1.1", 1));   // 아직 같은 구간
        passSeconds(2);                                                                              // 61초 후
        assertDoesNotThrow(() -> limiter.check("issue-lookup", "1.1.1.1", 1));
    }

    @Test
    void 막힌_뒤에도_요청을_계속_보내면_구간이_늘어나지_않는다() {
        limiter.check("issue-lookup", "1.1.1.1", 1);
        for (int i = 0; i < 20; i++) {
            passSeconds(2);                                                                          // 40초 동안 계속 시도
            assertThrows(BusinessException.class, () -> limiter.check("issue-lookup", "1.1.1.1", 1));
        }
        passSeconds(21);                                                                             // 처음 요청 후 61초
        assertDoesNotThrow(() -> limiter.check("issue-lookup", "1.1.1.1", 1));
    }
}
