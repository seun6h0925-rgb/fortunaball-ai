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
 *  - 이 클래스는 읽기 전용이다. 수정은 관리자 기능(API-59)을 만들 때 추가한다.
 *  - updated_at 은 DB 가 자동으로 채우므로 읽기만 한다 (insertable/updatable = false).
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

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected ContentPage() { }   // JPA 가 객체를 만들 때 쓰는 기본 생성자

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
