package com.fortunaball.ai.policy;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 정책을 DB 에서 읽어 1분 동안 기억해 두는 곳. 질문마다 DB 를 읽지 않으려는 것이다.
 * 관리자가 정책을 고치면 최대 1분 안에 반영된다.
 *
 * ⚠ DB 읽기에 실패하면 서비스가 멈추지 않도록 직전에 읽어 둔 정책(없으면 빈 목록)을 그대로 쓴다.
 */
@Service
public class CachedAiPolicySource implements AiPolicySource {

    private static final Logger log = LoggerFactory.getLogger(CachedAiPolicySource.class);
    static final long TTL_MILLIS = 60_000L;

    private final AiPolicyRepository repository;
    private volatile List<AiPolicy> cache = List.of();
    private volatile long loadedAt = 0L;

    public CachedAiPolicySource(AiPolicyRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AiPolicy> activePolicies() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > TTL_MILLIS) {
            refresh(now);
        }
        return cache;
    }

    private synchronized void refresh(long now) {
        if (now - loadedAt <= TTL_MILLIS) {
            return;   // 다른 요청이 방금 갱신했다
        }
        try {
            cache = repository.loadActive();
        } catch (RuntimeException e) {
            log.warn("AI 예외처리 정책을 읽지 못해 직전 값을 계속 사용합니다: {}", e.getClass().getSimpleName());
        }
        loadedAt = now;
    }
}
