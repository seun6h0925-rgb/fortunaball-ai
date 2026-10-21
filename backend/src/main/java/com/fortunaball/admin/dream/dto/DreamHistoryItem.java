package com.fortunaball.admin.dream.dto;

import java.time.LocalDateTime;

/** 수정 이력 1건 (admin_action_log). detail 에 바뀐 항목의 전 → 후가 적혀 있다. */
public record DreamHistoryItem(long id, long adminUserId, String actionType, String detail, LocalDateTime createdAt) {
}
