package com.fortunaball.admin.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 사용 기록 한 줄 (API-32). 질문·답변 원문과 사용자 식별값은 처음부터 저장하지 않는다. */
public record AiLogItem(Long id, String featureType, String requestStatus, String modelName, String policyCode,
                        int inputTokens, int outputTokens, Integer responseTimeMs, BigDecimal estimatedCostUsd,
                        String errorMessage, LocalDateTime createdAt) {
}
