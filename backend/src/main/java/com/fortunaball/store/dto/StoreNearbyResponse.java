package com.fortunaball.store.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * API-13 내 주변 판매점 조회 응답. 가까운 순서이고, 각 항목에 distanceMeters 가 들어 있다.
 *
 * ⚠ 사용자의 위치(위도·경도)는 요청 처리에만 쓰고, 응답에도 다시 담지 않는다. 저장·로그·AI 전달도 하지 않는다.
 */
public record StoreNearbyResponse(
        List<StoreItem> items,
        int radiusMeters,
        LocalDate baseDate,
        Integer statsFromRound) {
}
