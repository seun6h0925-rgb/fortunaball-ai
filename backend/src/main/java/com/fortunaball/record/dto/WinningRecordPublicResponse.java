package com.fortunaball.record.dto;

/** 당첨 기록 공개 설정 결과 (API-27). */
public record WinningRecordPublicResponse(Long recordId, boolean publicVisible) {
}
