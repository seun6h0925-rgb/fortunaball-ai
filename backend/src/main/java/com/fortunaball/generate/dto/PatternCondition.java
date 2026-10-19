package com.fortunaball.generate.dto;

import com.fortunaball.stat.domain.PatternCode;

import java.util.Set;

/**
 * 번호 생성에 쓰는 "패턴 조건".
 *  - code        : 선택한 패턴. null 이면 패턴 없이 공통 검증만으로 생성한다.
 *  - hotNumbers  : 핫넘버 패턴이 쓰는 핫 번호 집합 (stat_pattern_card.condition_params 의 hotNumbers)
 *  - sleepNumbers: 겨울잠 번호 패턴이 쓰는 겨울잠 번호 집합 (condition_params 의 sleepNumbers)
 * 다른 패턴(밸런스·하이로우·센터·구간)은 고정 규칙이라 PatternRule 의 상수를 그대로 쓰므로 집합이 필요 없다.
 */
public record PatternCondition(PatternCode code, Set<Integer> hotNumbers, Set<Integer> sleepNumbers) {

    public PatternCondition {
        hotNumbers = hotNumbers == null ? Set.of() : Set.copyOf(hotNumbers);
        sleepNumbers = sleepNumbers == null ? Set.of() : Set.copyOf(sleepNumbers);
    }

    /** 패턴 없이(공통 검증만) 생성 */
    public static PatternCondition none() {
        return new PatternCondition(null, Set.of(), Set.of());
    }
}
