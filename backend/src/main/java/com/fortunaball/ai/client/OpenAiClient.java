package com.fortunaball.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI(chat/completions) 호출 구현체. 응답이 다 만들어진 뒤 한 번에 받는다. (스트리밍 없음)
 * 📚 배운 내용: RestClient - 스프링이 기본으로 주는 HTTP 호출 도구. 라이브러리를 새로 추가하지 않았다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 키는 요청 헤더(Authorization)에만 쓴다. 로그·오류 상세·예외 메시지에 키나 응답 본문을 그대로 남기지 않는다.
 *    (OpenAI 의 401 응답 본문에는 키 일부가 들어 있다) 오류는 상태 코드와 error.type / error.code 만 남긴다.
 *  - 실패하면 모두 BusinessException(AI-005, 503) 하나로 바꿔 던진다. 사용자에게는 "잠시 후 다시 시도" 안내만 나간다.
 *  - 자동 재시도는 하지 않는다. (비용이 두 번 나갈 수 있어서, 재시도는 사용자가 버튼으로 한다)
 *  - 답변 길이 상한은 max_completion_tokens 로 건다. (일부 모델은 이 이름만 받는다)
 *  - 한도 검사·사용 기록은 여기서 하지 않는다. ai.AiGateway 가 맡는다.
 */
@Component
@EnableConfigurationProperties(AiProperties.class)
public class OpenAiClient implements AiClient {

    private static final String PATH = "/chat/completions";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final String USER_MESSAGE = "AI 응답을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AiProperties props;
    private final RestClient http;

    @Autowired
    public OpenAiClient(AiProperties props) {
        this(props, RestClient.builder().requestFactory(timeoutFactory(props)));
    }

    /** 테스트용: 가짜 서버(MockRestServiceServer)가 붙은 builder 를 넣는다 */
    OpenAiClient(AiProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.baseUrl(props.baseUrl()).build();
    }

    private static JdkClientHttpRequestFactory timeoutFactory(AiProperties props) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, props.timeoutSeconds())));
        return factory;
    }

    @Override
    public AiResponse complete(AiRequest request) {
        if (!props.hasApiKey()) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED, ErrorCode.AI_NOT_CONFIGURED.getDefaultMessage(),
                    "AI_API_KEY 가 비어 있음");
        }
        String model = props.modelFor(request.role());
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())),
                "max_completion_tokens", request.maxOutputTokens());

        JsonNode json;
        try {
            json = http.post()
                    .uri(PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // 응답은 왔지만 오류 상태(401·404·429·5xx 등). 본문은 통째로 남기지 않는다.
            throw failure("HTTP " + e.getStatusCode().value() + " " + errorSummary(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            // 연결 실패·시간 초과 등. 예외 메시지에 주소 정도만 들어 있고 키는 없다.
            throw failure("연결 실패 또는 시간 초과: " + e.getClass().getSimpleName());
        }

        String text = json == null ? "" : json.path("choices").path(0).path("message").path("content").asText("");
        if (text.isBlank()) {
            // 답변 길이 상한이 너무 작아 내용이 비는 경우도 여기에 걸린다.
            throw failure("빈 응답 (finish_reason="
                    + (json == null ? "-" : json.path("choices").path(0).path("finish_reason").asText("-")) + ")");
        }
        return new AiResponse(
                text.trim(),
                json.path("model").asText(model),
                json.path("usage").path("prompt_tokens").asInt(0),
                json.path("usage").path("completion_tokens").asInt(0));
    }

    private static BusinessException failure(String detail) {
        return new BusinessException(ErrorCode.AI_CALL_FAILED, USER_MESSAGE, detail);
    }

    /** 오류 본문에서 error.type / error.code 만 꺼낸다. (본문 전체에는 키 일부나 요청 내용이 들어 있을 수 있다) */
    static String errorSummary(String responseBody) {
        try {
            JsonNode error = JSON.readTree(responseBody).path("error");
            return "type=" + error.path("type").asText("-") + " code=" + error.path("code").asText("-");
        } catch (Exception e) {
            return "(본문 해석 불가)";
        }
    }
}
