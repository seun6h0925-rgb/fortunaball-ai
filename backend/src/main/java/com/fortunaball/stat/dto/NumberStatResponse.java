package com.fortunaball.stat.dto;

import java.util.List;

/**
 * 번호별 통계 (API-10). 번호 1~45 순서로 45개.
 *  - fromRound ~ toRound : 출현 횟수를 센 구간. roundCount = 구간에 실제 들어간 회차 수
 *  - lastRound  : toRound 이하에서 마지막으로 나온 회차 (한 번도 없으면 null)
 *  - absentRounds : toRound 기준 몇 회째 안 나왔는지 (toRound 에 나왔으면 0)
 */
public record NumberStatResponse(int basisRound, int fromRound, int toRound, int roundCount, List<Item> items) {

    public record Item(int number, int count, Integer lastRound, int absentRounds) {
    }
}
