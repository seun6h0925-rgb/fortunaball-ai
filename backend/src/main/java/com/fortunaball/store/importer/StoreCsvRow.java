package com.fortunaball.store.importer;

import java.math.BigDecimal;

/**
 * 판매점 당첨 CSV(winning_stores_262_1246.csv)의 한 줄.
 * 한 줄 = "어느 회차에 어느 판매점에서 몇 등이 나왔는지" 한 건이다.
 * (같은 판매점이 같은 회차에 여러 번 나오면 그만큼 줄이 반복된다. 당첨자 수만큼 세어야 하므로 합치지 않는다.)
 *
 * @param round     당첨 회차 (CSV 의 draw)
 * @param rank      당첨 등수 (1 또는 2)
 * @param shopCode  동행복권 판매점 번호 (CSV 의 shop_id). 판매점을 구분하는 기준값
 * @param closed    폐점 여부 (CSV 의 status 가 "폐점" 이면 true)
 * @param winType   AUTO / MANUAL / SEMI_AUTO. 자료에 구분이 없으면 null (2등은 대부분 null)
 */
public record StoreCsvRow(
        int round,
        int rank,
        String shopCode,
        String storeName,
        boolean closed,
        String region,
        String roadAddress,
        String jibunAddress,
        String winType,
        BigDecimal latitude,
        BigDecimal longitude) {
}
