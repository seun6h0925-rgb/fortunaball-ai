package com.fortunaball.store.service;

/**
 * 루키 명당 판정 규칙 (요구사항 BE-44).
 *
 * 최근 1년 안에 "1등 1회 이상" 또는 "2등 5회 이상" 이면 루키 명당이다. 두 조건 중 하나만 맞아도 된다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 총 당첨 횟수와 개점 시기는 따지지 않는다. "최근 당첨 이력이 생긴 판매점"이라는 뜻이다.
 *  - 기간(1년)과 횟수(1, 5)는 설정값이다 (application 설정 fortuna.store.rookie.*).
 *  - 3등은 판매점별 자료가 없어서 적용하지 않는다.
 *  - "최근 1년"의 기준일은 오늘이 아니라 최신 확정 회차의 추첨일이다 (StoreQueryService 에서 정한다).
 */
public record RookieRule(int minFirst, int minSecond) {

    public boolean isRookie(int recentFirst, int recentSecond) {
        return recentFirst >= minFirst || recentSecond >= minSecond;
    }
}
