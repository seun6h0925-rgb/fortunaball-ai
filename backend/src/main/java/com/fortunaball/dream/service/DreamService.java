package com.fortunaball.dream.service;

import com.fortunaball.ai.AiGateway;
import com.fortunaball.ai.AiSession;
import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiResponse;
import com.fortunaball.ai.policy.AiPolicyEngine;
import com.fortunaball.ai.policy.PolicyContext;
import com.fortunaball.ai.policy.PolicyHit;
import com.fortunaball.ai.usage.AiFeature;
import com.fortunaball.ai.usage.AiUsageSnapshot;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.dream.domain.DreamKnowledge.Interpretation;
import com.fortunaball.dream.domain.DreamKnowledge.MatchedSymbol;
import com.fortunaball.dream.domain.DreamKnowledge.PickedNumber;
import com.fortunaball.dream.domain.DreamKnowledge.SourceRef;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolEntry;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolNumber;
import com.fortunaball.dream.domain.DreamMatchResult;
import com.fortunaball.dream.dto.DreamRequest;
import com.fortunaball.dream.dto.DreamResponse;
import com.fortunaball.dream.repository.DreamKnowledgeRepository;
import com.fortunaball.stat.domain.StatPeriod;
import com.fortunaball.stat.dto.NumberStatResponse;
import com.fortunaball.stat.service.StatAnalysisService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 꿈풀이 (API-08) 처리 순서
 *
 *  1. 입력 검사 (5~300자)
 *  2. 상징 찾기 (이름·별칭 일치, AI 안 씀) + 꿈 속 숫자 찾기
 *  3. 정책 검사 (AiPolicyEngine): 당첨 보장·구매 유도·절박한 요청, 꿈이 아닌 입력(DREAM_OFF_TOPIC)이면 고정 응답.
 *     상징이 하나도 없으면 → 고정 응답 DREAM_LOW_RELEVANCE.  어느 쪽이든 AI 를 부르지 않고 무료 횟수도 차감하지 않는다.
 *  4. 해석 고르기 (한국 전통 우선) → 번호 고르기 (꿈 속 숫자 → 상징 번호 → 동의 시 통계 보완)
 *  5. 무료 횟수 1회 차감 후 AI 호출 (AI 는 해석을 쉬운 문장으로 풀어 쓰기만 한다) → 실패하면 횟수 환불(AiSession 이 처리)
 *  6. 금지 표현·길이 검사 → 통과 못 하면 AI 문장 대신 DB 해석으로 만든 대체 문장을 쓴다
 *
 * ⚠ 꿈 원문은 저장하지도, 로그에 남기지도, AI 에 보내지도 않는다.
 */
@Service
public class DreamService {

    private static final Logger log = LoggerFactory.getLogger(DreamService.class);

    static final int MIN_LENGTH = 5;
    static final int MAX_LENGTH = 300;
    static final String POLICY_LOW_RELEVANCE = "DREAM_LOW_RELEVANCE";

    static final String LOW_RELEVANCE_MESSAGE =
            "입력하신 꿈은 로또 꿈풀이와 직접 연결할 상징이 많지 않습니다.\n"
                    + "그래도 일부 키워드와 통계 데이터를 바탕으로 재미로 참고할 수 있는 번호 조합을 만들어드릴 수 있습니다.";
    static final String BASIS_NOTICE =
            "한국 전통 꿈풀이를 먼저 참고했고, 한국 자료가 없는 상징은 해외 자료를 참고했어요.";
    static final String NOTICE =
            "꿈 상징과 통계를 바탕으로 한 참고 정보이며 당첨을 보장하지 않습니다. 재미로 참고해 주세요.";

