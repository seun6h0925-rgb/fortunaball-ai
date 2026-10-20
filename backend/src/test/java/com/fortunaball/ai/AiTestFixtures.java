package com.fortunaball.ai;

import com.fortunaball.ai.client.AiProperties;

import java.math.BigDecimal;

/**
 * AI 테스트에서 설정 값을 쉽게 만드는 도구. (운영 코드가 아니다)
 * AiProperties 는 값이 14개라서 테스트마다 다 적으면 읽기 어려워 한 곳에 모았다.
 */
public final class AiTestFixtures {

    private AiTestFixtures() {
    }

    /** 키만 정하고 나머지는 기본값(시연용 한도) */
    public static AiProperties props(String apiKey) {
        return props(apiKey, 300, "10", 5, 20, 5);
    }

    /** 한도 값을 직접 정한다 */
    public static AiProperties props(String apiKey, int dailyCallLimit, String monthlyCostLimitUsd,
                                     int userDailyLimit, int ipDailyLimit, int perMinuteLimit) {
        return new AiProperties(apiKey, "https://api.openai.com/v1", "gpt-6-luna", "gpt-6-luna", 20,
                dailyCallLimit, new BigDecimal(monthlyCostLimitUsd), userDailyLimit, ipDailyLimit, perMinuteLimit,
                new BigDecimal("0.10"), new BigDecimal("0.50"), new BigDecimal("0.10"), new BigDecimal("0.50"));
    }

    /** 분류용·답변용 모델 이름을 바꾼 설정 (실제 호출 확인용) */
    public static AiProperties propsWithModels(String apiKey, String classifierModel, String answerModel) {
        return new AiProperties(apiKey, "https://api.openai.com/v1", classifierModel, answerModel, 30,
                300, new BigDecimal("10"), 5, 20, 5,
                new BigDecimal("0.10"), new BigDecimal("0.50"), new BigDecimal("0.10"), new BigDecimal("0.50"));
    }
}
