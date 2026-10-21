package com.fortunaball.admin.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.dream.dto.DreamNumberInput;
import com.fortunaball.admin.dream.dto.DreamNumberItem;
import com.fortunaball.admin.dream.dto.DreamSourceInput;
import com.fortunaball.admin.dream.dto.DreamSourceItem;
import com.fortunaball.admin.dream.dto.DreamSymbolCreateRequest;
import com.fortunaball.admin.dream.dto.DreamSymbolItem;
import com.fortunaball.admin.dream.dto.DreamSymbolUpdateRequest;
import com.fortunaball.admin.dream.repository.AdminDreamRepository;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class AdminDreamServiceTest {

    private final AdminDreamRepository repo = mock(AdminDreamRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final AdminActionRecorder recorder = mock(AdminActionRecorder.class);
    private final AdminDreamService service = new AdminDreamService(repo, tx, recorder);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L, "admin", "ADMIN"), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static DreamSymbolItem symbol(long id, boolean active) {
        return new DreamSymbolItem(id, "고양이", "동물", "설명", active, List.of("야옹이"),
                List.of(new DreamNumberItem(3, "SERVICE"), new DreamNumberItem(9, "SMORFIA")),
                List.of(new DreamSourceItem(5L, "출처A", "책", null, "HIGH", "메모")), List.of(),
                LocalDateTime.of(2026, 10, 21, 16, 0));
    }

    private static DreamSymbolCreateRequest createReq() {
        return new DreamSymbolCreateRequest(" 고양이 ", null, " 설명 ", List.of(" 야옹이 ", "야옹이"),
                List.of(new DreamNumberInput(3, null), new DreamNumberInput(3, "CHARADA"), new DreamNumberInput(9, "smorfia")),
                List.of(new DreamSourceInput(5L, null, null, null, null, "메모")));
    }

    // ---------------------------------------------------------------- 등록

    @Test
    void 등록하면_분류는_기타_번호출처는_기본값으로_저장하고_로그를_남긴다() {
        when(repo.insertSymbol("고양이", "기타", "설명")).thenReturn(11L);
        when(repo.sourceExists(5L)).thenReturn(true);
        when(repo.find(11L)).thenReturn(Optional.of(symbol(11L, true)));

        service.create(createReq());

        verify(repo).insertAlias(11L, "야옹이");
        verify(repo).insertNumber(11L, 3, "SERVICE");
        verify(repo).insertNumber(11L, 9, "SMORFIA");
        verify(repo).insertLink(11L, 5L, "메모");
        verify(recorder).record(eq(7L), eq("CREATE"), eq("dream_symbol"), eq(11L), contains("고양이"));
    }

    @Test
    void 새_출처는_함께_만들어_연결한다() {
        when(repo.insertSymbol(anyString(), anyString(), anyString())).thenReturn(11L);
        when(repo.insertSource("새출처", "웹", null, "LOW", null)).thenReturn(8L);
        when(repo.find(11L)).thenReturn(Optional.of(symbol(11L, true)));
        DreamSymbolCreateRequest req = new DreamSymbolCreateRequest("고양이", "동물", "설명", null,
                List.of(new DreamNumberInput(3, null)),
                List.of(new DreamSourceInput(null, "새출처", "웹", " ", null, null)));

        service.create(req);

        verify(repo).insertLink(11L, 8L, null);
    }

    @Test
    void 번호가_범위를_벗어나거나_없으면_거절한다() {
        assertThrows(BusinessException.class, () -> service.create(new DreamSymbolCreateRequest("고양이", null, "설명", null,
                List.of(new DreamNumberInput(46, null)), List.of(new DreamSourceInput(5L, null, null, null, null, null)))));
        assertThrows(BusinessException.class, () -> service.create(new DreamSymbolCreateRequest("고양이", null, "설명", null,
                List.of(), List.of(new DreamSourceInput(5L, null, null, null, null, null)))));
        verify(repo, never()).insertSymbol(anyString(), anyString(), anyString());
    }

    @Test
    void 출처가_없거나_별칭이_이름과_같으면_거절한다() {
        assertThrows(BusinessException.class, () -> service.create(new DreamSymbolCreateRequest("고양이", null, "설명", null,
                List.of(new DreamNumberInput(3, null)), List.of())));
        assertThrows(BusinessException.class, () -> service.create(new DreamSymbolCreateRequest("고양이", null, "설명",
                List.of("고양이"), List.of(new DreamNumberInput(3, null)),
                List.of(new DreamSourceInput(5L, null, null, null, null, null)))));
    }

    @Test
    void 없는_출처번호는_404_같은_이름_분류는_409() {
        when(repo.insertSymbol(anyString(), anyString(), anyString())).thenReturn(11L);
        when(repo.sourceExists(5L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(createReq()));

        when(repo.insertSymbol(anyString(), anyString(), anyString())).thenThrow(new DuplicateKeyException("dup"));
        assertThrows(BusinessException.class, () -> service.create(createReq()));
    }

    // ---------------------------------------------------------------- 수정

    @Test
    void 번호와_별칭을_바꾸면_차이만_반영하고_전후를_기록한다() {
        when(repo.find(11L)).thenReturn(Optional.of(symbol(11L, true)));

        service.update(11L, new DreamSymbolUpdateRequest(null, List.of("냥이"),
                List.of(new DreamNumberInput(3, "CHARADA"), new DreamNumberInput(20, null)), null, false));

        verify(repo).updateSymbol(11L, "설명", false);
        verify(repo).deleteAlias(11L, "야옹이");
        verify(repo).insertAlias(11L, "냥이");
        verify(repo).updateNumberOrigin(11L, 3, "CHARADA");
        verify(repo).deleteNumber(11L, 9);
        verify(repo).insertNumber(11L, 20, "SERVICE");
        verify(recorder).record(eq(7L), eq("UPDATE"), eq("dream_symbol"), eq(11L), contains("활성: 'true' → 'false'"));
    }

    @Test
    void 바뀐_게_없으면_로그를_남기지_않는다() {
        when(repo.find(11L)).thenReturn(Optional.of(symbol(11L, true)));

        service.update(11L, new DreamSymbolUpdateRequest("설명", List.of("야옹이"), null, null, true));

        verify(repo, never()).updateSymbol(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(recorder, never()).record(anyLong(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void 출처를_바꾸면_연결을_해제하고_새로_연결한다() {
        when(repo.find(11L)).thenReturn(Optional.of(symbol(11L, true)));
        when(repo.sourceExists(6L)).thenReturn(true);

        service.update(11L, new DreamSymbolUpdateRequest(null, null, null,
                List.of(new DreamSourceInput(6L, null, null, null, null, "새 메모")), null));

        verify(repo).deleteLink(11L, 5L);
        verify(repo).insertLink(11L, 6L, "새 메모");
    }

    @Test
    void 없는_상징_수정은_404_빈_요청은_400() {
        when(repo.find(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.update(99L, new DreamSymbolUpdateRequest("x", null, null, null, null)));
        assertThrows(BusinessException.class, () -> service.update(11L, new DreamSymbolUpdateRequest(null, null, null, null, null)));
        assertEquals(List.of("야옹이"), AdminDreamService.checkAliases(List.of(" 야옹이 "), "고양이"));
    }
}
