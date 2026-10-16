package com.fortunaball.draw.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 회차별 등수별 당첨금 (테이블 draw_prize). 한 회차에 1~5등 5줄이 생긴다.
 * 📚 배운 내용: JPA Entity - 테이블 한 줄 = 객체 하나로 연결한다.
 *
 * ⚠ roundNo 는 draw_round.round_no 를 가리키는 값이다 (DB 에 외래키가 걸려 있어서,
 *    draw_round 에 그 회차가 먼저 저장되어 있어야 이 값을 저장할 수 있다).
 *    객체끼리 연결(@ManyToOne)하지 않고 번호만 들고 있는 이유는, 조회가 단순하고 지금은 그걸로 충분해서다.
 */
@Entity
@Table(name = "draw_prize")
public class DrawPrize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int roundNo;
    private int winningRank;    // 등수 1~5
    private long prizeAmount;   // 해당 등수 1인 당첨금 (원)
    private int winnerCount;    // 해당 등수 당첨자 수

    protected DrawPrize() { }   // JPA 용 기본 생성자

    /** 수집기가 등수별 당첨금을 저장할 때 쓴다. */
    public DrawPrize(int roundNo, int winningRank, long prizeAmount, int winnerCount) {
        if (winningRank < 1 || winningRank > 5) {
            throw new IllegalArgumentException("등수는 1~5 여야 합니다: " + winningRank);
        }
        if (prizeAmount < 0 || winnerCount < 0) {
            throw new IllegalArgumentException("당첨금과 당첨자 수는 0 이상이어야 합니다.");
        }
        this.roundNo = roundNo;
        this.winningRank = winningRank;
        this.prizeAmount = prizeAmount;
        this.winnerCount = winnerCount;
    }

    public Long getId() { return id; }
    public int getRoundNo() { return roundNo; }
    public int getWinningRank() { return winningRank; }
    public long getPrizeAmount() { return prizeAmount; }
    public int getWinnerCount() { return winnerCount; }
}
