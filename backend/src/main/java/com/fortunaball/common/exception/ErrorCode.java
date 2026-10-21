package com.fortunaball.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 모음. 오류가 나면 화면·로그에 이 코드가 같이 나오므로,
 * 오류일지에는 "코드 + 추적번호(traceId)"만 적어도 원인을 찾을 수 있다.
 *
 * 코드 규칙: 영역-번호
 *   COMMON : 공통(입력값, 없음, 서버 오류 등)
 *   AI : AI 기능(키 없음, 호출 한도, 사용자별 횟수, AI 호출 실패)
 *   앞으로 기능을 만들 때 영역을 추가한다. (예: ISSUE 번호 발급, STORE 판매점, ADMIN 관리자)
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
    // ---- AI (사용자 문구는 기능명세서 예외처리 8·12번을 따른다. 번호 생성·통계는 AI 가 막혀도 계속 쓸 수 있다) ----
    AI_NOT_CONFIGURED("AI-001", HttpStatus.SERVICE_UNAVAILABLE,
            "현재 AI 기능 이용이 일시 중단되었어요. 번호 생성과 통계 조회는 계속 이용할 수 있어요."),
    AI_DAILY_LIMIT("AI-002", HttpStatus.SERVICE_UNAVAILABLE,
            "현재 AI 기능 이용이 일시 중단되었어요. 번호 생성과 통계 조회는 계속 이용할 수 있어요."),
    AI_MONTHLY_COST_LIMIT("AI-003", HttpStatus.SERVICE_UNAVAILABLE,
            "현재 AI 기능 이용이 일시 중단되었어요. 번호 생성과 통계 조회는 계속 이용할 수 있어요."),
    AI_USER_LIMIT("AI-004", HttpStatus.TOO_MANY_REQUESTS,
            "오늘 사용할 수 있는 무료 횟수를 모두 사용하셨어요. 내일 다시 찾아와 주시면 기쁘게 도와드릴게요! 번호 생성과 통계 조회는 계속 이용하실 수 있어요."),
    AI_CALL_FAILED("AI-005", HttpStatus.SERVICE_UNAVAILABLE,
            "AI 응답을 불러오지 못했어요. 잠시 후 다시 시도해 주세요. 번호 생성과 기본 통계 분석은 계속 이용할 수 있어요."),

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
