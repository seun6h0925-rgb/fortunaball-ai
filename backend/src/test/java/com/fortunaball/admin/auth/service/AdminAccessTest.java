package com.fortunaball.admin.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminAccessTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(1L, "admin", role), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void 로그인하지_않았으면_관리자가_아니다() {
        assertFalse(AdminAccess.isAdmin());
        assertTrue(AdminAccess.current().isEmpty());
    }

    @Test
    void 관리자로_로그인했으면_관리자다() {
        login("ADMIN");

        assertTrue(AdminAccess.isAdmin());
        assertEquals("admin", AdminAccess.current().orElseThrow().loginId());
    }

    @Test
    void 다른_권한은_관리자가_아니다() {
        login("USER");

        assertFalse(AdminAccess.isAdmin());
    }

    @Test
    void 익명_사용자는_관리자가_아니다() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertFalse(AdminAccess.isAdmin());
    }
}
