package com.fortunaball.admin.auth.service;

import com.fortunaball.admin.auth.config.AdminProperties;
import com.fortunaball.admin.auth.domain.AdminUser;
import com.fortunaball.admin.auth.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 서버를 켤 때 관리자 계정이 하나도 없으면 .env 의 ADMIN_LOGIN_ID / ADMIN_INITIAL_PASSWORD 로 첫 관리자를 만든다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 계정이 이미 있으면 아무것도 하지 않는다. (.env 를 바꿔도 비밀번호가 바뀌지 않는다. 비밀번호 변경은 따로 만든다)
 *  - 비밀번호 평문은 .env 에만 있고, DB 에는 BCrypt 해시만 저장한다. 코드·Git·로그에는 남기지 않는다.
 *  - 비밀번호는 8자 이상, 아이디는 3~50자(공백 없음)여야 한다. 안 맞으면 만들지 않고 이유만 로그에 남긴다.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminUserRepository repository;
    private final PasswordEncoder encoder;
    private final AdminProperties props;

    public AdminBootstrapRunner(AdminUserRepository repository, PasswordEncoder encoder, AdminProperties props) {
        this.repository = repository;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            createFirstAdminIfNeeded();
        } catch (RuntimeException e) {
            // 서버 시작을 막지는 않는다. 원인은 로그로 확인한다.
            log.error("첫 관리자 계정을 만들지 못했어요.", e);
        }
    }

    void createFirstAdminIfNeeded() {
        if (repository.count() > 0) {
            return;
        }
        if (!props.hasInitialAccount()) {
            log.warn("관리자 계정이 없어요. .env 에 ADMIN_LOGIN_ID, ADMIN_INITIAL_PASSWORD 를 적고 서버를 다시 켜 주세요.");
            return;
        }
        String loginId = props.loginId().trim();
        if (loginId.length() < 3 || loginId.length() > 50 || loginId.chars().anyMatch(Character::isWhitespace)) {
            log.error("ADMIN_LOGIN_ID 는 공백 없이 3~50자여야 해요. 관리자 계정을 만들지 않았어요.");
            return;
        }
        if (props.password().length() < 8) {
            log.error("ADMIN_INITIAL_PASSWORD 는 8자 이상이어야 해요. 관리자 계정을 만들지 않았어요.");
            return;
        }
        repository.save(new AdminUser(loginId, encoder.encode(props.password()), AdminUser.ROLE_ADMIN));
        log.info("첫 관리자 계정을 만들었어요. (아이디: {})", loginId);
    }
}
