package com.fortunaball.store.dto;

import java.time.LocalDate;
import java.util.List;

/** API-14 판매점 상세 조회 응답. 판매점 정보 + 회차별 당첨 이력(최근순) + 데이터 기준일. */
public record StoreDetailResponse(
        StoreItem store,
        List<StoreWinHistory> history,
        LocalDate baseDate,
        Integer statsFromRound) {
}
