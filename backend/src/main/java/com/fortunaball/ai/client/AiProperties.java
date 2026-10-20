package com.fortunaball.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * AI 설정. application.yml 의 fortuna.ai.* 를 읽는다. (값을 안 적으면 기본값)
 * 📚 배운 내용: @ConfigurationProperties
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - apiKey 는 .env 의 AI_API_KEY 에서 온다. 이 파일·application.yml 에 키를 직접 쓰지 않는다.
 *  - 비용은 모두 USD(달러)다. 가격표는 모델마다 달라서 모델을 바꾸면 아래 4개 가격도 같이 바꾼다.
 *  - 한도 숫자는 시연용 기본값이다. 실서비스에서는 월 예산에서 거꾸로 계산해 올린다.
 *  - record 의 toString() 은 모든 값을 찍는다. 키가 로그에 새지 않도록 toString() 을 직접 만들어 키를 가렸다.
 *
 * @param apiKey                      AI 접속 키 (비어 있으면 AI 기능 전체가 "이용 불가(503)")
 * @param baseUrl                     AI 서버 주소
 * @param classifierModel             분류용 모델 이름
 * @param answerModel                 답변용 모델 이름
 * @param timeoutSeconds              응답을 기다리는 최대 시간(초). 넘으면 실패로 처리
 * @param dailyCallLimit              서비스 전체 하루 AI 호출 상한 (서울 날짜 기준)
 * @param monthlyCostLimitUsd         서비스 전체 월 비용 상한 (USD, 서울 기준 매월 1일부터)
 * @param userDailyLimit              비회원 1명(guest_token)이 하루에 쓸 수 있는 무료 AI 횟수
 * @param ipDailyLimit                접속지(IP) 1곳이 하루에 쓸 수 있는 AI 횟수 (guest_token 을 바꿔 우회하는 것을 막는 안전망)
 * @param perMinuteLimit              1분에 보낼 수 있는 AI 요청 횟수 (자동 호출 방지용. 사용자(guest_token)별·접속지(IP)별로 각각 센다.
 *                                    일반 사용자는 못 느끼게 크게 두었다. 한 접속지를 여럿이 쓰는 발표장·학원에서도 걸리지 않게 하려는 값)
 * @param classifierInputPerMillionUsd  분류용 모델 입력 100만 토큰당 가격
 * @param classifierOutputPerMillionUsd 분류용 모델 출력 100만 토큰당 가격
 * @param answerInputPerMillionUsd      답변용 모델 입력 100만 토큰당 가격
 * @param answerOutputPerMillionUsd     답변용 모델 출력 100만 토큰당 가격
 */
@ConfigurationProperties(prefix = "fortuna.ai")
public record AiProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://api.openai.com/v1") String baseUrl,
        @DefaultValue("gpt-6-luna") String classifierModel,
        @DefaultValue("gpt-6-luna") String answerModel,
        @DefaultValue("20") int timeoutSeconds,
        @DefaultValue("300") int dailyCallLimit,
        @DefaultValue("10") BigDecimal monthlyCostLimitUsd,
        @DefaultValue("5") int userDailyLimit,
        @DefaultValue("20") int ipDailyLimit,
        @DefaultValue("50") int perMinuteLimit,
        @DefaultValue("0.10") BigDecimal classifierInputPerMillionUsd,
        @DefaultValue("0.50") BigDecimal classifierOutputPerMillionUsd,
        @DefaultValue("0.10") BigDecimal answerInputPerMillionUsd,
        @DefaultValue("0.50") BigDecimal answerOutputPerMillionUsd) {

    /** 키가 들어 있는지 (값은 확인만 하고 밖으로 내보내지 않는다) */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 역할에 맞는 모델 이름 */
    public String modelFor(AiRole role) {
        return role == AiRole.CLASSIFIER ? classifierModel : answerModel;
    }

    @Override
    public String toString() {
        return "AiProperties[apiKey=" + (hasApiKey() ? "****" : "(없음)")
                + ", baseUrl=" + baseUrl
                + ", classifierModel=" + classifierModel
                + ", answerModel=" + answerModel + ", ...]";
    }
}
