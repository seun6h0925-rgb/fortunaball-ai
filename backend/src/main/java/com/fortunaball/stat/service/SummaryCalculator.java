package com.fortunaball.stat.service;

import com.fortunaball.stat.domain.StatPeriod;
import com.fortunaball.stat.dto.DrawNumbers;
import com.fortunaball.stat.dto.StatSummaryResponse;
import com.fortunaball.stat.dto.StatSummaryResponse.Absence;
import com.fortunaball.stat.dto.StatSummaryResponse.AbsentNumber;
import com.fortunaball.stat.dto.StatSummaryResponse.Ac;
import com.fortunaball.stat.dto.StatSummaryResponse.Band;
import com.fortunaball.stat.dto.StatSummaryResponse.Consecutive;
import com.fortunaball.stat.dto.StatSummaryResponse.HotNumber;
import com.fortunaball.stat.dto.StatSummaryResponse.Prime;
import com.fortunaball.stat.dto.StatSummaryResponse.RoundDetail;
import com.fortunaball.stat.dto.StatSummaryResponse.Split;
import com.fortunaball.stat.dto.StatSummaryResponse.Sum;
import com.fortunaball.stat.dto.StatSummaryResponse.SumPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 통계 요약(API-09)을 계산한다. DB 를 쓰지 않는 계산 전용 클래스라 번호만 넘기면 시험할 수 있다.
 *
 * 기준 정의 (화면 문구와 같다)
 *  - 홀짝: 당첨번호 6개(보너스 제외)의 홀수/짝수 개수.  저고: 저 = 1~22, 고 = 23~45
 *  - 합계 구간: ~99 / 100~119 / 120~139 / 140~159 / 160~179 / 180~
 *  - 구간(섹션): 1~10 / 11~20 / 21~30 / 31~40 / 41~45 (번호 개수 기준)
 *  - 연속수: 정렬한 번호에서 이웃한 두 수의 차이가 1인 쌍의 개수
 *  - 끝수: 번호의 일의 자리(0~9)
 *  - AC 값: 번호 6개 사이 15쌍의 "서로 다른 차이의 개수" - 5  (0~10)
 *  - 소수: 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43
 * ⚠ 보너스 번호는 어떤 계산에도 쓰지 않는다. 비율은 소수 첫째 자리에서 반올림한다.
 */
public final class SummaryCalculator {

