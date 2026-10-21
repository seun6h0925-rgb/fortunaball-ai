package com.fortunaball.store.importer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 판매점별 당첨 횟수 집계 (테이블 store_win_summary 에 넣을 값).
 *
 * 집계 방법
 *  - 1등: 자동 / 수동 / 반자동 구분별 횟수
 *  - 2등: 횟수 (자료에 구분이 있어도 합쳐서 센다. 화면에는 2등은 횟수만 보여 주기로 했다)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 이 횟수는 "CSV 에 있는 회차 범위(262회~)" 안에서만 센 값이다. 그 이전 회차의 당첨은 자료가 없어 포함되지 않는다.
 *    화면에는 "262회 이후 기준" 이라는 기준을 함께 적어야 한다.
 *  - 같은 회차에 같은 판매점에서 2명이 1등이면 2번 센다 (CSV 에 줄이 2개이므로 그대로 센다).
 */
public final class StoreWinSummaryCalculator {

    public record Summary(int firstAuto, int firstManual, int firstSemi, int second) { }

    private StoreWinSummaryCalculator() { }

    /** 판매점 번호(shopCode) → 집계 */
    public static Map<String, Summary> calculate(List<StoreCsvRow> rows) {
        Map<String, int[]> counts = new HashMap<>();   // [자동, 수동, 반자동, 2등]
        for (StoreCsvRow row : rows) {
            int[] c = counts.computeIfAbsent(row.shopCode(), k -> new int[4]);
            if (row.rank() == 2) {
                c[3]++;
            } else if ("AUTO".equals(row.winType())) {
                c[0]++;
            } else if ("MANUAL".equals(row.winType())) {
                c[1]++;
            } else if ("SEMI_AUTO".equals(row.winType())) {
                c[2]++;
            } else {
                throw new IllegalArgumentException("1등인데 당첨 구분이 없습니다: 판매점 " + row.shopCode()
                        + ", " + row.round() + "회");
            }
        }
        Map<String, Summary> result = new HashMap<>();
        counts.forEach((shop, c) -> result.put(shop, new Summary(c[0], c[1], c[2], c[3])));
        return result;
    }
}
