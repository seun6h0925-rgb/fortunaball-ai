package com.fortunaball.notice.dto;

import com.fortunaball.notice.domain.NoticeType;

/** 공지 수정 요청 (API-36). 보낸 항목만 바꾼다. 아무것도 안 보내면 오류. */
public record NoticeUpdateRequest(String title, String content, NoticeType noticeType, Boolean visible) {
}
