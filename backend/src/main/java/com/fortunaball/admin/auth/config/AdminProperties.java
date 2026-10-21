package com.fortunaball.admin.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 관리자 설정 (application.yml 의 fortuna.admin).
 *
 * @param loginId          처음 서버를 켤 때 만들 관리자 아이디. (.env 의 ADMIN_LOGIN_ID) 비어 있으면 만들지 않는다.
 * @param password         처음 서버를 켤 때 만들 관리자 비밀번호. (.env 의 ADMIN_INITIAL_PASSWORD) 8자 이상. 코드·Git 에는 쓰지 않는다.
 * @param maxLoginFailures 한 접속지(IP)에서 이 횟수만큼 틀리면 잠시 로그인을 막는다.
 * @param lockMinutes      막는 시간(분). 이 시간이 지나면 횟수가 지워진다.
 */
@ConfigurationProperties(prefix = "fortuna.admin")
public record AdminProperties(
        @DefaultValue("") String loginId,
        @DefaultValue("") String password,
        @DefaultValue("5") int maxLoginFailures,
        @DefaultValue("15") int lockMinutes) {

    public boolean hasInitialAccount() {
        return !loginId.isBlank() && !password.isBlank();
    }

    @Override
    public String toString() {
        return "AdminProperties[loginId=" + loginId + ", password=***, maxLoginFailures=" + maxLoginFailures
                + ", lockMinutes=" + lockMinutes + "]";
    }
}
