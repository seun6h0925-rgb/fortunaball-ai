package com.fortunaball.stat.service;

import java.util.List;
import java.util.Set;

/**
 * 통계 패턴 6개의 "한 회차가 해당하는가" 기준과 숫자값을 한곳에 모은 클래스.
 * 기준을 바꾸고 싶을 때는 여기 숫자만 고치면 된다. (확정 2026-10-19)
 *
 *  1 핫넘버       : 기간 안에서 많이 나온 상위 HOT_TOP_COUNT 개 번호 중 HOT_MIN_MATCH 개 이상 포함
 *  2 겨울잠 번호  : 그 회차 직전 WINTER_GAP_ROUNDS 회 동안 한 번도 안 나온 번호가 1개 이상 포함
 *  3 밸런스 믹스  : 홀수 개수가 2·3·4개
 *  4 하이로우 믹스: 1~22 번호가 2·3·4개
 *  5 센터 존      : 16~30 번호가 4개 이상
 *  6 올 구간 스프레드: 5개 구간(1~9, 10~19, 20~29, 30~39, 40~45) 중 4구간 이상에 번호가 있음
 *
 * ⚠ 핫넘버·겨울잠 번호는 다른 회차 정보가 필요해서 PatternCalculator 가 계산하고, 여기에는 숫자값만 둔다.
 */
public final class PatternRule {

    private PatternRule() {
    }

    /** 카드로 보여 주는 기간(회). API-44 가 받을 수 있는 값 */
    public static final List<Integer> PERIODS = List.of(20, 50, 100);

    /** 기간을 생략했을 때 쓰는 값 */
    public static final int DEFAULT_PERIOD = 100;

    /** 번호 생성이 쓰는 기준 기간 (고정) */
    public static final int GENERATION_PERIOD = 100;

    // 핫넘버
    public static final int HOT_TOP_COUNT = 10;
    public static final int HOT_MIN_MATCH = 3;

    // 겨울잠 번호
    public static final int WINTER_GAP_ROUNDS = 20;

    // 밸런스 믹스 (홀수 개수)
    public static final int ODD_MIN = 2;
    public static final int ODD_MAX = 4;

    // 하이로우 믹스 (1~22 를 "낮은 번호"로 본다)
    public static final int LOW_MAX_NUMBER = 22;
    public static final int LOW_MIN = 2;
    public static final int LOW_MAX = 4;

    // 센터 존
    public static final int CENTER_FROM = 16;
    public static final int CENTER_TO = 30;
    public static final int CENTER_MIN = 4;

    // 올 구간 스프레드: 각 구간의 끝 번호 (1~9, 10~19, 20~29, 30~39, 40~45)
    private static final int[] SECTION_ENDS = {9, 19, 29, 39, 45};
    public static final int SPREAD_MIN_SECTIONS = 4;

    public static boolean isBalanceMix(List<Integer> numbers) {
        long odd = numbers.stream().filter(n -> n % 2 == 1).count();
        return odd >= ODD_MIN && odd <= ODD_MAX;
    }

    public static boolean isHighLowMix(List<Integer> numbers) {
        long low = numbers.stream().filter(n -> n <= LOW_MAX_NUMBER).count();
        return low >= LOW_MIN && low <= LOW_MAX;
    }

    public static boolean isCenterZone(List<Integer> numbers) {
        long center = numbers.stream().filter(n -> n >= CENTER_FROM && n <= CENTER_TO).count();
        return center >= CENTER_MIN;
    }

    public static boolean isAllSectionSpread(List<Integer> numbers) {
        boolean[] used = new boolean[SECTION_ENDS.length];
        for (int n : numbers) {
            used[sectionIndex(n)] = true;
        }
        int count = 0;
        for (boolean u : used) {
            if (u) {
                count++;
            }
        }
        return count >= SPREAD_MIN_SECTIONS;
    }

    /** 핫넘버 해당 여부: 핫 번호 집합과 겹치는 개수 */
    public static boolean isHotNumber(List<Integer> numbers, Set<Integer> hotSet) {
        long match = numbers.stream().filter(hotSet::contains).count();
        return match >= HOT_MIN_MATCH;
    }

    /** 겨울잠 번호 해당 여부: 직전 회차들에 한 번도 안 나온 번호가 1개 이상 */
    public static boolean isWinterSleep(List<Integer> numbers, Set<Integer> seenInPreviousRounds) {
        return numbers.stream().anyMatch(n -> !seenInPreviousRounds.contains(n));
    }

    private static int sectionIndex(int number) {
        for (int i = 0; i < SECTION_ENDS.length; i++) {
            if (number <= SECTION_ENDS[i]) {
                return i;
            }
        }
        throw new IllegalArgumentException("1~45 범위를 벗어난 번호: " + number);
    }
}
