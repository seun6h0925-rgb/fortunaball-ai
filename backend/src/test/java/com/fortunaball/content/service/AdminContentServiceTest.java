package com.fortunaball.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.content.domain.ContentPage;
import com.fortunaball.content.dto.AdminContentResponse;
import com.fortunaball.content.dto.ContentUpdateRequest;
import com.fortunaball.content.repository.ContentPageRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminContentServiceTest {

    private final ContentPageRepository repository = mock(ContentPageRepository.class);
    private final AdminActionRecorder recorder = mock(AdminActionRecorder.class);
    private final AdminContentService service = new AdminContentService(repository, recorder);

    @BeforeEach
    void loginAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L, "admin", "ADMIN"), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static ContentPage page(String type, String title, String content) {
        return new ContentPage(type, title, content, true);
    }

    @Test
    void 목록은_종류_선언_순서로_정렬하고_숨김도_포함한다() {
        when(repository.findAll()).thenReturn(List.of(
                page("FAQ", "자주 묻는 질문", "내용"),
                new ContentPage("SERVICE_INTRO", "서비스 소개", "내용", false),
                page("CLAIM_GUIDE", "수령 가이드", "내용")));

        List<AdminContentResponse> result = service.list(null);

        assertEquals(List.of("SERVICE_INTRO", "CLAIM_GUIDE", "FAQ"),
                result.stream().map(AdminContentResponse::contentType).toList());
        assertEquals(false, result.get(0).visible());
    }

    @Test
    void 종류를_지정하면_그_글만_보여_준다() {
        when(repository.findByPageType("FAQ")).thenReturn(Optional.of(page("FAQ", "자주 묻는 질문", "내용")));

        assertEquals(1, service.list("FAQ").size());
    }

    @Test
    void 모르는_종류는_오류다() {
        assertEquals(ErrorCode.INVALID_INPUT,
                assertThrows(BusinessException.class, () -> service.list("NOPE")).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.update("NOPE", new ContentUpdateRequest("제목", null))).getErrorCode());
    }

    @Test
    void 수정하면_바뀌고_수정_전_내용_전체가_기록된다() {
        ContentPage page = page("FAQ", "옛 제목", "옛 내용 전체");
        when(repository.findByPageType("FAQ")).thenReturn(Optional.of(page));

        AdminContentResponse result = service.update("FAQ", new ContentUpdateRequest("새 제목", "새 내용"));

        assertEquals("새 제목", result.title());
        assertEquals("새 내용", result.content());
        verify(recorder).record(eq(7L), eq("UPDATE"), eq("content_page"), any(),
                argThat(d -> d.contains("'옛 제목' → '새 제목'") && d.contains("[수정 전 내용 전체]")
                        && d.endsWith("옛 내용 전체")));
    }

    @Test
    void 제목만_보내면_내용은_그대로고_수정_전_내용은_기록하지_않는다() {
        ContentPage page = page("FAQ", "옛 제목", "그대로");
        when(repository.findByPageType("FAQ")).thenReturn(Optional.of(page));

        AdminContentResponse result = service.update("FAQ", new ContentUpdateRequest("새 제목", null));

        assertEquals("그대로", result.content());
        verify(recorder).record(anyLong(), anyString(), anyString(), any(),
                argThat(d -> !d.contains("[수정 전 내용 전체]")));
    }

    @Test
    void 값이_그대로면_작업기록을_남기지_않는다() {
        when(repository.findByPageType("FAQ")).thenReturn(Optional.of(page("FAQ", "제목", "내용")));

        service.update("FAQ", new ContentUpdateRequest("제목", "내용"));

        verify(recorder, never()).record(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void 빈_요청이나_길이_초과는_오류다() {
        assertThrows(BusinessException.class, () -> service.update("FAQ", new ContentUpdateRequest(null, null)));
        assertThrows(BusinessException.class, () -> service.update("FAQ", null));
        assertThrows(BusinessException.class, () -> service.update("FAQ", new ContentUpdateRequest(" ", null)));
        assertThrows(BusinessException.class,
                () -> service.update("FAQ", new ContentUpdateRequest("가".repeat(201), null)));
        assertThrows(BusinessException.class,
                () -> service.update("FAQ", new ContentUpdateRequest(null, "가".repeat(12001))));
    }

    @Test
    void 글이_없으면_찾을_수_없다고_답한다() {
        when(repository.findByPageType("FAQ")).thenReturn(Optional.empty());

        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.update("FAQ", new ContentUpdateRequest("제목", null))).getErrorCode());
    }

    @Test
    void 로그인하지_않았으면_수정할_수_없다() {
        SecurityContextHolder.clearContext();

        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> service.update("FAQ", new ContentUpdateRequest("제목", null))).getErrorCode());
    }
}
