package com.fortunaball.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.content.domain.ContentPage;
import com.fortunaball.content.dto.ContentResponse;
import com.fortunaball.content.repository.ContentPageRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 안내 콘텐츠 조회 규칙 확인 (DB 없이 저장소를 가짜로 바꿔서 확인한다). */
class ContentQueryServiceTest {

    private ContentPageRepository repository;
    private ContentQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(ContentPageRepository.class);
        service = new ContentQueryService(repository);
    }

    @Test
    void 공개된_글은_제목_내용_수정일을_돌려준다() {
        ContentPage page = mock(ContentPage.class);
        LocalDateTime updated = LocalDateTime.of(2026, 10, 21, 9, 0);
        when(page.getPageType()).thenReturn("CLAIM_GUIDE");
        when(page.getTitle()).thenReturn("당첨금 수령 가이드");
        when(page.getContent()).thenReturn("## 1등\n안내");
        when(page.getUpdatedAt()).thenReturn(updated);
        when(repository.findByPageTypeAndVisibleTrue("CLAIM_GUIDE")).thenReturn(Optional.of(page));

        ContentResponse response = service.getByType("CLAIM_GUIDE");

        assertEquals("CLAIM_GUIDE", response.contentType());
        assertEquals("당첨금 수령 가이드", response.title());
        assertEquals("## 1등\n안내", response.content());
        assertEquals(updated, response.updatedAt());
    }

    @Test
    void 정해진_종류가_아니면_400_이고_DB를_조회하지_않는다() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.getByType("ABC"));

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
        verify(repository, never()).findByPageTypeAndVisibleTrue("ABC");
    }

    @Test
    void 소문자는_다른_값으로_보고_400() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.getByType("faq"));

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void 종류는_맞는데_글이_없거나_숨김이면_404() {
        when(repository.findByPageTypeAndVisibleTrue("FAQ")).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> service.getByType("FAQ"));

        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }
}
