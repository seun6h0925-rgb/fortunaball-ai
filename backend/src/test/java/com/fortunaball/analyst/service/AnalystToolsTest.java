package com.fortunaball.analyst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.analyst.domain.AnalystEvidence;
import com.fortunaball.analyst.domain.AskContext;
import com.fortunaball.analyst.domain.EvidenceItem;
import com.fortunaball.analyst.domain.QuestionType;
import com.fortunaball.analyst.service.AnalystTools.ToolResult;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.draw.domain.DrawPrize;
import com.fortunaball.draw.dto.DrawResponse;
import com.fortunaball.draw.repository.DrawPrizeRepository;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.draw.service.DrawQueryService;
import com.fortunaball.generate.dto.GenerateResponse;
import com.fortunaball.generate.service.GenerateService;
import com.fortunaball.stat.domain.StatPeriod;
import com.fortunaball.stat.dto.NumberStatResponse;
import com.fortunaball.stat.service.StatAnalysisService;
import com.fortunaball.store.dto.StoreItem;
import com.fortunaball.store.service.StoreQueryService;
import com.fortunaball.store.service.StoreQueryService.AiSort;
import com.fortunaball.store.service.StoreQueryService.AiStoreResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** AI 가 부르는 조회 도구가 값을 검사하고 올바른 서비스를 부르는지 확인한다. (DB 없이 가짜로) */
class AnalystToolsTest {

    private static final GuestToken GUEST = GuestToken.of("guest-aaaaaaaaaaaaaaaa");
    private final ObjectMapper json = new ObjectMapper();

    private AnalystEvidenceService evidenceService;
    private GenerateService generateService;
    private DrawQueryService drawService;
    private DrawRoundRepository roundRepository;
    private DrawPrizeRepository prizeRepository;
    private StatAnalysisService statService;
    private StoreQueryService storeService;
    private AnalystTools tools;

    @BeforeEach
    void setUp() {
        evidenceService = mock(AnalystEvidenceService.class);
        generateService = mock(GenerateService.class);
        drawService = mock(DrawQueryService.class);
        roundRepository = mock(DrawRoundRepository.class);
        prizeRepository = mock(DrawPrizeRepository.class);
        statService = mock(StatAnalysisService.class);
        storeService = mock(StoreQueryService.class);
        tools = new AnalystTools(evidenceService, generateService, drawService, roundRepository, prizeRepository, statService,
                storeService, new AnalystStoreEvidence());
    }

    private JsonNode args(String text) throws Exception {
        return json.readTree(text);
    }

    private static AnalystEvidence evidence(QuestionType type) {
        return new AnalystEvidence(type, "기준", List.of(new EvidenceItem("항목", "값")), List.of("출처"), true);
    }

    // ------------------------------------------------------------------ round_info

    @Test
    void 회차를_주면_그_회차_근거를_돌려준다() throws Exception {
        when(evidenceService.roundEvidence(1000)).thenReturn(evidence(QuestionType.ROUND_ANALYSIS));
        ToolResult r = tools.run("round_info", args("{\"roundNo\":1000}"), GUEST, "ip");
        assertTrue(r.isOk());
        assertEquals(QuestionType.ROUND_ANALYSIS, r.evidence().type());
    }

    @Test
    void 회차를_안_주면_최신_회차를_쓴다() throws Exception {
        when(drawService.getLatest()).thenReturn(new DrawResponse(1246, LocalDate.of(2026, 10, 17), List.of(1, 2, 3, 4, 5, 6), 7, List.of()));
        when(evidenceService.roundEvidence(1246)).thenReturn(evidence(QuestionType.ROUND_ANALYSIS));
        assertTrue(tools.run("round_info", args("{}"), GUEST, "ip").isOk());
        verify(evidenceService).roundEvidence(1246);
    }

    @Test
    void 서비스_오류는_사용자_요청_실패가_아니라_AI가_읽는_실패_결과가_된다() throws Exception {
        when(evidenceService.roundEvidence(99999)).thenThrow(
                new BusinessException(ErrorCode.NOT_FOUND, "해당 회차 정보를 찾을 수 없어요.", "roundNo=99999"));
        ToolResult r = tools.run("round_info", args("{\"roundNo\":99999}"), GUEST, "ip");
        assertFalse(r.isOk());
        assertNotNull(r.error());
        assertTrue(r.toPromptText().contains("(실패)"));
    }

