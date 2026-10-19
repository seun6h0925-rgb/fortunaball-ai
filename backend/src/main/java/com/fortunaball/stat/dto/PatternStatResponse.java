package com.fortunaball.stat.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 통계 패턴 조회 응답 (API-44).
 *  - targetRound : 지금 번호 생성의 대상 회차 (예: 1247)
 *  - basisRound  : 통계를 계산한 기준 회차 = 이미 결과가 저장된 가장 최근 회차 (예: 1246)
 *  - status      : 통계 상태 코드 (PREPARING / WAITING / CONFIRMED), statusLabel 은 화면 표시용 (사전 분석 / 결과 대기 / 확정)
 */
public record PatternStatResponse(
        int targetRound,
        int basisRound,
        String status,
        String statusLabel,
        int period,
        LocalDateTime generatedAt,
        List<PatternCardResponse> patterns
) {
}
