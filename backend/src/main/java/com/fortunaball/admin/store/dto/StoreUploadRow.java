package com.fortunaball.admin.store.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** store_data_upload 한 줄. 업로드 이력(API-52)과 비교 결과 조회(API-49)의 머리 정보로 쓴다. */
public record StoreUploadRow(long id, String uploadType, String fileName, LocalDate baseDate, int totalRows,
                             int newRows, int changedRows, String status, long adminUserId, String errorMessage,
                             LocalDateTime uploadedAt, LocalDateTime appliedAt) {
}
