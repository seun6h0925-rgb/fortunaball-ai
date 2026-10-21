package com.fortunaball.content.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.exception.GlobalExceptionHandler;
import com.fortunaball.content.dto.ContentResponse;
import com.fortunaball.content.service.ContentQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** GET /api/contents/{contentType} 의 입출력 확인 (DB·서버 없이 컨트롤러만). */
class ContentControllerTest {

    private ContentQueryService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ContentQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ContentController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 정상_조회는_공통_성공_형식으로_돌려준다() throws Exception {
        when(service.getByType("CLAIM_GUIDE")).thenReturn(
                new ContentResponse("CLAIM_GUIDE", "당첨금 수령 가이드", "## 1등\n안내", null));

        mvc.perform(get("/api/contents/CLAIM_GUIDE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contentType").value("CLAIM_GUIDE"))
                .andExpect(jsonPath("$.data.title").value("당첨금 수령 가이드"))
                .andExpect(jsonPath("$.data.content").value("## 1등\n안내"));
    }

    @Test
    void 알_수_없는_종류는_400_공통_오류_형식() throws Exception {
        when(service.getByType("ABC")).thenThrow(
                new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 안내 종류예요.", "contentType=ABC"));

        mvc.perform(get("/api/contents/ABC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    @Test
    void 글이_없으면_404_공통_오류_형식() throws Exception {
        when(service.getByType("FAQ")).thenThrow(
                new BusinessException(ErrorCode.NOT_FOUND, "안내 내용을 준비 중이에요.", "없음"));

        mvc.perform(get("/api/contents/FAQ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON-002"));
    }
}
