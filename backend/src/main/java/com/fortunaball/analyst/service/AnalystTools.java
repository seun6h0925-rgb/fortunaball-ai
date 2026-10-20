package com.fortunaball.analyst.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fortunaball.analyst.domain.AnalystEvidence;
import com.fortunaball.analyst.domain.EvidenceItem;
import com.fortunaball.analyst.domain.QuestionType;
import com.fortunaball.common.exception.BusinessException;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * AI 가 부를 수 있는 "조회 도구" 모음. AI 는 도구 이름과 값(args)만 말하고, 실제 조회와 계산은 전부 여기서 서버가 한다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 모든 도구는 읽기 전용이다. (번호 생성도 임시 보관만 하고 발급 확정은 하지 않는다)
 *  - AI 가 준 값은 믿지 않는다. 범위·개수를 여기서 다시 검사하고, 잘못되면 AI 가 읽고 스스로 고칠 수 있게 쉬운 말의 오류 글을 돌려준다.
 *    (오류가 나도 사용자 요청이 실패하는 것이 아니라, AI 가 그 오류를 보고 다시 조회하거나 사용자에게 설명한다)
 *  - 없는 회차(404), 발급코드 조회 횟수 제한 같은 서비스 오류도 같은 방식으로 AI 에게 글로 전해진다.
 */
@Component
public class AnalystTools {

    public static final String ROUND_INFO = "round_info";
    public static final String PRIZE_RANKING = "prize_ranking";
    public static final String STAT_SUMMARY = "stat_summary";
    public static final String NUMBER_STATS = "number_stats";
    public static final String ANALYZE_NUMBERS = "analyze_numbers";
    public static final String ANALYZE_ISSUE = "analyze_issue";
    public static final String GENERATE_NUMBERS = "generate_numbers";

    static final int MAX_RANKING = 10;
    static final int MAX_NUMBERS = 10;

    /** 도구 한 번 실행한 결과. 근거가 있으면 evidence, 실패하면 error, 번호를 만들었으면 generated */
    public record ToolResult(String name, AnalystEvidence evidence, String error, GenerateResponse generated) {

        static ToolResult ok(String name, AnalystEvidence evidence) {
            return new ToolResult(name, evidence, null, null);
        }

        static ToolResult failed(String name, String error) {
            return new ToolResult(name, null, error, null);
        }

        public boolean isOk() {
            return error == null;
        }

        /** AI 에게 돌려줄 글 */
        public String toPromptText() {
            if (error != null) {
                return "### " + name + " (실패)\n" + error + "\n";
            }
            return "### " + name + "\n" + evidence.toPromptText();
        }
    }

    private final AnalystEvidenceService evidenceService;
    private final GenerateService generateService;
    private final DrawQueryService drawService;
    private final DrawRoundRepository roundRepository;
    private final DrawPrizeRepository prizeRepository;
    private final StatAnalysisService statService;

    public AnalystTools(AnalystEvidenceService evidenceService, GenerateService generateService,
                        DrawQueryService drawService, DrawRoundRepository roundRepository,
                        DrawPrizeRepository prizeRepository, StatAnalysisService statService) {
        this.evidenceService = evidenceService;
        this.generateService = generateService;
        this.drawService = drawService;
        this.roundRepository = roundRepository;
        this.prizeRepository = prizeRepository;
        this.statService = statService;
    }