    // ------------------------------------------------------------------ prize_ranking

    @Test
    void 당첨금_순위는_금액_내림차순으로_조회하고_회차와_금액을_담는다() throws Exception {
        when(prizeRepository.findByWinningRank(anyInt(), any(Pageable.class))).thenReturn(List.of(
                new DrawPrize(1000, 1, 4_000_000_000L, 5), new DrawPrize(900, 1, 3_000_000_000L, 7)));
        when(roundRepository.findByRoundNo(anyInt())).thenReturn(Optional.empty());

        ToolResult r = tools.run("prize_ranking", args("{\"rank\":1,\"limit\":2}"), GUEST, "ip");

        assertTrue(r.isOk());
        assertEquals("1위", r.evidence().items().get(0).label());
        assertEquals("1000회 · 1인당 4,000,000,000원 · 당첨자 5명", r.evidence().items().get(0).value());
        assertTrue(r.evidence().basis().contains("1등 1인당 당첨금 높은 순"));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(prizeRepository).findByWinningRank(eq(1), captor.capture());
        assertEquals(2, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("prizeAmount").getDirection());
    }

    @Test
    void 당첨자_수_적은_순도_조회할_수_있다() throws Exception {
        when(prizeRepository.findByWinningRank(anyInt(), any(Pageable.class))).thenReturn(List.of(new DrawPrize(5, 1, 1L, 1)));
        when(roundRepository.findByRoundNo(anyInt())).thenReturn(Optional.empty());

        ToolResult r = tools.run("prize_ranking", args("{\"by\":\"winners\",\"order\":\"asc\"}"), GUEST, "ip");

        assertTrue(r.evidence().basis().contains("당첨자 수 낮은 순"));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(prizeRepository).findByWinningRank(eq(1), captor.capture());
        assertEquals(Sort.Direction.ASC, captor.getValue().getSort().getOrderFor("winnerCount").getDirection());
        assertEquals(5, captor.getValue().getPageSize());   // 기본 5개
    }

    @Test
    void 순위_개수는_10개를_넘지_못하고_등수는_1에서_5만_된다() throws Exception {
        when(prizeRepository.findByWinningRank(anyInt(), any(Pageable.class))).thenReturn(List.of(new DrawPrize(5, 1, 1L, 1)));
        when(roundRepository.findByRoundNo(anyInt())).thenReturn(Optional.empty());

        tools.run("prize_ranking", args("{\"limit\":500}"), GUEST, "ip");
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(prizeRepository).findByWinningRank(eq(1), captor.capture());
        assertEquals(10, captor.getValue().getPageSize());

        assertFalse(tools.run("prize_ranking", args("{\"rank\":9}"), GUEST, "ip").isOk());
    }

    @Test
    void 당첨금_자료가_없으면_실패_결과() throws Exception {
        when(prizeRepository.findByWinningRank(anyInt(), any(Pageable.class))).thenReturn(List.of());
        assertFalse(tools.run("prize_ranking", args("{}"), GUEST, "ip").isOk());
    }

    // ------------------------------------------------------------------ stat_summary / number_stats

    @Test
    void 통계_요약은_기간_글자를_그대로_넘긴다() throws Exception {
        when(evidenceService.statEvidence("ALL")).thenReturn(evidence(QuestionType.STAT_ANALYSIS));
        assertTrue(tools.run("stat_summary", args("{\"period\":\"ALL\"}"), GUEST, "ip").isOk());
        verify(evidenceService).statEvidence("ALL");
    }

    private static NumberStatResponse stats() {
        return new NumberStatResponse(1246, 1147, 1246, 100, List.of(
                new NumberStatResponse.Item(27, 21, 1246, 0), new NumberStatResponse.Item(13, 19, 1240, 6),
                new NumberStatResponse.Item(45, 5, null, 100)));
    }

