package com.fortunaball.stat.repository;

import com.fortunaball.stat.domain.StatPatternCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 통계 패턴 카드 조회·저장 */
public interface StatPatternCardRepository extends JpaRepository<StatPatternCard, Long> {

    /** 통계 하나의 카드 전체 (패턴 6개 × 기간 3개) */
    List<StatPatternCard> findBySnapshotId(Long snapshotId);

    /** 통계 하나에서 특정 기간의 카드 6개 */
    List<StatPatternCard> findBySnapshotIdAndPeriodRounds(Long snapshotId, int periodRounds);

    /**
     * 통계 하나의 카드를 전부 지운다. (통계를 다시 계산하기 전에 사용)
     * ⚠ 직접 쓴 삭제문(@Query)으로 만든 이유: 이름 규칙(deleteBy...)으로 만들면 JPA 가 "새로 저장"을 먼저 실행해서
     *   같은 (통계, 패턴, 기간) 조합이 UNIQUE 제약에 걸릴 수 있다. 이 방식은 호출 즉시 DB 에서 지운다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StatPatternCard c where c.snapshotId = :snapshotId")
    int deleteBySnapshotId(@Param("snapshotId") Long snapshotId);
}
