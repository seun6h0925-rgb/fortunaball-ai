package com.fortunaball.admin.action.dto;

/**
 * 스케줄러 수동 실행 결과 (API-40).
 * status: SUCCESS / FAILED / SKIPPED_RUNNING(이미 실행 중) / NOTHING_TO_DO(할 일 없음)
 */
public record JobRunResult(String jobName, String status, int processedCount, String message) {
}
