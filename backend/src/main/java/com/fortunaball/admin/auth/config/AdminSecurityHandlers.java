package com.fortunaball.admin.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.logging.TraceIdFilter;
import com.fortunaball.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * 관리자 API 접근이 막혔을 때의 응답. 다른 API 와 같은 JSON 형식(ApiResponse)으로 준다.
 *  - 로그인 안 함·세션 만료 → 401 (COMMON-004 "로그인이 필요해요.")
 *  - 변조 요청 방지(CSRF) 토큰이 없거나 틀림 → 403 (COMMON-005)
 *  - 로그인은 했지만 권한이 없음 → 403 (COMMON-005)
 *
 * 스프링 보안 필터는 컨트롤러 앞단에서 동작해서 GlobalExceptionHandler 가 받지 못하므로 여기서 직접 쓴다.
 */
@Component
public class AdminSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityHandlers.class);

    static final String CSRF_MESSAGE = "요청을 확인하지 못했어요. 화면을 새로 고친 뒤 다시 시도해 주세요.";

    private final ObjectMapper mapper;

    public AdminSecurityHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("[{}] 관리자 API 로그인 필요: {} {}", ErrorCode.UNAUTHORIZED.getCode(), request.getMethod(),
                request.getRequestURI());
        write(response, ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getDefaultMessage());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        boolean csrf = accessDeniedException instanceof CsrfException;
        log.warn("[{}] 관리자 API 접근 거부({}): {} {}", ErrorCode.FORBIDDEN.getCode(), csrf ? "CSRF" : "권한",
                request.getMethod(), request.getRequestURI());
        write(response, ErrorCode.FORBIDDEN, csrf ? CSRF_MESSAGE : ErrorCode.FORBIDDEN.getDefaultMessage());
    }

    private void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(),
                ApiResponse.fail(code.getCode(), message, MDC.get(TraceIdFilter.TRACE_ID_KEY)));
    }
}
