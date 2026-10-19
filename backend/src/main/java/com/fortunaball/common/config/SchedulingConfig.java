package com.fortunaball.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스프링 스케줄링(@Scheduled) 켜기.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 이 설정이 있어야 @Scheduled 가 붙은 메서드가 정해진 시각에 자동으로 실행된다.
 *    없으면 어노테이션이 있어도 영원히 실행되지 않는다. (CacheConfig 의 @EnableCaching 과 같은 원리)
 *  - 스케줄 메서드는 기본적으로 스레드 1개로 차례대로 돈다. 한 작업이 길어지면 뒤 작업은 기다린다.
 *
 * 📚 배운 내용: @Configuration, @EnableScheduling, @Scheduled(cron)
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
