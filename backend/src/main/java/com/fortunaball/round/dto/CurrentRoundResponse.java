package com.fortunaball.round.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * GET /api/rounds/current 응답.
 *  - targetRound   : 지금 번호를 만들면 대상이 되는 회차
 *  - drawDate      : 그 회차의 추첨 예정일(토요일)
 *  - basisRound    : 통계 분석에 쓰는 가장 최근 확정(수집 완료) 회차
 *  - nextSwitchAt  : 다음 회차로 바뀌는 시각 (서울 시간, 토요일 20:00)
 *  - statStatus    : 내부 상태값 (PREPARING / WAITING / CONFIRMED)
 *  - statStatusLabel : 화면에 보이는 문구 ("준비 중" / "확정")
 */
public record CurrentRoundResponse(
        int targetRound,
        LocalDate drawDate,
        int basisRound,
        LocalDateTime nextSwitchAt,
        String statStatus,
        String statStatusLabel) {
}
