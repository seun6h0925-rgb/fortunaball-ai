package com.fortunaball.notice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.notice.domain.Notice;
import com.fortunaball.notice.domain.NoticeType;
import com.fortunaball.notice.dto.NoticeDetailResponse;
import com.fortunaball.notice.dto.NoticePageResponse;
import com.fortunaball.notice.dto.NoticeSummaryResponse;
import com.fortunaball.notice.repository.NoticeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class NoticeServiceTest {

    private final NoticeRepository repository = mock(NoticeRepository.class);
    private final NoticeService service = new NoticeService(repository);

    private static Notice notice(String title) {
        return new Notice(1L, title, "내용", NoticeType.GENERAL, true);
    }

    @Test
    void 목록은_공개_공지만_조회하고_요약만_담는다() {
        when(repository.findByVisibleTrueOrderByCreatedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notice("안내")), org.springframework.data.domain.PageRequest.of(0, 20), 1));

        NoticePageResponse<NoticeSummaryResponse> result = service.list(null, null);

        assertEquals(1, result.items().size());
        assertEquals("안내", result.items().get(0).title());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
    }

    @Test
    void 페이지_크기는_1에서_50_사이로_보정한다() {
        assertEquals(20, NoticeService.safeSize(null));
        assertEquals(20, NoticeService.safeSize(0));
        assertEquals(50, NoticeService.safeSize(500));
        assertEquals(10, NoticeService.safeSize(10));
        assertEquals(0, NoticeService.safePage(-3));
        assertEquals(2, NoticeService.safePage(2));
    }

    @Test
    void 공개된_공지는_상세를_볼_수_있다() {
        when(repository.findByIdAndVisibleTrue(5L)).thenReturn(Optional.of(notice("안내")));

        NoticeDetailResponse detail = service.detail(5L);

        assertEquals("안내", detail.title());
        assertEquals("내용", detail.content());
    }

    @Test
    void 없거나_비공개인_공지는_찾을_수_없다고_답한다() {
        when(repository.findByIdAndVisibleTrue(9L)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> service.detail(9L));

        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }
}
