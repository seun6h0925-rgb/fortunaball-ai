package com.fortunaball.content.repository;

import com.fortunaball.content.domain.ContentPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 안내 콘텐츠 조회용 저장소 (테이블 content_page).
 * 사용자 화면은 "공개된 것(is_visible = 1)" 만 보여야 하므로 공개 조건을 이름에 넣었다.
 */
public interface ContentPageRepository extends JpaRepository<ContentPage, Long> {

    /** 페이지 종류와 공개 여부로 한 건 조회. 없거나 숨김이면 비어 있다. */
    Optional<ContentPage> findByPageTypeAndVisibleTrue(String pageType);
}
