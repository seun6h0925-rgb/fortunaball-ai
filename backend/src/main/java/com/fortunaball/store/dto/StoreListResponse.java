package com.fortunaball.store.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * API-12 지역별 판매점 조회 응답.
 *
 * @param baseDate       데이터 기준일 (최신 확정 회차의 추첨일). 화면의 "데이터 기준일" 안내에 쓴다
 * @param statsFromRound 당첨 횟수를 센 첫 회차 (예: 262). "262회 이후 기준" 안내에 쓴다
 */
public record StoreListResponse(
        List<StoreItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        LocalDate baseDate,
        Integer statsFromRound) {
}
