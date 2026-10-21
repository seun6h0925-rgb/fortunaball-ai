package com.fortunaball.admin.ai.dto;

import com.fortunaball.admin.common.AdminPage;

/** API-32 응답: 기록 목록 + 기간 요약 + 현재 한도 상태 */
public record AiLogResponse(AdminPage<AiLogItem> logs, AiLogSummary summary, AiLimitStatus limit) {
}
