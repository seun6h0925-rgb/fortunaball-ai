package com.fortunaball.admin.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 조회한 기간의 AI 사용 요약.
 * callCount: 실제로 AI 를 부른 횟수(성공+실패). blockedCount 는 AI 를 부르지 않고 막은 횟수라 따로 센다.
 * policyCounts: 정책 코드별 막힌 건수 (주제 이탈, 한도 초과 등)
 */
public record AiLogSummary(long callCount, long failedCount, long blockedCount, BigDecimal totalCostUsd,
                           List<PolicyCount> policyCounts) {

    public record PolicyCount(String policyCode, long count) {
    }
}
