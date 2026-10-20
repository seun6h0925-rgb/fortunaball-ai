package com.fortunaball.analyst.service;

import com.fortunaball.analyst.domain.AnalystEvidence;
import com.fortunaball.analyst.domain.EvidenceItem;
import com.fortunaball.analyst.domain.ParsedQuestion;
import com.fortunaball.analyst.domain.QuestionType;
import com.fortunaball.draw.dto.DrawResponse;
import com.fortunaball.draw.service.DrawQueryService;
import com.fortunaball.issue.dto.IssueDetailResponse;
import com.fortunaball.issue.service.IssueQueryService;
import com.fortunaball.stat.domain.StatPeriod;
import com.fortunaball.stat.dto.NumberStatResponse;
import com.fortunaball.stat.dto.StatSummaryResponse;
import com.fortunaball.stat.service.StatAnalysisService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 질문 유형별로 서버가 가진 데이터를 모아 "근거"로 만든다. 새로 계산하지 않고 이미 있는 서비스(통계·회차·발급 조회)를 쓴다.
 *
 *  STAT_ANALYSIS / NUMBER_GENERATION  통계 요약 (핫넘버, 장기 미출현, 홀짝·저고, 합계, 연속수, 최근 회차)
 *  ROUND_ANALYSIS                     그 회차의 당첨번호·보너스·1등 당첨금 + 번호 구조
 *  ISSUE_ANALYSIS                     발급코드의 게임별 번호 구조 + 최근 100회 출현 횟수
 *  NUMBER_ANALYSIS                    직접 적은 번호의 구조 + 최근 100회 출현 횟수
 *  MAP_STORE                          판매점 데이터가 아직 없어서 ready=false (7번 지도 기능을 만들 때 연결한다)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 질문에 "최근 20회"처럼 통계 기간 선택지(10·50·100)에 없는 값이 있으면 그보다 큰 가장 가까운 기간을 쓰고,
 *    기준 안내(basis)에 "요청하신 20회 대신 50회 기준"이라고 밝힌다. (조용히 바꾸지 않는다)
 *  - 발급코드 조회는 IssueQueryService 규칙(접속지별 조회 횟수 제한, 없는 코드는 404)을 그대로 따른다.
 *  - 게임이 많은 발급은 앞의 MAX_GAMES 게임만 근거에 담고 나머지는 개수만 알린다. (AI 에 주는 글이 길어지지 않게)
 */
@Service
public class AnalystEvidenceService {

    static final int MAX_GAMES = 5;
    private static final int HOT_COUNT = 6;
    private static final int ABSENT_COUNT = 5;
    private static final int RECENT_ROUNDS = 3;

    private final StatAnalysisService statService;
    private final DrawQueryService drawService;
    private final IssueQueryService issueService;

    public AnalystEvidenceService(StatAnalysisService statService, DrawQueryService drawService,
                                  IssueQueryService issueService) {
        this.statService = statService;
        this.drawService = drawService;
        this.issueService = issueService;
    }

    public AnalystEvidence collect(ParsedQuestion q, String clientIp) {
        return switch (q.type()) {
            case ROUND_ANALYSIS -> roundEvidence(q.roundNo());
            case ISSUE_ANALYSIS -> issueEvidence(q.issueCode(), clientIp);
            case NUMBER_ANALYSIS -> numberEvidence(q.givenNumbers());
            case MAP_STORE -> new AnalystEvidence(QuestionType.MAP_STORE, null, List.of(), List.of(), false);
            case STAT_ANALYSIS, NUMBER_GENERATION -> statEvidence(q.type(), q.periodRounds());
        };
    }

