package com.fortunaball.stat.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 당첨금 추이 (API-11). 회차 범위 안의 회차별 등수별 당첨금·당첨자 수와, 범위 평균을 준다.
 *  - rounds : 회차 오름차순. 당첨금 정보가 없는 회차는 prizes 가 비어 있다
 *  - averages : 등수(1~5)별 범위 평균. 당첨금 정보가 있는 회차만으로 계산하고, 하나도 없으면 비어 있다
 */
public record PrizeTrendResponse(int fromRound, int toRound, int roundCount, List<RoundPrizes> rounds,
                                 List<RankAverage> averages) {

    public record Prize(int rank, long prizeAmount, int winnerCount) {
    }

    public record RoundPrizes(int roundNo, LocalDate drawDate, List<Prize> prizes) {
    }

    /** averageWinnerCount 는 소수 첫째 자리까지, averagePrizeAmount 는 원 단위 반올림 */
    public record RankAverage(int rank, double averageWinnerCount, long averagePrizeAmount) {
    }
}
