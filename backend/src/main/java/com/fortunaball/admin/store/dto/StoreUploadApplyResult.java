package com.fortunaball.admin.store.dto;

import java.time.LocalDate;

/**
 * 판매점 파일 반영 결과 (API-50).
 * geocodePendingCount 는 좌표 변환이 끝나지 않은(SUCCESS 가 아닌) 판매점 수다. CSV 에 좌표가 들어 있으면 보통 0이다.
 */
public record StoreUploadApplyResult(long uploadId, String status, int stores, int newStores, int closedStores,
                                     int histories, int summaries, int minRound, int maxRound, LocalDate baseDate,
                                     int geocodePendingCount) {
}
