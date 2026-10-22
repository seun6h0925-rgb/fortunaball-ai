package com.fortunaball.stat.dto;

import java.util.List;

/**
 * 패턴 카드 한 장 (API-44 응답의 patterns 항목).
 * hitCount 는 "최근 period 회 중 이 패턴에 해당한 회차 수"이다. (예: 최근 100회 중 63회)
 * exampleNumbers 는 화면의 "구성 예시" 공 6개다. 이 패턴 조건으로 서버가 실제로 만든 번호 1게임이며(번호 생성과 같은 규칙),
 * 만들지 못했으면 null 이다. 발급 기록에는 남지 않는다.
 */
public record PatternCardResponse(
        String code,          // 패턴 코드 (HOT_NUMBER 등) - 화면에서 "이 통계로 번호 생성" 때 서버에 되돌려 보낸다
        String name,          // 화면에 보이는 이름 (핫넘버 등)
        String description,   // 실제 규칙 설명 (사실만, 예측·확률 표현 없음)
        int period,           // 기간 (20 / 50 / 100)
        int hitCount,         // 최근 period 회 중 해당 횟수
        List<Integer> exampleNumbers   // 구성 예시 번호 6개 (오름차순). 없으면 null
) {

    /** 예시 번호 없이 만드는 기본 형태 (통계 조회는 이걸로 만들고, 예시는 나중에 붙인다) */
    public PatternCardResponse(String code, String name, String description, int period, int hitCount) {
        this(code, name, description, period, hitCount, null);
    }
}
