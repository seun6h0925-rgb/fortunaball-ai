package com.fortunaball.admin.ai.service;

import com.fortunaball.admin.ai.dto.AiLimitStatus;
import com.fortunaball.admin.common.AdminQuery;
import com.fortunaball.ai.client.AiProperties;
import com.fortunaball.ai.usage.AiUsageLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지금 AI 한도를 얼마나 썼는지 계산한다. (오늘 호출 수 / 일일 한도, 이번 달 비용 / 월 한도)
 * 한도 검사(AiUsageGuard)와 같은 기준(서울 시간, 모델 이름이 있는 호출만 카운트)으로 센다.
 */
@Component
public class AiLimitStatusProvider {

    private final AiUsageLogRepository usageLogRepository;
    private final AiProperties properties;

    public AiLimitStatusProvider(AiUsageLogRepository usageLogRepository, AiProperties properties) {
        this.usageLogRepository = usageLogRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public AiLimitStatus current() {
        LocalDateTime todayStart = AdminQuery.today().atStartOfDay();
        LocalDateTime monthStart = AdminQuery.today().withDayOfMonth(1).atStartOfDay();
        long todayCalls = usageLogRepository.countCallsSince(todayStart);
        BigDecimal monthCost = usageLogRepository.sumCostSince(monthStart);
        return build(properties.dailyCallLimit(), todayCalls, properties.monthlyCostLimitUsd(),
                monthCost == null ? BigDecimal.ZERO : monthCost);
    }

    /** 숫자 계산만 따로 뺐다. (테스트하기 쉽게) */
    static AiLimitStatus build(int dailyLimit, long todayCalls, BigDecimal monthlyLimit, BigDecimal monthCost) {
        double dailyPercent = dailyLimit <= 0 ? 0 : round1(todayCalls * 100.0 / dailyLimit);
        double monthPercent = monthlyLimit == null || monthlyLimit.signum() <= 0 ? 0
                : monthCost.multiply(BigDecimal.valueOf(100)).divide(monthlyLimit, 1, RoundingMode.HALF_UP).doubleValue();
        return new AiLimitStatus(dailyLimit, todayCalls, dailyPercent, monthlyLimit, monthCost, monthPercent);
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