    static final int LOW_MAX = 22;
    static final int HOT_COUNT = 5;
    static final int ABSENT_COUNT = 8;
    static final int RECENT_COUNT = 5;
    static final int SERIES_MAX = 100;
    static final Set<Integer> PRIMES = Set.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43);
    /** 번호 한 개가 평균 몇 회에 한 번 나오는지: 45개 번호 ÷ 회차당 6개 */
    static final double AVERAGE_INTERVAL = 45.0 / 6.0;
    private static final int[] SECTION_UPPER = {10, 20, 30, 40, 45};
    private static final String[] SECTION_LABEL = {"1~10", "11~20", "21~30", "31~40", "41~45"};
    private static final int[] SUM_LOWER = {100, 120, 140, 160, 180};
    private static final String[] SUM_LABEL = {"~99", "100~119", "120~139", "140~159", "160~179", "180~"};

    private SummaryCalculator() {
    }

    /**
     * @param history 기준 회차 이하의 모든 회차(오래된 순, 비어 있으면 안 된다)
     * @param period  계산 구간
     */
    public static StatSummaryResponse summarize(List<DrawNumbers> history, StatPeriod period,
                                                String status, String statusLabel) {
        if (history.isEmpty()) {
            throw new IllegalArgumentException("회차 이력이 비어 있습니다.");
        }
        int total = history.size();
        int size = period.isAll() ? total : Math.min(period.rounds(), total);
        List<DrawNumbers> window = history.subList(total - size, total);
        int basis = history.get(total - 1).roundNo();
        int from = window.get(0).roundNo();

        // 바로 앞 같은 길이 구간(핫넘버 증감 비교용). 전체이거나 이력이 모자라면 없다.
        List<DrawNumbers> previous = (!period.isAll() && total >= 2 * size)
                ? history.subList(total - 2 * size, total - size) : null;

        return new StatSummaryResponse(basis, period.label(), size, from, basis, status, statusLabel,
                hot(window, previous), absence(history, from, basis),
                split(window, true), split(window, false), sum(window), sections(window),
                consecutive(window), lastDigits(window), ac(window), prime(window), recent(window));
    }

    // ---------- 번호 빈도 / 장기 미출현 ----------

    private static int[] countByNumber(List<DrawNumbers> draws) {
        int[] c = new int[NumberStatCalculator.MAX_NUMBER + 1];
        for (DrawNumbers d : draws) {
            for (int n : d.numbers()) {
                c[n]++;
            }
        }
        return c;
    }

    private static List<HotNumber> hot(List<DrawNumbers> window, List<DrawNumbers> previous) {
        int[] now = countByNumber(window);
        int[] before = previous == null ? null : countByNumber(previous);
        List<Integer> numbers = new ArrayList<>();
        for (int n = 1; n <= NumberStatCalculator.MAX_NUMBER; n++) {
            numbers.add(n);
        }
        numbers.sort(Comparator.comparingInt((Integer n) -> -now[n]).thenComparingInt(n -> n));
        List<HotNumber> result = new ArrayList<>();
        for (int i = 0; i < HOT_COUNT; i++) {
            int n = numbers.get(i);
            result.add(new HotNumber(n, now[n], before == null ? null : now[n] - before[n]));
        }
        return result;
    }

    private static Absence absence(List<DrawNumbers> history, int from, int basis) {
        List<NumberStatCalculator.NumberStat> stats = new ArrayList<>(NumberStatCalculator.calculate(history, from, basis));
        stats.sort(Comparator.comparingInt((NumberStatCalculator.NumberStat s) -> -s.absentRounds())
                .thenComparingInt(NumberStatCalculator.NumberStat::number));
        int max = stats.get(0).absentRounds();
        int over10 = (int) stats.stream().filter(s -> s.absentRounds() >= 10).count();
        List<AbsentNumber> top = stats.stream().limit(ABSENT_COUNT)
                .map(s -> new AbsentNumber(s.number(), s.lastRound(), s.absentRounds())).toList();
        return new Absence(max, AVERAGE_INTERVAL, over10, top);
    }

    // ---------- 홀짝 / 저고 ----------

    private static int oddCount(DrawNumbers d) {
        return (int) d.numbers().stream().filter(n -> n % 2 == 1).count();
    }

    private static int lowCount(DrawNumbers d) {
        return (int) d.numbers().stream().filter(n -> n <= LOW_MAX).count();
    }

    /** @param odd true 면 홀짝, false 면 저고 */
    private static Split split(List<DrawNumbers> window, boolean odd) {
        Map<String, Integer> combos = new LinkedHashMap<>();
        for (int first = 6; first >= 0; first--) {
            combos.put(first + ":" + (6 - first), 0);
        }
        int firstTotal = 0;
        for (DrawNumbers d : window) {
            int first = odd ? oddCount(d) : lowCount(d);
            firstTotal += first;
            combos.merge(first + ":" + (6 - first), 1, Integer::sum);
        }
        int all = window.size() * 6;
        int secondTotal = all - firstTotal;
        return new Split(firstTotal, secondTotal, percent(firstTotal, all), percent(secondTotal, all), combos);
    }

    // ---------- 합계 / 구간 ----------

    private static int sumOf(DrawNumbers d) {
        return d.numbers().stream().mapToInt(Integer::intValue).sum();
    }

    private static Sum sum(List<DrawNumbers> window) {
        int[] bands = new int[SUM_LABEL.length];
        long total = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (DrawNumbers d : window) {
            int s = sumOf(d);
            total += s;
            min = Math.min(min, s);
            max = Math.max(max, s);
            int idx = 0;
            while (idx < SUM_LOWER.length && s >= SUM_LOWER[idx]) {
                idx++;
            }
            bands[idx]++;
        }
        List<Band> bandList = new ArrayList<>();
        for (int i = 0; i < bands.length; i++) {
            bandList.add(new Band(SUM_LABEL[i], bands[i]));
        }
        List<SumPoint> series = window.subList(Math.max(0, window.size() - SERIES_MAX), window.size()).stream()
                .map(d -> new SumPoint(d.roundNo(), sumOf(d))).toList();
        return new Sum(round1((double) total / window.size()), min, max, bandList, series);
    }

    private static int sectionIndex(int number) {
        int i = 0;
        while (number > SECTION_UPPER[i]) {
            i++;
        }
        return i;
    }

    private static List<Band> sections(List<DrawNumbers> window) {
        int[] c = new int[SECTION_LABEL.length];
        for (DrawNumbers d : window) {
            for (int n : d.numbers()) {
                c[sectionIndex(n)]++;
            }
        }
        List<Band> list = new ArrayList<>();
        for (int i = 0; i < c.length; i++) {
            list.add(new Band(SECTION_LABEL[i], c[i]));
        }
        return list;
    }

    // ---------- 연속수 / 끝수 / AC / 소수 ----------

    static int consecutivePairs(DrawNumbers d) {
        List<Integer> n = d.numbers().stream().sorted().toList();
        int pairs = 0;
        for (int i = 1; i < n.size(); i++) {
            if (n.get(i) - n.get(i - 1) == 1) {
                pairs++;
            }
        }
        return pairs;
    }

    private static Consecutive consecutive(List<DrawNumbers> window) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("0", 0);
        dist.put("1", 0);
        dist.put("2", 0);
        dist.put("3+", 0);
        int with = 0;
        for (DrawNumbers d : window) {
            int p = consecutivePairs(d);
            if (p > 0) {
                with++;
            }
            dist.merge(p >= 3 ? "3+" : String.valueOf(p), 1, Integer::sum);
        }
        return new Consecutive(with, percent(with, window.size()), dist);
    }

    private static List<Band> lastDigits(List<DrawNumbers> window) {
        int[] c = new int[10];
        for (DrawNumbers d : window) {
            for (int n : d.numbers()) {
                c[n % 10]++;
            }
        }
        List<Band> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(new Band(String.valueOf(i), c[i]));
        }
        return list;
    }

    /** AC 값 = (번호 6개 사이 15쌍의 서로 다른 차이의 개수) - 5 */
    static int acValue(DrawNumbers d) {
        List<Integer> n = d.numbers().stream().sorted().toList();
        Set<Integer> diffs = new java.util.HashSet<>();
        for (int i = 0; i < n.size(); i++) {
            for (int j = i + 1; j < n.size(); j++) {
                diffs.add(n.get(j) - n.get(i));
            }
        }
        return diffs.size() - 5;
    }

    private static Ac ac(List<DrawNumbers> window) {
        int[] c = new int[11];
        long total = 0;
        for (DrawNumbers d : window) {
            int v = acValue(d);
            c[v]++;
            total += v;
        }
        List<Band> list = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            list.add(new Band(String.valueOf(i), c[i]));
        }
        return new Ac(round1((double) total / window.size()), list);
    }

    static int primeCount(DrawNumbers d) {
        return (int) d.numbers().stream().filter(PRIMES::contains).count();
    }

    private static Prime prime(List<DrawNumbers> window) {
        int[] c = new int[7];
        long total = 0;
        for (DrawNumbers d : window) {
            int p = primeCount(d);
            c[p]++;
            total += p;
        }
        List<Band> list = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            list.add(new Band(String.valueOf(i), c[i]));
        }
        return new Prime(round1((double) total / window.size()), list, PRIMES.stream().sorted().toList());
    }

    // ---------- 최근 회차 ----------

    private static List<RoundDetail> recent(List<DrawNumbers> window) {
        List<RoundDetail> list = new ArrayList<>();
        for (int i = window.size() - 1; i >= Math.max(0, window.size() - RECENT_COUNT); i--) {
            DrawNumbers d = window.get(i);
            int odd = oddCount(d);
            int low = lowCount(d);
            int[] sec = new int[SECTION_LABEL.length];
            for (int n : d.numbers()) {
                sec[sectionIndex(n)]++;
            }
            list.add(new RoundDetail(d.roundNo(), d.numbers().stream().sorted().toList(), odd, 6 - odd, low, 6 - low,
                    sumOf(d), java.util.Arrays.stream(sec).boxed().toList(), consecutivePairs(d), acValue(d), primeCount(d)));
        }
        return list;
    }

    // ---------- 공통 ----------

    private static double percent(int part, int whole) {
        return whole == 0 ? 0.0 : round1(part * 100.0 / whole);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
