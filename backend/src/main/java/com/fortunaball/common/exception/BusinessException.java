package com.fortunaball.common.exception;

/**
 * 우리가 일부러 던지는 오류. (예: 대상 회차가 바뀜, 임시 확정 토큰 만료)
 * 사용자에게 보여줄 문구는 message, 개발자가 원인을 볼 때 쓰는 내용은 detail에 따로 적는다.
 * detail은 로그에만 남고 화면에는 나가지 않는다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public BusinessException(ErrorCode errorCode, String userMessage) {
        super(userMessage);
        this.errorCode = errorCode;
        this.detail = null;
    }

    public BusinessException(ErrorCode errorCode, String userMessage, String detail) {
        super(userMessage);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public String getDetail() { return detail; }
}
