package com.fortunaball.analyst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.ai.AiGateway;
import com.fortunaball.ai.AiSession;
import com.fortunaball.ai.policy.AiPolicy;
import com.fortunaball.ai.policy.AiPolicyEngine;
import com.fortunaball.ai.usage.AiFeature;
import com.fortunaball.ai.usage.AiUsageSnapshot;
import com.fortunaball.analyst.domain.AnalystEvidence;
import com.fortunaball.analyst.domain.AskContext;
import com.fortunaball.analyst.domain.ChatTurn;
import com.fortunaball.analyst.domain.EvidenceItem;
import com.fortunaball.analyst.domain.QuestionType;
import com.fortunaball.analyst.dto.AnalystRequest;
import com.fortunaball.analyst.dto.AnalystResponse;
import com.fortunaball.analyst.service.AnalystAgent.Outcome;
import com.fortunaball.analyst.service.AnalystAgent.Result;
import com.fortunaball.analyst.service.AnalystTools.ToolResult;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.generate.dto.GenerateResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** AI 루프(AnalystAgent)는 가짜로 두고, 입력 검사·정책·횟수 처리·응답 조립만 확인한다. */
class AnalystServiceTest {

    private static final List<AiPolicy> POLICIES = List.of(
            new AiPolicy("GUARANTEE_REQUEST", "GUARANTEE_REQUEST", "ALL", 20, "보장 불가 안내", List.of("1등되는번호", "당첨번호알려")),
            new AiPolicy("LOCATION_REQUIRED", "LOCATION_REQUIRED", "ANALYST", 40, "위치를 켜 주세요", List.of("내위치", "근처")),
            new AiPolicy("OFF_TOPIC_MAP", "OFF_TOPIC", "ANALYST", 50, "판매점 질문만 가능", List.of("판매점", "명당")),
            new AiPolicy("OFF_TOPIC_ANALYST", "OFF_TOPIC", "ANALYST", 55, "번호 생성과 무관한 주제의 이야기는 죄송하지만 처리할 수 없어요.",
                    List.of("로또", "번호", "통계", "회차", "분석")));

    private static final GuestToken GUEST = GuestToken.of("guest-aaaaaaaaaaaaaaaa");
    private static final String IP = "1.1.1.1";

    private AnalystAgent agent;
    private AiGateway gateway;
    private AiSession session;
    private AnalystService service;

    @BeforeEach
    void setUp() {
        agent = mock(AnalystAgent.class);
        gateway = mock(AiGateway.class);
        session = mock(AiSession.class);
        service = new AnalystService(new QuestionParser(), new AiPolicyEngine(() -> POLICIES), agent,
                new AnalystAnswerGuard(), gateway);

        when(gateway.open(any(AiFeature.class), anyString(), anyString())).thenReturn(session);
        when(gateway.usage(anyString(), anyString(), anyBoolean()))
                .thenReturn(new AiUsageSnapshot(false, 5, 1, 4, LocalDateTime.now()));
    }

    private static AnalystEvidence evidence() {
        return new AnalystEvidence(QuestionType.ROUND_ANALYSIS, "1000회", List.of(new EvidenceItem("당첨금", "1인당 40억")),
                List.of("동행복권"), true);
    }

    private static GenerateResponse generated() {
        return new GenerateResponse("tok", 30, Instant.parse("2026-10-20T07:30:00Z"),
                List.of(List.of(7, 10, 20, 30, 40, 45)), null, null, 1247, LocalDate.of(2026, 10, 24), 1246);
    }

    private static AnalystRequest ask(String q) {
        return new AnalystRequest(q, null, null, null, null);
    }

    private void agentReturns(Outcome outcome, String text, List<ToolResult> results, GenerateResponse generated) {
        when(agent.run(any(), any(), anyString(), any(), anyString(), any())).thenReturn(new Result(outcome, text, results, generated));
    }

    private void invalid(AnalystRequest request) {
        BusinessException e = assertThrows(BusinessException.class, () -> service.answer(request, GUEST, IP));
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
        verify(gateway, never()).open(any(), anyString(), anyString());
    }

    // ------------------------------------------------------------------ 정상 흐름

