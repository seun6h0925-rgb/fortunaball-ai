package com.fortunaball.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API의 공통 응답 형식. (API 명세서 3절)
 *  - 성공: success, data, message
 *  - 실패: success, errorCode, message  (+ traceId: 오류 추적 번호, 화면에는 보이지 않아도 된다)
 *
 * null 값은 응답에서 빼서 성공/실패 모양이 명세와 같게 보이게 한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String errorCode,
        String message,
        String traceId
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, "OK", null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, null, message, null);
    }

    public static ApiResponse<Void> fail(String errorCode, String message, String traceId) {
        return new ApiResponse<>(false, null, errorCode, message, traceId);
    }
}
