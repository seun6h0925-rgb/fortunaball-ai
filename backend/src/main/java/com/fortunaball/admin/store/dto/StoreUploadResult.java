package com.fortunaball.admin.store.dto;

/** 판매점 파일 업로드 결과 (API-30). 아직 DB 에 반영된 것은 없다. 확인 후 반영(API-50)해야 한다. */
public record StoreUploadResult(long uploadId, String uploadType, String fileName, String status,
                                UploadValidation validation, DiffSummary summary) {
}
