package com.fortunaball.content.dto;

import com.fortunaball.content.domain.ContentPage;
import java.time.LocalDateTime;

/** 관리자용 안내 콘텐츠 (API-58·59). 종류, 제목, 내용(마크다운), 공개 여부, 수정일 */
public record AdminContentResponse(String contentType, String title, String content, boolean visible,
                                   LocalDateTime updatedAt) {

    public static AdminContentResponse from(ContentPage page) {
        return new AdminContentResponse(page.getPageType(), page.getTitle(), page.getContent(),
                page.isVisible(), page.getUpdatedAt());
    }
}
