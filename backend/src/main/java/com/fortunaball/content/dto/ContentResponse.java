package com.fortunaball.content.dto;

import com.fortunaball.content.domain.ContentPage;

import java.time.LocalDateTime;

/**
 * API-20 응답. 안내 제목, 안내 내용(마크다운), 최종 수정일.
 * 수정일은 화면의 "최신 확인일" 표시에 쓴다.
 */
public record ContentResponse(String contentType, String title, String content, LocalDateTime updatedAt) {

    public static ContentResponse from(ContentPage page) {
        return new ContentResponse(page.getPageType(), page.getTitle(), page.getContent(), page.getUpdatedAt());
    }
}
