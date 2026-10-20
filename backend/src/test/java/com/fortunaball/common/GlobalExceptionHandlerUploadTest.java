package com.fortunaball.common;

import com.fortunaball.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 사진 업로드 관련 공통 오류 응답(용량 초과 413, 사진 칸 누락 400) 확인. DB 없이 돌아간다. */
class GlobalExceptionHandlerUploadTest {

    @RestController
    static class DummyUploadController {
        @PostMapping("/test/upload")
        public String upload(@RequestParam("photo") MultipartFile photo) {
            return "ok";
        }

        @PostMapping("/test/too-large")
        public String tooLarge() {
            // 서버 설정 한도(spring.servlet.multipart)를 넘으면 스프링이 이 예외를 던진다.
            throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyUploadController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 용량_초과는_413_과_COMMON009() throws Exception {
        mockMvc.perform(multipart("/test/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON-009"));
    }

    @Test
    void 사진_칸이_없으면_400_과_COMMON001() throws Exception {
        mockMvc.perform(multipart("/test/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }
}
