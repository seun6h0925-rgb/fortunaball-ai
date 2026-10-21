package com.fortunaball.admin.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.admin.auth.config.AdminProperties;
import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.domain.AdminUser;
import com.fortunaball.admin.auth.repository.AdminUserRepository;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** 아이디·비밀번호 확인, 실패 횟수 제한, 작업 기록을 확인한다. (DB 없이 가짜 저장소로) */
class AdminAuthServiceTest {

    private static final String IP = "1.1.1.1";
    private static final String PASSWORD = "correct-pass-1";

    private AdminUserRepository repository;
    private AdminActionRecorder recorder;
    private AdminAuthService service;
    private AdminUser admin;

    @BeforeEach
    void setUp() {
        repository = mock(AdminUserRepository.class);
        recorder = mock(AdminActionRecorder.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);   // 테스트는 빠르게 (실서버는 기본 강도)
        AdminLoginThrottle throttle = new AdminLoginThrottle(new AdminProperties("", "", 3, 15));
        service = new AdminAuthService(repository, encoder, throttle, recorder);

        admin = new AdminUser("admin", encoder.encode(PASSWORD), AdminUser.ROLE_ADMIN);
        ReflectionTestUtils.setField(admin, "id", 7L);
        when(repository.findByLoginId("admin")).thenReturn(Optional.of(admin));
        when(repository.findByLoginId("nobody")).thenReturn(Optional.empty());
    }

    private void assertLoginFails(String loginId, String password) {
        BusinessException e = assertThrows(BusinessException.class, () -> service.authenticate(loginId, password, IP));
        assertEquals(ErrorCode.ADMIN_LOGIN_FAILED, e.getErrorCode());
    }

    @Test
    void 맞는_아이디와_비밀번호면_관리자를_돌려주고_마지막_로그인_시간과_작업_기록을_남긴다() {
        AdminUser result = service.authenticate("admin", PASSWORD, IP);

        assertSame(admin, result);
        assertNotNull(admin.getLastLoginAt());
        verify(recorder).record(7L, "LOGIN", "admin_user", 7L, "관리자 로그인");
    }

    @Test
    void 아이디_앞뒤_공백은_무시한다() {
        when(repository.findByLoginId("admin")).thenReturn(Optional.of(admin));

        assertSame(admin, service.authenticate("  admin ", PASSWORD, IP));
    }

    @Test
    void 비밀번호가_틀리면_같은_실패_문구이고_작업_기록은_남기지_않는다() {
        assertLoginFails("admin", "wrong-password");

        verify(recorder, never()).record(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void 없는_아이디도_틀린_비밀번호와_똑같이_실패한다() {
        BusinessException unknown = assertThrows(BusinessException.class, () -> service.authenticate("nobody", PASSWORD, IP));
        BusinessException wrong = assertThrows(BusinessException.class, () -> service.authenticate("admin", "x", IP));

        assertEquals(unknown.getErrorCode(), wrong.getErrorCode());
        assertEquals(unknown.getMessage(), wrong.getMessage());     // 어느 쪽이 틀렸는지 알려 주지 않는다
    }

    @Test
    void 연속으로_한도만큼_틀리면_맞는_비밀번호여도_막고_비밀번호_확인도_하지_않는다() {
        assertLoginFails("admin", "bad1");
        assertLoginFails("admin", "bad2");
        assertLoginFails("admin", "bad3");

        BusinessException e = assertThrows(BusinessException.class, () -> service.authenticate("admin", PASSWORD, IP));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, e.getErrorCode());
        verify(repository, times(3)).findByLoginId("admin");        // 막힌 뒤에는 저장소도 조회하지 않는다
    }

    @Test
    void 다른_접속지는_영향이_없다() {
        assertLoginFails("admin", "bad1");
        assertLoginFails("admin", "bad2");
        assertLoginFails("admin", "bad3");

        assertSame(admin, service.authenticate("admin", PASSWORD, "2.2.2.2"));
    }

    @Test
    void 로그인에_성공하면_실패_횟수가_지워진다() {
        assertLoginFails("admin", "bad1");
        assertLoginFails("admin", "bad2");
        service.authenticate("admin", PASSWORD, IP);                 // 성공 → 횟수 초기화

        assertLoginFails("admin", "bad3");
        assertLoginFails("admin", "bad4");
        assertSame(admin, service.authenticate("admin", PASSWORD, IP));   // 아직 한도 전
    }

    @Test
    void 비밀번호가_비어_있어도_오류없이_실패로_처리한다() {
        assertLoginFails("admin", null);
        assertLoginFails(null, PASSWORD);
    }

    @Test
    void 로그아웃도_작업_기록을_남긴다() {
        service.recordLogout(new AdminPrincipal(7L, "admin", "ADMIN"));

        verify(recorder).record(eq(7L), eq("LOGOUT"), eq("admin_user"), eq(7L), anyString());
    }
}
