package com.fortunaball.notice.dto;

import com.fortunaball.notice.domain.Notice;
import com.fortunaball.notice.domain.NoticeType;
import java.time.LocalDateTime;

/** 관리자용 공지 (API-35·36·60): 비공개 여부와 수정일까지 보여 준다. */
public record AdminNoticeResponse(Long noticeId, String title, String content, NoticeType noticeType,
                                  boolean visible, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static AdminNoticeResponse from(Notice n) {
        return new AdminNoticeResponse(n.getId(), n.getTitle(), n.getContent(), n.getNoticeType(),
                n.isVisible(), n.getCreatedAt(), n.getUpdatedAt());
    }
}
