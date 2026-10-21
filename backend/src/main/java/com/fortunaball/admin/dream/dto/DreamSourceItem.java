package com.fortunaball.admin.dream.dto;

/** 상징에 연결된 출처. memo 는 이 상징과 연결할 때 적은 참고 내용(서비스 문장으로 재작성한 것). */
public record DreamSourceItem(long id, String sourceName, String sourceType, String sourceUrl, String trustLevel,
                              String memo) {
}
