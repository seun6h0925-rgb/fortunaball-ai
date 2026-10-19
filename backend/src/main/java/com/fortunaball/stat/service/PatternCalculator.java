package com.fortunaball.stat.service;

import com.fortunaball.stat.domain.PatternCode;
import com.fortunaball.stat.dto.DrawNumbers;
import com.fortunaball.stat.dto.PatternResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 회차 목록을 받아 패턴 6개의 "최근 N회 중 M회 해당"을 계산한다. (DB 를 직접 보지 않는다)
 * 📚 배운 내용: @Component - 스프링이 객체를 하나 만들어 두고 필요한 곳에 넣어준다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - history 는 "회차 번호 오름차순"으로 넘겨야 한다. (맨 뒤가 가장 최근 회차)
 *  - 계산 범위(창)는 history 의 마지막 period 개 회차다.
 *  - 겨울잠 번호는 창 안의 각 회차마다 "그 회차 바로 앞 20회"가 필요하다.
 *    그래서 창보다 앞쪽 회차도 history 에 함께 넘겨야 한다. 앞 20회가 모자라는 회차는 해당으로 세지 않는다.
 *  - 핫넘버의 "많이 나온 번호"는 창 안의 출현 횟수로 정한다. (한 번도 안 나온 번호는 핫 번호에 넣지 않는다)
 *  - 기간은 20·50·100 만 허용한다. 그 밖의 값은 IllegalArgumentException.
 */
@Component
public class PatternCalculator {

    /** 패턴 6개를 한 번에 계산한다. (결과 순서는 PatternCode 선언 순서) */
    public List<PatternResult> calculate(List<DrawNumbers> history, int period) {
        if (!PatternRule.PERIODS.contains(period)) {
            throw new IllegalArgumentException("지원하지 않는 기간입니다: " + period);
        }
        int windowSize = Math.min(period, history.size());
        int windowStart = history.size() - windowSize;
        List<DrawNumbers> window = history.subList(windowStart, history.size());

        Set<Integer> hotSet = hotNumbers(window);

        int hot = 0;
        int winter = 0;
        int balance = 0;
        int highLow = 0;
        int center = 0;
        int spread = 0;

        for (int i = 0; i < window.size(); i++) {
            List<Integer> numbers = window.get(i).numbers();
            if (PatternRule.isHotNumber(numbers, hotSet)) {
                hot++;
            }
            if (isWinterSleep(history, windowStart + i)) {
                winter++;
            }
            if (PatternRule.isBalanceMix(numbers)) {
                balance++;
            }
            if (PatternRule.isHighLowMix(numbers)) {
                highLow++;
            }
            if (PatternRule.isCenterZone(numbers)) {
                center++;
            }
            if (PatternRule.isAllSectionSpread(numbers)) {
                spread++;
            }
        }

        List<PatternResult> results = new ArrayList<>();
        results.add(new PatternResult(PatternCode.HOT_NUMBER, period, windowSize, hot));
        results.add(new PatternResult(PatternCode.WINTER_SLEEP, period, windowSize, winter));
        results.add(new PatternResult(PatternCode.BALANCE_MIX, period, windowSize, balance));
        results.add(new PatternResult(PatternCode.HIGH_LOW_MIX, period, windowSize, highLow));
        results.add(new PatternResult(PatternCode.CENTER_ZONE, period, windowSize, center));
        results.add(new PatternResult(PatternCode.ALL_SECTION_SPREAD, period, windowSize, spread));
        return results;
    }

    /** 창 안에서 많이 나온 상위 번호 집합. 같은 횟수면 작은 번호를 먼저 넣는다. */
    public Set<Integer> hotNumbers(List<DrawNumbers> window) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (DrawNumbers draw : window) {
            for (int n : draw.numbers()) {
                counts.merge(n, 1, Integer::sum);
            }
        }
        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparingInt(Map.Entry::getKey));
        Set<Integer> hot = new HashSet<>();
        for (int i = 0; i < Math.min(PatternRule.HOT_TOP_COUNT, sorted.size()); i++) {
            hot.add(sorted.get(i).getKey());
        }
        return hot;
    }

    /**
     * 지금 "겨울잠 번호"인 번호들: history 의 가장 최근 WINTER_GAP_ROUNDS 회 동안 한 번도 안 나온 번호 (1~45 중).
     * 번호 생성이 쓰는 조건값이다. history 가 그보다 짧으면 판단할 수 없으므로 빈 집합을 돌려준다.
     */
    public Set<Integer> sleepNumbers(List<DrawNumbers> history) {
        Set<Integer> sleep = new HashSet<>();
        if (history.size() < PatternRule.WINTER_GAP_ROUNDS) {
            return sleep;
        }
        Set<Integer> seen = new HashSet<>();
        for (int i = history.size() - PatternRule.WINTER_GAP_ROUNDS; i < history.size(); i++) {
            seen.addAll(history.get(i).numbers());
        }
        for (int n = 1; n <= 45; n++) {
            if (!seen.contains(n)) {
                sleep.add(n);
            }
        }
        return sleep;
    }

    /** index 번째 회차가 겨울잠 번호 패턴에 해당하는지. 바로 앞 20회가 없으면 해당하지 않는다. */
    private boolean isWinterSleep(List<DrawNumbers> history, int index) {
        if (index < PatternRule.WINTER_GAP_ROUNDS) {
            return false;
        }
        Set<Integer> seen = new HashSet<>();
        for (int i = index - PatternRule.WINTER_GAP_ROUNDS; i < index; i++) {
            seen.addAll(history.get(i).numbers());
        }
        return PatternRule.isWinterSleep(history.get(index).numbers(), seen);
    }
}
