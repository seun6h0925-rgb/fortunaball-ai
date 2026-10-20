package com.fortunaball.record.dto;

import jakarta.validation.constraints.NotNull;

/** 당첨 기록 공개 설정 요청 (API-27). publicVisible: true=공개, false=비공개 */
public record WinningRecordPublicRequest(@NotNull Boolean publicVisible) {
}
