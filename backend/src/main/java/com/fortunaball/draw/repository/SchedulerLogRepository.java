package com.fortunaball.draw.repository;

import com.fortunaball.draw.domain.SchedulerLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * scheduler_log 조회·저장.
 * 관리자 대시보드(API-22)가 "마지막 실행 상태"를 볼 때 findTopByJobNameOrderByStartedAtDesc 를 쓴다.
 */
public interface SchedulerLogRepository extends JpaRepository<SchedulerLog, Long> {

    /** 해당 작업의 가장 최근 실행 기록 */
    Optional<SchedulerLog> findTopByJobNameOrderByStartedAtDesc(String jobName);
}
