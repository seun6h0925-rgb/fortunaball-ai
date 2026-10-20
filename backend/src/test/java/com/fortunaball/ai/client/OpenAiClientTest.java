package com.fortunaball.ai.client;

import com.fortunaball.ai.AiTestFixtures;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OpenAI 호출부 확인. 진짜 OpenAI 가 아니라 "가짜 서버"(MockRestServiceServer)로 요청 모양과 응답 처리를 본다.
 * 핵심: 오류가 나도 키·응답 본문이 예외 메시지·상세에 새지 않는다.
 */
class OpenAiClientTest {

    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private MockRestServiceServer server;
    private OpenAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiClient(AiTestFixtures.props("test-key-123"), builder);
    }

    private static AiRequest request() {
        return new AiRequest(AiRole.ANSWER, "지시문", "질문입니다", 100);
    }

    @Test
    void 성공하면_답변_모델_토큰을_돌려준다() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key-123"))
                .andExpect(jsonPath("$.model").value("gpt-6-luna"))
                .andExpect(jsonPath("$.max_completion_tokens").value(100))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("지시문"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("질문입니다"))
                .andRespond(withSuccess("""
                        {"model":"gpt-6-luna-2026","choices":[{"message":{"content":"  답변이에요  "},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":123,"completion_tokens":45}}
                        """, MediaType.APPLICATION_JSON));

        AiResponse response = client.complete(request());

        assertEquals("답변이에요", response.text());           // 앞뒤 공백은 지운다
        assertEquals("gpt-6-luna-2026", response.modelName());  // 실제 응답한 모델 이름
        assertEquals(123, response.inputTokens());
        assertEquals(45, response.outputTokens());
        server.verify();
    }

    @Test
    void 분류용_요청은_분류용_모델을_쓴다() {
        client = new OpenAiClient(AiTestFixtures.propsWithModels("k", "classifier-x", "answer-y"), rebuild());
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.model").value("classifier-x"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"A\"}}]}", MediaType.APPLICATION_JSON));

        AiResponse response = client.complete(new AiRequest(AiRole.CLASSIFIER, "", "분류해줘", 10));

        assertEquals("A", response.text());
        assertEquals("classifier-x", response.modelName());   // 응답에 model 이 없으면 요청한 모델 이름을 쓴다
        assertEquals(0, response.inputTokens());               // usage 가 없으면 0
    }

    /** 분류용 모델 확인용: 새 가짜 서버를 다시 붙인 builder 를 만든다 */
    private RestClient.Builder rebuild() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return builder;
    }

    @Test
    void 키가_틀려_401이면_AI_005이고_키와_본문이_새지_않는다() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"Incorrect API key provided: sk-secret999\","
                        + "\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}"));

        BusinessException e = assertThrows(BusinessException.class, () -> client.complete(request()));

        assertEquals(ErrorCode.AI_CALL_FAILED, e.getErrorCode());
        assertTrue(e.getDetail().contains("HTTP 401"));
        assertTrue(e.getDetail().contains("invalid_api_key"));       // 원인 파악용 type/code 는 남긴다
        assertFalse(e.getDetail().contains("secret999"));            // 본문(키 일부)은 남기지 않는다
        assertFalse(e.getMessage().contains("secret999"));
        assertFalse(e.getDetail().contains("test-key-123"));
    }

    @Test
    void 서버_오류_500이면_AI_005이다() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        BusinessException e = assertThrows(BusinessException.class, () -> client.complete(request()));

        assertEquals(ErrorCode.AI_CALL_FAILED, e.getErrorCode());
        assertTrue(e.getDetail().contains("HTTP 500"));
    }

    @Test
    void 요청이_너무_많아_429여도_AI_005로_통일한다() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"type\":\"rate_limit_error\",\"code\":\"rate_limit_exceeded\"}}"));

        BusinessException e = assertThrows(BusinessException.class, () -> client.complete(request()));

        assertEquals(ErrorCode.AI_CALL_FAILED, e.getErrorCode());
        assertTrue(e.getDetail().contains("rate_limit_exceeded"));
    }

    @Test
    void 내용이_비어_있으면_실패로_본다() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":\"\"},\"finish_reason\":\"length\"}]}",
                MediaType.APPLICATION_JSON));

        BusinessException e = assertThrows(BusinessException.class, () -> client.complete(request()));

        assertEquals(ErrorCode.AI_CALL_FAILED, e.getErrorCode());
        assertTrue(e.getDetail().contains("빈 응답"));
        assertTrue(e.getDetail().contains("length"));   // 답변 길이 상한 때문인지 알 수 있게 남긴다
    }

    @Test
    void 키가_없으면_호출하지_않고_AI_001이다() {
        OpenAiClient noKey = new OpenAiClient(AiTestFixtures.props(""), RestClient.builder());

        BusinessException e = assertThrows(BusinessException.class, () -> noKey.complete(request()));

        assertEquals(ErrorCode.AI_NOT_CONFIGURED, e.getErrorCode());
    }

    @Test
    void 오류_요약은_본문이_깨져도_안전하다() {
        assertEquals("(본문 해석 불가)", OpenAiClient.errorSummary("<html>bad gateway</html>"));
        assertEquals("(본문 해석 불가)", OpenAiClient.errorSummary(null));
        assertEquals("type=- code=-", OpenAiClient.errorSummary("{}"));
    }
}
