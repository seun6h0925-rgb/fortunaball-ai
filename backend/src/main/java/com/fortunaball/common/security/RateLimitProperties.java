package com.fortunaball.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 조회 횟수 제한 설정. application.yml 의 fortuna.rate-limit.* 를 읽는다. (값을 안 적으면 기본값)
 * 📚 배운 내용: @ConfigurationProperties
 *
 * @param issueLookupPerMinute 발급코드 상세 조회(API-05)를 한 접속지에서 1분에 부를 수 있는 횟수
 * @param issueResultPerMinute 당첨 검증 결과 조회(API-06)를 한 접속지에서 1분에 부를 수 있는 횟수
 */
@ConfigurationProperties(prefix = "fortuna.rate-limit")
public record RateLimitProperties(
        @DefaultValue("30") int issueLookupPerMinute,
        @DefaultValue("30") int issueResultPerMinute) {
}
