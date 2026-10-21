package com.fortunaball.admin.action.dto;

import java.time.LocalDateTime;

/** 회차 통계 재계산 결과 (API-47). statStatus 는 통계 상태(PREPARING / WAITING / CONFIRMED). */
public record RecalculateResult(int roundNo, int basisRoundNo, String statStatus, LocalDateTime generatedAt) {
}
