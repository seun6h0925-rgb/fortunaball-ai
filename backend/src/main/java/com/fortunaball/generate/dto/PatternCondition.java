package com.fortunaball.generate.dto;

import com.fortunaball.stat.domain.PatternCode;

import java.util.Set;

/**
 * 번호 생성에 쓰는 "패턴 조건".
 *  - code        : 선택한 패턴. null 이면 패턴 없이 공통 검증만으로 생성한다.
 *  - hotNumbers  : 핫넘버 패턴이 쓰는 핫 번호 집합 (stat_pattern_card.condition_params 의 hotNumbers)
 *  - sleepNumbers: 겨울잠 번호 패턴이 쓰는 겨울잠 번호 집합 (condition_params 의 sleepNumbers)
 * 다른 패턴(밸런스·하이로우·센터·구간)은 고정 규칙이라 PatternRule 의 상수를 그대로 쓰므로 집합이 필요 없다.
 *  - includeNumbers: 꼭 넣어 달라고 한 번호 (AI 애널리스트 요청에서만 쓴다. 패턴 없이 만들 때만 적용된다)
 *  - excludeNumbers: 빼 달라고 한 번호 (같은 조건)
 */
public record PatternCondition(PatternCode code, Set<Integer> hotNumbers, Set<Integer> sleepNumbers,
                               Set<Integer> includeNumbers, Set<Integer> excludeNumbers) {

    public PatternCondition {
        hotNumbers = hotNumbers == null ? Set.of() : Set.copyOf(hotNumbers);
        sleepNumbers = sleepNumbers == null ? Set.of() : Set.copyOf(sleepNumbers);
        includeNumbers = includeNumbers == null ? Set.of() : Set.copyOf(includeNumbers);
        excludeNumbers = excludeNumbers == null ? Set.of() : Set.copyOf(excludeNumbers);
    }

    /** 포함·제외 번호 없이 만드는 기존 방식 (번호 생성 화면, 기존 테스트가 쓴다) */
    public PatternCondition(PatternCode code, Set<Integer> hotNumbers, Set<Integer> sleepNumbers) {
        this(code, hotNumbers, sleepNumbers, Set.of(), Set.of());
    }

    /** 패턴 없이(공통 검증만) 생성 */
    public static PatternCondition none() {
        return new PatternCondition(null, Set.of(), Set.of());
    }

    /** 패턴 없이, 포함·제외 번호만 지켜서 생성 (AI 애널리스트용) */
    public static PatternCondition withNumbers(Set<Integer> include, Set<Integer> exclude) {
        return new PatternCondition(null, Set.of(), Set.of(), include, exclude);
    }
}
