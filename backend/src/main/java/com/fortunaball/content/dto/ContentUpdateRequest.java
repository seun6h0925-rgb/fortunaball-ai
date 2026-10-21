package com.fortunaball.content.dto;

/** 안내 콘텐츠 수정 요청 (API-59). 보낸 항목만 바꾼다. 아무것도 안 보내면 오류. */
public record ContentUpdateRequest(String title, String content) {
}