    @Test
    void 번호별_통계는_물어본_번호만_담는다() throws Exception {
        when(statService.numbers(any(StatPeriod.class), isNull(), isNull())).thenReturn(stats());

        ToolResult r = tools.run("number_stats", args("{\"numbers\":[27,45]}"), GUEST, "ip");

        assertEquals(2, r.evidence().items().size());
        assertEquals("27번", r.evidence().items().get(0).label());
        assertEquals("21회 출현 · 마지막 출현 1246회 (가장 최근 회차)", r.evidence().items().get(0).value());
        assertEquals("5회 출현 · 기간 안에 나온 적 없음", r.evidence().items().get(1).value());
        assertEquals("1147~1246회 (100회) 기준", r.evidence().basis());
    }

    @Test
    void 번호를_안_주면_출현_상위를_준다() throws Exception {
        when(statService.numbers(any(StatPeriod.class), isNull(), isNull())).thenReturn(stats());
        ToolResult r = tools.run("number_stats", args("{}"), GUEST, "ip");
        assertEquals("출현 상위 27번", r.evidence().items().get(0).label());
    }

    @Test
    void 회차_범위를_주면_서비스에_그대로_넘긴다() throws Exception {
        when(statService.numbers(any(StatPeriod.class), eq(1000), eq(1100))).thenReturn(stats());
        assertTrue(tools.run("number_stats", args("{\"numbers\":[27],\"fromRound\":1000,\"toRound\":1100}"), GUEST, "ip").isOk());
        verify(statService).numbers(any(StatPeriod.class), eq(1000), eq(1100));
    }

    @Test
    void 번호별_통계의_범위와_개수를_검사한다() throws Exception {
        assertFalse(tools.run("number_stats", args("{\"numbers\":[46]}"), GUEST, "ip").isOk());
        assertFalse(tools.run("number_stats", args("{\"numbers\":[1,2,3,4,5,6,7,8,9,10,11]}"), GUEST, "ip").isOk());
        verify(statService, never()).numbers(any(), any(), any());
    }

    @Test
    void 기간_값이_이상하면_실패_결과로_알려준다() throws Exception {
        // StatPeriod.parse 가 허용하지 않는 값(예: 20)은 입력 오류 → AI 가 읽고 다시 고른다
        ToolResult r = tools.run("number_stats", args("{\"numbers\":[1],\"period\":\"20\"}"), GUEST, "ip");
        assertFalse(r.isOk());
    }

    // ------------------------------------------------------------------ analyze_*

    @Test
    void 직접_적은_번호는_서로_다른_6개여야_한다() throws Exception {
        when(evidenceService.numberEvidence(any())).thenReturn(evidence(QuestionType.NUMBER_ANALYSIS));
        assertTrue(tools.run("analyze_numbers", args("{\"numbers\":[3,12,19,27,33,41]}"), GUEST, "ip").isOk());
        assertFalse(tools.run("analyze_numbers", args("{\"numbers\":[3,3,19,27,33,41]}"), GUEST, "ip").isOk());
        assertFalse(tools.run("analyze_numbers", args("{\"numbers\":[3,12,19]}"), GUEST, "ip").isOk());
        assertFalse(tools.run("analyze_numbers", args("{\"numbers\":[3,12,19,27,33,46]}"), GUEST, "ip").isOk());
    }

    @Test
    void 발급코드는_대문자로_바꿔_접속지와_함께_조회한다() throws Exception {
        when(evidenceService.issueEvidence(anyString(), anyString())).thenReturn(evidence(QuestionType.ISSUE_ANALYSIS));
        assertTrue(tools.run("analyze_issue", args("{\"issueCode\":\" abcdefghjkmn \"}"), GUEST, "9.9.9.9").isOk());
        verify(evidenceService).issueEvidence("ABCDEFGHJKMN", "9.9.9.9");
        assertFalse(tools.run("analyze_issue", args("{}"), GUEST, "ip").isOk());
    }

    // ------------------------------------------------------------------ generate_numbers

    private static GenerateResponse generated() {
        return new GenerateResponse("tok", 30, Instant.parse("2026-10-20T07:30:00Z"),
                List.of(List.of(7, 10, 20, 30, 40, 45)), null, null, 1247, LocalDate.of(2026, 10, 24), 1246);
    }

