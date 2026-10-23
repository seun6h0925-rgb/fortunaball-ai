package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.ai.AiGateway;
import com.fortunaball.ai.AiSession;
import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiResponse;
import com.fortunaball.ai.policy.AiPolicy;
import com.fortunaball.ai.policy.AiPolicyEngine;
import com.fortunaball.ai.usage.AiFeature;
import com.fortunaball.ai.usage.AiUsageSnapshot;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.dream.domain.DreamKnowledge.Interpretation;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolEntry;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolNumber;
import com.fortunaball.dream.domain.DreamNumberOrigin;
import com.fortunaball.dream.dto.DreamRequest;
import com.fortunaball.dream.dto.DreamResponse;
import com.fortunaball.dream.repository.DreamKnowledgeRepository;
import com.fortunaball.generate.dto.GenerateResponse;
import com.fortunaball.generate.service.GenerateService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 꿈풀이 한 턴씩의 동작 검사. AI 와 번호 생성은 가짜로 바꾸고, 말을 읽는 곳(DreamReplyReader)은 진짜를 쓴다.
 * 말을 AI 가 쓰는 곳(DreamNarrator)은 기본으로 "못 썼다(null)"로 두어 정해진 문구가 나오게 하고,
 * AI 가 쓴 말이 쓰이는지는 따로 검사한다.
 */
class DreamServiceTest {

    private static final GuestToken GUEST = GuestToken.of("guest-aaaaaaaaaaaaaaaa");
    private static final String IP = "1.1.1.1";

    /** 시드(docs/db/seed_ai_policy.sql) 중 꿈풀이에 적용되는 정책만 옮겨 둔 테스트용 값 */
    private static final List<AiPolicy> POLICIES = List.of(
            new AiPolicy("GUARANTEE_REQUEST", "GUARANTEE_REQUEST", "ALL", 20, "보장 불가 안내", List.of("당첨번호알려", "1등되는번호")),
            new AiPolicy("DREAM_OFF_TOPIC", "OFF_TOPIC", "DREAM", 70, "꿈 내용을 적어 주세요", List.of("꿈", "꿨", "꾼", "꾸었", "꾸는")));

    // 상징별 번호: 돼지 4개, 용 2개, 거미·나비·개구리 1개씩 → 돼지+용 = 6개, 돼지 = 4개, 거미+나비+개구리 = 3개, 전부 = 9개
    private static final String DREAM_FOUR = "돼지가 나오는 꿈을 꿨어요";
    private static final String DREAM_SIX = "돼지와 용이 나오는 꿈을 꿨어요";
    private static final String DREAM_THREE = "거미와 나비와 개구리가 나오는 꿈을 꿨어요";
    private static final String DREAM_NINE = "돼지, 용, 거미, 나비, 개구리가 나오는 꿈을 꿨어요";
    private static final List<Integer> NINE = List.of(2, 3, 4, 8, 12, 22, 26, 32, 35);

    private DreamKnowledgeRepository repository;
    private AiGateway gateway;
    private AiSession session;
    private GenerateService generateService;
    private DreamNarrator narrator;
    private DreamStatRanker ranker;
    private DreamService service;
    /** 번호 생성이 불릴 때마다 올라간다. 올라갈 때마다 통계로 채우는 번호가 달라진다. */
    private final AtomicInteger generated = new AtomicInteger();

