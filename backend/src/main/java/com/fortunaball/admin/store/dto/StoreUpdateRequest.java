package com.fortunaball.admin.store.dto;

import java.math.BigDecimal;

/**
 * 판매점 수정 요청 (API-31). 보내지 않은(null) 항목은 바꾸지 않는다.
 * 주소·지역은 빈 글자("")를 보내면 값을 비운다. 좌표는 위도·경도를 함께 보내야 한다.
 */
public record StoreUpdateRequest(String storeName, String roadAddress, String jibunAddress, String sido,
                                 String sigungu, BigDecimal latitude, BigDecimal longitude) {
}