    @Test
    void 번호_생성은_조건을_넘기고_만든_번호를_함께_돌려준다() throws Exception {
        when(generateService.generateWithNumbers(any(), any(), any(), any())).thenReturn(generated());
        when(evidenceService.describeGames(any())).thenReturn(evidence(QuestionType.NUMBER_GENERATION));

        ToolResult r = tools.run("generate_numbers", args("{\"gameCount\":2,\"include\":[7],\"exclude\":[1,3]}"), GUEST, "ip");

        verify(generateService).generateWithNumbers(GUEST, 2, Set.of(7), Set.of(1, 3));
        assertNotNull(r.generated());
        assertEquals("tok", r.generated().confirmToken());
        assertTrue(r.isOk());
    }

    @Test
    void 번호_생성_조건_오류는_실패_결과로_AI가_읽는다() throws Exception {
        when(generateService.generateWithNumbers(any(), any(), any(), any())).thenThrow(
                new BusinessException(ErrorCode.INVALID_INPUT, "7번을 포함과 제외에 모두 적으셨어요.", "충돌"));
        ToolResult r = tools.run("generate_numbers", args("{\"include\":[7],\"exclude\":[7]}"), GUEST, "ip");
        assertFalse(r.isOk());
        assertNull(r.generated());
    }

    @Test
    void 문자로_온_숫자도_읽고_이상한_값은_무시한다() throws Exception {
        when(generateService.generateWithNumbers(any(), any(), any(), any())).thenReturn(generated());
        when(evidenceService.describeGames(any())).thenReturn(evidence(QuestionType.NUMBER_GENERATION));

        tools.run("generate_numbers", args("{\"gameCount\":\"3\",\"include\":[\"7\",\"abc\",null]}"), GUEST, "ip");

        verify(generateService).generateWithNumbers(GUEST, 3, Set.of(7), Set.of());
    }

    // ------------------------------------------------------------------ 그 밖

    @Test
    void 모르는_도구_이름은_실패_결과() throws Exception {
        ToolResult r = tools.run("drop_table", args("{}"), GUEST, "ip");
        assertFalse(r.isOk());
        assertTrue(r.error().contains("알 수 없는"));
        assertFalse(tools.run(null, null, GUEST, "ip").isOk());
    }

    // ------------------------------------------------------------------ 판매점 도구

    private static StoreItem store(long id, String name, Integer distance) {
        return new StoreItem(id, name, "서울 강남구 테헤란로 " + id, "", "서울", "강남구", 37.5001, 127.0001,
                2, 1, 0, 3, 4, 7, 1, 0, true, distance);
    }

    private static AiStoreResult storeResult(List<StoreItem> items, Long total) {
        return new AiStoreResult(items, total, LocalDate.of(2026, 10, 17), 262, 12, 1, 5);
    }

    @Test
    void store_search_는_지역과_정렬을_서비스에_넘기고_근거를_만든다() throws Exception {
        when(storeService.searchForAi("서울", "강남구", null, AiSort.TOTAL, false, null))
                .thenReturn(storeResult(List.of(store(1, "행운복권", null)), 120L));

        ToolResult r = tools.run("store_search", args("{\"sido\":\"서울\",\"sigungu\":\"강남구\"}"), GUEST, "ip");

        assertTrue(r.isOk());
        assertEquals(QuestionType.MAP_STORE, r.evidence().type());
        assertTrue(r.evidence().basis().contains("서울 강남구"));
        assertTrue(r.evidence().items().get(0).label().contains("행운복권"));
    }

    @Test
    void 루키_명당만_원하면_최근_당첨_정렬로_바꿔_부른다() throws Exception {
        when(storeService.searchForAi(null, null, null, AiSort.RECENT, true, 3))
                .thenReturn(storeResult(List.of(store(1, "행운복권", null)), null));

        ToolResult r = tools.run("store_search", args("{\"rookieOnly\":true,\"limit\":3}"), GUEST, "ip");

        assertTrue(r.isOk());
        verify(storeService).searchForAi(null, null, null, AiSort.RECENT, true, 3);
    }

