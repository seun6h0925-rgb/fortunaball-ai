package com.fortunaball.notice.dto;

import com.fortunaball.notice.domain.Notice;
import com.fortunaball.notice.domain.NoticeType;
import java.time.LocalDateTime;

/** 손님용 공지 상세 (API-19): 제목, 내용, 작성일 */
public record NoticeDetailResponse(Long noticeId, String title, String content, NoticeType noticeType,
                                   LocalDateTime createdAt) {

    public static NoticeDetailResponse from(Notice n) {
        return new NoticeDetailResponse(n.getId(), n.getTitle(), n.getContent(), n.getNoticeType(), n.getCreatedAt());
    }
}
