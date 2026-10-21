package com.fortunaball.store.dto;

import java.time.LocalDate;

/**
 * 판매점 당첨 이력 한 줄 (상세 조회에서 최근 회차부터 나열).
 *
 * @param rank    1 또는 2
 * @param winType AUTO / MANUAL / SEMI_AUTO. 자료에 구분이 없으면 null (2등 대부분). 화면은 2등의 구분을 보여주지 않는다
 */
public record StoreWinHistory(int roundNo, LocalDate drawDate, int rank, String winType) {
}
