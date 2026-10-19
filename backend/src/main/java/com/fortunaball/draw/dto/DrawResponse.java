package com.fortunaball.draw.dto;

import com.fortunaball.draw.domain.DrawPrize;
import com.fortunaball.draw.domain.DrawRound;

import java.time.LocalDate;
import java.util.List;

/**
 * 회차 조회 응답 (API-01 최신 회차, API-02 회차별 조회 공용).
 * 당첨번호 6개(오름차순), 보너스번호, 1~5등 당첨금·당첨자 수를 한 번에 담는다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - DB 엔티티(DrawRound, DrawPrize)를 그대로 내보내지 않고 이 응답 객체로 바꿔서 내보낸다.
 *    (DB 구조가 바뀌어도 API 모양은 그대로 두기 위해서)
 *  - prizeAmount 는 "해당 등수 1인당 당첨금"이다. (원 단위, long)
 */
public record DrawResponse(
        int roundNo,
        LocalDate drawDate,
        List<Integer> numbers,
        int bonusNumber,
        List<PrizeItem> prizes
) {

    /** 등수별 당첨금 한 줄 */
    public record PrizeItem(int winningRank, long prizeAmount, int winnerCount) {
        static PrizeItem from(DrawPrize p) {
            return new PrizeItem(p.getWinningRank(), p.getPrizeAmount(), p.getWinnerCount());
        }
    }

    public static DrawResponse of(DrawRound round, List<DrawPrize> prizes) {
        return new DrawResponse(
                round.getRoundNo(),
                round.getDrawDate(),
                round.getNumbers(),
                round.getBonusNumber(),
                prizes.stream().map(PrizeItem::from).toList());
    }
}
