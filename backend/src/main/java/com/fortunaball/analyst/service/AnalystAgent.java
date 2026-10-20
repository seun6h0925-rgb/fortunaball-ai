package com.fortunaball.analyst.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.ai.AiSession;
import com.fortunaball.ai.client.AiResponse;
import com.fortunaball.analyst.domain.ChatTurn;
import com.fortunaball.analyst.service.AnalystTools.ToolResult;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.generate.dto.GenerateResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 애널리스트의 "생각하는 루프". AI 가 질문을 보고 → 조회가 필요하면 조회 도구를 고르고 → 서버가 조회해 결과를 주고
 * → AI 가 그 결과를 보고 답하거나 한 번 더 조회한다. (예: "그 회차 번호로 번호 만들어줘" = 회차 조회 → 번호 생성 → 답)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 사용자 횟수는 질문 1번에 1회만 쓴다. (AiSession 이 한 요청 안의 여러 호출을 한 번으로 센다) 호출 기록은 호출마다 남는다.
 *  - AI 호출은 최대 MAX_STEPS 번. 마지막 단계에서는 "반드시 답하라"고 알리고, 그래도 조회를 또 고르면 포기(GAVE_UP)로 끝낸다.
 *  - AI 가 JSON 형식을 어기고 그냥 글로 답하면 그 글을 답변으로 쓴다. ("{" 가 하나도 없을 때만)
 *  - 번호 생성은 한 질문에 한 번만 한다. 두 번째 요청은 실패 결과로 돌려 AI 가 알아서 정리하게 한다.
 */
@Component
public class AnalystAgent {

    static final int MAX_STEPS = 4;
    static final int MAX_CALLS_PER_STEP = 3;

    /** 루프가 끝난 모양. ANSWER 면 text 가 답변, OFF_TOPIC 이면 주제 이탈, GAVE_UP 이면 답을 만들지 못함 */
    public enum Outcome { ANSWER, OFF_TOPIC, GAVE_UP }

    public record Result(Outcome outcome, String text, List<ToolResult> toolResults, GenerateResponse generated) {

        public List<ToolResult> okResults() {
            return toolResults.stream().filter(ToolResult::isOk).toList();
        }
    }

    private final AnalystPromptBuilder promptBuilder;
    private final AnalystTools tools;
    private final ObjectMapper mapper;

    public AnalystAgent(AnalystPromptBuilder promptBuilder, AnalystTools tools, ObjectMapper mapper) {
        this.promptBuilder = promptBuilder;
        this.tools = tools;
        this.mapper = mapper;
    }

    public Result run(AiSession session, List<ChatTurn> history, String question, GuestToken guest, String clientIp) {
        List<String> observations = new ArrayList<>();
        List<ToolResult> results = new ArrayList<>();
        GenerateResponse generated = null;

        for (int step = 1; step <= MAX_STEPS; step++) {
            int toolStepsLeft = MAX_STEPS - step;
            AiResponse response = session.call(promptBuilder.build(history, question, observations, toolStepsLeft));
            Decision decision = parse(response.text());

            switch (decision.action()) {
                case "off_topic":
                    return new Result(Outcome.OFF_TOPIC, null, results, generated);
                case "answer":
                    return new Result(Outcome.ANSWER, decision.text(), results, generated);
                case "tools":
                    if (toolStepsLeft == 0) {
                        return new Result(Outcome.GAVE_UP, null, results, generated);
                    }
                    for (JsonNode call : decision.calls()) {
                        ToolResult result;
                        if (AnalystTools.GENERATE_NUMBERS.equals(call.path("name").asText()) && generated != null) {
                            result = ToolResult.failed(AnalystTools.GENERATE_NUMBERS,
                                    "번호 생성은 한 질문에 한 번만 할 수 있어요. 이미 만든 번호로 답해 주세요.");
                        } else {
                            result = tools.run(call.path("name").asText(), call.get("args"), guest, clientIp);
                        }
                        results.add(result);
                        observations.add(result.toPromptText());
                        if (result.generated() != null) {
                            generated = result.generated();
                        }
                    }
                    break;
                default:
                    return new Result(Outcome.GAVE_UP, null, results, generated);
            }
        }
        return new Result(Outcome.GAVE_UP, null, results, generated);
    }

    // ------------------------------------------------------------------ AI 응답 읽기

    /** action: answer / off_topic / tools / invalid */
    record Decision(String action, String text, List<JsonNode> calls) {
    }

    Decision parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0) {
            // JSON 이 아니라 그냥 글로 답한 경우 ("{" 가 하나도 없을 때만 답변으로 인정한다)
            return text.isEmpty() ? new Decision("invalid", null, List.of()) : new Decision("answer", text, List.of());
        }
        if (end <= start) {
            // "{" 는 있는데 닫히지 않은 깨진 JSON → 화면에 그대로 내보내지 않고 포기 처리
            return new Decision("invalid", null, List.of());
        }
        try {
            JsonNode node = mapper.readTree(text.substring(start, end + 1));
            String action = node.path("action").asText("");
            switch (action) {
                case "off_topic":
                    return new Decision("off_topic", null, List.of());
                case "answer": {
                    String answer = node.path("text").asText("").trim();
                    return answer.isEmpty() ? new Decision("invalid", null, List.of()) : new Decision("answer", answer, List.of());
                }
                case "tools": {
                    List<JsonNode> calls = new ArrayList<>();
                    JsonNode array = node.path("calls");
                    if (array.isArray()) {
                        for (JsonNode call : array) {
                            if (calls.size() < MAX_CALLS_PER_STEP) {
                                calls.add(call);
                            }
                        }
                    }
                    return calls.isEmpty() ? new Decision("invalid", null, List.of()) : new Decision("tools", null, calls);
                }
                default:
                    return new Decision("invalid", null, List.of());
            }
        } catch (Exception e) {
            return new Decision("invalid", null, List.of());
        }
    }
}
