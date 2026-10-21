package com.fortunaball.admin.auth.service;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.domain.AdminUser;
import com.fortunaball.admin.auth.repository.AdminUserRepository;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 로그인 확인 (API-21, 45). 아이디·비밀번호만 확인하고, 세션 만들기는 AdminSessionManager 가 맡는다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 틀린 이유를 알려 주지 않는다. (없는 아이디도, 틀린 비밀번호도 같은 문구·같은 처리 시간)
 *    그래서 없는 아이디일 때도 가짜 해시와 비교해서 시간 차이로 아이디 존재 여부를 알아내지 못하게 한다.
 *  - 비밀번호는 로그에 남기지 않는다. 실패 로그에는 아이디도 남기지 않는다. (비밀번호를 아이디 칸에 잘못 쓰는 경우가 있어서)
 *  - 실패는 IP 별로 세고, 한도를 넘으면 비밀번호 확인도 하지 않고 429 를 준다.
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    static final String ACTION_LOGIN = "LOGIN";
    static final String ACTION_LOGOUT = "LOGOUT";
    static final String TARGET = "admin_user";

    private final AdminUserRepository repository;
    private final PasswordEncoder encoder;
    private final AdminLoginThrottle throttle;
    private final AdminActionRecorder recorder;
    private final String dummyHash;

    public AdminAuthService(AdminUserRepository repository, PasswordEncoder encoder, AdminLoginThrottle throttle,
                            AdminActionRecorder recorder) {
        this.repository = repository;
        this.encoder = encoder;
        this.throttle = throttle;
        this.recorder = recorder;
        this.dummyHash = encoder.encode("not-a-real-password-" + System.nanoTime());
    }

    @Transactional
    public AdminUser authenticate(String loginId, String password, String clientIp) {
        throttle.checkAllowed(clientIp);

        AdminUser admin = repository.findByLoginId(loginId == null ? "" : loginId.trim()).orElse(null);
        boolean matched = encoder.matches(password == null ? "" : password,
                admin == null ? dummyHash : admin.getPasswordHash());
        if (admin == null || !matched) {
            throttle.recordFailure(clientIp);
            log.warn("[{}] 관리자 로그인 실패", ErrorCode.ADMIN_LOGIN_FAILED.getCode());
            throw new BusinessException(ErrorCode.ADMIN_LOGIN_FAILED, ErrorCode.ADMIN_LOGIN_FAILED.getDefaultMessage(),
                    "관리자 로그인 실패");
        }

        throttle.clear(clientIp);
        admin.markLoggedIn(LocalDateTime.now());
        recorder.record(admin.getId(), ACTION_LOGIN, TARGET, admin.getId(), "관리자 로그인");
        return admin;
    }

    @Transactional
    public void recordLogout(AdminPrincipal admin) {
        recorder.record(admin.id(), ACTION_LOGOUT, TARGET, admin.id(), "관리자 로그아웃");
    }
}
