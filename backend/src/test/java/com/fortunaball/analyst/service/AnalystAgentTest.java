package com.fortunaball.analyst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.ai.AiSession;
import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiResponse;
import com.fortunaball.analyst.domain.AnalystEvidence;
import com.fortunaball.analyst.domain.ChatTurn;
import com.fortunaball.analyst.domain.EvidenceItem;
import com.fortunaball.analyst.domain.QuestionType;
import com.fortunaball.analyst.service.AnalystAgent.Outcome;
import com.fortunaball.analyst.service.AnalystAgent.Result;
import com.fortunaball.analyst.service.AnalystTools.ToolResult;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.generate.dto.GenerateResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** AI 가 "조회 → 답변" 으로 이어지는 루프가 올바르게 돌고, 이상한 응답에도 멈추는지 확인한다. */
class AnalystAgentTest {

    private static final GuestToken GUEST = GuestToken.of("guest-aaaaaaaaaaaaaaaa");

    private AiSession session;
    private AnalystTools tools;
    private AnalystAgent agent;

    @BeforeEach
    void setUp() {
        session = mock(AiSession.class);
        tools = mock(AnalystTools.class);
        agent = new AnalystAgent(new AnalystPromptBuilder(), tools, new ObjectMapper());
    }

    private static AiResponse reply(String text) {
        return new AiResponse(text, "model", 10, 10);
    }

    private void script(String... texts) {
        var stub = when(session.call(any(AiRequest.class)));
        stub = stub.thenReturn(reply(texts[0]));
        for (int i = 1; i < texts.length; i++) {
            stub = stub.thenReturn(reply(texts[i]));
        }
    }

    private static ToolResult okResult(String name) {
        return ToolResult.ok(name, new AnalystEvidence(QuestionType.ROUND_ANALYSIS, "기준",
                List.of(new EvidenceItem("항목", "값")), List.of("출처"), true));
    }

    private static GenerateResponse generated() {
        return new GenerateResponse("tok", 30, Instant.parse("2026-10-20T07:30:00Z"),
                List.of(List.of(7, 10, 20, 30, 40, 45)), null, null, 1247, LocalDate.of(2026, 10, 24), 1246);
    }

    private Result run() {
        return agent.run(session, List.of(), "질문", GUEST, "ip");
    }

    @Test
    void 조회_없이_바로_답하면_한_번만_부른다() {
        script("{\"action\":\"answer\",\"text\":\"안녕하세요!\"}");
        Result r = run();
        assertEquals(Outcome.ANSWER, r.outcome());
        assertEquals("안녕하세요!", r.text());
        verify(session, times(1)).call(any(AiRequest.class));
        verify(tools, never()).run(any(), any(), any(), any(), any());
    }

    @Test
    void 조회_후_결과를_보고_답한다() {
        script("{\"action\":\"tools\",\"calls\":[{\"name\":\"round_info\",\"args\":{\"roundNo\":1000}}]}",
                "{\"action\":\"answer\",\"text\":\"1000회는 이래요\"}");
        when(tools.run(eq("round_info"), any(), eq(GUEST), eq("ip"), any())).thenReturn(okResult("round_info"));

        Result r = run();

        assertEquals(Outcome.ANSWER, r.outcome());
        assertEquals(1, r.okResults().size());
        verify(session, times(2)).call(any(AiRequest.class));
    }

    @Test
    void 두번째_호출의_프롬프트에_조회_결과가_들어간다() {
        script("{\"action\":\"tools\",\"calls\":[{\"name\":\"round_info\",\"args\":{}}]}",
                "{\"action\":\"answer\",\"text\":\"끝\"}");
        when(tools.run(any(), any(), any(), any(), any())).thenReturn(okResult("round_info"));

        run();

        var captor = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
        verify(session, times(2)).call(captor.capture());
        assertTrue(captor.getAllValues().get(1).userPrompt().contains("round_info"));
    }

