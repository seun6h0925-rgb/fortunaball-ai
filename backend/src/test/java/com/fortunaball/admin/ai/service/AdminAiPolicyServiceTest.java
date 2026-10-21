package com.fortunaball.admin.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.admin.ai.dto.AiPolicyCreateRequest;
import com.fortunaball.admin.ai.dto.AiPolicyItem;
import com.fortunaball.admin.ai.dto.AiPolicyKeywordInput;
import com.fortunaball.admin.ai.dto.AiPolicyKeywordItem;
import com.fortunaball.admin.ai.dto.AiPolicyUpdateRequest;
import com.fortunaball.admin.ai.repository.AdminAiPolicyRepository;
import com.fortunaball.admin.auth.domain.AdminPrincipal;
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

class AdminAiPolicyServiceTest {

    private final AdminAiPolicyRepository repo = mock(AdminAiPolicyRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final AdminActionRecorder recorder = mock(AdminActionRecorder.class);
    private final AdminAiPolicyService service = new AdminAiPolicyService(repo, tx, recorder);

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

    private static AiPolicyItem policy(long id, boolean active, List<AiPolicyKeywordItem> kws) {
        return new AiPolicyItem(id, "PURCHASE_PUSH", "구매 유도 요청", "ALL", "PURCHASE_PUSH", "FIXED_RESPONSE", 30,
                "안내 문구", active, kws, LocalDateTime.of(2026, 10, 21, 16, 0));
    }

    private static AiPolicyCreateRequest createReq() {
        return new AiPolicyCreateRequest("new_policy", "새 정책", "all", "off_topic", "fixed_response", 80, "안내",
                List.of(new AiPolicyKeywordInput(" 로또 구매 ", null, null), new AiPolicyKeywordInput("로또구매", "EXACT", false),
                        new AiPolicyKeywordInput("ABC", "exact", true)));
    }

    // ---------------------------------------------------------------- 등록

    @Test
    void 등록하면_코드는_대문자로_키워드는_공백을_지워_저장한다() {
        when(repo.insertPolicy(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(11L);
        when(repo.find(11L)).thenReturn(Optional.of(policy(11L, true, List.of())));

        service.create(createReq());

        verify(repo).insertPolicy("NEW_POLICY", "새 정책", "ALL", "OFF_TOPIC", "FIXED_RESPONSE", 80, "안내");
        verify(repo).insertKeyword(11L, "로또구매", "CONTAINS", true);   // 같은 키워드는 처음 것만
        verify(repo).insertKeyword(11L, "abc", "EXACT", true);
        verify(repo, never()).insertKeyword(11L, "로또구매", "EXACT", false);
        verify(recorder).record(eq(7L), eq("CREATE"), eq("ai_input_policy"), eq(11L), contains("NEW_POLICY"));
    }

    @Test
    void 같은_정책_코드는_충돌() {
        doThrow(new DuplicateKeyException("dup")).when(repo)
                .insertPolicy(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString());
        assertThrows(BusinessException.class, () -> service.create(createReq()));
        verify(recorder, never()).record(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void 등록_입력_검사() {
        assertThrows(BusinessException.class, () -> service.create(null));
        assertThrows(BusinessException.class, () -> service.create(new AiPolicyCreateRequest("1bad", "n", "ALL", "T1", "FIXED_RESPONSE", 1, "m", null)));
        assertThrows(BusinessException.class, () -> service.create(new AiPolicyCreateRequest("OK_CODE", " ", "ALL", "TT", "FIXED_RESPONSE", 1, "m", null)));
        assertThrows(BusinessException.class, () -> service.create(new AiPolicyCreateRequest("OK_CODE", "n", "WEB", "TT", "FIXED_RESPONSE", 1, "m", null)));
        assertThrows(BusinessException.class, () -> service.create(new AiPolicyCreateRequest("OK_CODE", "n", "ALL", "t t", "FIXED_RESPONSE", 1, "m", null)));
        assertThrows(BusinessException.class, () -> service.create(new AiPolicyCreateRequest("OK_CODE", "n", "ALL", "TT", "RUN", 1, "m", null)));
        assertThrows(BusinessException.class, () -> service.create(new AiPolicyCreateRequest("OK_CODE", "n", "ALL", "TT", "FIXED_RESPONSE", 0, "m", null)));
        assertThrows(BusinessException.class, () -> service.create(new AiPolicyCreateRequest("OK_CODE", "n", "ALL", "TT", "FIXED_RESPONSE", 1, " ", null)));
        assertThrows(BusinessException.class, () -> service.create(new AiPolicyCreateRequest("OK_CODE", "n", "ALL", "TT", "FIXED_RESPONSE", 1, "m",
                List.of(new AiPolicyKeywordInput("  ", null, null)))));
        assertThrows(BusinessException.class, () -> service.create(new AiPolicyCreateRequest("OK_CODE", "n", "ALL", "TT", "FIXED_RESPONSE", 1, "m",
                List.of(new AiPolicyKeywordInput("a", "REGEX", null)))));
        verify(repo, never()).insertPolicy(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    // ---------------------------------------------------------------- 수정

    @Test
    void 이름과_활성을_바꾸면_전후를_기록한다() {
        when(repo.find(3L)).thenReturn(Optional.of(policy(3L, true, List.of())));
        service.update(3L, new AiPolicyUpdateRequest("새 이름", null, null, null, false));
        verify(repo).updatePolicy(3L, "새 이름", 30, "안내 문구", false);
        verify(recorder).record(eq(7L), eq("UPDATE"), eq("ai_input_policy"), eq(3L), contains("'구매 유도 요청' → '새 이름'"));
    }

    @Test
    void 키워드는_삭제_추가_수정을_나눠서_처리한다() {
        List<AiPolicyKeywordItem> old = List.of(new AiPolicyKeywordItem(1, "남김", "CONTAINS", true),
                new AiPolicyKeywordItem(2, "지움", "CONTAINS", true), new AiPolicyKeywordItem(3, "바꿈", "CONTAINS", true));
        when(repo.find(3L)).thenReturn(Optional.of(policy(3L, true, old)));

        service.update(3L, new AiPolicyUpdateRequest(null, null, null, List.of(
                new AiPolicyKeywordInput("남김", null, null),       // 그대로
                new AiPolicyKeywordInput("바꿈", "EXACT", false),   // 수정
                new AiPolicyKeywordInput("새 키워드", null, null)), // 추가
                null));

        verify(repo).deleteKeyword(2L);
        verify(repo, never()).deleteKeyword(1L);
        verify(repo).updateKeyword(3L, "EXACT", false);
        verify(repo, never()).updateKeyword(eq(1L), anyString(), anyBoolean());
        verify(repo).insertKeyword(3L, "새키워드", "CONTAINS", true);
        verify(repo, never()).updatePolicy(anyLong(), anyString(), anyInt(), anyString(), anyBoolean());
    }

    @Test
    void 키워드_목록을_빈_배열로_보내면_모두_지운다() {
        List<AiPolicyKeywordItem> old = List.of(new AiPolicyKeywordItem(1, "a", "CONTAINS", true));
        when(repo.find(3L)).thenReturn(Optional.of(policy(3L, true, old)));
        service.update(3L, new AiPolicyUpdateRequest(null, null, null, List.of(), null));
        verify(repo).deleteKeyword(1L);
    }

    @Test
    void 바뀐_것이_없으면_기록을_남기지_않는다() {
        when(repo.find(3L)).thenReturn(Optional.of(policy(3L, true, List.of())));
        service.update(3L, new AiPolicyUpdateRequest("구매 유도 요청", 30, "안내 문구", null, true));
        verify(repo, never()).updatePolicy(anyLong(), anyString(), anyInt(), anyString(), anyBoolean());
        verify(recorder, never()).record(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void 수정_입력_검사와_없는_정책() {
        when(repo.find(3L)).thenReturn(Optional.of(policy(3L, true, List.of())));
        when(repo.find(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.update(3L, null));
        assertThrows(BusinessException.class, () -> service.update(3L, new AiPolicyUpdateRequest(null, null, null, null, null)));
        assertThrows(BusinessException.class, () -> service.update(3L, new AiPolicyUpdateRequest(null, 10000, null, null, null)));
        assertThrows(BusinessException.class, () -> service.update(3L, new AiPolicyUpdateRequest(" ", null, null, null, null)));
        assertThrows(BusinessException.class, () -> service.update(99L, new AiPolicyUpdateRequest("x", null, null, null, null)));
    }

    // ---------------------------------------------------------------- 목록 · 로그인

    @Test
    void 목록은_범위_값을_검사하고_대문자로_바꿔_넘긴다() {
        assertThrows(BusinessException.class, () -> service.list("WEB", null));
        service.list(" dream ", true);
        verify(repo).list("DREAM", true);
        service.list(null, null);
        verify(repo).list(null, null);
    }

    @Test
    void 키워드는_공백을_모두_지운_소문자로_맞춘다() {
        assertEquals("1등되는번호", AdminAiPolicyService.normalizeKeyword(" 1등 되는\t번호 "));
        assertEquals("abc", AdminAiPolicyService.normalizeKeyword("A b C"));
        assertEquals("", AdminAiPolicyService.normalizeKeyword(null));
    }

    @Test
    void 로그인_없이는_등록도_수정도_안_된다() {
        SecurityContextHolder.clearContext();
        assertThrows(BusinessException.class, () -> service.create(createReq()));
        assertThrows(BusinessException.class, () -> service.update(3L, new AiPolicyUpdateRequest("x", null, null, null, null)));
    }
}
