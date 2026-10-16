package com.fortunaball.draw.repository;

import com.fortunaball.draw.domain.DrawRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 회차별 당첨번호(draw_round) DB 조회·저장.
 * 📚 배운 내용: Spring Data JPA - 인터페이스만 만들면 save, findAll 같은 기본 기능이 자동으로 생기고,
 *   메서드 이름(findBy..., existsBy...)만 규칙대로 지으면 SQL 도 자동으로 만들어진다.
 *
 * ⚠ 이름 규칙으로 만든 조회는 필드 이름(roundNo)과 맞아야 한다. 이름이 틀리면 서버가 켜질 때 바로 에러가 난다.
 */
public interface DrawRoundRepository extends JpaRepository<DrawRound, Long> {

    /** 이 회차가 이미 저장돼 있는지 (수집할 때 중복 저장을 막는다) */
    boolean existsByRoundNo(int roundNo);

    /** 회차 번호로 한 건 조회 (API-02, 당첨 검증에서 사용) */
    Optional<DrawRound> findByRoundNo(int roundNo);

    /** DB 에 저장된 가장 최근 회차 (API-01, 수집이 어디까지 됐는지 확인할 때 사용) */
    Optional<DrawRound> findTopByOrderByRoundNoDesc();

    /** 최근 100회를 최신순으로 (통계 계산의 기준 기간. 20·50회는 앞에서부터 잘라 쓴다) */
    List<DrawRound> findTop100ByOrderByRoundNoDesc();

    /** 회차 번호 순서대로 전체 (처음부터 끝까지 통계를 볼 때 사용) */
    List<DrawRound> findAllByOrderByRoundNoAsc();
}
