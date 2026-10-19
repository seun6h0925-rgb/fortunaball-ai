package com.fortunaball.stat.repository;

import com.fortunaball.stat.domain.StatRoundSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 회차별 통계 상태 조회·저장. 메서드 이름만 적으면 스프링이 조회문을 만들어 준다. */
public interface StatRoundSnapshotRepository extends JpaRepository<StatRoundSnapshot, Long> {

    /** 대상 회차로 찾는다 (회차당 한 줄) */
    Optional<StatRoundSnapshot> findByTargetRoundNo(int targetRoundNo);
}