    @Test
    void sort_recent_는_최근_당첨_정렬이다() throws Exception {
        when(storeService.searchForAi("경기", null, null, AiSort.RECENT, false, null))
                .thenReturn(storeResult(List.of(store(1, "행운복권", null)), null));

        assertTrue(tools.run("store_search", args("{\"sido\":\"경기\",\"sort\":\"recent\"}"), GUEST, "ip").isOk());
    }

    @Test
    void sort_first_는_1등_많은_순이다() throws Exception {
        when(storeService.searchForAi("서울", "강남구", null, AiSort.FIRST, false, null))
                .thenReturn(storeResult(List.of(store(1, "행운복권", null)), null));

        ToolResult r = tools.run("store_search", args("{\"sido\":\"서울\",\"sigungu\":\"강남구\",\"sort\":\"first\"}"), GUEST, "ip");

        assertTrue(r.isOk());
        assertTrue(r.evidence().basis().contains("1등 많은 순"));
    }

    @Test
    void 조건에_맞는_판매점이_없으면_AI가_읽을_안내를_돌려준다() throws Exception {
        when(storeService.searchForAi("서울", "없는구", null, AiSort.TOTAL, false, null))
                .thenReturn(storeResult(List.of(), 0L));

        ToolResult r = tools.run("store_search", args("{\"sido\":\"서울\",\"sigungu\":\"없는구\"}"), GUEST, "ip");

        assertFalse(r.isOk());
        assertTrue(r.error().contains("찾지 못했어요"));
    }

    @Test
    void 검색어가_짧으면_서비스_오류_메시지가_AI에게_전해진다() throws Exception {
        when(storeService.searchForAi(null, null, "가", AiSort.TOTAL, false, null))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "검색어는 2글자 이상 50글자 이하로 써야 해요.", "keyword 길이 오류"));

        ToolResult r = tools.run("store_search", args("{\"keyword\":\"가\"}"), GUEST, "ip");

        assertFalse(r.isOk());
        assertTrue(r.error().contains("2글자"));
    }

    @Test
    void 위치가_없으면_내_주변은_조회하지_않는다() throws Exception {
        ToolResult r = tools.run("store_nearby", args("{}"), GUEST, "ip", AskContext.NONE);

        assertFalse(r.isOk());
        assertTrue(r.error().contains("위치"));
        verify(storeService, never()).nearbyForAi(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), anyInt(), any());
    }

    @Test
    void 위치가_있으면_서버가_가진_좌표로_조회하고_좌표는_근거에_없다() throws Exception {
        AskContext ctx = new AskContext(true, new AskContext.Location(37.5001, 127.0001));
        when(storeService.nearbyForAi(37.5001, 127.0001, 500, null))
                .thenReturn(storeResult(List.of(store(1, "행운복권", 230)), 8L));

        ToolResult r = tools.run("store_nearby", args("{}"), GUEST, "ip", ctx);

        assertTrue(r.isOk());
        String prompt = r.toPromptText();
        assertTrue(prompt.contains("230m"));
        assertFalse(prompt.contains("37.50"));    // 위도·경도는 AI 에게 가지 않는다
        assertFalse(prompt.contains("127.00"));
    }

    @Test
    void 허용되지_않는_반경은_서비스_오류가_AI에게_전해진다() throws Exception {
        AskContext ctx = new AskContext(true, new AskContext.Location(37.5, 127.0));
        when(storeService.nearbyForAi(37.5, 127.0, 400, null))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "반경은 300m, 500m, 1km 중에서 골라 주세요.", "radius=400"));

        ToolResult r = tools.run("store_nearby", args("{\"radius\":400}"), GUEST, "ip", ctx);

        assertFalse(r.isOk());
        assertTrue(r.error().contains("반경"));
    }

    @Test
    void 반경_안에_판매점이_없으면_넓혀_보라고_안내한다() throws Exception {
        AskContext ctx = new AskContext(true, new AskContext.Location(37.5, 127.0));
        when(storeService.nearbyForAi(37.5, 127.0, 300, null)).thenReturn(storeResult(List.of(), 0L));

        ToolResult r = tools.run("store_nearby", args("{\"radius\":300}"), GUEST, "ip", ctx);

        assertFalse(r.isOk());
        assertTrue(r.error().contains("반경 300m"));
    }
}
