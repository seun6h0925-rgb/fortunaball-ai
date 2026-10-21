package com.fortunaball.admin.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.domain.AdminUser;
import com.fortunaball.admin.auth.service.AdminAuthService;
import com.fortunaball.admin.auth.service.AdminSessionManager;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 컨트롤러가 서비스·세션 관리자와 제대로 이어지는지 확인한다. (보안 필터는 서버를 켜서 확인) */
class AdminAuthControllerTest {

    private AdminAuthService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AdminAuthService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminAuthController(service, new AdminSessionManager()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static final String BODY = "{\"loginId\":\"admin01\",\"password\":\"my-secret-pw\"}";

    @Test
    void 로그인에_성공하면_세션에_로그인_정보를_담고_관리자_정보를_돌려준다() throws Exception {
        when(service.authenticate(eq("admin01"), eq("my-secret-pw"), anyString()))
                .thenReturn(new AdminUser("admin01", "hash", AdminUser.ROLE_ADMIN));

        MvcResult result = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("admin01"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.csrfHeaderName").value("X-XSRF-TOKEN"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        assertNotNull(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    @Test
    void 로그인에_실패하면_401과_실패_코드() throws Exception {
        when(service.authenticate(anyString(), anyString(), anyString())).thenThrow(
                new BusinessException(ErrorCode.ADMIN_LOGIN_FAILED, ErrorCode.ADMIN_LOGIN_FAILED.getDefaultMessage(), "실패"));

        MvcResult result = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ADMIN-001"))
                .andReturn();

        assertNull(result.getRequest().getSession(false));     // 실패하면 세션을 만들지 않는다
    }

    @Test
    void 아이디나_비밀번호가_비면_400() throws Exception {
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인_상태면_me가_관리자_정보를_준다() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(1L, "admin01", "ADMIN"), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mvc.perform(get("/api/admin/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("admin01"));
    }

    @Test
    void 로그인_상태가_아니면_me는_401() throws Exception {
        mvc.perform(get("/api/admin/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON-004"));
    }

    @Test
    void 로그아웃하면_기록을_남기고_세션과_로그인_정보를_없앤다() throws Exception {
        AdminPrincipal admin = new AdminPrincipal(1L, "admin01", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/admin/auth/logout").session(session))
                .andExpect(status().isOk());

        verify(service).recordLogout(admin);
        assertEquals(true, session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
