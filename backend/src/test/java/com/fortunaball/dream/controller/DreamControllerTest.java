package com.fortunaball.dream.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.exception.GlobalExceptionHandler;
import com.fortunaball.common.guest.GuestTokenArgumentResolver;
import com.fortunaball.dream.dto.DreamResponse;
import com.fortunaball.dream.service.DreamService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DreamControllerTest {

    private DreamService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DreamService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DreamController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new GuestTokenArgumentResolver())
                .build();
    }

    @Test
    void 정상_요청은_성공_응답으로_감싸서_돌려준다() throws Exception {
        when(service.interpret(any(), anyString(), anyString())).thenReturn(new DreamResponse("PARTIAL_MATCH", null, null,
                false, List.of(), "설명", List.of(new DreamResponse.NumberView(3, "SERVICE")), List.of(), "기준", "고지", 4));

        mvc.perform(post("/api/ai/dream").header("X-Guest-Token", "guest-token-1234567890")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dream\":\"돼지가 집에 들어오는 꿈\",\"supplementWithStats\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matchResult").value("PARTIAL_MATCH"))
                .andExpect(jsonPath("$.data.numbers[0].number").value(3))
                .andExpect(jsonPath("$.data.remainingToday").value(4));
    }

    @Test
    void 입력_오류는_400_공통_오류_형식() throws Exception {
        when(service.interpret(any(), anyString(), anyString())).thenThrow(
                new BusinessException(ErrorCode.INVALID_INPUT, "꿈 내용은 5자 이상 300자 이하로 입력해 주세요.", "길이 오류"));

        mvc.perform(post("/api/ai/dream").header("X-Guest-Token", "guest-token-1234567890")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dream\":\"돼지\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    @Test
    void 무료_횟수를_다_쓰면_429() throws Exception {
        when(service.interpret(any(), anyString(), anyString())).thenThrow(
                new BusinessException(ErrorCode.AI_USER_LIMIT, ErrorCode.AI_USER_LIMIT.getDefaultMessage(), "횟수 초과"));

        mvc.perform(post("/api/ai/dream").header("X-Guest-Token", "guest-token-1234567890")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dream\":\"돼지가 나온 꿈입니다\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("AI-004"));
    }
}
