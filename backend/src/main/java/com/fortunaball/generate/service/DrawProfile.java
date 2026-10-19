package com.fortunaball.generate.service;

import com.fortunaball.generate.domain.Combo;
import com.fortunaball.stat.dto.DrawNumbers;
import com.fortunaball.stat.service.PatternRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 최근 회차들의 "번호 모양 분포" - 공통 검증의 기준이 된다.
 * 홀수 개수, 22 이하 개수, 합계, 번호가 놓인 구간 수, 이웃한 번호 쌍 수, 끝수 종류 수를 회차마다 재서
 * 각 지표의 5%~95% 범위(통과 범위)와 25%~75% 범위(중심 범위)를 만든다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 고정 숫자(예: "합계 100~175")를 코드에 박지 않고 최근 100회 분포에서 계산한다. 통계 기반이라는 서비스 설명과 맞고,
 *    회차가 쌓여도 기준이 자동으로 따라간다. 범위의 폭(5%, 25% 등)은 GenerationRule 에서 바꾼다.
 *  - 이 클래스는 번호를 "거르는" 일만 한다. 점수는 ComboScorer, 패턴 조건은 CandidateFactory 가 맡는다.
 */
public final class DrawProfile {

    public enum Metric { ODD, LOW, SUM, SECTIONS, CONSECUTIVE_PAIRS, LAST_DIGIT_KINDS }

    private final Map<Metric, int[]> band = new EnumMap<>(Metric.class);
    private final Map<Metric, int[]> core = new EnumMap<>(Metric.class);

    private DrawProfile() {
    }

    /** history: 분포를 만들 회차들(보통 최근 100회). 비어 있으면 만들 수 없다. */
    public static DrawProfile from(List<DrawNumbers> history) {
        if (history == null || history.isEmpty()) {
            throw new IllegalArgumentException("분포를 만들 회차가 없습니다");
        }
        DrawProfile p = new DrawProfile();
        for (Metric m : Metric.values()) {
            int[] values = history.stream().mapToInt(d -> measure(m, d.numbers())).sorted().toArray();
            p.band.put(m, new int[]{
                    percentile(values, GenerationRule.BAND_LOW_PERCENT),
                    percentile(values, GenerationRule.BAND_HIGH_PERCENT)});
            p.core.put(m, new int[]{
                    percentile(values, GenerationRule.CORE_LOW_PERCENT),
                    percentile(values, GenerationRule.CORE_HIGH_PERCENT)});
        }
        return p;
    }

    /** 모든 지표가 통과 범위 안이면 true */
    public boolean accepts(Combo combo) {
        for (Metric m : Metric.values()) {
            int v = measure(m, combo.numbers());
            int[] b = band.get(m);
            if (v < b[0] || v > b[1]) {
                return false;
            }
        }
        return true;
    }

    /** 중심 범위 안에 들어온 지표 개수 (점수 가산용) */
    public int coreHits(Combo combo) {
        int hits = 0;
        for (Metric m : Metric.values()) {
            int v = measure(m, combo.numbers());
            int[] c = core.get(m);
            if (v >= c[0] && v <= c[1]) {
                hits++;
            }
        }
        return hits;
    }

    /** 통과 범위 [최소, 최대] (확인·테스트용) */
    public int[] bandOf(Metric m) {
        return band.get(m).clone();
    }

    static int measure(Metric m, List<Integer> numbers) {
        List<Integer> s = new ArrayList<>(numbers);
        Collections.sort(s);
        switch (m) {
            case ODD:
                return (int) s.stream().filter(n -> n % 2 == 1).count();
            case LOW:
                return (int) s.stream().filter(n -> n <= PatternRule.LOW_MAX_NUMBER).count();
            case SUM:
                return s.stream().mapToInt(Integer::intValue).sum();
            case SECTIONS: {
                Set<Integer> sections = new HashSet<>();
                for (int n : s) {
                    sections.add(sectionOf(n));
                }
                return sections.size();
            }
            case CONSECUTIVE_PAIRS: {
                int pairs = 0;
                for (int i = 0; i + 1 < s.size(); i++) {
                    if (s.get(i + 1) - s.get(i) == 1) {
                        pairs++;
                    }
                }
                return pairs;
            }
            case LAST_DIGIT_KINDS: {
                Set<Integer> digits = new HashSet<>();
                for (int n : s) {
                    digits.add(n % 10);
                }
                return digits.size();
            }
            default:
                throw new IllegalStateException("알 수 없는 지표: " + m);
        }
    }

    /** 1~9, 10~19, 20~29, 30~39, 40~45 구간 번호 */
    private static int sectionOf(int n) {
        if (n <= 9) {
            return 0;
        }
        if (n <= 19) {
            return 1;
        }
        if (n <= 29) {
            return 2;
        }
        if (n <= 39) {
            return 3;
        }
        return 4;
    }

    /** 가까운 순위(nearest-rank) 방식 백분위수. sorted 는 오름차순. */
    private static int percentile(int[] sorted, int percent) {
        int idx = (int) Math.ceil(percent / 100.0 * sorted.length) - 1;
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return sorted[idx];
    }
}
