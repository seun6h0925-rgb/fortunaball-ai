package com.fortunaball.stat.dto;

/**
 * 패턴 카드 한 장 (API-44 응답의 patterns 항목).
 * hitCount 는 "최근 period 회 중 이 패턴에 해당한 회차 수"이다. (예: 최근 100회 중 63회)
 */
public record PatternCardResponse(
        String code,          // 패턴 코드 (HOT_NUMBER 등) - 화면에서 "이 통계로 번호 생성" 때 서버에 되돌려 보낸다
        String name,          // 화면에 보이는 이름 (핫넘버 등)
        String description,   // 실제 규칙 설명 (사실만, 예측·확률 표현 없음)
        int period,           // 기간 (20 / 50 / 100)
        int hitCount          // 최근 period 회 중 해당 횟수
) {
}
