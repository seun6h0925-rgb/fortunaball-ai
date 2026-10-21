package com.fortunaball.notice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.fortunaball.notice.domain.Notice;
import com.fortunaball.notice.domain.NoticeType;
import com.fortunaball.notice.dto.AdminNoticeResponse;
import com.fortunaball.notice.dto.NoticeCreateRequest;
import com.fortunaball.notice.dto.NoticeUpdateRequest;
import com.fortunaball.notice.repository.NoticeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminNoticeServiceTest {

    private final NoticeRepository repository = mock(NoticeRepository.class);
    private final AdminActionRecorder recorder = mock(AdminActionRecorder.class);
    private final AdminNoticeService service = new AdminNoticeService(repository, recorder);

    @BeforeEach
    void loginAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L, "admin", "ADMIN"), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Notice existing() {
        return new Notice(7L, "옛 제목", "옛 내용", NoticeType.GENERAL, true);
    }

    @Test
    void 등록하면_저장하고_작업기록을_남긴다() {
        when(repository.save(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminNoticeResponse result = service.create(new NoticeCreateRequest("  점검 안내 ", "내용입니다", null, null));

        assertEquals("점검 안내", result.title());
        assertEquals(NoticeType.GENERAL, result.noticeType());
        assertTrue(result.visible());
        verify(recorder).record(eq(7L), eq("CREATE"), eq("notice"), any(), argThat(d -> d.contains("점검 안내")));
    }

    @Test
    void 제목이나_내용이_비었거나_너무_길면_등록할_수_없다() {
        assertThrows(BusinessException.class, () -> service.create(new NoticeCreateRequest(" ", "내용", null, null)));
        assertThrows(BusinessException.class, () -> service.create(new NoticeCreateRequest("제목", null, null, null)));
        assertThrows(BusinessException.class,
                () -> service.create(new NoticeCreateRequest("가".repeat(201), "내용", null, null)));
        assertThrows(BusinessException.class,
                () -> service.create(new NoticeCreateRequest("제목", "가".repeat(5001), null, null)));
        assertThrows(BusinessException.class, () -> service.create(null));
        verify(repository, never()).save(any());
    }

    @Test
    void 로그인하지_않았으면_등록할_수_없다() {
        SecurityContextHolder.clearContext();

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.create(new NoticeCreateRequest("제목", "내용", null, null)));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
        verify(repository, never()).save(any());
    }

    @Test
    void 수정하면_보낸_항목만_바뀌고_전후_값이_기록된다() {
        Notice notice = existing();
        when(repository.findById(3L)).thenReturn(Optional.of(notice));

        AdminNoticeResponse result = service.update(3L, new NoticeUpdateRequest("새 제목", null, null, false));

        assertEquals("새 제목", result.title());
        assertEquals("옛 내용", result.content());
        assertFalse(result.visible());
        verify(recorder).record(eq(7L), eq("UPDATE"), eq("notice"), eq(3L),
                argThat(d -> d.contains("'옛 제목' → '새 제목'") && d.contains("공개: true → false") && !d.contains("내용:")));
    }

    @Test
    void 값이_그대로면_작업기록을_남기지_않는다() {
        when(repository.findById(3L)).thenReturn(Optional.of(existing()));

        service.update(3L, new NoticeUpdateRequest("옛 제목", null, null, null));

        verify(recorder, never()).record(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void 수정할_내용이_하나도_없으면_오류다() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update(3L, new NoticeUpdateRequest(null, null, null, null)));

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void 없는_공지는_수정도_삭제도_찾을_수_없다() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.update(99L, new NoticeUpdateRequest("제목", null, null, null))).getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.delete(99L)).getErrorCode());
    }

    @Test
    void 삭제하면_지우고_작업기록을_남긴다() {
        Notice notice = existing();
        when(repository.findById(3L)).thenReturn(Optional.of(notice));

        service.delete(3L);

        verify(repository).delete(notice);
        verify(recorder).record(eq(7L), eq("DELETE"), eq("notice"), eq(3L), argThat(d -> d.contains("옛 제목")));
    }

    @Test
    void 목록은_공개여부_조건에_따라_다른_조회를_쓴다() {
        when(repository.findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existing()), PageRequest.of(0, 20), 1));
        when(repository.findByVisibleOrderByCreatedAtDescIdDesc(eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertEquals(1, service.list(null, null, null).items().size());
        assertEquals(0, service.list(false, null, null).items().size());
        verify(repository).findByVisibleOrderByCreatedAtDescIdDesc(eq(false), any(Pageable.class));
    }
}
