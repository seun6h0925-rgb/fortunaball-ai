package com.fortunaball.record.dto;

import java.util.List;

/** 당첨 기록 목록 한 쪽 (API-17). page 는 0부터 시작한다. */
public record WinningRecordListResponse(List<WinningRecordItem> items, int page, int size,
                                        long totalElements, int totalPages) {
}
