package com.fortunaball.admin.ai.dto;

import java.math.BigDecimal;

/**
 * AI 일일 호출·월 비용 한도 사용 현황. (대시보드 API-22, AI 로그 API-32 가 함께 쓴다)
 * 퍼센트는 소수 첫째 자리까지다. 한도가 0 이하로 설정돼 있으면 0 으로 보여 준다.
 */
public record AiLimitStatus(int dailyCallLimit, long todayCalls, double dailyUsagePercent,
                            BigDecimal monthlyCostLimitUsd, BigDecimal monthCostUsd, double monthUsagePercent) {
}
