package com.fortunaball.admin.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.admin.auth.config.AdminProperties;
import com.fortunaball.admin.auth.domain.AdminUser;
import com.fortunaball.admin.auth.repository.AdminUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminBootstrapRunnerTest {

    private AdminUserRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void setUp() {
        repository = mock(AdminUserRepository.class);
        when(repository.count()).thenReturn(0L);
    }

    private AdminBootstrapRunner runner(String loginId, String password) {
        return new AdminBootstrapRunner(repository, encoder, new AdminProperties(loginId, password, 5, 15));
    }

    @Test
    void 관리자가_없고_설정이_맞으면_해시로_저장한다() {
        runner("admin01", "my-secret-pw").createFirstAdminIfNeeded();

        ArgumentCaptor<AdminUser> saved = ArgumentCaptor.forClass(AdminUser.class);
        verify(repository).save(saved.capture());
        assertEquals("admin01", saved.getValue().getLoginId());
        assertEquals("ADMIN", saved.getValue().getRole());
        assertNotEquals("my-secret-pw", saved.getValue().getPasswordHash());          // 평문 저장 금지
        assertTrue(encoder.matches("my-secret-pw", saved.getValue().getPasswordHash()));
    }

    @Test
    void 이미_관리자가_있으면_아무것도_하지_않는다() {
        when(repository.count()).thenReturn(1L);

        runner("admin01", "my-secret-pw").createFirstAdminIfNeeded();

        verify(repository, never()).save(any());
    }

    @Test
    void 설정이_비어_있으면_만들지_않는다() {
        runner("", "").createFirstAdminIfNeeded();

        verify(repository, never()).save(any());
    }

    @Test
    void 비밀번호가_8자_미만이면_만들지_않는다() {
        runner("admin01", "short7!").createFirstAdminIfNeeded();

        verify(repository, never()).save(any());
    }

    @Test
    void 아이디가_너무_짧거나_공백이_있으면_만들지_않는다() {
        runner("ab", "my-secret-pw").createFirstAdminIfNeeded();
        runner("ad min", "my-secret-pw").createFirstAdminIfNeeded();

        verify(repository, never()).save(any());
    }

    @Test
    void 저장_중_오류가_나도_서버_시작을_막지_않는다() {
        when(repository.count()).thenThrow(new IllegalStateException("DB 오류"));

        runner("admin01", "my-secret-pw").run(null);    // 예외가 밖으로 나오지 않아야 한다
    }
}
