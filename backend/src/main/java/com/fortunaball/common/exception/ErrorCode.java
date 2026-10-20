package com.fortunaball.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 모음. 오류가 나면 화면·로그에 이 코드가 같이 나오므로,
 * 오류일지에는 "코드 + 추적번호(traceId)"만 적어도 원인을 찾을 수 있다.
 *
 * 코드 규칙: 영역-번호
 *   COMMON : 공통(입력값, 없음, 서버 오류 등)
 *   앞으로 기능을 만들 때 영역을 추가한다. (예: ISSUE 번호 발급, AI, STORE 판매점, ADMIN 관리자)
 *
 * 기능 문서의 정책 코드(OFF_TOPIC 등 AI 고정 응답)는 오류가 아니라 정상 응답의 값이라 여기에 넣지 않는다.
 */
public enum ErrorCode {

    // ---- 공통 ----
    INVALID_INPUT("COMMON-001", HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    NOT_FOUND("COMMON-002", HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없어요."),
    METHOD_NOT_ALLOWED("COMMON-003", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요."),
    UNAUTHORIZED("COMMON-004", HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN("COMMON-005", HttpStatus.FORBIDDEN, "접근 권한이 없어요."),
    CONFLICT("COMMON-006", HttpStatus.CONFLICT, "요청이 현재 상태와 맞지 않아요."),
    TOO_MANY_REQUESTS("COMMON-007", HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."),
    SERVICE_UNAVAILABLE("COMMON-008", HttpStatus.SERVICE_UNAVAILABLE, "지금은 이용할 수 없어요. 잠시 후 다시 시도해 주세요."),
    PAYLOAD_TOO_LARGE("COMMON-009", HttpStatus.PAYLOAD_TOO_LARGE, "파일이 너무 커요. 더 작은 파일로 올려 주세요."),
    INTERNAL_ERROR("COMMON-999", HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
    public String getDefaultMessage() { return defaultMessage; }
}
