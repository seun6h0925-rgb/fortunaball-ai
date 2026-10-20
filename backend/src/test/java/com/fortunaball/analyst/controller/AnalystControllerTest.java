package com.fortunaball.analyst.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunaball.analyst.domain.EvidenceItem;
import com.fortunaball.analyst.dto.AnalystResponse;
import com.fortunaball.analyst.service.AnalystService;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.exception.GlobalExceptionHandler;
import com.fortunaball.common.guest.GuestTokenArgumentResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnalystControllerTest {

    private AnalystService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AnalystService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AnalystController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new GuestTokenArgumentResolver())
                .build();
    }

    @Test
    void 정상_요청은_성공_응답으로_감싸서_돌려준다() throws Exception {
        when(service.answer(any(), any(), anyString())).thenReturn(new AnalystResponse("STAT_ANALYSIS", null, null, "설명",
                null, new AnalystResponse.Evidence("기준", List.of(new EvidenceItem("홀짝", "홀수 3개"))), List.of("출처"), "고지", 4, "설명"));

        mvc.perform(post("/api/ai/analyst").header("X-Guest-Token", "guest-token-1234567890")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"그 회차로 번호 만들어줘\",\"context\":null,\"lat\":null,\"lng\":null,"
                                + "\"history\":[{\"role\":\"user\",\"content\":\"제일 컸던 회차\"},{\"role\":\"assistant\",\"content\":\"1000회예요\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionType").value("STAT_ANALYSIS"))
                .andExpect(jsonPath("$.data.answer").value("설명"))
                .andExpect(jsonPath("$.data.evidence.items[0].label").value("홀짝"))
                .andExpect(jsonPath("$.data.remainingToday").value(4))
                .andExpect(jsonPath("$.data.historyText").value("설명"));
    }

    @Test
    void 입력_오류는_400_공통_오류_형식() throws Exception {
        when(service.answer(any(), any(), anyString())).thenThrow(
                new BusinessException(ErrorCode.INVALID_INPUT, "질문을 입력해 주세요.", "길이 오류"));

        mvc.perform(post("/api/ai/analyst").header("X-Guest-Token", "guest-token-1234567890")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    @Test
    void 무료_횟수를_다_쓰면_429() throws Exception {
        when(service.answer(any(), any(), anyString())).thenThrow(
                new BusinessException(ErrorCode.AI_USER_LIMIT, ErrorCode.AI_USER_LIMIT.getDefaultMessage(), "횟수 초과"));

        mvc.perform(post("/api/ai/analyst").header("X-Guest-Token", "guest-token-1234567890")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"번호 분석\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("AI-004"));
    }

    @Test
    void 게스트_토큰이_없으면_400() throws Exception {
        mvc.perform(post("/api/ai/analyst").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"번호 분석\"}"))
                .andExpect(status().isBadRequest());
    }
}
