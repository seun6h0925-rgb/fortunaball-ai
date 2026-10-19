package com.fortunaball.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cache 켜기. (요구사항 BE-10: 최신 회차·회차별 당첨번호·통계 결과를 캐시로 관리)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 이 설정이 있어야 @Cacheable / @CacheEvict 가 동작한다. 없으면 어노테이션이 있어도 아무 일도 안 일어난다.
 *  - 지금은 별도 라이브러리 없이 서버 메모리(기본 방식)에 저장한다. 서버를 다시 켜면 캐시는 비워진다.
 *  - 만료 시간(TTL)이 필요한 캐시(번호 생성 임시 결과 30분)를 만들 때는 그때 Caffeine 을 추가한다.
 *    (회차 데이터는 새 회차가 저장될 때 직접 비우므로 만료 시간이 필요 없다.)
 *
 * 📚 배운 내용: Spring 의 캐시 추상화(@EnableCaching, @Cacheable, @CacheEvict)
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