    /** 결과를 단정하거나 당첨 가능성을 높인다고 말하는 표현. 걸리면 AI 문장을 쓰지 않는다. */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "반드시|확실히|틀림없|무조건|보장|100\\s*%|당첨\\s*(됩니다|돼요|될 거|될거|확률이 (높|올))|1등\\s*(됩니다|돼요|될 거)");

    private final DreamKnowledgeRepository repository;
    private final DreamSymbolMatcher matcher;
    private final DreamNumberExtractor numberExtractor;
    private final DreamNumberPicker numberPicker;
    private final DreamInterpretationSelector selector;
    private final DreamPromptBuilder promptBuilder;
    private final AiGateway gateway;
    private final StatAnalysisService statService;
    private final AiPolicyEngine policyEngine;

    public DreamService(DreamKnowledgeRepository repository, DreamSymbolMatcher matcher,
                        DreamNumberExtractor numberExtractor, DreamNumberPicker numberPicker,
                        DreamInterpretationSelector selector, DreamPromptBuilder promptBuilder,
                        AiGateway gateway, StatAnalysisService statService, AiPolicyEngine policyEngine) {
        this.repository = repository;
        this.matcher = matcher;
        this.numberExtractor = numberExtractor;
        this.numberPicker = numberPicker;
        this.selector = selector;
        this.promptBuilder = promptBuilder;
        this.gateway = gateway;
        this.statService = statService;
        this.policyEngine = policyEngine;
    }

    public DreamResponse interpret(DreamRequest request, String guestKey, String clientIp) {
        String dream = validate(request);
        boolean supplement = Boolean.TRUE.equals(request.supplementWithStats());

        List<SymbolEntry> entries = repository.loadActiveSymbols();
        List<MatchedSymbol> symbols = matcher.match(dream, entries);
        List<Integer> dreamNumbers = numberExtractor.extract(dream);

        // 정책에 걸리면(당첨 보장 요구, 구매 유도, 절박한 요청, 꿈이 아닌 입력) AI 없이 정책 문구로 응답한다.
        // 이때는 번호도 만들지 않는다. 횟수 차감 없이 기록만 남긴다.
        PolicyHit hit = policyEngine.check(PolicyContext.dream(dream, !symbols.isEmpty())).orElse(null);
        if (hit != null) {
            gateway.recordBlocked(AiFeature.DREAM, hit.policyCode());
            return new DreamResponse(DreamMatchResult.NO_MATCH.name(), hit.policyCode(), hit.message(), false,
                    List.of(), null, List.of(), List.of(), BASIS_NOTICE, NOTICE, remaining(guestKey, clientIp));
        }

        // 상징이 없으면 AI 없이 고정 응답. 횟수 차감 없이 기록만 남긴다.
        if (symbols.isEmpty()) {
            gateway.recordBlocked(AiFeature.DREAM, POLICY_LOW_RELEVANCE);
            List<PickedNumber> picked = numberPicker.pick(dreamNumbers, List.of(), supplement, statNumbersIf(supplement));
            return new DreamResponse(DreamMatchResult.NO_MATCH.name(), POLICY_LOW_RELEVANCE, LOW_RELEVANCE_MESSAGE, true,
                    List.of(), null, numberViews(picked), List.of(), BASIS_NOTICE, NOTICE, remaining(guestKey, clientIp));
        }

        List<Long> ids = symbols.stream().map(MatchedSymbol::id).toList();
        Map<Long, List<Interpretation>> chosen = chooseInterpretations(dream, symbols, repository.loadInterpretations(ids));
        List<List<SymbolNumber>> numbersBySymbol = numbersInSymbolOrder(symbols, repository.loadNumbers(ids));
        List<PickedNumber> picked = numberPicker.pick(dreamNumbers, numbersBySymbol, supplement, statNumbersIf(supplement));
        DreamMatchResult match = numberPicker.isComplete(picked) ? DreamMatchResult.FULL_MATCH : DreamMatchResult.PARTIAL_MATCH;

        // 여기서 무료 횟수가 1회 차감된다. AI 호출이 실패하면 AiSession 이 자동으로 환불한다.
        AiSession session = gateway.open(AiFeature.DREAM, guestKey, clientIp);
        AiRequest aiRequest = promptBuilder.build(symbols, chosen);
        AiResponse aiResponse = session.call(aiRequest);
        String text = sanitize(aiResponse.text(), symbols, chosen);

        return new DreamResponse(match.name(), null, null, false, symbolViews(symbols, chosen), text,
                numberViews(picked), sourceViews(repository.loadSources(ids)), BASIS_NOTICE, NOTICE,
                remaining(guestKey, clientIp));
    }

    // ------------------------------------------------------------------ 보조

    private static String validate(DreamRequest request) {
        String dream = request == null || request.dream() == null ? "" : request.dream().trim();
        if (dream.length() < MIN_LENGTH || dream.length() > MAX_LENGTH) {
            // 꿈 내용 자체는 오류 상세에도 남기지 않고 길이만 남긴다.
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "꿈 내용은 " + MIN_LENGTH + "자 이상 " + MAX_LENGTH + "자 이하로 입력해 주세요.",
                    "꿈 내용 길이 오류: " + dream.length());
        }
        return dream;
    }

    private Map<Long, List<Interpretation>> chooseInterpretations(String dream, List<MatchedSymbol> symbols,
                                                                  List<Interpretation> all) {
        Map<Long, List<Interpretation>> grouped = new LinkedHashMap<>();
        for (Interpretation i : all) {
            grouped.computeIfAbsent(i.symbolId(), k -> new ArrayList<>()).add(i);
        }
        Map<Long, List<Interpretation>> chosen = new LinkedHashMap<>();
        for (MatchedSymbol s : symbols) {
            chosen.put(s.id(), selector.select(dream, grouped.getOrDefault(s.id(), List.of())));
        }
        return chosen;
    }

    private static List<List<SymbolNumber>> numbersInSymbolOrder(List<MatchedSymbol> symbols, List<SymbolNumber> all) {
        List<List<SymbolNumber>> result = new ArrayList<>();
        for (MatchedSymbol s : symbols) {
            List<SymbolNumber> list = new ArrayList<>();
            for (SymbolNumber n : all) {
                if (n.symbolId() == s.id()) {
                    list.add(n);
                }
            }
            // 전통 번호(스모르피아·차라다)를 서비스 매핑보다 먼저 쓴다. 근거가 있는 번호를 우선하기 위해서다.
            list.sort(Comparator.comparingInt(n -> n.origin() == com.fortunaball.dream.domain.DreamNumberOrigin.SERVICE ? 1 : 0));
            result.add(list);
        }
        return result;
    }

    /** 통계 보완 후보: 최근 100회 출현 횟수 많은 순(같으면 작은 번호 먼저). 실패하면 보완 없이 진행한다. */
    private List<Integer> statNumbersIf(boolean supplement) {
        if (!supplement) {
            return List.of();
        }
        try {
            NumberStatResponse stat = statService.numbers(StatPeriod.parse("100"), null, null);
            return stat.items().stream()
                    .sorted(Comparator.comparingInt(NumberStatResponse.Item::count).reversed()
                            .thenComparingInt(NumberStatResponse.Item::number))
                    .map(NumberStatResponse.Item::number)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("꿈풀이 통계 보완 번호를 가져오지 못했습니다: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    /** AI 문장이 비었거나 길거나 단정 표현이 있으면 DB 해석으로 만든 문장으로 바꾼다. */
    String sanitize(String aiText, List<MatchedSymbol> symbols, Map<Long, List<Interpretation>> chosen) {
        String text = aiText == null ? "" : aiText.trim();
        boolean bad = text.isEmpty() || text.length() > DreamPromptBuilder.MAX_ANSWER_CHARS || FORBIDDEN.matcher(text).find();
        return bad ? fallbackText(symbols, chosen) : text;
    }

    private static String fallbackText(List<MatchedSymbol> symbols, Map<Long, List<Interpretation>> chosen) {
        StringBuilder sb = new StringBuilder();
        for (MatchedSymbol s : symbols) {
            for (Interpretation i : chosen.getOrDefault(s.id(), List.of())) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append('[').append(s.keyword()).append("] ").append(i.summary());
            }
        }
        sb.append(" 재미로 참고해 주세요.");
        String text = sb.toString();
        return text.length() > DreamPromptBuilder.MAX_ANSWER_CHARS
                ? text.substring(0, DreamPromptBuilder.MAX_ANSWER_CHARS - 1) + "…" : text;
    }

    private Integer remaining(String guestKey, String clientIp) {
        AiUsageSnapshot snapshot = gateway.usage(guestKey, clientIp, false);
        return snapshot.unlimited() ? null : snapshot.remainingToday();
    }

    private static List<DreamResponse.SymbolView> symbolViews(List<MatchedSymbol> symbols,
                                                              Map<Long, List<Interpretation>> chosen) {
        List<DreamResponse.SymbolView> views = new ArrayList<>();
        for (MatchedSymbol s : symbols) {
            List<DreamResponse.InterpretationView> items = new ArrayList<>();
            for (Interpretation i : chosen.getOrDefault(s.id(), List.of())) {
                items.add(new DreamResponse.InterpretationView(i.situation(), i.summary(), i.polarity(),
                        i.originRegion(), i.sourceName()));
            }
            views.add(new DreamResponse.SymbolView(s.keyword(), items));
        }
        return views;
    }

    private static List<DreamResponse.NumberView> numberViews(List<PickedNumber> picked) {
        return picked.stream().map(p -> new DreamResponse.NumberView(p.number(), p.origin().name())).toList();
    }

    private static List<DreamResponse.SourceView> sourceViews(List<SourceRef> refs) {
        Set<String> seen = new LinkedHashSet<>();
        List<DreamResponse.SourceView> views = new ArrayList<>();
        for (SourceRef r : refs) {
            if (seen.add(r.name())) {
                views.add(new DreamResponse.SourceView(r.name(), r.type(), r.trustLevel()));
            }
        }
        return views;
    }
}
