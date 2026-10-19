package com.fortunaball.stat.domain;

/**
 * 통계 패턴 6개의 코드와 화면 이름.
 * DB(stat_pattern_card.pattern_code)에는 name() 값(HOT_NUMBER 등)이 그대로 저장된다.
 * 📚 배운 내용: enum - 정해진 값만 쓸 수 있게 묶어 두는 자바 문법. 오타가 나면 컴파일 단계에서 바로 잡힌다.
 *
 * ⚠ 이름·설명에는 "예측", "확률 상승" 같은 표현을 쓰지 않는다. (과거 사실만 표기하는 서비스 기준)
 */
public enum PatternCode {

    HOT_NUMBER("핫넘버"),
    WINTER_SLEEP("겨울잠 번호"),
    BALANCE_MIX("밸런스 믹스"),
    HIGH_LOW_MIX("하이로우 믹스"),
    CENTER_ZONE("센터 존"),
    ALL_SECTION_SPREAD("올 구간 스프레드");

    private final String displayName;

    PatternCode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
