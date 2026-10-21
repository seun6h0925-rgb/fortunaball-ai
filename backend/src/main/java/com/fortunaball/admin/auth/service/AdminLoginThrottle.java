package com.fortunaball.admin.auth.service;

import com.fortunaball.admin.auth.config.AdminProperties;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 관리자 로그인 실패 횟수 제한. 비밀번호를 계속 바꿔 대입하는 공격을 막는다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 접속지(IP) 기준으로 센다. 아이디 기준으로 세면 남이 일부러 틀려서 관리자를 잠글 수 있다.
 *  - 첫 실패부터 lockMinutes 가 지나면 횟수가 저절로 지워진다. (구간은 첫 실패 때 시작)
 *  - 로그인에 성공하면 그 접속지의 횟수를 지운다.
 *  - 서버를 다시 켜면 초기화된다. 프록시 뒤에서는 접속지가 프록시 주소로 보일 수 있다. (배포 때 점검: 미룬 항목)
 */
@Component
public class AdminLoginThrottle {

    static final int MAX_KEYS = 10_000;

    private final int maxFailures;
    private final Cache<String, AtomicInteger> failures;

    @Autowired
    public AdminLoginThrottle(AdminProperties props) {
        this(props, Ticker.systemTicker());
    }

    AdminLoginThrottle(AdminProperties props, Ticker ticker) {
        this.maxFailures = props.maxLoginFailures();
        this.failures = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(props.lockMinutes()))
                .maximumSize(MAX_KEYS)
                .ticker(ticker)
                .build();
    }

    /** 이미 너무 많이 틀렸으면 429. 비밀번호 확인 전에 먼저 부른다. */
    public void checkAllowed(String clientIp) {
        AtomicInteger count = failures.getIfPresent(clientIp);
        if (count != null && count.get() >= maxFailures) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "로그인에 여러 번 실패했어요. 잠시 후 다시 시도해 주세요.", "관리자 로그인 실패 횟수 초과");
        }
    }

    public void recordFailure(String clientIp) {
        failures.get(clientIp, k -> new AtomicInteger()).incrementAndGet();
    }

    public void clear(String clientIp) {
        failures.invalidate(clientIp);
    }
}
