package com.fortunaball.admin.auth.service;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.domain.AdminUser;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * "지금 요청이 로그인한 관리자의 것인가"를 알려 주는 도구.
 * 관리자 여부는 반드시 서버의 로그인 세션으로만 판단한다. 요청에 담긴 값(헤더·본문)으로 판단하면 안 된다.
 *
 * 쓰이는 곳: AI 한도 면제(AiGateway), 관리자 작업 기록(누가 했는지)
 */
public final class AdminAccess {

    private AdminAccess() {
    }

    public static Optional<AdminPrincipal> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return auth.getPrincipal() instanceof AdminPrincipal principal ? Optional.of(principal) : Optional.empty();
    }

    public static boolean isAdmin() {
        return current().filter(p -> AdminUser.ROLE_ADMIN.equals(p.role())).isPresent();
    }
}
