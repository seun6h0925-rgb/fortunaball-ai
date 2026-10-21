package com.fortunaball.admin.dashboard.dto;

import com.fortunaball.admin.ai.dto.AiLimitStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 대시보드 (API-22): 서비스 운영 상태를 한 번에 보여 준다.
 *  - issues: 번호 발급 건수 (오늘 / 이번 달 / 전체)
 *  - ai: AI 일일 호출·월 비용 한도 사용률, aiFailedToday 는 오늘 AI 호출 실패 건수
 *  - errors: 최근 24시간 외부 API 오류 (총 건수와 유형별)
 *  - latestRound: 수집된 최신 회차와 다음 회차의 통계 상태
 *  - scheduler: 작업별 마지막 실행 상태
 */
public record AdminDashboardResponse(IssueCounts issues, AiLimitStatus ai, long aiFailedToday, ErrorStatus errors,
                                     LatestRound latestRound, List<SchedulerStatus> scheduler) {

    public record IssueCounts(long today, long thisMonth, long total) {
    }

    public record ErrorStatus(long last24Hours, List<TypeCount> byType) {
    }

    public record TypeCount(String errorType, long count) {
    }

    /** 수집된 회차가 하나도 없으면 latestRoundNo 가 null 이다. nextStat 은 다음 회차의 통계 상태(없으면 null) */
    public record LatestRound(Integer latestRoundNo, LocalDate drawDate, Integer nextRoundNo, String nextStatStatus,
                              String nextStatLabel) {
    }

    public record SchedulerStatus(String jobName, String jobStatus, LocalDateTime startedAt, LocalDateTime endedAt,
                                  Integer processedCount, String errorMessage, boolean interrupted) {
    }
}