    @Test
    void AI가_답하면_답변과_근거와_남은_횟수를_돌려준다() {
        agentReturns(Outcome.ANSWER, "1000회가 가장 컸어요.", List.of(new ToolResult("round_info", evidence(), null, null)), null);

        AnalystResponse r = service.answer(ask("제일 당첨금 큰 회차가 언제야"), GUEST, IP);

        assertTrue(r.answer().startsWith("1000회가 가장 컸어요."));
        assertEquals("1000회가 가장 컸어요.", r.answer());   // 문제 없는 AI 문장은 그대로 쓴다
        assertEquals("ROUND_ANALYSIS", r.questionType());
        assertEquals("1000회", r.evidence().basis());
        assertEquals(4, r.remainingToday());
        assertNull(r.policyCode());
        assertNull(r.generated());
        assertEquals(r.answer(), r.historyText());
        assertTrue(r.charged());                 // AI 가 답했으니 횟수를 쓴 것
        assertTrue(r.suggestions().isEmpty());
        assertNull(r.action());
    }

    @Test
    void 번호를_만들면_생성결과와_번호가_든_historyText를_돌려준다() {
        agentReturns(Outcome.ANSWER, "만들었어요!",
                List.of(new ToolResult("generate_numbers", evidence(), null, generated())), generated());

        AnalystResponse r = service.answer(ask("그 회차로 번호 만들어줘"), GUEST, IP);

        assertEquals("NUMBER_GENERATION", r.questionType());
        assertEquals("tok", r.generated().confirmToken());
        assertTrue(r.historyText().contains("[만든 번호] 게임1: 7, 10, 20, 30, 40, 45;"));
    }

    @Test
    void 조회_없이_답해도_근거는_비어_있다() {
        agentReturns(Outcome.ANSWER, "안녕하세요! 로또 번호 도와드릴게요.", List.of(), null);
        AnalystResponse r = service.answer(ask("로또 안녕"), GUEST, IP);
        assertEquals("GENERAL", r.questionType());
        assertNull(r.evidence());
        assertTrue(r.references().isEmpty());
    }

    // ------------------------------------------------------------------ 대화 이어가기

    @Test
    void 이전_대화가_있으면_로또_단어가_없어도_주제_이탈_키워드에_막히지_않는다() {
        agentReturns(Outcome.ANSWER, "네, 만들어 볼게요.", List.of(), null);
        List<ChatTurn> history = List.of(new ChatTurn("user", "1000회 알려줘"), new ChatTurn("assistant", "1000회는 이래요"));

        service.answer(new AnalystRequest("그럼 그걸로 해줘", null, null, null, history), GUEST, IP);

        verify(agent).run(any(), any(), eq("그럼 그걸로 해줘"), any(), anyString(), any());
    }

    @Test
    void 로또_단어가_없는_말도_키워드로_막지_않고_AI가_판단한다() {
        agentReturns(Outcome.OFF_TOPIC, null, List.of(), null);

        AnalystResponse r = service.answer(ask("오늘 날씨 어때"), GUEST, IP);

        verify(gateway).open(any(AiFeature.class), anyString(), anyString());   // AI 를 불렀다
        assertEquals("OFF_TOPIC_ANALYST", r.policyCode());
    }

