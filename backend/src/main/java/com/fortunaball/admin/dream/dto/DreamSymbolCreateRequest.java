package com.fortunaball.admin.dream.dto;

import java.util.List;

/** 꿈 상징 등록 요청 (API-33). category 를 안 보내면 "기타". numbers·sources 는 1개 이상 필요. */
public record DreamSymbolCreateRequest(String keyword, String category, String description, List<String> aliases,
                                       List<DreamNumberInput> numbers, List<DreamSourceInput> sources) {
}
