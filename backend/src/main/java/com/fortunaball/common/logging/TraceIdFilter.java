package com.fortunaball.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 "추적번호(traceId)"를 붙인다. 오류를 쉽게 찾기 위한 핵심 장치.
 *
 * 흐름: 요청 시작 → traceId 생성 → 로그 한 줄마다 [traceId]가 같이 찍힘 → 응답 헤더 X-Trace-Id와
 *       오류 응답 본문의 traceId에도 같은 값이 들어감.
 * 사용 방법: 화면/응답에 나온 traceId를 로그 파일(logs/app.log)에서 검색하면 그 요청의 모든 로그가 나온다.
 *
 * 주의(개인정보): 쿼리스트링과 본문은 로그에 남기지 않는다.
 *   지도 API는 위치(위도·경도)가 쿼리로 올 수 있어서, 경로(path)만 기록한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 외부에서 들어온 값은 믿지 않고 항상 새로 만든다. (로그 위조 방지)
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = System.currentTimeMillis() - start;
            log.info("REQ {} {} -> {} ({}ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), ms);
            MDC.remove(TRACE_ID_KEY);   // 스레드 재사용 시 이전 요청의 번호가 남지 않게 반드시 지운다.
        }
    }
}
