package com.fortunaball.admin.store.dto;

import com.fortunaball.admin.common.AdminPage;

/** 변경점 비교 결과 조회 (API-49). items 는 kind(NEW / CHANGED / DELETED) 로 거른 목록이다. */
public record StoreUploadDetail(StoreUploadRow upload, DiffSummary summary, AdminPage<StoreDiffItem> items) {
}
