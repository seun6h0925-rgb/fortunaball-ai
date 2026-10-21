package com.fortunaball.dream.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.exception.GlobalExceptionHandler;
import com.fortunaball.common.guest.GuestTokenArgumentResolver;
import com.fortunaball.dream.dto.DreamRequest;
import com.fortunaball.dream.dto.DreamResponse;
import com.fortunaball.dream.service.DreamMessages;
import com.fortunaball.dream.service.DreamService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        when(service.interpret(any(), any(), anyString())).thenReturn(new DreamResponse("PARTIAL_MATCH",
                DreamResponse.STEP_CONFIRM, null, "첫마디", "이 번호로 확정하시겠어요?", List.of(), "설명",
                List.of(new DreamResponse.NumberView(3, "SERVICE")), List.of(), List.of(3), 1,
                "tok-1", 30, 1100, null, DreamMessages.REPLIES_CONFIRM, List.of(), true, "기준", "고지", 4));

        mvc.perform(post("/api/ai/dream").header("X-Guest-Token", "guest-token-1234567890")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dream\":\"돼지가 집에 들어오는 꿈\",\"reply\":\"네, 채워 주세요\",\"step\":\"ASK_FILL\","
                                + "\"keep\":[3],\"gameCount\":1,\"confirmToken\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matchResult").value("PARTIAL_MATCH"))
                .andExpect(jsonPath("$.data.numbers[0].number").value(3))
                .andExpect(jsonPath("$.data.step").value("CONFIRM"))
                .andExpect(jsonPath("$.data.question").value("이 번호로 확정하시겠어요?"))
                .andExpect(jsonPath("$.data.confirmToken").value("tok-1"))
                .andExpect(jsonPath("$.data.keep[0]").value(3))
                .andExpect(jsonPath("$.data.gameCount").value(1))
                .andExpect(jsonPath("$.data.quickReplies[0]").value("확정할게요"))
                .andExpect(jsonPath("$.data.charged").value(true))
                .andExpect(jsonPath("$.data.remainingToday").value(4));
    }

    @Test
    void 요청_본문의_이어지는_값이_서비스까지_그대로_전달된다() throws Exception {
        when(service.interpret(any(), any(), anyString())).thenReturn(new DreamResponse("NO_MATCH",
                DreamResponse.STEP_TALK, null, "안녕하세요", null, List.of(), null, List.of(), List.of(), null, null,
                null, null, null, null, List.of(), List.of(), false, "기준", "고지", 5));

        mvc.perform(post("/api/ai/dream").header("X-Guest-Token", "guest-token-1234567890")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dream\":\"돼지 꿈\",\"reply\":\"3게임으로 나눠 주세요\",\"step\":\"ASK_OVERFLOW\","
                                + "\"keep\":[2,3,4],\"gameCount\":3,\"confirmToken\":\"tok-9\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<DreamRequest> captured = ArgumentCaptor.forClass(DreamRequest.class);
        verify(service).interpret(captured.capture(), any(), anyString());
        assertEquals("3게임으로 나눠 주세요", captured.getValue().reply());
        assertEquals(DreamResponse.STEP_ASK_OVERFLOW, captured.getValue().step());
        assertEquals(List.of(2, 3, 4), captured.getValue().keep());
        assertEquals(3, captured.getValue().gameCount());
        assertEquals("tok-9", captured.getValue().confirmToken());
    }

    @Test
    void 입력_오류는_400_공통_오류_형식() throws Exception {
        when(service.interpret(any(), any(), anyString())).thenThrow(
                new BusinessException(ErrorCode.INVALID_INPUT, "꿈 내용은 5자 이상 300자 이하로 입력해 주세요.", "길이 오류"));

        mvc.perform(post("/api/ai/dream").header("X-Guest-Token", "guest-token-1234567890")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dream\":\"돼지\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    @Test
    void 무료_횟수를_다_쓰면_429() throws Exception {
        when(service.interpret(any(), any(), anyString())).thenThrow(
                new BusinessException(ErrorCode.AI_USER_LIMIT, ErrorCode.AI_USER_LIMIT.getDefaultMessage(), "횟수 초과"));

        mvc.perform(post("/api/ai/dream").header("X-Guest-Token", "guest-token-1234567890")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dream\":\"돼지가 나온 꿈입니다\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("AI-004"));
    }
}
