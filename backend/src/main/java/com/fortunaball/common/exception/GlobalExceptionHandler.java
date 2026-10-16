package com.fortunaball.common.exception;

import com.fortunaball.common.logging.TraceIdFilter;
import com.fortunaball.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 📚 배운 내용: 예외 처리기(@RestControllerAdvice) — 컨트롤러에서 난 오류를 한 곳에서 모아 같은 형식으로 응답한다.
 *
 * 원칙
 *  1) 사용자에게는 짧은 안내 문구 + 오류 코드 + traceId만 준다. 스택 트레이스·SQL·내부 경로는 절대 내보내지 않는다.
 *  2) 로그에는 traceId와 함께 원인을 모두 남긴다. (4xx는 WARN 한 줄, 5xx는 ERROR + 스택)
 *  3) 입력값 오류 메시지에 사용자가 보낸 값을 그대로 넣지 않는다. (위치·개인정보가 로그에 남는 것 방지)
 *
 * 새 오류를 추가할 때: ErrorCode에 코드를 추가하고 BusinessException으로 던지면 여기서 자동 처리된다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- 우리가 일부러 던진 오류 ----
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("[{}] {} / detail={}", code.getCode(), e.getMessage(), e.getDetail(), e);
        } else {
            log.warn("[{}] {} / detail={}", code.getCode(), e.getMessage(), e.getDetail());
        }
        return build(code, e.getMessage());
    }

    // ---- 입력값 오류 (400) ----
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse(ErrorCode.INVALID_INPUT.getDefaultMessage());
        log.warn("[{}] 요청 본문 검증 실패: fields={}", ErrorCode.INVALID_INPUT.getCode(),
                e.getBindingResult().getFieldErrors().stream().map(fe -> fe.getField()).toList());
        return build(ErrorCode.INVALID_INPUT, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e) {
        log.warn("[{}] 파라미터 검증 실패: paths={}", ErrorCode.INVALID_INPUT.getCode(),
                e.getConstraintViolations().stream().map(v -> v.getPropertyPath().toString()).toList());
        return build(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getDefaultMessage());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        // 값 자체는 로그에 남기지 않고 오류 종류만 남긴다.
        log.warn("[{}] 잘못된 요청: {}", ErrorCode.INVALID_INPUT.getCode(), e.getClass().getSimpleName());
        return build(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getDefaultMessage());
    }

    // ---- 없는 주소 / 잘못된 요청 방식 ----
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        log.warn("[{}] 없는 주소: {}", ErrorCode.NOT_FOUND.getCode(), e.getResourcePath());
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getDefaultMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("[{}] 지원하지 않는 요청 방식: {}", ErrorCode.METHOD_NOT_ALLOWED.getCode(), e.getMethod());
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage());
    }

    // ---- 예상하지 못한 오류 (500) : 마지막 안전망 ----
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        // 여기에 걸리면 코드 버그이거나 DB·외부 서비스 문제다. 스택 트레이스를 모두 남긴다.
        log.error("[{}] 예상하지 못한 오류", ErrorCode.INTERNAL_ERROR.getCode(), e);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.getCode(), message, traceId));
    }
}