    /** 생성된 번호들의 근거 (번호 생성 단계에서 쓴다). 최근 100회 출현 횟수와 함께 번호 구조를 풀어 쓴다. */
    public AnalystEvidence describeGames(List<List<Integer>> games) {
        NumberStatResponse stat = statService.numbers(new StatPeriod(StatPeriod.DEFAULT_ROUNDS), null, null);
        Map<Integer, Integer> counts = counts(stat);
        List<EvidenceItem> items = new ArrayList<>();
        for (int i = 0; i < Math.min(games.size(), MAX_GAMES); i++) {
            items.addAll(ComboDescriber.describe(games.get(i), counts, stat.roundCount(), "게임 " + (i + 1) + " "));
        }
        if (games.size() > MAX_GAMES) {
            items.add(new EvidenceItem("그 밖의 게임", (games.size() - MAX_GAMES) + "게임 더 있음"));
        }
        return new AnalystEvidence(QuestionType.NUMBER_GENERATION,
                stat.toRound() + "회까지 최근 " + stat.roundCount() + "회 기준", items,
                List.of("동행복권 회차별 당첨번호"), true);
    }

    // ------------------------------------------------------------------ 유형별

    /**
     * 통계 요약 근거. 기간은 "10" "50" "100" "ALL" 이고 비우면 100. (AI 도구 stat_summary 가 쓴다)
     * 10·50·100 이 아닌 숫자(예: 20)는 그보다 큰 가장 가까운 기간으로 바꾸고 그 사실을 기준 안내에 밝힌다.
     */
    public AnalystEvidence statEvidence(String periodArg) {
        String p = periodArg == null ? "" : periodArg.trim();
        if (p.equalsIgnoreCase("ALL")) {
            return buildStat(QuestionType.STAT_ANALYSIS, StatPeriod.ALL, "");
        }
        Integer requested = null;
        if (!p.isEmpty()) {
            if (!p.matches("\\d{1,4}")) {
                throw new com.fortunaball.common.exception.BusinessException(com.fortunaball.common.exception.ErrorCode.INVALID_INPUT,
                        "기간은 10, 50, 100, ALL 중에서 골라 주세요.", "period=" + periodArg);
            }
            requested = Integer.parseInt(p);
        }
        return statEvidence(QuestionType.STAT_ANALYSIS, requested);
    }

    private AnalystEvidence statEvidence(QuestionType type, Integer requestedRounds) {
        StatPeriod period = periodOf(requestedRounds);
        return buildStat(type, period, adjustedNote(requestedRounds, period));
    }

    private AnalystEvidence buildStat(QuestionType type, StatPeriod period, String periodNote) {
        StatSummaryResponse s = statService.summary(period, null);

        List<EvidenceItem> items = new ArrayList<>();
        items.add(new EvidenceItem("많이 나온 번호", s.hotNumbers().stream().limit(HOT_COUNT)
                .map(h -> h.number() + "번 " + h.count() + "회").reduce((a, b) -> a + ", " + b).orElse("없음")));
        items.add(new EvidenceItem("오래 안 나온 번호", s.absence().numbers().stream().limit(ABSENT_COUNT)
                .map(a -> a.number() + "번 " + a.absentRounds() + "회째").reduce((a, b) -> a + ", " + b).orElse("없음")));
        items.add(new EvidenceItem("홀짝 비율", "홀수 " + percent(s.oddEven().firstPercent())
                + " · 짝수 " + percent(s.oddEven().secondPercent())));
        items.add(new EvidenceItem("저고 비율", "저(1~22) " + percent(s.highLow().firstPercent())
                + " · 고(23~45) " + percent(s.highLow().secondPercent())));
        items.add(new EvidenceItem("합계", "평균 " + String.format("%.1f", s.sum().average())
                + " (최소 " + s.sum().min() + " ~ 최대 " + s.sum().max() + ")"));
        items.add(new EvidenceItem("연속수", "이웃한 숫자가 나온 회차 " + percent(s.consecutive().percent())));
        for (StatSummaryResponse.RoundDetail r : s.recentRounds().stream().limit(RECENT_ROUNDS).toList()) {
            items.add(new EvidenceItem(r.roundNo() + "회 당첨번호", ComboDescriber.join(r.numbers())));
        }
        String basis = s.toRound() + "회까지 " + (period.isAll() ? "전체 " : "최근 ") + s.roundCount() + "회 기준" + periodNote;
        return new AnalystEvidence(type, basis, items, List.of("동행복권 회차별 당첨번호 통계"), true);
    }

