package com.fortunaball.ai.usage;

import com.fortunaball.ai.client.AiProperties;
import com.fortunaball.ai.client.AiRole;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * AI 호출 1회의 비용을 추정한다. (USD) 공식: (입력토큰 × 입력가격 + 출력토큰 × 출력가격) ÷ 100만
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 어디까지나 "추정값"이다. 실제 청구액은 AI 회사 사이트에서 확인한다. 가격표는 application.yml 의 fortuna.ai.* 값이다.
 *  - 모델을 바꾸면서 가격을 안 바꾸면 월 비용 한도가 실제와 어긋난다.
 *  - ai_usage_log.estimated_cost 가 DECIMAL(10,6) 이라 소수 6자리까지만 저장한다.
 */
@Component
@EnableConfigurationProperties(AiProperties.class)
public class AiCostCalculator {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final AiProperties props;

    public AiCostCalculator(AiProperties props) {
        this.props = props;
    }

    public BigDecimal estimate(AiRole role, int inputTokens, int outputTokens) {
        BigDecimal inputPrice = role == AiRole.CLASSIFIER
                ? props.classifierInputPerMillionUsd() : props.answerInputPerMillionUsd();
        BigDecimal outputPrice = role == AiRole.CLASSIFIER
                ? props.classifierOutputPerMillionUsd() : props.answerOutputPerMillionUsd();
        return inputPrice.multiply(BigDecimal.valueOf(Math.max(0, inputTokens)))
                .add(outputPrice.multiply(BigDecimal.valueOf(Math.max(0, outputTokens))))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
