package com.fortunaball.common;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공통 오류 응답이 명세(success, errorCode, message)대로 나가는지 확인한다.
 * DB 없이 돌아가므로 세팅 직후 가장 먼저 `gradlew test` 로 실행해 볼 수 있다.
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class DummyController {
        @GetMapping("/test/business")
        public String business() {
            throw new BusinessException(ErrorCode.CONFLICT, "대상 회차가 바뀌었어요.", "테스트용 상세 내용");
        }

        @GetMapping("/test/boom")
        public String boom() {
            throw new IllegalStateException("내부 비밀 내용");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 업무_오류는_정해진_상태와_코드로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON-006"))
                .andExpect(jsonPath("$.message").value("대상 회차가 바뀌었어요."));
    }

    @Test
    void 예상하지_못한_오류는_내부_내용을_숨기고_500으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("COMMON-999"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }

    @Test
    void 오류_코드는_서로_겹치지_않는다() {
        Set<String> codes = Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode).collect(Collectors.toSet());
        assertThat(codes).hasSize(ErrorCode.values().length);
    }
}