    public AnalystEvidence roundEvidence(Integer roundNo) {
        DrawResponse d = drawService.getByRound(roundNo);   // 없는 회차는 404 오류가 그대로 나간다
        List<EvidenceItem> items = new ArrayList<>();
        items.add(new EvidenceItem("회차", d.roundNo() + "회 (추첨일 " + d.drawDate() + ")"));
        items.addAll(ComboDescriber.describe(d.numbers(), null, 0, "당첨 "));
        items.add(new EvidenceItem("보너스 번호", String.valueOf(d.bonusNumber())));
        d.prizes().stream().filter(p -> p.winningRank() == 1).findFirst().ifPresent(p -> items.add(new EvidenceItem(
                "1등", "당첨자 " + p.winnerCount() + "명 · 1인당 " + String.format("%,d원", p.prizeAmount()))));
        return new AnalystEvidence(QuestionType.ROUND_ANALYSIS, d.roundNo() + "회 당첨 결과", items,
                List.of("동행복권 회차별 당첨번호"), true);
    }

    public AnalystEvidence issueEvidence(String issueCode, String clientIp) {
        IssueDetailResponse issue = issueService.getDetail(issueCode, clientIp);
        NumberStatResponse stat = statService.numbers(new StatPeriod(StatPeriod.DEFAULT_ROUNDS), null, null);
        Map<Integer, Integer> counts = counts(stat);

        List<EvidenceItem> items = new ArrayList<>();
        items.add(new EvidenceItem("발급 정보", "대상 " + issue.targetRound() + "회 · " + issue.gameCount()
                + "게임 · " + issue.verificationLabel()));
        for (IssueDetailResponse.Game g : issue.games().stream().limit(MAX_GAMES).toList()) {
            items.addAll(ComboDescriber.describe(g.numbers(), counts, stat.roundCount(), "게임 " + g.gameOrder() + " "));
        }
        if (issue.games().size() > MAX_GAMES) {
            items.add(new EvidenceItem("그 밖의 게임", (issue.games().size() - MAX_GAMES) + "게임 더 있음"));
        }
        return new AnalystEvidence(QuestionType.ISSUE_ANALYSIS, stat.toRound() + "회까지 최근 " + stat.roundCount() + "회 기준",
                items, List.of("발급 내역", "동행복권 회차별 당첨번호"), true);
    }

    public AnalystEvidence numberEvidence(List<Integer> numbers) {
        NumberStatResponse stat = statService.numbers(new StatPeriod(StatPeriod.DEFAULT_ROUNDS), null, null);
        List<EvidenceItem> items = ComboDescriber.describe(numbers, counts(stat), stat.roundCount(), "입력 ");
        return new AnalystEvidence(QuestionType.NUMBER_ANALYSIS, stat.toRound() + "회까지 최근 " + stat.roundCount() + "회 기준",
                items, List.of("동행복권 회차별 당첨번호"), true);
    }

    // ------------------------------------------------------------------ 보조

    /** 통계 기간은 10·50·100 중에서만 고를 수 있다. 질문 값 이상인 가장 가까운 기간을 쓴다. 없으면 100. */
    static StatPeriod periodOf(Integer requested) {
        if (requested == null) {
            return new StatPeriod(StatPeriod.DEFAULT_ROUNDS);
        }
        for (int allowed : StatPeriod.ALLOWED) {
            if (requested <= allowed) {
                return new StatPeriod(allowed);
            }
        }
        return new StatPeriod(StatPeriod.DEFAULT_ROUNDS);
    }

    private static String adjustedNote(Integer requested, StatPeriod used) {
        if (requested == null || used.rounds() == null || requested.equals(used.rounds())) {
            return "";
        }
        return " (요청하신 최근 " + requested + "회 대신 최근 " + used.rounds() + "회 기준으로 보여드려요)";
    }

    private static Map<Integer, Integer> counts(NumberStatResponse stat) {
        Map<Integer, Integer> map = new HashMap<>();
        for (NumberStatResponse.Item item : stat.items()) {
            map.put(item.number(), item.count());
        }
        return map;
    }

    private static String percent(double value) {
        return String.format("%.1f%%", value);
    }
}
