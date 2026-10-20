package com.fortunaball.stat.service;

import com.fortunaball.stat.dto.DrawNumbers;

import java.util.ArrayList;
import java.util.List;

/**
 * 번호(1~45)별 출현 횟수와 미출현 기간을 계산한다. (API-10, API-09 의 핫넘버·겨울잠 번호) DB 를 쓰지 않는 계산 전용 클래스다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 출현 횟수는 [fromRound ~ toRound] 구간만 센다.
 *  - 미출현 기간은 구간이 아니라 "전체 이력"에서 toRound 이전 마지막 출현을 찾아 계산한다.
 *    (구간 안에서 안 나왔어도 그 앞 회차에 나왔을 수 있어서, 구간만 보면 틀린다)
 *  - 미출현 기간 = toRound - 마지막 출현 회차. toRound 에 나왔으면 0, 이력 전체에서 한 번도 안 나왔으면 이력의 회차 수.
 */
public final class NumberStatCalculator {

    public static final int MAX_NUMBER = 45;

    /** @param lastRound 마지막 출현 회차(toRound 이하). 한 번도 없으면 null */
    public record NumberStat(int number, int count, Integer lastRound, int absentRounds) {
    }

    private NumberStatCalculator() {
    }

    /**
     * @param history   toRound 이하의 모든 회차(오래된 순). 이보다 뒤 회차가 섞여 있으면 안 된다
     * @param fromRound 출현 횟수를 셀 구간 시작(포함)
     * @param toRound   구간 끝(포함) = 기준 회차
     * @return 번호 1~45 순서의 결과 45개
     */
    public static List<NumberStat> calculate(List<DrawNumbers> history, int fromRound, int toRound) {
        int[] count = new int[MAX_NUMBER + 1];
        Integer[] last = new Integer[MAX_NUMBER + 1];
        for (DrawNumbers d : history) {
            if (d.roundNo() > toRound) {
                continue;
            }
            boolean inWindow = d.roundNo() >= fromRound;
            for (int n : d.numbers()) {
                if (inWindow) {
                    count[n]++;
                }
                if (last[n] == null || d.roundNo() > last[n]) {
                    last[n] = d.roundNo();
                }
            }
        }
        List<NumberStat> result = new ArrayList<>(MAX_NUMBER);
        for (int n = 1; n <= MAX_NUMBER; n++) {
            int absent = last[n] == null ? history.size() : toRound - last[n];
            result.add(new NumberStat(n, count[n], last[n], absent));
        }
        return result;
    }
}
