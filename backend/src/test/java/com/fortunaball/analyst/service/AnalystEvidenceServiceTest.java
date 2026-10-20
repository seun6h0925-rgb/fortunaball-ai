package com.fortunaball.analyst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.analyst.domain.AnalystEvidence;
import com.fortunaball.analyst.domain.EvidenceItem;
import com.fortunaball.analyst.domain.QuestionType;
import com.fortunaball.draw.dto.DrawResponse;
import com.fortunaball.draw.service.DrawQueryService;
import com.fortunaball.issue.dto.IssueDetailResponse;
import com.fortunaball.issue.service.IssueQueryService;
import com.fortunaball.stat.domain.StatPeriod;
import com.fortunaball.stat.dto.NumberStatResponse;
import com.fortunaball.stat.dto.StatSummaryResponse;
import com.fortunaball.stat.service.StatAnalysisService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalystEvidenceServiceTest {

    private StatAnalysisService stat;
    private DrawQueryService draw;
    private IssueQueryService issue;
    private AnalystEvidenceService service;
    private final QuestionParser parser = new QuestionParser();

    @BeforeEach
    void setUp() {
        stat = mock(StatAnalysisService.class);
        draw = mock(DrawQueryService.class);
        issue = mock(IssueQueryService.class);
        service = new AnalystEvidenceService(stat, draw, issue);

        List<NumberStatResponse.Item> items = new ArrayList<>();
        for (int n = 1; n <= 45; n++) {
            items.add(new NumberStatResponse.Item(n, 10, 1245 - n, n));
        }
        when(stat.numbers(eq(new StatPeriod(100)), isNull(), isNull()))
                .thenReturn(new NumberStatResponse(1245, 1146, 1245, 100, items));
        when(stat.summary(any(StatPeriod.class), isNull())).thenAnswer(inv -> summary(((StatPeriod) inv.getArgument(0)).rounds()));
    }

    private static StatSummaryResponse summary(int rounds) {
        return new StatSummaryResponse(1245, String.valueOf(rounds), rounds, 1245 - rounds + 1, 1245, "CONFIRMED", "확정",
                List.of(new StatSummaryResponse.HotNumber(34, 12, 2), new StatSummaryResponse.HotNumber(27, 11, null)),
                new StatSummaryResponse.Absence(20, 7.5, 3, List.of(new StatSummaryResponse.AbsentNumber(9, 1225, 20))),
                new StatSummaryResponse.Split(150, 150, 50.0, 50.0, Map.of()),
                new StatSummaryResponse.Split(140, 160, 46.7, 53.3, Map.of()),
                new StatSummaryResponse.Sum(138.2, 70, 210, List.of(), List.of()),
                null, new StatSummaryResponse.Consecutive(30, 60.0, Map.of()), null, null, null,
                List.of(new StatSummaryResponse.RoundDetail(1245, List.of(3, 14, 22, 35, 38, 44), 3, 3, 2, 4, 156,
                        List.of(), 1, 5, 1)));
    }

    private static String valueOf(AnalystEvidence e, String label) {
        return e.items().stream().filter(i -> i.label().equals(label)).findFirst().map(EvidenceItem::value).orElseThrow();
    }

    @Test
    void 통계_질문은_기본_100회_기준_통계_요약을_모은다() {
        AnalystEvidence e = service.collect(parser.parse("요즘 자주 나온 번호 알려줘", false), "ip");

        verify(stat).summary(new StatPeriod(100), null);
        assertTrue(e.ready());
        assertEquals("1245회까지 최근 100회 기준", e.basis());
        assertEquals("34번 12회, 27번 11회", valueOf(e, "많이 나온 번호"));
        assertEquals("홀수 50.0% · 짝수 50.0%", valueOf(e, "홀짝 비율"));
    }

    @Test
    void 선택지에_없는_기간은_가까운_큰_기간으로_바꾸고_그_사실을_밝힌다() {
        AnalystEvidence e = service.collect(parser.parse("최근 20회 자주 나온 번호", false), "ip");

        verify(stat).summary(new StatPeriod(50), null);
        assertTrue(e.basis().contains("요청하신 최근 20회 대신 최근 50회 기준"));
    }

    @Test
    void 회차_질문은_그_회차_결과를_모은다() {
        when(draw.getByRound(1245)).thenReturn(new DrawResponse(1245, LocalDate.of(2026, 10, 10),
                List.of(3, 14, 22, 35, 38, 44), 27, List.of(new DrawResponse.PrizeItem(1, 2_000_000_000L, 12))));

        AnalystEvidence e = service.collect(parser.parse("1245회차 특징이 뭐야", false), "ip");

        assertEquals(QuestionType.ROUND_ANALYSIS, e.type());
        assertEquals("1245회 (추첨일 2026-10-10)", valueOf(e, "회차"));
        assertEquals("27", valueOf(e, "보너스 번호"));
        assertEquals("당첨자 12명 · 1인당 2,000,000,000원", valueOf(e, "1등"));
    }

    @Test
    void 발급코드_질문은_접속지와_함께_조회하고_게임이_많으면_앞부분만_담는다() {
        List<IssueDetailResponse.Game> games = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            games.add(new IssueDetailResponse.Game(i, List.of(1, 2, 10, 20, 30, 40)));
        }
        when(issue.getDetail("AB3CD4EF5GH6", "1.2.3.4")).thenReturn(new IssueDetailResponse("AB3CD4EF5GH6", 1246,
                LocalDateTime.of(2026, 10, 20, 15, 0), 7, null, null, games, "PENDING", "검증 대기"));

        AnalystEvidence e = service.collect(parser.parse("AB3CD4EF5GH6 분석해줘", false), "1.2.3.4");

        assertEquals("대상 1246회 · 7게임 · 검증 대기", valueOf(e, "발급 정보"));
        assertEquals("2게임 더 있음", valueOf(e, "그 밖의 게임"));
        assertTrue(e.items().stream().anyMatch(i -> i.label().equals("게임 5 번호")));
        assertFalse(e.items().stream().anyMatch(i -> i.label().equals("게임 6 번호")));
    }

    @Test
    void 직접_적은_번호는_최근_100회_출현_횟수와_함께_풀어_쓴다() {
        AnalystEvidence e = service.collect(parser.parse("3 14 22 35 38 44 분석해줘", false), "ip");

        assertEquals(QuestionType.NUMBER_ANALYSIS, e.type());
        assertEquals("3, 14, 22, 35, 38, 44", valueOf(e, "입력 번호"));
        assertTrue(valueOf(e, "입력 최근 100회 출현").startsWith("3번 10회"));
    }

    @Test
    void 지도_질문은_근거를_모을_수_없고_다른_서비스를_부르지_않는다() {
        AnalystEvidence e = service.collect(parser.parse("1등 많이 나온 판매점", true), "ip");

        assertFalse(e.ready());
        verify(stat, never()).summary(any(), any());
    }

    @Test
    void 생성된_번호의_근거를_만든다() {
        AnalystEvidence e = service.describeGames(List.of(List.of(1, 2, 3, 4, 5, 6)));

        assertEquals("1245회까지 최근 100회 기준", e.basis());
        assertEquals("1, 2, 3, 4, 5, 6", valueOf(e, "게임 1 번호"));
    }

    @Test
    void 프롬프트_재료에는_기준과_항목이_한_줄씩_들어간다() {
        AnalystEvidence e = service.collect(parser.parse("자주 나온 번호", false), "ip");
        String text = e.toPromptText();
        assertTrue(text.startsWith("기준: 1245회까지 최근 100회 기준\n"));
        assertTrue(text.contains("- 많이 나온 번호: 34번 12회, 27번 11회\n"));
    }
}
