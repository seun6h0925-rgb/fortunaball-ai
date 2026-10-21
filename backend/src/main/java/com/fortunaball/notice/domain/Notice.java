package com.fortunaball.notice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 공지사항 (notice 표).
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 손님에게는 visible=true 인 공지만 보여 준다. 비공개(false)는 관리자 화면에서만 보인다.
 *  - 삭제(API-37)는 진짜로 지운다. 누가 언제 무엇을 지웠는지는 admin_action_log 에 남는다.
 */
@Entity
@Table(name = "notice")
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_type", nullable = false, length = 30)
    private NoticeType noticeType;

    @Column(name = "is_visible", nullable = false)
    private boolean visible;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Notice() {
    }

    public Notice(Long adminUserId, String title, String content, NoticeType noticeType, boolean visible) {
        this.adminUserId = adminUserId;
        this.title = title;
        this.content = content;
        this.noticeType = noticeType;
        this.visible = visible;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 값이 null 인 항목은 바꾸지 않는다. (PATCH: 보낸 것만 수정) */
    public void update(String title, String content, NoticeType noticeType, Boolean visible) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (noticeType != null) {
            this.noticeType = noticeType;
        }
        if (visible != null) {
            this.visible = visible;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getAdminUserId() { return adminUserId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public NoticeType getNoticeType() { return noticeType; }
    public boolean isVisible() { return visible; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
