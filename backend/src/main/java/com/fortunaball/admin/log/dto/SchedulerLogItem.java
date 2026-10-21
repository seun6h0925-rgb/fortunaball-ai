package com.fortunaball.admin.log.dto;

import java.time.LocalDateTime;

/**
 * 스케줄러 실행 로그 한 줄 (API-39).
 * interrupted: RUNNING 인데 1시간이 넘도록 안 끝난 줄. 서버가 도중에 꺼져서 "중단됨"으로 봐야 하는 경우다.
 */
public record SchedulerLogItem(Long id, String jobName, String jobStatus, Integer targetRoundNo,
                               LocalDateTime startedAt, LocalDateTime endedAt, Integer processedCount,
                               String errorMessage, boolean interrupted) {
}
