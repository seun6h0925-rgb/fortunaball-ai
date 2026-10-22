package com.fortunaball.stat.dto;

import java.util.List;

/**
 * 지역별 당첨 통계 (API-41). 회차 범위 안에서 시도(서울·경기 등)별로 1·2등 판매점 당첨이 몇 번 나왔는지와,
 * 바로 앞의 같은 길이 구간과 비교한 1등 증감률을 준다. (홈 화면 "최근 상승 지역", 통계 화면 지역별 통계)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 당첨 횟수는 판매점 당첨 이력(store_winning_history) 한 줄이 1건이다. (동행복권 판매점 자료는 262회부터)
 *  - previousFromRound/previousToRound 는 "바로 앞 구간"이다. 그 구간이 자료 시작 회차(dataFromRound)보다 앞서면
 *    비교할 수 없으므로 previousAvailable=false 이고 모든 firstChangeRate 는 null 이다. (없는 자료를 0으로 세면 증가율이 거짓이 된다)
 *  - firstChangeRate 는 % 단위 소수 첫째 자리. 앞 구간 1등이 0건이면 null (0으로 나눌 수 없음)
 *  - regions 는 1등 많은 순(같으면 시도 이름 순)이다. "상승 지역"은 firstChangeRate 큰 순으로 화면에서 고른다.
 */
public record RegionStatResponse(int fromRound, int toRound,
                                 int previousFromRound, int previousToRound, boolean previousAvailable,
                                 Integer dataFromRound, List<RegionItem> regions) {

    /**
     * @param sido               시도 짧은 이름 (서울, 경기 …)
     * @param storeCount         구간 안에 1·2등이 나온 서로 다른 판매점 수
     * @param firstCount         구간 안 1등 당첨 건수
     * @param secondCount        구간 안 2등 당첨 건수
     * @param previousFirstCount 앞 구간 1등 당첨 건수 (비교 불가면 0)
     * @param firstChangeRate    1등 증감률(%). 비교 불가이거나 앞 구간이 0건이면 null
     */
    public record RegionItem(String sido, int storeCount, int firstCount, int secondCount,
                             int previousFirstCount, Double firstChangeRate) {
    }
}
