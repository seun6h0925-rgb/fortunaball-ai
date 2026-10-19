package com.fortunaball.draw.service;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 회차 캐시 비우기 담당. 새 회차가 저장되거나 재수집으로 회차 데이터가 바뀌면 호출한다.
 * (캐시 이름은 DrawQueryService 에 정의된 상수를 쓴다.)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - "최신 회차" 캐시는 새 회차가 들어오면 반드시 비워야 한다. 안 비우면 예전 회차가 계속 최신으로 나온다.
 *  - 이미 저장된 회차(과거)는 보통 바뀌지 않지만, 재수집(API-23) 때는 그 회차만 비운다.
 */
@Component
public class DrawCacheEvictor {

    private final CacheManager cacheManager;

    public DrawCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /** 새 회차가 저장됐을 때: 최신 회차 캐시와 해당 회차 캐시를 비운다. */
    public void evictRound(int roundNo) {
        Cache latest = cacheManager.getCache(DrawQueryService.CACHE_LATEST);
        if (latest != null) {
            latest.clear();
        }
        Cache byRound = cacheManager.getCache(DrawQueryService.CACHE_BY_ROUND);
        if (byRound != null) {
            byRound.evict(roundNo);
        }
    }
}
