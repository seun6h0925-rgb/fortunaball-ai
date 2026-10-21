package com.fortunaball.notice.dto;

import com.fortunaball.notice.domain.NoticeType;

/**
 * 공지 등록 요청 (API-35). 제목·내용만 필수이고, 종류는 안 보내면 GENERAL, 공개 여부는 안 보내면 공개(true)로 처리한다.
 * 글자 수 검사는 서비스(AdminNoticeService)에서 한다.
 */
public record NoticeCreateRequest(String title, String content, NoticeType noticeType, Boolean visible) {
}