    @Test
    void 회차_조회_후_번호_생성까지_이어진다() {
        script("{\"action\":\"tools\",\"calls\":[{\"name\":\"round_info\",\"args\":{\"roundNo\":1000}}]}",
                "{\"action\":\"tools\",\"calls\":[{\"name\":\"generate_numbers\",\"args\":{\"include\":[7]}}]}",
                "{\"action\":\"answer\",\"text\":\"만들었어요\"}");
        when(tools.run(eq("round_info"), any(), any(), any(), any())).thenReturn(okResult("round_info"));
        when(tools.run(eq("generate_numbers"), any(), any(), any(), any()))
                .thenReturn(new ToolResult("generate_numbers", okResult("x").evidence(), null, generated()));

        Result r = run();

        assertEquals(Outcome.ANSWER, r.outcome());
        assertNotNull(r.generated());
        assertEquals("tok", r.generated().confirmToken());
    }

    @Test
    void 번호_생성은_한_질문에_한_번만() {
        script("{\"action\":\"tools\",\"calls\":[{\"name\":\"generate_numbers\",\"args\":{}}]}",
                "{\"action\":\"tools\",\"calls\":[{\"name\":\"generate_numbers\",\"args\":{}}]}",
                "{\"action\":\"answer\",\"text\":\"정리\"}");
        when(tools.run(eq("generate_numbers"), any(), any(), any(), any()))
                .thenReturn(new ToolResult("generate_numbers", okResult("x").evidence(), null, generated()));

        Result r = run();

        verify(tools, times(1)).run(eq("generate_numbers"), any(), any(), any(), any());   // 두 번째는 서버가 막음
        assertEquals(Outcome.ANSWER, r.outcome());
        assertEquals(2, r.toolResults().size());
        assertTrue(r.toolResults().get(1).error() != null);
    }

    @Test
    void 한_번에_조회는_3개까지만() {
        script("{\"action\":\"tools\",\"calls\":[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"},{\"name\":\"d\"}]}",
                "{\"action\":\"answer\",\"text\":\"끝\"}");
        when(tools.run(any(), any(), any(), any(), any())).thenReturn(okResult("x"));
        run();
        verify(tools, times(3)).run(any(), any(), any(), any(), any());
    }

    @Test
    void 주제가_다르면_OFF_TOPIC() {
        script("{\"action\":\"off_topic\"}");
        Result r = run();
        assertEquals(Outcome.OFF_TOPIC, r.outcome());
        assertNull(r.text());
    }

    @Test
    void 가벼운_인사_잡담은_SMALL_TALK() {
        script("{\"action\":\"small_talk\",\"text\":\"반가워요! 번호 통계가 궁금하면 물어보세요.\"}");
        Result r = run();
        assertEquals(Outcome.SMALL_TALK, r.outcome());
        assertEquals("반가워요! 번호 통계가 궁금하면 물어보세요.", r.text());
    }

    @Test
    void 인사_답이_비어_있으면_주제_이탈로_본다() {
        script("{\"action\":\"small_talk\",\"text\":\"  \"}");
        assertEquals(Outcome.OFF_TOPIC, run().outcome());
    }

    @Test
    void JSON_없이_글로만_답해도_답변으로_쓴다() {
        script("그냥 글로 답했어요");
        Result r = run();
        assertEquals(Outcome.ANSWER, r.outcome());
        assertEquals("그냥 글로 답했어요", r.text());
    }

    @Test
    void 코드블록으로_감싸도_읽는다() {
        script("```json\n{\"action\":\"answer\",\"text\":\"네\"}\n```");
        assertEquals("네", run().text());
    }

    @Test
    void 깨진_JSON이나_빈_응답은_포기() {
        script("{\"action\":");
        assertEquals(Outcome.GAVE_UP, run().outcome());
        script("{\"action\":\"answer\",\"text\":\"\"}");
        assertEquals(Outcome.GAVE_UP, run().outcome());
        script("{\"action\":\"fly\"}");
        assertEquals(Outcome.GAVE_UP, run().outcome());
    }

    @Test
    void 계속_조회만_고르면_4번에서_멈추고_포기() {
        script("{\"action\":\"tools\",\"calls\":[{\"name\":\"round_info\",\"args\":{}}]}");
        when(tools.run(any(), any(), any(), any(), any())).thenReturn(okResult("round_info"));

        Result r = run();

        assertEquals(Outcome.GAVE_UP, r.outcome());
        verify(session, times(AnalystAgent.MAX_STEPS)).call(any(AiRequest.class));
        verify(tools, times(AnalystAgent.MAX_STEPS - 1)).run(any(), any(), any(), any(), any());   // 마지막 단계의 조회는 실행하지 않는다
    }
}
