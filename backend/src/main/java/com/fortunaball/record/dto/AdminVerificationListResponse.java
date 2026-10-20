package com.fortunaball.record.dto;

import java.util.List;

/** 관리자 인증 목록 한 쪽 (API-28). page 는 0부터 시작한다. */
public record AdminVerificationListResponse(List<AdminVerificationItem> items, int page, int size,
                                            long totalElements, int totalPages) {
}
