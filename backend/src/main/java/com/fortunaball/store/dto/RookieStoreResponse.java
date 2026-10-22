package com.fortunaball.store.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 루키 명당 조회 응답 (API-66, 홈 화면 "루키 명당" 카드용).
 *
 * @param items          루키 명당 판매점 (최근 1년 당첨이 많은 순). 모두 rookie=true 이다
 * @param baseDate       데이터 기준일 (최신 확정 회차의 추첨일). 루키 "최근 1년"의 끝 날짜
 * @param periodMonths   "최근" 기간(개월). 설정값 fortuna.store.rookie.period-months
 * @param minFirst       루키 기준: 최근 기간 안 1등 N회 이상
 * @param minSecond      루키 기준: 최근 기간 안 2등 N회 이상 (1등 기준과 둘 중 하나만 맞아도 루키)
 * @param statsFromRound 당첨 횟수를 센 첫 회차 (예: 262)
 */
public record RookieStoreResponse(List<StoreItem> items, LocalDate baseDate, int periodMonths,
                                  int minFirst, int minSecond, Integer statsFromRound) {
}
