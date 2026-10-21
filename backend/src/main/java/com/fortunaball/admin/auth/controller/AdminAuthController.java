package com.fortunaball.admin.auth.controller;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.domain.AdminUser;
import com.fortunaball.admin.auth.dto.AdminLoginRequest;
import com.fortunaball.admin.auth.dto.AdminSessionResponse;
import com.fortunaball.admin.auth.service.AdminAccess;
import com.fortunaball.admin.auth.service.AdminAuthService;
import com.fortunaball.admin.auth.service.AdminSessionManager;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 로그인·로그아웃 (API-21, API-45) 와 내 로그인 상태 확인(GET /api/admin/auth/me).
 *
 *  - POST /api/admin/auth/login   아이디·비밀번호 확인 → 세션 쿠키 발급 + CSRF 토큰 안내
 *  - POST /api/admin/auth/logout  세션 무효화 (CSRF 헤더 필요)
 *  - GET  /api/admin/auth/me      로그인 상태 확인. 화면을 새로 열 때 로그인 유지 여부를 알아보는 용도
 *
 * 컨트롤러는 입력을 받아 서비스에 넘기고 결과를 감싸서 돌려주는 일만 한다.
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private final AdminAuthService authService;
    private final AdminSessionManager sessionManager;

    public AdminAuthController(AdminAuthService authService, AdminSessionManager sessionManager) {
        this.authService = authService;
        this.sessionManager = sessionManager;
    }

    @PostMapping("/login")
    public ApiResponse<AdminSessionResponse> login(@Valid @RequestBody AdminLoginRequest body,
                                                   HttpServletRequest request, HttpServletResponse response) {
        AdminUser admin = authService.authenticate(body.loginId(), body.password(), request.getRemoteAddr());
        sessionManager.establish(admin, request, response);
        return ApiResponse.ok(toResponse(admin.getLoginId(), admin.getRole(), request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        AdminAccess.current().ifPresent(authService::recordLogout);
        sessionManager.clear(request);
        return ApiResponse.ok(null, "로그아웃했어요.");
    }

    @GetMapping("/me")
    public ApiResponse<AdminSessionResponse> me(HttpServletRequest request) {
        AdminPrincipal admin = AdminAccess.current().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getDefaultMessage(), "관리자 세션 없음"));
        return ApiResponse.ok(toResponse(admin.loginId(), admin.role(), request));
    }

    /** 필터가 요청에 달아 둔 CSRF 토큰을 꺼낸다. 꺼내는 순간 XSRF-TOKEN 쿠키도 내려간다. */
    private static AdminSessionResponse toResponse(String loginId, String role, HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return new AdminSessionResponse(loginId, role,
                token == null ? CSRF_HEADER : token.getHeaderName(),
                token == null ? null : token.getToken());
    }
}
