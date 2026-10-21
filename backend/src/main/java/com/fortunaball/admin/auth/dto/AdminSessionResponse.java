package com.fortunaball.admin.auth.dto;

/**
 * 로그인한 관리자 정보 (API-21 로그인 응답, GET /api/admin/auth/me 응답).
 *
 * csrfHeaderName / csrfToken : 로그인 뒤 데이터를 바꾸는 요청(POST·PATCH·DELETE)에는
 *   이 헤더 이름에 이 값을 담아 보내야 한다. (다른 사이트가 몰래 요청을 보내는 것을 막는 장치)
 *   값은 쿠키(XSRF-TOKEN)로도 내려가고, 화면(React)은 응답 값을 그대로 써도 된다.
 */
public record AdminSessionResponse(String loginId, String role, String csrfHeaderName, String csrfToken) {
}
