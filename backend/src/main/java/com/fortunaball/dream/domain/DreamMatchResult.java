package com.fortunaball.dream.domain;

/**
 * 꿈풀이 매칭 결과 (API-08 응답의 matchResult).
 *
 *  FULL_MATCH    꿈에서 찾은 상징·꿈 속 숫자만으로 번호 6개가 채워짐
 *  PARTIAL_MATCH 꿈에서 나온 번호가 6개보다 적음 (통계로 채운다)
 *  NO_MATCH      꿈에서 나온 번호가 하나도 없음 → AI 를 부르지 않고 "통계 번호를 만들어 볼까요?"를 묻는다 (DREAM_LOW_RELEVANCE)
 */
public enum DreamMatchResult {
    FULL_MATCH,
    PARTIAL_MATCH,
    NO_MATCH
}
