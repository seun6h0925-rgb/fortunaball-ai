package com.fortunaball.common.security;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 조회 횟수 제한(요청 횟수 초과 → 429). 서버 메모리에서 "1분 단위 고정 구간"으로 센다.
 * 발급코드를 무작위로 대입해 보는 공격을 막는 용도이고, 이후 당첨 검증(API-06)·AI 기능에서도 같은 부품을 쓴다.
 * 📚 배운 내용: @Component, 생성자 주입
 *
 * 동작
 *  - 구간(1분)은 해당 키의 첫 요청 때 시작한다. 구간 안에서 limit 번까지 허용하고, 넘으면 429.
 *  - 구간이 끝나면 저절로 지워져서 다시 처음부터 센다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 키는 "용도 + 접속지(IP)"로 만든다. (예: issue-lookup:127.0.0.1) 용도가 다르면 따로 센다.
 *  - 서버를 다시 켜면 횟수는 초기화된다. 서버가 여러 대가 되면 서버마다 따로 세므로 그때 Redis 등으로 바꾼다.
 *  - 프록시 뒤에 배포하면 접속지 IP 가 프록시 주소로 보일 수 있다. 배포 때 X-Forwarded-For 처리를 따로 점검한다. (지금은 로컬)
 *  - 키가 한없이 늘어나지 않게 MAX_KEYS 로 보관 개수를 제한한다.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RequestRateLimiter {

    static final int MAX_KEYS = 100_000;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Cache<String, AtomicInteger> counters;

    public RequestRateLimiter() {
        this(Ticker.systemTicker());
    }

    /** 테스트용: 시간을 직접 움직일 수 있는 Ticker 를 넣는다 */
    RequestRateLimiter(Ticker ticker) {
        // expireAfterWrite 는 "처음 넣은 시각" 기준이다. 카운터 값만 올릴 때는 만료 시각이 늘어나지 않으므로 고정 구간이 된다.
        this.counters = Caffeine.newBuilder().expireAfterWrite(WINDOW).maximumSize(MAX_KEYS).ticker(ticker).build();
    }

    /**
     * 요청 1회를 센다. 구간 안에서 limitPerMinute 번을 넘으면 429(BusinessException TOO_MANY_REQUESTS).
     *
     * @param purpose 용도 이름 (예: "issue-lookup")
     * @param client  접속지 구분값 (보통 IP)
     */
    public void check(String purpose, String client, int limitPerMinute) {
        String key = purpose + ":" + client;
        int count = counters.get(key, k -> new AtomicInteger()).incrementAndGet();
        if (count > limitPerMinute) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.",
                    "요청 횟수 초과: purpose=" + purpose + " limit/분=" + limitPerMinute + " count=" + count);
        }
    }
}
