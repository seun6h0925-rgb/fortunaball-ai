package com.fortunaball.admin.round.dto;

import java.time.LocalDate;

/**
 * 회차 한 줄 (API-46).
 * missing: 이 회차 당첨 데이터가 DB 에 없음 (누락). 이때 drawDate 와 prizeStatus 는 null.
 * prizeStatus: COMPLETE(1~5등 당첨금 5건 모두 수집) / INCOMPLETE(일부만 수집). prizeRanksCollected 는 수집된 등수 개수.
 * statStatus: PREPARING / WAITING / CONFIRMED, 없으면 null. statLabel 은 화면 표시용 한글.
 */
public record AdminDrawItem(int roundNo, LocalDate drawDate, boolean missing, String prizeStatus,
                            int prizeRanksCollected, String statStatus, String statLabel) {
}