    @BeforeEach
    void setUp() {
        repository = mock(DreamKnowledgeRepository.class);
        gateway = mock(AiGateway.class);
        session = mock(AiSession.class);
        generateService = mock(GenerateService.class);
        narrator = mock(DreamNarrator.class);
        ranker = mock(DreamStatRanker.class);
        service = new DreamService(repository, new DreamSymbolMatcher(), new DreamNumberExtractor(),
                new DreamNumberPicker(), new DreamInterpretationSelector(), new DreamPromptBuilder(), gateway,
                generateService, new AiPolicyEngine(() -> POLICIES), new DreamMessages(),
                new DreamReplyReader(new ObjectMapper()), narrator, ranker, new DreamChat());

        when(repository.loadActiveSymbols()).thenReturn(List.of(
                new SymbolEntry(1, "돼지", List.of("새끼돼지")),
                new SymbolEntry(2, "용", List.of()),
                new SymbolEntry(3, "거미", List.of()),
                new SymbolEntry(4, "나비", List.of()),
                new SymbolEntry(5, "개구리", List.of())));
        when(repository.loadInterpretations(any())).thenReturn(List.of(
                new Interpretation(1, "집에 들어옴", "재물이나 좋은 인연을 얻는다고 전통적으로 읽는다.", "POSITIVE", "MEDIUM", "KR", "스포츠경향"),
                new Interpretation(1, "다가옴", "해외 해석", "POSITIVE", "LOW", "GLOBAL", null)));
        // 앞의 두 개는 전통 번호, 나머지는 서비스 매핑으로 본다. (전통 번호가 서비스 매핑보다 앞선다)
        List<SymbolNumber> numbers = new ArrayList<>();
        addNumbers(numbers, 1, 4, 32, 3, 8);
        addNumbers(numbers, 2, 12, 26);
        addNumbers(numbers, 3, 35);
        addNumbers(numbers, 4, 2);
        addNumbers(numbers, 5, 22);
        when(repository.loadNumbers(any())).thenReturn(numbers);

        when(gateway.open(any(AiFeature.class), anyString(), anyString())).thenReturn(session);
        when(gateway.usage(anyString(), anyString(), anyBoolean()))
                .thenReturn(new AiUsageSnapshot(false, 5, 1, 4, LocalDateTime.now()));
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("돼지 꿈은 재물과 좋은 인연으로 읽는 전통이 있어요. 재미로 참고하세요.", "m", 10, 10));
        // 기본: AI 가 말을 못 썼다고 보고 정해진 문구를 쓴다. (Mockito 기본값은 빈 목록이라 null 을 직접 지정해야 한다)
        when(narrator.narrate(any(), any(), anyInt(), any())).thenReturn(null);
        when(ranker.rank(any())).thenAnswer(inv -> {
            Collection<Integer> c = inv.getArgument(0);
            return new ArrayList<>(new TreeSet<>(c));   // 통계 순서 대신 작은 번호 먼저
        });
        // 한 게임: 포함 번호에 40번대부터 (호출할 때마다 달라지는) 번호를 붙여 6개를 만든다
        when(generateService.generateWithNumbers(any(), any(), any(), any())).thenAnswer(inv -> {
            Set<Integer> include = inv.getArgument(2);
            int n = generated.incrementAndGet();
            return response(n, List.of(fill(include, 40 + n)));
        });
        // 여러 게임: 게임마다 포함 번호 + 서로 다른 채움 번호
        when(generateService.generateEachWithNumbers(any(), any())).thenAnswer(inv -> {
            List<Set<Integer>> perGame = inv.getArgument(1);
            int n = generated.incrementAndGet();
            List<List<Integer>> games = new ArrayList<>();
            int start = 40 + n;
            for (Set<Integer> include : perGame) {
                List<Integer> game = fill(include, start);
                start += 6;
                games.add(game);
            }
            return response(n, games);
        });
    }

    private static void addNumbers(List<SymbolNumber> list, long symbolId, int... numbers) {
        for (int i = 0; i < numbers.length; i++) {
            list.add(new SymbolNumber(symbolId, numbers[i],
                    i == 0 ? DreamNumberOrigin.SMORFIA : i == 1 ? DreamNumberOrigin.CHARADA : DreamNumberOrigin.SERVICE));
        }
    }

    private static List<Integer> fill(Set<Integer> include, int start) {
        TreeSet<Integer> game = new TreeSet<>(include);
        for (int next = start; game.size() < 6; next++) {
            game.add(next);
        }
        return new ArrayList<>(game);
    }

    private static GenerateResponse response(int n, List<List<Integer>> games) {
        return new GenerateResponse("tok-" + n, 30, Instant.parse("2026-10-24T00:00:00Z"), games, null, null, 1100,
                LocalDate.of(2026, 10, 24), 1099);
    }

    private static DreamRequest first(String dream) {
        return DreamRequest.first(dream);
    }

    /** 이어지는 요청: 직전 응답의 step·keep·gameCount·confirmToken 을 그대로 돌려보낸다 */
    private static DreamRequest reply(String dream, String text, String step, List<Integer> keep, Integer gameCount,
                                      String token) {
        return new DreamRequest(dream, text, step, keep, gameCount, token);
    }

    private static DreamRequest reply(String dream, String text, String step, List<Integer> keep) {
        return reply(dream, text, step, keep, null, "tok-0");
    }

    private static List<Integer> numbers(DreamResponse r) {
        return r.numbers().stream().map(DreamResponse.NumberView::number).toList();
    }

    private static long statCount(DreamResponse r) {
        return r.numbers().stream().filter(n -> n.origin().equals("STAT")).count();
    }

    private void neverOpened() {
        verify(gateway, never()).open(any(AiFeature.class), anyString(), anyString());
    }

    // ------------------------------------------------------------------ 입력 검사 / 고정 응답

    @Test
    void 너무_긴_꿈이나_말은_입력_오류() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.interpret(first("가".repeat(301)), GUEST, IP));
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
        assertThrows(BusinessException.class, () -> service.interpret(
                reply(DREAM_FOUR, "가".repeat(301), DreamResponse.STEP_CONFIRM, List.of()), GUEST, IP));
        neverOpened();
    }

    @Test
    void 짧은_말도_막지_않고_AI_가_대화로_받으며_횟수_1회를_쓴다() {
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("돼지 꿈이군요! 어떤 장면이었는지 조금 더 들려주세요.", "m", 10, 10));

        DreamResponse r = service.interpret(first("돼지"), GUEST, IP);

        assertEquals(DreamResponse.STEP_TALK, r.step());
        assertEquals("돼지 꿈이군요! 어떤 장면이었는지 조금 더 들려주세요.", r.message());
        assertTrue(r.charged());
        assertTrue(r.numbers().isEmpty());
        assertEquals(DreamMessages.SUGGESTIONS, r.suggestions());
        verify(gateway, times(1)).open(AiFeature.DREAM, GUEST.value(), IP);
    }

    @Test
    void 대화_글에_숫자가_있거나_무관_표시면_횟수를_돌려주고_고정_문구로_안내한다() {
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("이번엔 7번이 좋아요.", "m", 1, 1));
        DreamResponse withNumber = service.interpret(first("돼지"), GUEST, IP);
        assertEquals("DREAM_OFF_TOPIC", withNumber.policyCode());
        assertFalse(withNumber.charged());

        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("[OFF_TOPIC]", "m", 1, 1));
        DreamResponse off = service.interpret(first("용"), GUEST, IP);
        assertEquals("DREAM_OFF_TOPIC", off.policyCode());
        assertFalse(off.charged());
        verify(session, times(2)).refund();
    }

    @Test
    void 비어_있는_입력은_AI_없이_더_적어_달라는_고정_응답이고_차감하지_않는다() {
        DreamResponse r = service.interpret(first(""), GUEST, IP);
        assertEquals(DreamResponse.STEP_NEED_MORE, r.step());
        assertEquals("DREAM_TOO_SHORT", r.policyCode());
        assertFalse(r.charged());
        verify(gateway).recordBlocked(AiFeature.DREAM, "DREAM_TOO_SHORT");
        neverOpened();
        assertEquals(DreamResponse.STEP_NEED_MORE, service.interpret(new DreamRequest(null, null, null, null, null, null), GUEST, IP).step());
        assertEquals(DreamResponse.STEP_NEED_MORE, service.interpret(null, GUEST, IP).step());
    }

    @Test
    void 인사와_감사는_짧아도_고정_응답이고_사용법에는_안내_열기_동작이_붙는다() {
        DreamResponse hello = service.interpret(first("안녕"), GUEST, IP);
        assertEquals(DreamResponse.STEP_TALK, hello.step());
        assertEquals("SMALL_TALK_GREETING", hello.policyCode());
        assertNull(hello.action());
        assertFalse(hello.charged());

        assertEquals("SMALL_TALK_THANKS", service.interpret(first("고마워요"), GUEST, IP).policyCode());

        DreamResponse usage = service.interpret(first("사용법 알려줘"), GUEST, IP);
        assertEquals("USAGE_GUIDE", usage.policyCode());
        assertEquals(DreamResponse.ACTION_OPEN_GUIDE, usage.action());
        assertTrue(usage.message().contains("당첨을 알려 드리지는 않아요"));
        neverOpened();
    }

    @Test
    void 꿈과_거리가_먼_말은_AI_가_대화로_받고_정말_무관하면_고정_문구로_안내한다_당첨_보장_요구는_바로_막는다() {
        // 조금 벗어난 말: AI 가 짧게 받아 주고 꿈 이야기로 이끈다 (횟수 1회)
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("점심 고민이시군요! 혹시 어젯밤 꿈은 기억나세요?", "m", 1, 1));
        DreamResponse chat = service.interpret(first("오늘 점심 뭐 먹을지 고민이에요"), GUEST, IP);
        assertEquals(DreamResponse.STEP_TALK, chat.step());
        assertTrue(chat.charged());
        assertTrue(chat.numbers().isEmpty());

        // 정말 무관한 말: AI 가 판단하면 고정 문구, 횟수는 돌려준다
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("[OFF_TOPIC]", "m", 1, 1));
        DreamResponse off = service.interpret(first("오늘 점심 뭐 먹을지 3시에 정해요"), GUEST, IP);
        assertEquals("NO_MATCH", off.matchResult());
        assertEquals("DREAM_OFF_TOPIC", off.policyCode());
        assertTrue(off.message().contains("꿈"));
        assertFalse(off.charged());
        assertTrue(off.numbers().isEmpty());
        assertEquals(DreamMessages.SUGGESTIONS, off.suggestions());
        verify(gateway).recordBlocked(AiFeature.DREAM, "DREAM_OFF_TOPIC");

        DreamResponse guarantee = service.interpret(first("돼지꿈 꿨어요 당첨번호알려줘"), GUEST, IP);
        assertEquals("GUARANTEE_REQUEST", guarantee.policyCode());
        assertEquals("보장 불가 안내", guarantee.message());
        verify(gateway, times(2)).open(AiFeature.DREAM, GUEST.value(), IP);   // 위 두 번의 대화만 AI 를 불렀다
        verify(generateService, never()).generateWithNumbers(any(), any(), any(), any());
    }

    @Test
    void 꿈에서_나온_번호가_하나도_없으면_통계_번호를_만들지_묻고_차감하지_않는다() {
        DreamResponse r = service.interpret(first("꿈에서 아무것도 기억이 안 나는데 기분이 이상했어요"), GUEST, IP);

        assertEquals("NO_MATCH", r.matchResult());
        assertEquals(DreamResponse.STEP_ASK_STAT_NUMBERS, r.step());
        assertEquals(DreamMessages.REPLIES_STAT, r.quickReplies());
        assertTrue(r.charged());   // 앞의 말은 AI 가 받아 주므로 횟수 1회
        assertTrue(r.numbers().isEmpty());
        verify(gateway, times(1)).open(AiFeature.DREAM, GUEST.value(), IP);
    }

    @Test
    void 꿈에서_나온_번호가_없을_때_AI_글을_못_쓰면_횟수를_돌려주고_고정_문구로_묻는다() {
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("[OFF_TOPIC]", "m", 1, 1));

        DreamResponse r = service.interpret(first("꿈에서 아무것도 기억이 안 나는데 기분이 이상했어요"), GUEST, IP);

        assertEquals(DreamResponse.STEP_ASK_STAT_NUMBERS, r.step());
        assertEquals("DREAM_LOW_RELEVANCE", r.policyCode());
        assertEquals(DreamMessages.REPLIES_STAT, r.quickReplies());
        assertFalse(r.charged());
        verify(session).refund();
        verify(gateway).recordBlocked(AiFeature.DREAM, "DREAM_LOW_RELEVANCE");
    }

    // ------------------------------------------------------------------ 처음 풀이: 번호 개수별

    @Test
    void 번호가_6개면_그대로_쓰고_확정을_묻는다_횟수는_한_번만_쓴다() {
        DreamResponse r = service.interpret(first(DREAM_SIX), GUEST, IP);

        assertEquals("FULL_MATCH", r.matchResult());
        assertEquals(DreamResponse.STEP_CONFIRM, r.step());
        assertEquals(List.of(3, 4, 8, 12, 26, 32), numbers(r));
        assertEquals(0, statCount(r));
        assertEquals("tok-1", r.confirmToken());
        assertEquals(30, r.tokenTtlMinutes());
        assertEquals(1100, r.targetRound());
        assertEquals(DreamMessages.REPLIES_CONFIRM_ONLY, r.quickReplies());
        assertEquals(List.of(3, 4, 8, 12, 26, 32), r.keep());
        assertEquals(1, r.gameCount());
        assertTrue(r.games().isEmpty());
        assertNotNull(r.interpretation());
        assertEquals(2, r.symbols().size());
        assertEquals(DreamService.BASIS_NOTICE, r.basisNotice());
        assertEquals(DreamService.NOTICE, r.notice());
        assertTrue(r.charged());
        assertEquals(4, r.remainingToday());
        // 풀이(AI)와 말(AI) 모두 같은 세션이다. 횟수는 한 번만 쓴다.
        verify(gateway, times(1)).open(AiFeature.DREAM, GUEST.value(), IP);
        ArgumentCaptor<Set<Integer>> include = ArgumentCaptor.forClass(Set.class);
        verify(generateService).generateWithNumbers(any(), eq(1), include.capture(), eq(Set.of()));
        assertEquals(Set.of(3, 4, 8, 12, 26, 32), include.getValue());
    }

    @Test
    void 번호가_4개면_모자란_번호를_통계로_바로_채우고_확정_또는_다시_만들기를_묻는다() {
        DreamResponse r = service.interpret(first(DREAM_FOUR), GUEST, IP);

        assertEquals("PARTIAL_MATCH", r.matchResult());
        assertEquals(DreamResponse.STEP_CONFIRM, r.step());
        assertEquals(6, r.numbers().size());
        assertEquals(2, statCount(r));
        assertTrue(numbers(r).containsAll(List.of(3, 4, 8, 32)));
        assertEquals(DreamMessages.REPLIES_CONFIRM, r.quickReplies());
        assertEquals(List.of(3, 4, 8, 32), r.keep());           // 통계 번호는 keep 에 없다
        assertTrue(r.question().contains("1회"));               // 다시 만들면 횟수가 쓰인다는 안내
        assertTrue(r.message().contains("돼지 → 4·32·3·8") || r.message().contains("[돼지"));
        assertEquals("tok-1", r.confirmToken());
    }

    @Test
    void 번호가_3개_이하면_채우기_전에_먼저_동의를_묻고_번호를_만들지_않는다() {
        DreamResponse r = service.interpret(first(DREAM_THREE), GUEST, IP);

        assertEquals("PARTIAL_MATCH", r.matchResult());
        assertEquals(DreamResponse.STEP_ASK_FILL, r.step());
        assertEquals(List.of(2, 22, 35), numbers(r));
        assertEquals(List.of(2, 22, 35), r.keep());
        assertNull(r.confirmToken());
        assertEquals(DreamMessages.REPLIES_FILL, r.quickReplies());
        assertTrue(r.message().contains("거미"));
        assertTrue(r.charged());                                  // 풀이와 대화를 AI 가 썼다
        verify(generateService, never()).generateWithNumbers(any(), any(), any(), any());
    }

    @Test
    void 상징_번호가_6개를_넘어도_자르지_않고_모두_보여_주며_몇_게임으로_나눌지_묻는다() {
        DreamResponse r = service.interpret(first(DREAM_NINE), GUEST, IP);

        assertEquals(DreamResponse.STEP_ASK_OVERFLOW, r.step());
        assertEquals(NINE, numbers(r));
        assertEquals(NINE, r.keep());
        assertNull(r.confirmToken());
        assertEquals(DreamMessages.REPLIES_OVERFLOW, r.quickReplies());
        assertTrue(r.message().contains("9개"));
        assertTrue(r.charged());
        verify(generateService, never()).generateWithNumbers(any(), any(), any(), any());
    }

    @Test
    void 상징_없이_꿈_속_숫자만_있어도_같은_방식으로_이어진다() {
        DreamResponse r = service.interpret(first("상징 없이 숫자 17 과 29 만 나온 꿈"), GUEST, IP);

        assertEquals(DreamResponse.STEP_ASK_FILL, r.step());
        assertEquals(List.of(17, 29), numbers(r));
        assertTrue(r.message().contains("[꿈 속 숫자 → 17·29]"));
        assertNull(r.interpretation());                           // 풀이할 상징이 없다
        assertTrue(r.symbols().isEmpty());
    }

    @Test
    void AI_가_쓴_말이_있으면_정해진_문구_대신_그_말을_쓴다() {
        when(narrator.narrate(any(), eq(DreamNarrator.Scene.FULL), anyInt(), any()))
                .thenReturn(List.of("AI 가 쓴 첫마디예요.", "AI 가 쓴 질문이에요."));

        DreamResponse r = service.interpret(first(DREAM_SIX), GUEST, IP);

        assertEquals("AI 가 쓴 첫마디예요.", r.message());
        assertEquals("AI 가 쓴 질문이에요.", r.question());
    }

    @Test
    void 풀이에_단정_표현이나_너무_긴_글이_있으면_DB_해석으로_만든_문장을_쓴다() {
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("반드시 당첨되는 꿈이에요!", "m", 1, 1));
        DreamResponse forbidden = service.interpret(first(DREAM_FOUR), GUEST, IP);
        assertTrue(forbidden.interpretation().contains("[돼지]"));
        assertFalse(forbidden.interpretation().contains("반드시"));

        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("가".repeat(600), "m", 1, 1));
        DreamResponse tooLong = service.interpret(first(DREAM_FOUR), GUEST, IP);
        assertTrue(tooLong.interpretation().length() <= 500);
        assertTrue(tooLong.interpretation().contains("[돼지]"));
    }

    @Test
    void AI_호출이_실패하면_횟수를_되돌리고_오류를_그대로_던진다() {
        BusinessException failure = new BusinessException(ErrorCode.INVALID_INPUT, "AI 실패", "x");
        when(session.call(any(AiRequest.class))).thenThrow(failure);

        assertThrows(BusinessException.class, () -> service.interpret(first(DREAM_FOUR), GUEST, IP));
        verify(session).refund();
    }

    // ------------------------------------------------------------------ 이어서: 확정 / 다시 만들기

    @Test
    void 말로_확정하면_확정_동작과_토큰을_돌려주고_차감하지_않는다() {
        DreamResponse r = service.interpret(
                reply(DREAM_FOUR, "확정할게요", DreamResponse.STEP_CONFIRM, List.of(3, 4, 8, 32), 1, "tok-7"), GUEST, IP);

        assertEquals(DreamResponse.STEP_CONFIRMED, r.step());
        assertEquals(DreamResponse.ACTION_ISSUE, r.action());
        assertEquals("tok-7", r.confirmToken());
        assertEquals(List.of(3, 4, 8, 32), r.keep());
        assertFalse(r.charged());
        neverOpened();
        verify(generateService, never()).generateWithNumbers(any(), any(), any(), any());
    }

    @Test
    void 확정할_토큰이_없으면_입력_오류() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.interpret(
                reply(DREAM_FOUR, "확정할게요", DreamResponse.STEP_CONFIRM, List.of(), 1, null), GUEST, IP));
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void 다시_만들어_달라고_하면_꿈_번호는_그대로_두고_통계_번호만_새로_만든다_횟수_1회() {
        DreamResponse r = service.interpret(
                reply(DREAM_FOUR, "다시 만들어 주세요", DreamResponse.STEP_CONFIRM, List.of(3, 4, 8, 32)), GUEST, IP);

        assertEquals(DreamResponse.STEP_CONFIRM, r.step());
        assertEquals(6, r.numbers().size());
        assertEquals(2, statCount(r));
        assertTrue(numbers(r).containsAll(List.of(3, 4, 8, 32)));
        assertNotEquals("tok-0", r.confirmToken());
        assertEquals(DreamMessages.REPLIES_CONFIRM, r.quickReplies());
        assertTrue(r.charged());
        verify(gateway, times(1)).open(AiFeature.DREAM, GUEST.value(), IP);
        ArgumentCaptor<Set<Integer>> include = ArgumentCaptor.forClass(Set.class);
        verify(generateService).generateWithNumbers(any(), eq(1), include.capture(), eq(Set.of()));
        assertEquals(Set.of(3, 4, 8, 32), include.getValue());
    }

    @Test
    void 통계로_채운_번호가_없으면_다시_만들지_않고_차감도_하지_않는다() {
        DreamResponse r = service.interpret(
                reply(DREAM_SIX, "다시 만들어 주세요", DreamResponse.STEP_CONFIRM, List.of(3, 4, 8, 12, 26, 32), 1, "tok-5"), GUEST, IP);

        assertEquals(DreamResponse.STEP_CONFIRM, r.step());
        assertEquals("tok-5", r.confirmToken());                  // 원래 토큰이 그대로 유효하다
        assertEquals(DreamMessages.REPLIES_CONFIRM_ONLY, r.quickReplies());
        assertFalse(r.charged());
        neverOpened();
        verify(generateService, never()).generateWithNumbers(any(), any(), any(), any());
    }

    @Test
    void 다시_만들다가_번호를_못_만들면_횟수를_되돌려_준다() {
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT, "생성 실패", "x"))
                .when(generateService).generateWithNumbers(any(), any(), any(), any());

        assertThrows(BusinessException.class, () -> service.interpret(
                reply(DREAM_FOUR, "다시 만들어 주세요", DreamResponse.STEP_CONFIRM, List.of(3, 4, 8, 32)), GUEST, IP));
        verify(session).refund();
    }

    @Test
    void 여러_게임은_같은_게임_수로_다시_만든다() {
        DreamResponse r = service.interpret(
                reply(DREAM_NINE, "다시 만들어 주세요", DreamResponse.STEP_CONFIRM, NINE, 3, "tok-0"), GUEST, IP);

        assertEquals(3, r.games().size());
        assertEquals(3, r.gameCount());
        assertTrue(r.numbers().isEmpty());
        verify(generateService).generateEachWithNumbers(any(), any());
    }

    @Test
    void keep_이_꿈에서_나온_번호가_아니면_입력_오류() {
        assertThrows(BusinessException.class, () -> service.interpret(
                reply(DREAM_FOUR, "다시 만들어 주세요", DreamResponse.STEP_CONFIRM, List.of(3, 4, 8, 45)), GUEST, IP));
        assertThrows(BusinessException.class, () -> service.interpret(
                reply(DREAM_THREE, "네, 채워 주세요", DreamResponse.STEP_ASK_FILL, List.of(1)), GUEST, IP));
    }

    // ------------------------------------------------------------------ 이어서: 채우기 동의 / 통계 번호

    @Test
    void 채워_달라고_하면_통계로_채우고_확정_또는_다시_만들기를_묻는다() {
        DreamResponse r = service.interpret(
                reply(DREAM_THREE, "네, 채워 주세요", DreamResponse.STEP_ASK_FILL, List.of(2, 22, 35)), GUEST, IP);

        assertEquals(DreamResponse.STEP_CONFIRM, r.step());
        assertEquals(6, r.numbers().size());
        assertEquals(3, statCount(r));
        assertTrue(r.numbers().stream().anyMatch(n -> n.number() == 22 && !n.origin().equals("STAT")));
        assertEquals(List.of(2, 22, 35), r.keep());
        assertTrue(r.message().contains("1회"));
        assertTrue(r.charged());
        ArgumentCaptor<Set<Integer>> include = ArgumentCaptor.forClass(Set.class);
        verify(generateService).generateWithNumbers(any(), eq(1), include.capture(), eq(Set.of()));
        assertEquals(Set.of(2, 22, 35), include.getValue());
    }

    @Test
    void 채우지_않겠다고_하면_고정_문구로_받아들이고_차감하지_않는다() {
        DreamResponse r = service.interpret(
                reply(DREAM_THREE, "아니요 다른 꿈 이야기할래요", DreamResponse.STEP_ASK_FILL, List.of(2, 22, 35)), GUEST, IP);

        assertEquals(DreamResponse.STEP_TALK, r.step());
        assertEquals(DreamMessages.DECLINED, r.message());
        assertEquals(DreamMessages.SUGGESTIONS, r.suggestions());
        assertFalse(r.charged());
        neverOpened();
    }

    @Test
    void 통계_번호를_만들어_달라고_하면_통계만으로_한_게임을_만든다() {
        DreamResponse r = service.interpret(
                reply("꿈에서 아무것도 기억이 안 나는데 기분이 이상했어요", "네, 통계 번호 만들어 주세요",
                        DreamResponse.STEP_ASK_STAT_NUMBERS, null), GUEST, IP);

        assertEquals("NO_MATCH", r.matchResult());
        assertEquals(DreamResponse.STEP_CONFIRM, r.step());
        assertEquals(6, statCount(r));
        assertTrue(r.keep().isEmpty());
        assertTrue(r.charged());
        verify(generateService).generateWithNumbers(any(), eq(1), eq(Set.of()), eq(Set.of()));
    }

    @Test
    void 질문과_상관없는_말은_새_이야기로_보고_처음부터_다시_처리한다() {
        // 채우기를 묻는 중인데 새 꿈 이야기를 했다
        DreamResponse newDream = service.interpret(
                reply(DREAM_THREE, "새끼돼지가 집에 들어오는 꿈을 꿨어요", DreamResponse.STEP_ASK_FILL, List.of(2, 22, 35)), GUEST, IP);
        assertEquals(DreamResponse.STEP_CONFIRM, newDream.step());     // 돼지 4개 → 바로 채움
        assertEquals(List.of(3, 4, 8, 32), newDream.keep());

        // 확정을 묻는 중인데 인사를 했다 → 인사 고정 응답 (AI 가 읽지도 않는다)
        DreamResponse hello = service.interpret(
                reply(DREAM_FOUR, "안녕하세요", DreamResponse.STEP_CONFIRM, List.of(3, 4, 8, 32)), GUEST, IP);
        assertEquals(DreamResponse.STEP_TALK, hello.step());
        assertEquals("SMALL_TALK_GREETING", hello.policyCode());

        // 알 수 없는 step 이면 말을 새 이야기로 본다
        assertEquals(DreamResponse.STEP_CONFIRM,
                service.interpret(reply("", DREAM_FOUR, "엉뚱한값", null), GUEST, IP).step());
    }

    @Test
    void 질문_중에_꿈과_무관한_말을_하면_AI_가_대화로_받고_같은_선택_버튼을_다시_보여_준다() {
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("괜찮아요, 기억나지 않아도 괜찮아요. 천천히 생각해 보세요.", "m", 1, 1));

        DreamResponse r = service.interpret(
                reply(DREAM_FOUR, "아 근데 기억이 잘 안 나요", DreamResponse.STEP_CONFIRM, List.of(3, 4, 8, 32), 1, "tok-3"), GUEST, IP);

        assertEquals(DreamResponse.STEP_TALK, r.step());
        assertEquals(DreamMessages.REPLIES_CONFIRM, r.quickReplies());
        assertTrue(r.charged());
        assertTrue(r.numbers().isEmpty());
        verify(gateway, times(1)).open(AiFeature.DREAM, GUEST.value(), IP);   // 읽기와 대화를 합쳐 1회
    }

    @Test
    void 짧아서_단어로_못_읽은_답은_AI_가_읽고_그_턴은_횟수를_쓴다() {
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("{\"intent\":\"confirm\",\"gameCount\":null,\"remove\":[]}", "m", 1, 1));

        DreamResponse r = service.interpret(
                reply(DREAM_FOUR, "음 그러자", DreamResponse.STEP_CONFIRM, List.of(3, 4, 8, 32), 1, "tok-3"), GUEST, IP);

        assertEquals(DreamResponse.STEP_CONFIRMED, r.step());
        assertEquals("tok-3", r.confirmToken());
        assertTrue(r.charged());
        verify(gateway, times(1)).open(AiFeature.DREAM, GUEST.value(), IP);
    }

    // ------------------------------------------------------------------ 이어서: 6개를 넘을 때

    @Test
    void 게임_수로_답하면_꿈_번호를_게임마다_골고루_나누고_모자란_자리는_통계로_채운다() {
        DreamResponse r = service.interpret(
                reply(DREAM_NINE, "3게임으로 나눠 주세요", DreamResponse.STEP_ASK_OVERFLOW, NINE), GUEST, IP);

        assertEquals(DreamResponse.STEP_CONFIRM, r.step());
        assertEquals(3, r.games().size());
        assertEquals(3, r.gameCount());
        assertTrue(r.numbers().isEmpty());
        assertEquals(NINE, r.keep());
        assertEquals("tok-1", r.confirmToken());
        assertEquals(DreamMessages.REPLIES_CONFIRM, r.quickReplies());
        assertTrue(r.charged());

        ArgumentCaptor<List<Set<Integer>>> perGame = ArgumentCaptor.forClass(List.class);
        verify(generateService).generateEachWithNumbers(any(), perGame.capture());
        assertEquals(List.of(Set.of(2, 8, 26), Set.of(3, 12, 32), Set.of(4, 22, 35)), perGame.getValue());
        // 모든 게임이 6개이고 꿈 번호가 한 번씩 들어갔다
        r.games().forEach(game -> assertEquals(6, game.size()));
    }

    @Test
    void 금액으로_말해도_게임_수로_읽는다() {
        DreamResponse r = service.interpret(
                reply(DREAM_NINE, "3게임만 할게, 3천원어치", DreamResponse.STEP_ASK_OVERFLOW, NINE), GUEST, IP);

        assertEquals(3, r.games().size());
    }

    @Test
    void 통계로_6개만_골라_달라고_하면_통계_순서로_6개를_쓴다() {
        DreamResponse r = service.interpret(
                reply(DREAM_NINE, "통계로 6개만 골라 주세요", DreamResponse.STEP_ASK_OVERFLOW, NINE), GUEST, IP);

        assertEquals(DreamResponse.STEP_CONFIRM, r.step());
        assertEquals(List.of(2, 3, 4, 8, 12, 22), numbers(r));    // 가짜 통계 순서는 작은 번호 먼저
        assertEquals(List.of(2, 3, 4, 8, 12, 22), r.keep());
        assertEquals(0, statCount(r));
        assertEquals(DreamMessages.REPLIES_CONFIRM_ONLY, r.quickReplies());
        assertTrue(r.message().contains("통계"));
    }

    @Test
    void 뺄_번호를_알려_주면_빼고_다시_정리하고_빠진_번호는_다시_살아나지_않는다() {
        DreamResponse first = service.interpret(
                reply(DREAM_NINE, "3번이랑 8번 빼고 해주세요", DreamResponse.STEP_ASK_OVERFLOW, NINE), GUEST, IP);

        // 7개가 남아 아직 6개를 넘는다 → 다시 묻는다
        assertEquals(DreamResponse.STEP_ASK_OVERFLOW, first.step());
        assertEquals(List.of(2, 4, 12, 22, 26, 32, 35), first.keep());
        assertEquals(List.of(2, 4, 12, 22, 26, 32, 35), numbers(first));

        // 화면이 keep 을 그대로 돌려보내므로 빠진 번호는 되살아나지 않는다. 하나 더 빼면 6개
        DreamResponse second = service.interpret(
                reply(DREAM_NINE, "35번 빼고", DreamResponse.STEP_ASK_OVERFLOW, first.keep()), GUEST, IP);
        assertEquals(DreamResponse.STEP_CONFIRM, second.step());
        assertEquals(List.of(2, 4, 12, 22, 26, 32), numbers(second));
        assertTrue(second.message().contains("빼고 정리했어요"));
    }

    @Test
    void 빼고_나니_모자라면_통계로_채우고_더_모자라면_동의를_묻는다() {
        // 9개 중 5개를 빼서 4개가 남으면 모자란 2개를 통계로 바로 채운다
        DreamResponse filled = service.interpret(
                reply(DREAM_NINE, "2 3 4 8 12 빼고", DreamResponse.STEP_ASK_OVERFLOW, NINE), GUEST, IP);
        assertEquals(DreamResponse.STEP_CONFIRM, filled.step());
        assertEquals(2, statCount(filled));
        assertEquals(List.of(22, 26, 32, 35), filled.keep());

        // 6개를 빼서 3개가 남으면 먼저 동의를 묻는다
        DreamResponse ask = service.interpret(
                reply(DREAM_NINE, "2 3 4 8 12 22 빼고", DreamResponse.STEP_ASK_OVERFLOW, NINE), GUEST, IP);
        assertEquals(DreamResponse.STEP_ASK_FILL, ask.step());
        assertEquals(List.of(26, 32, 35), ask.keep());
    }

    @Test
    void 전부_빼면_남는_번호가_없다고_다시_묻는다() {
        DreamResponse r = service.interpret(
                reply(DREAM_NINE, "2 3 4 8 12 22 26 32 35 빼고", DreamResponse.STEP_ASK_OVERFLOW, NINE), GUEST, IP);

        assertEquals(DreamResponse.STEP_ASK_OVERFLOW, r.step());
        assertEquals(NINE, r.keep());
        assertTrue(r.message().contains("남는 번호가 없어요"));
        verify(generateService, never()).generateWithNumbers(any(), any(), any(), any());
    }

    @Test
    void 못_알아들은_짧은_답은_다시_묻고_긴_말은_새_이야기로_본다() {
        DreamResponse unclear = service.interpret(
                reply(DREAM_NINE, "음 글쎄요", DreamResponse.STEP_ASK_OVERFLOW, NINE), GUEST, IP);
        assertEquals(DreamResponse.STEP_ASK_OVERFLOW, unclear.step());
        assertEquals(DreamMessages.REPLIES_OVERFLOW, unclear.quickReplies());
        assertTrue(unclear.message().contains("이해하지 못했어요"));

        DreamResponse newDream = service.interpret(
                reply(DREAM_NINE, "새끼돼지가 집에 들어오는 꿈을 꿨어요", DreamResponse.STEP_ASK_OVERFLOW, NINE), GUEST, IP);
        assertEquals(DreamResponse.STEP_CONFIRM, newDream.step());
    }

    @Test
    void 이어지는_요청인데_꿈에서_나온_번호를_확인할_수_없으면_입력_오류() {
        assertThrows(BusinessException.class, () -> service.interpret(
                reply("", "3게임으로 나눠 주세요", DreamResponse.STEP_ASK_OVERFLOW, null), GUEST, IP));
        assertThrows(BusinessException.class, () -> service.interpret(
                reply("", "네, 채워 주세요", DreamResponse.STEP_ASK_FILL, null), GUEST, IP));
    }

    // ------------------------------------------------------------------ 이어지는 대화 (이야기 중인 꿈을 기억한 채 답한다)

    /** 새 이야기처럼 보내는 요청: dream 은 방금 한 말, ongoing 은 이야기 중인 꿈 */
    private static DreamRequest ask(String text, String ongoing, String step) {
        return new DreamRequest(text, null, step, List.of(), null, "tok-1", ongoing, null);
    }

    @Test
    void 꿈에_대한_후속_질문은_이야기_중인_꿈과_함께_AI_에게_보내고_질문_버튼을_유지한다() {
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse("돼지 꿈에는 전통 해석이 있고, 번호는 상징에서 나왔어요.", "m", 10, 10));

        DreamResponse r = service.interpret(
                ask("해당 꿈에 대한 상징성이나 번호가 매칭이 되는게 있어?", DREAM_FOUR, DreamResponse.STEP_CONFIRM), GUEST, IP);

        assertEquals(DreamResponse.STEP_TALK, r.step());
        assertEquals("돼지 꿈에는 전통 해석이 있고, 번호는 상징에서 나왔어요.", r.message());
        assertEquals(DreamMessages.REPLIES_CONFIRM, r.quickReplies());
        assertTrue(r.charged());
        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(session).call(sent.capture());
        assertTrue(sent.getValue().userPrompt().contains("[이야기 중인 꿈]"));
        assertTrue(sent.getValue().userPrompt().contains(DREAM_FOUR));
        assertTrue(sent.getValue().userPrompt().contains("꿈 상징 돼지"));
    }

    @Test
    void AI_가_새_꿈_이야기라고_표시하면_이전_꿈이_아니라_새_풀이로_처리하고_횟수는_1회만_쓴다() {
        String newDream = "연예인이 먼저 인사하고 친구들이 부러워하는 꿈을 꿨어";
        when(session.call(any(AiRequest.class)))
                .thenReturn(new AiResponse("[NEW_DREAM]", "m", 1, 1))
                .thenReturn(new AiResponse("연예인이 반겨 준 꿈이군요. 통계로 번호를 만들어 볼 수 있어요.", "m", 1, 1));

        DreamResponse r = service.interpret(ask(newDream, DREAM_FOUR, DreamResponse.STEP_CONFIRM), GUEST, IP);

        assertEquals(DreamResponse.STEP_ASK_STAT_NUMBERS, r.step());
        assertTrue(r.charged());
        verify(gateway, times(1)).open(AiFeature.DREAM, GUEST.value(), IP);
    }

    @Test
    void 이야기_중인_꿈이_있어도_당첨_보장_요구는_대화로_받지_않고_고정_문구로_답한다() {
        DreamResponse r = service.interpret(ask("당첨번호알려줘", DREAM_FOUR, DreamResponse.STEP_CONFIRM), GUEST, IP);

        assertEquals("GUARANTEE_REQUEST", r.policyCode());
        assertFalse(r.charged());
        verify(session, never()).call(any(AiRequest.class));
    }
}
