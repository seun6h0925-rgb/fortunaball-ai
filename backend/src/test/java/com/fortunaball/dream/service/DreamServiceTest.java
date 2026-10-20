package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.ai.AiGateway;
import com.fortunaball.ai.AiSession;
import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiResponse;
import com.fortunaball.ai.usage.AiFeature;
import com.fortunaball.ai.usage.AiUsageSnapshot;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.dream.domain.DreamKnowledge.Interpretation;
import com.fortunaball.dream.domain.DreamKnowledge.SourceRef;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolEntry;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolNumber;
import com.fortunaball.dream.domain.DreamNumberOrigin;
import com.fortunaball.dream.dto.DreamRequest;
import com.fortunaball.dream.dto.DreamResponse;
import com.fortunaball.dream.repository.DreamKnowledgeRepository;
import com.fortunaball.stat.dto.NumberStatResponse;
import com.fortunaball.stat.service.StatAnalysisService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DreamServiceTest {

    private DreamKnowledgeRepository repository;
    private AiGateway gateway;
    private AiSession session;
    private StatAnalysisService stat;
    private DreamService service;

    @BeforeEach
    void setUp() {
        repository = mock(DreamKnowledgeRepository.class);
        gateway = mock(AiGateway.class);
        session = mock(AiSession.class);
        stat = mock(StatAnalysisService.class);
        service = new DreamService(repository, new DreamSymbolMatcher(), new DreamNumberExtractor(),
                new DreamNumberPicker(), new DreamInterpretationSelector(), new DreamPromptBuilder(), gateway, stat);

        when(repository.loadActiveSymbols()).thenReturn(List.of(
                new SymbolEntry(1, "돼지", List.of("새끼돼지")),
                new SymbolEntry(2, "용", List.of())));
        when(repository.loadInterpretations(any())).thenReturn(List.of(
                new Interpretation(1, "집에 들어옴", "재물이나 좋은 인연을 얻는다고 전통적으로 읽는다.", "POSITIVE", "MEDIUM", "KR", "스포츠경향"),
                new Interpretation(1, "다가옴", "해외 해석", "POSITIVE", "LOW", "GLOBAL", null)));
        when(repository.loadNumbers(any())).thenReturn(List.of(
                new SymbolNumber(1, 4, DreamNumberOrigin.SMORFIA), new SymbolNumber(1, 32, DreamNumberOrigin.CHARADA),
                new SymbolNumber(1, 3, DreamNumberOrigin.SERVICE), new SymbolNumber(1, 8, DreamNumberOrigin.SERVICE)));
        when(repository.loadSources(any())).thenReturn(List.of(new SourceRef(1, "스포츠경향", "언론 칼럼", "MEDIUM")));
        when(gateway.open(any(AiFeature.class), anyString(), anyString())).thenReturn(session);
        when(gateway.usage(anyString(), anyString(), anyBoolean()))
                .thenReturn(new AiUsageSnapshot(false, 5, 1, 4, LocalDateTime.now()));
    }

    private static DreamRequest req(String dream, boolean supplement) {
        return new DreamRequest(dream, supplement);
    }

    @Test
    void 너무_짧거나_긴_꿈은_입력_오류() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.interpret(req("돼지", false), "g", "1.1.1.1"));
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
        assertThrows(BusinessException.class, () -> service.interpret(req("가".repeat(301), false), "g", "1.1.1.1"));
        assertThrows(BusinessException.class, () -> service.interpret(new DreamRequest(null, null), "g", "1.1.1.1"));
        verify(gateway, never()).open(any(AiFeature.class), anyString(), anyString());
    }

    @Test
    void 상징이_없으면_고정_응답이고_AI를_부르지_않으며_횟수를_차감하지_않는다() {
        DreamResponse r = service.interpret(req("아무 상징도 없는 평범한 이야기입니다", false), "g", "1.1.1.1");

        assertEquals("NO_MATCH", r.matchResult());
        assertEquals("DREAM_LOW_RELEVANCE", r.policyCode());
        assertEquals(DreamService.LOW_RELEVANCE_MESSAGE, r.message());
        assertTrue(r.suggestStatGeneration());
        assertNull(r.interpretation());
        verify(gateway).recordBlocked(AiFeature.DREAM, "DREAM_LOW_RELEVANCE");
        verify(gateway, never()).open(any(AiFeature.class), anyString(), anyString());   // 차감은 open 에서 일어난다
    }

    @Test
    void 상징이_없어도_꿈_속_숫자는_번호로_돌려준다() {
        DreamResponse r = service.interpret(req("상징 없이 숫자 17 과 29 만 나온 꿈", false), "g", "1.1.1.1");
        assertEquals(List.of(17, 29), r.numbers().stream().map(DreamResponse.NumberView::number).toList());
    }

    @Test
    void 상징을_찾으면_AI가_해석하고_한국_해석을_먼저_쓰며_꿈_원문은_AI에_보내지_않는다() {
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("돼지 꿈은 재물과 좋은 인연으로 읽는 전통이 있어요. 재미로 참고하세요.", "m", 10, 10));

        String dream = "새끼돼지가 집에 들어오는 꿈을 꾸었어요 비밀문장XYZ";
        DreamResponse r = service.interpret(req(dream, false), "g", "1.1.1.1");

        assertEquals("PARTIAL_MATCH", r.matchResult());           // 번호 4개뿐이라 6개가 안 됨
        assertNull(r.policyCode());
        assertEquals("돼지", r.symbols().get(0).keyword());
        assertEquals(1, r.symbols().get(0).interpretations().size());   // 한국 해석만
        assertEquals("KR", r.symbols().get(0).interpretations().get(0).region());
        assertEquals(4, r.remainingToday());
        // 번호는 전통 번호(4, 32)를 서비스 매핑(3, 8)보다 앞서 고르고, 결과는 오름차순
        assertEquals(List.of(3, 4, 8, 32), r.numbers().stream().map(DreamResponse.NumberView::number).toList());

        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(session).call(captor.capture());
        assertTrue(captor.getValue().userPrompt().contains("돼지"));
        assertTrue(!captor.getValue().userPrompt().contains("비밀문장XYZ"));   // 원문은 AI 에 가지 않는다
    }

    @Test
    void 동의하면_통계로_보완하고_FULL_이_아니다() {
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("재미로 참고하세요.", "m", 1, 1));
        when(stat.numbers(any(), isNull(), isNull())).thenReturn(new NumberStatResponse(1100, 1001, 1100, 100, List.of(
                new NumberStatResponse.Item(3, 20, 1099, 1), new NumberStatResponse.Item(10, 19, 1098, 2),
                new NumberStatResponse.Item(20, 18, 1097, 3), new NumberStatResponse.Item(30, 17, 1096, 4))));

        DreamResponse r = service.interpret(req("새끼돼지가 나왔어요", true), "g", "1.1.1.1");

        assertEquals(6, r.numbers().size());
        assertEquals("PARTIAL_MATCH", r.matchResult());
        assertEquals(2, r.numbers().stream().filter(n -> n.origin().equals("STAT")).count());
    }

    @Test
    void AI가_단정_표현을_쓰면_DB_해석으로_만든_문장으로_바꾼다() {
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("이 꿈은 반드시 1등 당첨됩니다!", "m", 1, 1));
        DreamResponse r = service.interpret(req("새끼돼지가 나왔어요", false), "g", "1.1.1.1");
        assertTrue(!r.interpretation().contains("반드시"));
        assertTrue(r.interpretation().contains("[돼지]"));
    }

    @Test
    void 너무_긴_AI_문장도_대체_문장을_쓴다() {
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("가".repeat(501), "m", 1, 1));
        DreamResponse r = service.interpret(req("새끼돼지가 나왔어요", false), "g", "1.1.1.1");
        assertTrue(r.interpretation().length() <= 500);
        assertTrue(r.interpretation().contains("[돼지]"));
    }
}
