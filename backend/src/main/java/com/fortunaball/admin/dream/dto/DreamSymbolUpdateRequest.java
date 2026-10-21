package com.fortunaball.admin.dream.dto;

import java.util.List;

/**
 * 꿈 상징 수정 요청 (API-34). 보내지 않은(null) 항목은 바꾸지 않는다.
 * aliases·numbers·sources 를 보내면 "그 목록이 전부"가 된다. (빠진 것은 삭제)
 */
public record DreamSymbolUpdateRequest(String description, List<String> aliases, List<DreamNumberInput> numbers,
                                       List<DreamSourceInput> sources, Boolean active) {
}
