package com.fortunaball.generate.service;

import com.fortunaball.generate.domain.Combo;
import com.fortunaball.generate.domain.PatternUnavailableException;
import com.fortunaball.generate.dto.PatternCondition;
import com.fortunaball.stat.domain.PatternCode;
import com.fortunaball.stat.service.PatternRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * 선택한 패턴의 조건을 만족하는 후보 조합을 "난수로" 하나 만든다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 패턴 조건은 stat 의 PatternRule 과 같은 규칙으로 맞춘다. 통계 화면에서 "이 패턴에 해당"이라고 센 기준과
 *    번호 생성이 만든 기준이 같아야 하기 때문이다. 마지막에는 PatternRule 로 한 번 더 확인한다.
 *  - 핫넘버·겨울잠·센터 존은 "조건에 필요한 번호를 먼저 넣고 나머지를 채우는" 방식이고,
 *    밸런스·하이로우·구간 스프레드는 "무작위로 만들어 보고 조건에 맞을 때까지 다시 뽑는" 방식이다.
 *    (후자는 통과 확률이 60% 이상이라 금방 끝난다)
 *  - 조건을 만족하는 조합 자체를 만들 수 없으면(예: 겨울잠 번호가 하나도 없음) PatternUnavailableException 을 던진다.
 *    패턴 조건은 완화하지 않는다.
 */
public final class CandidateFactory {

    private CandidateFactory() {
    }

    public static Combo next(PatternCondition cond, RandomGenerator rng) {
        PatternCode code = cond.code();
        if (code == null) {
            return Combo.of(fill(new HashSet<>(), rng));
        }
        switch (code) {
            case HOT_NUMBER:
                return hot(cond, rng);
            case WINTER_SLEEP:
                return winterSleep(cond, rng);
            case CENTER_ZONE:
                return center(rng);
            case BALANCE_MIX:
                return sampled(rng, PatternRule::isBalanceMix, code);
            case HIGH_LOW_MIX:
                return sampled(rng, PatternRule::isHighLowMix, code);
            case ALL_SECTION_SPREAD:
                return sampled(rng, PatternRule::isAllSectionSpread, code);
            default:
                throw new IllegalStateException("알 수 없는 패턴: " + code);
        }
    }

    /** 핫넘버: 핫 번호를 3~4개 넣고 나머지는 아무 번호로 채운다 */
    private static Combo hot(PatternCondition cond, RandomGenerator rng) {
        List<Integer> hotPool = new ArrayList<>(cond.hotNumbers());
        if (hotPool.size() < PatternRule.HOT_MIN_MATCH) {
            throw new PatternUnavailableException("핫 번호가 " + PatternRule.HOT_MIN_MATCH + "개보다 적습니다: " + hotPool.size());
        }
        int take = Math.min(hotPool.size(), PatternRule.HOT_MIN_MATCH + rng.nextInt(2));
        Set<Integer> chosen = new HashSet<>(pick(hotPool, take, rng));
        List<Integer> numbers = fill(chosen, rng);
        verify(PatternRule.isHotNumber(numbers, cond.hotNumbers()), PatternCode.HOT_NUMBER);
        return Combo.of(numbers);
    }

    /** 겨울잠 번호: 겨울잠 번호를 1~2개 넣고 나머지는 아무 번호로 채운다 */
    private static Combo winterSleep(PatternCondition cond, RandomGenerator rng) {
        List<Integer> sleepPool = new ArrayList<>(cond.sleepNumbers());
        if (sleepPool.isEmpty()) {
            throw new PatternUnavailableException("겨울잠 번호가 없습니다");
        }
        int take = Math.min(sleepPool.size(), rng.nextInt(4) == 0 ? 2 : 1);
        Set<Integer> chosen = new HashSet<>(pick(sleepPool, take, rng));
        List<Integer> numbers = fill(chosen, rng);
        // PatternRule 은 "이전 회차에 나온 번호 집합"을 받으므로, 겨울잠 번호의 반대편 집합을 넘긴다
        Set<Integer> seen = new HashSet<>();
        for (int n = 1; n <= 45; n++) {
            if (!cond.sleepNumbers().contains(n)) {
                seen.add(n);
            }
        }
        verify(PatternRule.isWinterSleep(numbers, seen), PatternCode.WINTER_SLEEP);
        return Combo.of(numbers);
    }

    /** 센터 존: 16~30 에서 4~5개를 넣고 나머지는 아무 번호로 채운다 */
    private static Combo center(RandomGenerator rng) {
        List<Integer> zone = new ArrayList<>();
        for (int n = PatternRule.CENTER_FROM; n <= PatternRule.CENTER_TO; n++) {
            zone.add(n);
        }
        int take = PatternRule.CENTER_MIN + (rng.nextInt(5) == 0 ? 1 : 0);
        Set<Integer> chosen = new HashSet<>(pick(zone, take, rng));
        List<Integer> numbers = fill(chosen, rng);
        verify(PatternRule.isCenterZone(numbers), PatternCode.CENTER_ZONE);
        return Combo.of(numbers);
    }

    /** 무작위로 만들어 보고 조건에 맞을 때까지 다시 뽑는다 */
    private static Combo sampled(RandomGenerator rng, java.util.function.Predicate<List<Integer>> rule, PatternCode code) {
        for (int i = 0; i < GenerationRule.MAX_PATTERN_ATTEMPTS; i++) {
            List<Integer> numbers = fill(new HashSet<>(), rng);
            if (rule.test(numbers)) {
                return Combo.of(numbers);
            }
        }
        throw new PatternUnavailableException(code + " 조건을 만족하는 조합을 찾지 못했습니다");
    }

    private static List<Integer> pick(List<Integer> pool, int count, RandomGenerator rng) {
        List<Integer> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, rng);
        return copy.subList(0, count);
    }

    /** 이미 고른 번호 외에 1~45 에서 무작위로 6개가 될 때까지 채운다 */
    private static List<Integer> fill(Set<Integer> chosen, RandomGenerator rng) {
        while (chosen.size() < 6) {
            chosen.add(1 + rng.nextInt(45));
        }
        return new ArrayList<>(chosen);
    }

    private static void verify(boolean ok, PatternCode code) {
        if (!ok) {
            // 위 로직이 맞다면 일어나지 않는다. 일어나면 PatternRule 과 이 클래스의 규칙이 어긋난 것이므로 바로 드러내도록 예외를 던진다.
            throw new IllegalStateException(code + " 조건 검증에 실패했습니다 (생성 규칙과 PatternRule 불일치)");
        }
    }
}
