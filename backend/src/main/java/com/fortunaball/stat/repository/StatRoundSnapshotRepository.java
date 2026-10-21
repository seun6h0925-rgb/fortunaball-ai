package com.fortunaball.stat.repository;

import com.fortunaball.stat.domain.StatRoundSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 회차별 통계 상태 조회·저장. 메서드 이름만 적으면 스프링이 조회문을 만들어 준다. */
public interface StatRoundSnapshotRepository extends JpaRepository<StatRoundSnapshot, Long> {

    /** 대상 회차로 찾는다 (회차당 한 줄) */
    Optional<StatRoundSnapshot> findByTargetRoundNo(int targetRoundNo);

    /** 기준 회차가 범위 안인 통계들 (한 회차 데이터가 바뀌었을 때 영향받는 통계를 찾는 용도) */
    List<StatRoundSnapshot> findByBasisRoundNoBetweenOrderByTargetRoundNoAsc(int fromBasis, int toBasis);
}
