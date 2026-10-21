package com.fortunaball.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 로그인 요청 (API-21). 비밀번호는 로그에 남기지 않는다. */
public record AdminLoginRequest(
        @NotBlank(message = "아이디를 입력해 주세요.") @Size(max = 50, message = "아이디가 너무 길어요.") String loginId,
        @NotBlank(message = "비밀번호를 입력해 주세요.") @Size(max = 100, message = "비밀번호가 너무 길어요.") String password) {

    /** 실수로 로그에 찍혀도 비밀번호가 보이지 않게 한다. */
    @Override
    public String toString() {
        return "AdminLoginRequest[loginId=" + loginId + ", password=***]";
    }
}
