package com.fortunaball.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 고정 안내 페이지 (테이블 content_page). 페이지 종류(page_type)마다 한 줄이다.
 * 📚 배운 내용: JPA Entity - 테이블 한 줄 = 객체 하나로 연결한다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - content 는 마크다운 글이다. 화면(프론트)이 그대로 그려 준다.
 *    · CLAIM_GUIDE : 등수별로 "## 1등", "## 2등" 소제목을 쓴다 (화면은 이 소제목으로 탭을 나눈다).
 *    · FAQ         : 질문은 "### Q. ...", 답은 바로 아래 문단이다.
 *  - is_visible 이 0 이면 사용자에게 보이지 않는다 (조회 쪽에서 걸러 낸다).
 *  - 수정은 관리자 기능(API-59)이 update() 로만 한다. 글을 새로 만드는 기능은 없다 (시드 SQL 로 한 번 넣는다).
 *  - updated_at 은 새로 넣을 때는 DB 가 채우고(insertable = false), 수정할 때는 update() 가 현재 시각으로 바꾼다.
 *    (DB 의 ON UPDATE 에만 맡기면 수정 직후 응답에 옛 수정일이 담기므로 직접 채운다)
 */
@Entity
@Table(name = "content_page")
public class ContentPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String pageType;   // ContentType 의 이름과 같은 값
    private String title;
    private String content;    // 마크다운 글

    @Column(name = "is_visible")
    private boolean visible;

    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    protected ContentPage() { }   // JPA 가 객체를 만들 때 쓰는 기본 생성자

    /** 테스트·시드용 생성자. 운영 화면에서는 새로 만들지 않는다. */
    public ContentPage(String pageType, String title, String content, boolean visible) {
        this.pageType = pageType;
        this.title = title;
        this.content = content;
        this.visible = visible;
        this.updatedAt = LocalDateTime.now();
    }

    /** 값이 null 인 항목은 바꾸지 않는다. (PATCH: 보낸 것만 수정) */
    public void update(String title, String content) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getPageType() {
        return pageType;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public boolean isVisible() {
        return visible;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
