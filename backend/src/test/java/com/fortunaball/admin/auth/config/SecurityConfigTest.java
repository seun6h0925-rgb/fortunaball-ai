package com.fortunaball.admin.auth.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 어떤 요청에 변조 요청 방지(CSRF) 검사를 하는지만 확인한다. (나머지 보안 규칙은 서버를 켜서 확인) */
class SecurityConfigTest {

    private static boolean check(String method, String uri) {
        return SecurityConfig.needsCsrfCheck(new MockHttpServletRequest(method, uri));
    }

    @Test
    void 관리자_API의_데이터를_바꾸는_요청은_검사한다() {
        assertTrue(check("POST", "/api/admin/notices"));
        assertTrue(check("PATCH", "/api/admin/verifications/3"));
        assertTrue(check("PUT", "/api/admin/stores/1"));
        assertTrue(check("DELETE", "/api/admin/notices/2"));
        assertTrue(check("POST", "/api/admin/auth/logout"));
    }

    @Test
    void 읽기_요청은_검사하지_않는다() {
        assertFalse(check("GET", "/api/admin/dashboard"));
        assertFalse(check("HEAD", "/api/admin/dashboard"));
        assertFalse(check("OPTIONS", "/api/admin/dashboard"));
    }

    @Test
    void 로그인_요청은_검사하지_않는다() {
        assertFalse(check("POST", "/api/admin/auth/login"));
    }

    @Test
    void 일반_사용자_API는_검사하지_않는다() {
        assertFalse(check("POST", "/api/ai/dream"));
        assertFalse(check("POST", "/api/issues"));
        assertFalse(check("PATCH", "/api/records/1"));
    }
}
