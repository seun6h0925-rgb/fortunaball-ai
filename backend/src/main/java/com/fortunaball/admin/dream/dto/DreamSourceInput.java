package com.fortunaball.admin.dream.dto;

/**
 * 출처 입력. sourceId 를 보내면 이미 있는 출처를 연결하고(이때 memo 는 연결 메모),
 * 안 보내면 sourceName·sourceType·trustLevel 로 새 출처를 만들어 연결한다.
 */
public record DreamSourceInput(Long sourceId, String sourceName, String sourceType, String sourceUrl,
                               String trustLevel, String memo) {
}
