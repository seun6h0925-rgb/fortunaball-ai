package com.fortunaball.stat.repository;

import com.fortunaball.stat.domain.StatPatternCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 통계 패턴 카드 조회·저장 */
public interface StatPatternCardRepository extends JpaRepository<StatPatternCard, Long> {

    /** 통계 하나의 카드 전체 (패턴 6개 × 기간 3개) */
    List<StatPatternCard> findBySnapshotId(Long snapshotId);

    /** 통계 하나에서 특정 기간의 카드 6개 */
    List<StatPatternCard> findBySnapshotIdAndPeriodRounds(Long snapshotId, int periodRounds);
}
