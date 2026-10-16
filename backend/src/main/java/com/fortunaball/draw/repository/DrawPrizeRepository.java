package com.fortunaball.draw.repository;

import com.fortunaball.draw.domain.DrawPrize;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 회차별 등수별 당첨금(draw_prize) DB 조회·저장.
 * 📚 배운 내용: Spring Data JPA - 메서드 이름만으로 조회 SQL 이 자동 생성된다.
 *
 * ⚠ draw_prize 는 draw_round 에 그 회차가 먼저 저장돼 있어야 저장할 수 있다 (DB 외래키).
 *    저장 순서는 반드시 "당첨번호(draw_round) → 당첨금(draw_prize)" 이다.
 */
public interface DrawPrizeRepository extends JpaRepository<DrawPrize, Long> {

    /** 이 회차의 당첨금이 이미 저장돼 있는지 (중복 저장 방지) */
    boolean existsByRoundNo(int roundNo);

    /** 한 회차의 1~5등 당첨금을 등수 순서로 (API-01, 02) */
    List<DrawPrize> findByRoundNoOrderByWinningRankAsc(int roundNo);
}
