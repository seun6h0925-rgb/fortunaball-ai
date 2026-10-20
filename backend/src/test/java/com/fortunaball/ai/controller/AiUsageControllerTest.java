package com.fortunaball.ai.controller;

import com.fortunaball.ai.AiGateway;
import com.fortunaball.ai.usage.AiUsageSnapshot;
import com.fortunaball.common.exception.GlobalExceptionHandler;
import com.fortunaball.common.guest.GuestTokenArgumentResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/ai/usage 의 입출력 확인. (DB·서버 없이 컨트롤러만 확인한다)
 * 핵심: 비회원 토큰이 없으면 400(COMMON-001), 있으면 남은 횟수를 돌려주고, 관리자 값은 사용자가 정할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class AiUsageControllerTest {

    private static final String TOKEN = "11111111-2222-3333-4444-555555555555";

    @Mock
    private AiGateway gateway;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // 실제 서버(Spring Boot)처럼 날짜를 "2026-10-21T00:00:00" 문자열로 내보내게 맞춘다. (테스트용 MockMvc 는 기본이 배열이다)
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new AiUsageController(gateway))
                .setCustomArgumentResolvers(new GuestTokenArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 남은_횟수를_돌려준다() throws Exception {
        when(gateway.usage(anyString(), anyString(), anyBoolean())).thenReturn(
                new AiUsageSnapshot(false, 5, 2, 3, LocalDateTime.of(2026, 10, 21, 0, 0)));

        mvc.perform(get("/api/ai/usage").header("X-Guest-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unlimited").value(false))
                .andExpect(jsonPath("$.data.dailyFreeLimit").value(5))
                .andExpect(jsonPath("$.data.usedToday").value(2))
                .andExpect(jsonPath("$.data.remainingToday").value(3))
                .andExpect(jsonPath("$.data.resetAt").value("2026-10-21T00:00:00"));
    }

    @Test
    void 관리자_여부는_항상_false로_넘긴다() throws Exception {
        when(gateway.usage(anyString(), anyString(), anyBoolean())).thenReturn(
                new AiUsageSnapshot(false, 5, 0, 5, LocalDateTime.of(2026, 10, 21, 0, 0)));

        mvc.perform(get("/api/ai/usage").header("X-Guest-Token", TOKEN).param("admin", "true"))
                .andExpect(status().isOk());

        // 요청에 admin=true 를 붙여도 소용없다. 관리자는 로그인으로만 판단한다.
        verify(gateway).usage(org.mockito.ArgumentMatchers.eq(TOKEN), anyString(),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void 비회원_토큰이_없으면_400이다() throws Exception {
        mvc.perform(get("/api/ai/usage"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));

        verify(gateway, never()).usage(anyString(), anyString(), anyBoolean());
    }
}
