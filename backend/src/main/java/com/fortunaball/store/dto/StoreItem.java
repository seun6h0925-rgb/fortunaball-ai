package com.fortunaball.store.dto;

/**
 * 판매점 한 곳 (목록·내 주변·상세에서 공통으로 쓴다).
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 당첨 횟수(firstAuto 등)는 CSV 에 있는 "262회 이후" 기준이다. 응답의 statsFromRound 를 화면에 함께 적는다.
 *  - recentFirst, recentSecond 는 "최근 1년(설정값)" 안의 횟수이고, rookie 판정에 쓴다.
 *  - distanceMeters 는 내 주변 조회일 때만 값이 있고, 그 외에는 null 이다.
 *  - 2등은 자동·수동 구분 없이 횟수만 보여 준다 (second).
 */
public record StoreItem(
        long storeId,
        String name,
        String roadAddress,
        String jibunAddress,
        String sido,
        String sigungu,
        double latitude,
        double longitude,
        int firstAuto,
        int firstManual,
        int firstSemi,
        int firstTotal,
        int second,
        int totalWins,
        int recentFirst,
        int recentSecond,
        boolean rookie,
        Integer distanceMeters) {
}
