package com.fortunaball.admin.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

class AdminSecurityHandlersTest {

    private final AdminSecurityHandlers handlers = new AdminSecurityHandlers(new ObjectMapper());
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/dashboard");

    @Test
    void 로그인이_없으면_401과_공통_형식의_JSON() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.commence(request, response, new InsufficientAuthenticationException("로그인 필요"));

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(401, response.getStatus());
        assertTrue(body.contains("\"success\":false"));
        assertTrue(body.contains("COMMON-004"));
        assertTrue(body.contains("로그인이 필요해요."));
    }

    @Test
    void CSRF_토큰이_없으면_403과_새로_고침_안내() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.handle(request, response, new MissingCsrfTokenException("x"));

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(403, response.getStatus());
        assertTrue(body.contains("COMMON-005"));
        assertTrue(body.contains(AdminSecurityHandlers.CSRF_MESSAGE));
    }

    @Test
    void 권한이_없으면_403과_기본_문구() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.handle(request, response, new AccessDeniedException("권한 없음"));

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(403, response.getStatus());
        assertTrue(body.contains("접근 권한이 없어요."));
    }
}
