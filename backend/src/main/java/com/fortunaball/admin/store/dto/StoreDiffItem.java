package com.fortunaball.admin.store.dto;

import java.util.Map;

/**
 * 변경점 한 건. kind 는 NEW / CHANGED. before·after 는 "바뀐 항목 이름 → 값" 이다.
 * NEW 는 before 가 비어 있고 after 에 파일 값이 모두 들어 있다. CHANGED 는 바뀐 항목만 들어 있다.
 */
public record StoreDiffItem(String kind, String shopCode, String storeName,
                            Map<String, String> before, Map<String, String> after) {
}