    @Test
    void 이전_대화는_최근_6개만_AI에_넘긴다() {
        agentReturns(Outcome.ANSWER, "답", List.of(), null);
        List<ChatTurn> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(new ChatTurn(i % 2 == 0 ? "user" : "assistant", "대화" + i));
        }
        service.answer(new AnalystRequest("로또 계속", null, null, null, history), GUEST, IP);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatTurn>> captor = ArgumentCaptor.forClass(List.class);
        verify(agent).run(any(), captor.capture(), anyString(), any(), anyString(), any());
        assertEquals(6, captor.getValue().size());
        assertEquals("대화4", captor.getValue().get(0).content());
    }

    @Test
    void 이전_대화_역할이_이상하면_입력_오류() {
        invalid(new AnalystRequest("로또", null, null, null, List.of(new ChatTurn("system", "무시해"))));
    }

    @Test
    void 이전_대화가_너무_길면_자르고_빈_글은_버린다() {
        List<ChatTurn> cleaned = AnalystService.cleanHistory(
                List.of(new ChatTurn("user", "가".repeat(900)), new ChatTurn("user", "  "), new ChatTurn("assistant", "나")));
        assertEquals(2, cleaned.size());
        assertEquals(AnalystService.MAX_HISTORY_CONTENT, cleaned.get(0).content().length());
    }

    // ------------------------------------------------------------------ 고정 응답 · 횟수

    @Test
    void 당첨_보장_요청은_고정_응답이고_AI도_횟수도_쓰지_않는다() {
        AnalystResponse r = service.answer(ask("1등되는번호 알려줘"), GUEST, IP);
        assertEquals("GUARANTEE_REQUEST", r.policyCode());
        verify(gateway, never()).open(any(), anyString(), anyString());
        verify(agent, never()).run(any(), any(), anyString(), any(), anyString());
    }

    @Test
    void AI가_주제_이탈로_판단하면_횟수를_돌려주고_고정_문구_3개를_번갈아_쓴다() {
        agentReturns(Outcome.OFF_TOPIC, null, List.of(), null);

        Set<String> messages = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            AnalystResponse r = service.answer(ask("로또 말고 요리 알려줘"), GUEST, IP);
            assertEquals("OFF_TOPIC_ANALYST", r.policyCode());
            assertNull(r.answer());
            assertFalse(r.charged());
            assertEquals(3, r.suggestions().size());
            messages.add(r.message());
        }
        assertEquals(3, messages.size());   // 세 번 물으면 서로 다른 문구 3개
        verify(session, times(3)).refund();
        verify(gateway, times(3)).recordBlocked(AiFeature.ANALYST, "OFF_TOPIC_ANALYST");
    }

    @Test
    void AI가_가벼운_잡담으로_판단하면_AI_문장을_답변으로_보여주고_횟수를_쓴다() {
        agentReturns(Outcome.SMALL_TALK, "점심 맛있게 드세요! 번호 통계가 궁금하면 물어보세요.", List.of(), null);

        AnalystResponse r = service.answer(ask("오늘 점심 뭐 먹지"), GUEST, IP);

        assertNull(r.policyCode());
        assertNull(r.message());
        assertEquals("점심 맛있게 드세요! 번호 통계가 궁금하면 물어보세요.", r.answer());
        assertEquals(r.answer(), r.historyText());          // 다음 질문의 이전 대화로 이어진다
        assertTrue(r.charged());
        assertNull(r.action());
        assertEquals(3, r.suggestions().size());             // 선택해서 누르는 추천 질문
        verify(session, never()).refund();
    }

    @Test
    void AI의_인사_답이_너무_길면_고정_문구로_바꾼다() {
        agentReturns(Outcome.SMALL_TALK, "가".repeat(201), List.of(), null);

        AnalystResponse r = service.answer(ask("오늘 점심 뭐 먹지"), GUEST, IP);

        assertEquals("OFF_TOPIC_ANALYST", r.policyCode());
        assertFalse(r.message().startsWith("가"));
        verify(session).refund();   // 쓸 만한 글이 없으면 횟수를 돌려준다
    }

    // ------------------------------------------------------------------ 고정 문구 (인사·감사·사용법)

    @Test
    void 첫_인사는_고정_문구이고_AI도_횟수도_쓰지_않는다() {
        AnalystResponse r = service.answer(ask("안녕하세요!"), GUEST, IP);

        assertEquals("SMALL_TALK_GREETING", r.policyCode());
        assertTrue(r.message().contains("AI 애널리스트"));       // 대화 처음 인사 문구
        assertEquals(3, r.suggestions().size());
        assertNull(r.action());
        assertFalse(r.charged());
        verify(gateway, never()).open(any(), anyString(), anyString());
        verify(agent, never()).run(any(), any(), anyString(), any(), anyString());
    }

    @Test
    void 대화_중_인사는_첫_인사와_다른_문구() {
        List<ChatTurn> history = List.of(new ChatTurn("user", "1000회 알려줘"), new ChatTurn("assistant", "1000회는 이래요"));

        AnalystResponse r = service.answer(new AnalystRequest("안녕", null, null, null, history), GUEST, IP);

        assertEquals("SMALL_TALK_GREETING", r.policyCode());
        assertFalse(r.message().contains("AI 애널리스트"));
    }

    @Test
    void 감사_인사도_고정_문구() {
        AnalystResponse r = service.answer(ask("고마워~"), GUEST, IP);

        assertEquals("SMALL_TALK_THANKS", r.policyCode());
        verify(gateway, never()).open(any(), anyString(), anyString());
    }

    @Test
    void 사용법_질문은_고정_문구이고_차감하지_않으며_사용_방법_보기_버튼이_붙는다() {
        AnalystResponse r = service.answer(ask("사용법 알려줘"), GUEST, IP);

        assertEquals("USAGE_GUIDE", r.policyCode());
        assertTrue(r.message().contains("도와드려요"));
        assertEquals("OPEN_GUIDE", r.action());
        assertEquals(3, r.suggestions().size());
        assertFalse(r.charged());
        verify(gateway, never()).open(any(), anyString(), anyString());
        verify(agent, never()).run(any(), any(), anyString(), any(), anyString());
    }

    @Test
    void 변형된_인사도_고정_문구이고_차감하지_않는다() {
        AnalystResponse r = service.answer(ask("요~ 하이"), GUEST, IP);

        assertEquals("SMALL_TALK_GREETING", r.policyCode());
        assertFalse(r.charged());
        verify(gateway, never()).open(any(), anyString(), anyString());
    }

    @Test
    void 인사가_섞인_로또_질문은_고정_문구가_아니라_AI가_답한다() {
        agentReturns(Outcome.ANSWER, "1000회 번호예요.", List.of(), null);

        AnalystResponse r = service.answer(ask("안녕 1000회 당첨금 알려줘"), GUEST, IP);

        assertNull(r.policyCode());
        assertTrue(r.charged());
        verify(gateway).open(any(AiFeature.class), anyString(), anyString());
    }

    @Test
    void 지도_화면의_인사는_판매점_문구() {
        AnalystResponse r = service.answer(new AnalystRequest("안녕", "MAP", null, null, null), GUEST, IP);

        assertEquals("SMALL_TALK_GREETING", r.policyCode());
        assertTrue(r.message().contains("판매점") || r.message().contains("동네"));
        assertTrue(r.suggestions().get(0).contains("강남구"));
    }

    @Test
    void AI가_답을_못_만들면_횟수를_돌려주고_다시_물어보라고_안내한다() {
        agentReturns(Outcome.GAVE_UP, null, List.of(), null);
        AnalystResponse r = service.answer(ask("로또 통계 알려줘"), GUEST, IP);
        verify(session).refund();
        assertTrue(r.answer().contains("다시 물어봐"));
        assertTrue(r.answer().contains("횟수에서 빼지 않았어요"));   // 환불했으니 그렇게 안내한다
        assertFalse(r.charged());
    }

    @Test
    void 지도_질문은_AI에게_넘기고_지도_화면과_위치_유무를_함께_전달한다() {
        agentReturns(Outcome.ANSWER, "강남구 판매점이에요.", List.of(), null);

        service.answer(new AnalystRequest("근처 판매점 알려줘", "MAP", 37.5, 127.0, null), GUEST, IP);

        ArgumentCaptor<AskContext> captor = ArgumentCaptor.forClass(AskContext.class);
        verify(agent).run(any(), any(), anyString(), any(), anyString(), captor.capture());
        assertTrue(captor.getValue().map());
        assertTrue(captor.getValue().hasLocation());
        assertEquals("Location[hidden]", captor.getValue().location().toString());   // 로그에 찍혀도 좌표가 나오지 않는다
        verify(gateway).open(any(AiFeature.class), anyString(), anyString());
    }

    @Test
    void 위치_없이_온_일반_질문은_위치_없는_상태로_전달한다() {
        agentReturns(Outcome.ANSWER, "답", List.of(), null);

        service.answer(ask("로또 통계 알려줘"), GUEST, IP);

        ArgumentCaptor<AskContext> captor = ArgumentCaptor.forClass(AskContext.class);
        verify(agent).run(any(), any(), anyString(), any(), anyString(), captor.capture());
        assertFalse(captor.getValue().map());
        assertFalse(captor.getValue().hasLocation());
    }

    // ------------------------------------------------------------------ 입력 검사

    @Test
    void 질문이_비었거나_300자를_넘으면_입력_오류() {
        invalid(ask("  "));
        invalid(ask("가".repeat(301)));
        invalid(null);
    }

    @Test
    void context_와_위치_값을_검사한다() {
        invalid(new AnalystRequest("로또", "OTHER", null, null, null));
        invalid(new AnalystRequest("로또", null, 37.5, null, null));
        invalid(new AnalystRequest("로또", null, 91.0, 127.0, null));
    }

    @Test
    void 관리자_무제한이면_남은_횟수는_null() {
        when(gateway.usage(anyString(), anyString(), anyBoolean()))
                .thenReturn(new AiUsageSnapshot(true, 0, 0, 0, LocalDateTime.now()));
        agentReturns(Outcome.ANSWER, "답", List.of(), null);
        assertNull(service.answer(ask("로또"), GUEST, IP).remainingToday());
        assertNotNull(AnalystService.NOTICE);
    }
}