    public ToolResult run(String name, JsonNode args, GuestToken guest, String clientIp) {
        JsonNode a = args == null || args.isNull() ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode() : args;
        try {
            return switch (name == null ? "" : name) {
                case ROUND_INFO -> roundInfo(a);
                case PRIZE_RANKING -> prizeRanking(a);
                case STAT_SUMMARY -> ToolResult.ok(name, evidenceService.statEvidence(text(a, "period")));
                case NUMBER_STATS -> numberStats(a);
                case ANALYZE_NUMBERS -> analyzeNumbers(a);
                case ANALYZE_ISSUE -> analyzeIssue(a, clientIp);
                case GENERATE_NUMBERS -> generate(a, guest);
                default -> ToolResult.failed(String.valueOf(name), "알 수 없는 조회 도구예요. 목록에 있는 도구만 쓸 수 있어요.");
            };
        } catch (BusinessException e) {
            return ToolResult.failed(name, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 도구

    private ToolResult roundInfo(JsonNode a) {
        Integer roundNo = integer(a, "roundNo");
        int round = roundNo != null ? roundNo : drawService.getLatest().roundNo();
        return ToolResult.ok(ROUND_INFO, evidenceService.roundEvidence(round));
    }

    private ToolResult prizeRanking(JsonNode a) {
        int rank = orDefault(integer(a, "rank"), 1);
        if (rank < 1 || rank > 5) {
            return ToolResult.failed(PRIZE_RANKING, "rank 는 1부터 5까지만 쓸 수 있어요.");
        }
        int limit = Math.min(MAX_RANKING, Math.max(1, orDefault(integer(a, "limit"), 5)));
        boolean asc = "asc".equalsIgnoreCase(text(a, "order"));
        boolean byWinners = "winners".equalsIgnoreCase(text(a, "by"));

        Sort sort = Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, byWinners ? "winnerCount" : "prizeAmount")
                .and(Sort.by(Sort.Direction.DESC, "roundNo"));
        List<DrawPrize> rows = prizeRepository.findByWinningRank(rank, PageRequest.of(0, limit, sort));

        List<EvidenceItem> items = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            DrawPrize p = rows.get(i);
            String date = roundRepository.findByRoundNo(p.getRoundNo())
                    .map(r -> " (추첨일 " + r.getDrawDate() + ")").orElse("");
            items.add(new EvidenceItem((i + 1) + "위",
                    p.getRoundNo() + "회 · 1인당 " + String.format("%,d원", p.getPrizeAmount())
                            + " · 당첨자 " + p.getWinnerCount() + "명" + date));
        }
        if (items.isEmpty()) {
            return ToolResult.failed(PRIZE_RANKING, "당첨금 자료가 없어요.");
        }
        String basis = "저장된 전체 회차 중 " + rank + "등 " + (byWinners ? "당첨자 수" : "1인당 당첨금")
                + (asc ? " 낮은 순" : " 높은 순");
        return ToolResult.ok(PRIZE_RANKING, new AnalystEvidence(QuestionType.STAT_ANALYSIS, basis, items,
                List.of("동행복권 회차별 당첨금"), true));
    }

    private ToolResult numberStats(JsonNode a) {
        List<Integer> numbers = integers(a, "numbers");
        if (numbers.size() > MAX_NUMBERS) {
            return ToolResult.failed(NUMBER_STATS, "번호는 한 번에 " + MAX_NUMBERS + "개까지만 조회할 수 있어요.");
        }
        for (int n : numbers) {
            if (n < 1 || n > 45) {
                return ToolResult.failed(NUMBER_STATS, "번호는 1부터 45까지만 쓸 수 있어요.");
            }
        }
        Integer from = integer(a, "fromRound");
        Integer to = integer(a, "toRound");
        StatPeriod period = StatPeriod.parse(text(a, "period"));
        NumberStatResponse stat = statService.numbers(period, from, to);

        List<EvidenceItem> items = new ArrayList<>();
        if (numbers.isEmpty()) {
            stat.items().stream()
                    .sorted(Comparator.comparingInt(NumberStatResponse.Item::count).reversed()
                            .thenComparingInt(NumberStatResponse.Item::number))
                    .limit(MAX_NUMBERS)
                    .forEach(i -> items.add(describe(i, "출현 상위 ")));
        } else {
            for (int n : numbers) {
                stat.items().stream().filter(i -> i.number() == n).findFirst()
                        .ifPresent(i -> items.add(describe(i, "")));
            }
        }
        String basis = stat.fromRound() + "~" + stat.toRound() + "회 (" + stat.roundCount() + "회) 기준";
        return ToolResult.ok(NUMBER_STATS, new AnalystEvidence(QuestionType.STAT_ANALYSIS, basis, items,
                List.of("동행복권 회차별 당첨번호 통계"), true));
    }

    private static EvidenceItem describe(NumberStatResponse.Item i, String labelPrefix) {
        String last = i.lastRound() == null ? "기간 안에 나온 적 없음"
                : "마지막 출현 " + i.lastRound() + "회" + (i.absentRounds() == 0 ? " (가장 최근 회차)" : " · " + i.absentRounds() + "회째 안 나옴");
        return new EvidenceItem(labelPrefix + i.number() + "번", i.count() + "회 출현 · " + last);
    }

    private ToolResult analyzeNumbers(JsonNode a) {
        List<Integer> numbers = integers(a, "numbers");
        Set<Integer> distinct = new LinkedHashSet<>(numbers);
        if (distinct.size() != 6 || distinct.stream().anyMatch(n -> n < 1 || n > 45)) {
            return ToolResult.failed(ANALYZE_NUMBERS, "서로 다른 1~45 번호 6개가 필요해요.");
        }
        return ToolResult.ok(ANALYZE_NUMBERS, evidenceService.numberEvidence(new ArrayList<>(distinct)));
    }

    private ToolResult analyzeIssue(JsonNode a, String clientIp) {
        String code = text(a, "issueCode");
        if (code == null || code.isBlank()) {
            return ToolResult.failed(ANALYZE_ISSUE, "발급코드가 필요해요.");
        }
        return ToolResult.ok(ANALYZE_ISSUE, evidenceService.issueEvidence(code.trim().toUpperCase(), clientIp));
    }

    private ToolResult generate(JsonNode a, GuestToken guest) {
        Set<Integer> include = new LinkedHashSet<>(integers(a, "include"));
        Set<Integer> exclude = new LinkedHashSet<>(integers(a, "exclude"));
        GenerateResponse generated = generateService.generateWithNumbers(guest, integer(a, "gameCount"), include, exclude);
        AnalystEvidence evidence = evidenceService.describeGames(generated.games());
        return new ToolResult(GENERATE_NUMBERS, evidence, null, generated);
    }

    // ------------------------------------------------------------------ 값 읽기

    private static Integer integer(JsonNode a, String field) {
        JsonNode v = a.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isInt() || v.isLong()) {
            return v.asInt();
        }
        if (v.isTextual() && v.asText().trim().matches("-?\\d{1,9}")) {
            return Integer.parseInt(v.asText().trim());
        }
        return null;
    }

    private static List<Integer> integers(JsonNode a, String field) {
        List<Integer> list = new ArrayList<>();
        JsonNode v = a.get(field);
        if (v != null && v.isArray()) {
            for (JsonNode item : v) {
                if (item.isInt() || item.isLong()) {
                    list.add(item.asInt());
                } else if (item.isTextual() && item.asText().trim().matches("\\d{1,3}")) {
                    list.add(Integer.parseInt(item.asText().trim()));
                }
            }
        }
        return list;
    }

    private static String text(JsonNode a, String field) {
        JsonNode v = a.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
