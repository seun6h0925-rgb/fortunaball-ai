package com.fortunaball.admin.auth.domain;

/** 로그인한 관리자를 세션에 담는 최소 정보. (비밀번호 해시는 담지 않는다) */
public record AdminPrincipal(Long id, String loginId, String role) {
}
