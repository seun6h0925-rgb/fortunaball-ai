package com.fortunaball.admin.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 관리자 계정 (admin_user).
 *
 * ⚠ 비밀번호는 BCrypt 로 해시한 값만 저장한다. 평문은 어디에도 두지 않는다. (코드·로그·응답·문서 모두)
 *    role 은 지금 "ADMIN" 하나뿐이다. 권한은 "ROLE_" + role 로 만들어 관리자 API 접근을 검사한다.
 */
@Entity
@Table(name = "admin_user")
public class AdminUser {

    public static final String ROLE_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 50, unique = true)
    private String loginId;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AdminUser() {
    }

    public AdminUser(String loginId, String passwordHash, String role) {
        this.loginId = loginId;
        this.password = passwordHash;
        this.role = role;
        this.createdAt = LocalDateTime.now();
    }

    public void markLoggedIn(LocalDateTime at) {
        this.lastLoginAt = at;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return password; }
    public String getRole() { return role; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
}
