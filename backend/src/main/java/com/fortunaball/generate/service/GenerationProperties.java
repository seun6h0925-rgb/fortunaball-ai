package com.fortunaball.generate.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 번호 생성 설정값. application.yml 의 fortuna.generate.* 를 읽는다. (값을 안 적으면 아래 기본값)
 * 📚 배운 내용: @ConfigurationProperties (설정 파일의 값을 객체로 묶어 받는 방법)
 *
 * @param ttlMinutes 임시 결과 보관 시간(분)
 * @param maxGames   한 번에 만들 수 있는 최대 게임 수
 */
@ConfigurationProperties(prefix = "fortuna.generate")
public record GenerationProperties(
        @DefaultValue("30") int ttlMinutes,
        @DefaultValue("50") int maxGames) {
}
