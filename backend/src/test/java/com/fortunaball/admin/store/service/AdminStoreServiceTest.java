package com.fortunaball.admin.store.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.admin.store.dto.AdminStoreItem;
import com.fortunaball.admin.store.dto.StoreUpdateRequest;
import com.fortunaball.admin.store.repository.AdminStoreRepository;
import com.fortunaball.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminStoreServiceTest {

    private final AdminStoreRepository repo = mock(AdminStoreRepository.class);
    private final AdminActionRecorder recorder = mock(AdminActionRecorder.class);
    private final AdminStoreService service = new AdminStoreService(repo, recorder);

    @BeforeEach
    void login() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L, "admin", "ADMIN"), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(repo.find(3L)).thenReturn(Optional.of(store()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static AdminStoreItem store() {
        return new AdminStoreItem(3L, "1001", "가게A", "서울 중구 퇴계로 1", "서울 중구 장충동 1", "서울", "중구",
                new BigDecimal("37.5000000"), new BigDecimal("127.0000000"), "FAILED", false);
    }

    private static StoreUpdateRequest nameOnly(String name) {
        return new StoreUpdateRequest(name, null, null, null, null, null, null);
    }

    @Test
    void 이름을_고치면_전후를_기록한다() {
        service.update(3L, nameOnly("  새이름 "));
        verify(repo).update(eq(3L), eq("새이름"), eq("서울 중구 퇴계로 1"), eq("서울 중구 장충동 1"), eq("서울"), eq("중구"),
                any(), any(), eq("FAILED"), eq(false), any());
        verify(recorder).record(eq(7L), eq("UPDATE"), eq("lotto_store"), eq(3L), contains("'가게A' → '새이름'"));
    }

    @Test
    void 좌표를_고치면_좌표_변환_상태가_SUCCESS로_바뀐다() {
        service.update(3L, new StoreUpdateRequest(null, null, null, null, null, new BigDecimal("37.51"), new BigDecimal("127.01")));
        verify(repo).update(eq(3L), anyString(), any(), any(), any(), any(), eq(new BigDecimal("37.51")),
                eq(new BigDecimal("127.01")), eq("SUCCESS"), eq(true), any());
        verify(recorder).record(eq(7L), eq("UPDATE"), eq("lotto_store"), eq(3L), contains("좌표변환상태: 'FAILED' → 'SUCCESS'"));
    }

    @Test
    void 바뀐_것이_없으면_DB도_기록도_건드리지_않는다() {
        service.update(3L, nameOnly("가게A"));
        verify(repo, never()).update(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(recorder, never()).record(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void 주소에_빈_글자를_보내면_값을_비운다() {
        service.update(3L, new StoreUpdateRequest(null, "", null, null, null, null, null));
        verify(repo).update(eq(3L), anyString(), eq(null), any(), any(), any(), any(), any(), any(), eq(false), any());
    }

    @Test
    void 잘못된_입력은_거절() {
        assertThrows(BusinessException.class, () -> service.update(3L, null));
        assertThrows(BusinessException.class, () -> service.update(3L, new StoreUpdateRequest(null, null, null, null, null, null, null)));
        assertThrows(BusinessException.class, () -> service.update(3L, nameOnly("   ")));
        assertThrows(BusinessException.class, () -> service.update(3L, nameOnly("가".repeat(101))));
        // 위도·경도 한쪽만
        assertThrows(BusinessException.class, () -> service.update(3L,
                new StoreUpdateRequest(null, null, null, null, null, new BigDecimal("37.5"), null)));
        // 범위 밖 (위도·경도를 뒤바꿔 넣은 경우)
        assertThrows(BusinessException.class, () -> service.update(3L,
                new StoreUpdateRequest(null, null, null, null, null, new BigDecimal("127.0"), new BigDecimal("37.5"))));
        verify(repo, never()).update(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void 없는_판매점은_NOT_FOUND() {
        when(repo.find(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.update(99L, nameOnly("x")));
    }

    @Test
    void 같은_이름_주소가_이미_있으면_충돌로_알린다() {
        doThrow(new DuplicateKeyException("dup")).when(repo)
                .update(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        assertThrows(BusinessException.class, () -> service.update(3L, nameOnly("다른이름")));
        verify(recorder, never()).record(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void 목록의_좌표_변환_상태_값을_검사한다() {
        assertThrows(BusinessException.class, () -> service.list(null, null, null, "WHAT", null, null, null));
        service.list(" 서울 ", "", " 가게 ", "failed", null, null, null);
        verify(repo).list(eq("서울"), eq(null), eq("가게"), eq("FAILED"), eq(null), eq(0), eq(20));
    }

    @Test
    void 로그인_없이는_수정할_수_없다() {
        SecurityContextHolder.clearContext();
        assertThrows(BusinessException.class, () -> service.update(3L, nameOnly("x")));
    }
}
