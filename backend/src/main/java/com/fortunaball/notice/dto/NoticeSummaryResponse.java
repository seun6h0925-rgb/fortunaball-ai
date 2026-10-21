package com.fortunaball.notice.dto;

import com.fortunaball.notice.domain.Notice;
import com.fortunaball.notice.domain.NoticeType;
import java.time.LocalDateTime;

/** 손님용 공지 목록의 한 줄 (API-18). 내용은 상세(API-19)에서 보여 준다. */
public record NoticeSummaryResponse(Long noticeId, String title, NoticeType noticeType, LocalDateTime createdAt) {

    public static NoticeSummaryResponse from(Notice n) {
        return new NoticeSummaryResponse(n.getId(), n.getTitle(), n.getNoticeType(), n.getCreatedAt());
    }
}
