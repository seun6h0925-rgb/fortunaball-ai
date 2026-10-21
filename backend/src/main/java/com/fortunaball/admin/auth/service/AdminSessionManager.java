package com.fortunaball.admin.auth.service;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.domain.AdminUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 로그인 세션을 만들고 없애는 곳.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 세션 번호는 로그인 때 새로 바꾼다. (로그인 전에 남이 심어 둔 세션 번호를 쓰지 못하게 하는 세션 고정 공격 방지)
 *  - 세션은 서버에 있고 브라우저에는 번호(쿠키)만 간다. 쿠키는 HttpOnly 라서 화면 스크립트가 읽지 못한다.
 *  - 권한 이름은 "ROLE_" + DB 의 role (예: ROLE_ADMIN). SecurityConfig 가 /api/admin/** 에 ROLE_ADMIN 을 요구한다.
 */
@Component
public class AdminSessionManager {

    private final HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    public void establish(AdminUser admin, HttpServletRequest request, HttpServletResponse response) {
        AdminPrincipal principal = new AdminPrincipal(admin.getId(), admin.getLoginId(), admin.getRole());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + admin.getRole())));

        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);   // 세션에 저장 (세션이 없으면 새로 만든다)
    }

    public void clear(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
