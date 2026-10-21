package com.fortunaball.admin.store.dto;

import java.math.BigDecimal;

/** 관리자용 판매점 한 곳 (API-53, 31). geocodeStatus: PENDING / SUCCESS / FAILED */
public record AdminStoreItem(long id, String shopCode, String storeName, String roadAddress, String jibunAddress,
                             String sido, String sigungu, BigDecimal latitude, BigDecimal longitude,
                             String geocodeStatus, boolean closed) {
}
