package com.fortunaball.dream.service;

import com.fortunaball.ai.AiGateway;
import com.fortunaball.ai.AiSession;
import com.fortunaball.ai.client.AiResponse;
import com.fortunaball.ai.policy.AiPolicyEngine;
import com.fortunaball.ai.policy.PolicyContext;
import com.fortunaball.ai.policy.PolicyHit;
import com.fortunaball.ai.usage.AiFeature;
import com.fortunaball.ai.usage.AiUsageSnapshot;
import com.fortunaball.analyst.service.AnalystSmallTalk;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.dream.domain.DreamKnowledge.Interpretation;
import com.fortunaball.dream.domain.DreamKnowledge.MatchedSymbol;
import com.fortunaball.dream.domain.DreamKnowledge.PickedNumber;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolEntry;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolNumber;
import com.fortunaball.dream.domain.DreamMatchResult;
import com.fortunaball.dream.domain.DreamNumberOrigin;
import com.fortunaball.dream.dto.DreamRequest;
import com.fortunaball.dream.dto.DreamResponse;
import com.fortunaball.dream.repository.DreamKnowledgeRepository;
import com.fortunaball.dream.service.DreamNarrator.Scene;
import com.fortunaball.dream.service.DreamReplyReader.Intent;
import com.fortunaball.dream.service.DreamReplyReader.Reading;
import com.fortunaball.generate.dto.GenerateResponse;
import com.fortunaball.generate.service.GenerateService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * AI 꿈풀이 (API-08) - 사용자와 말로 이어 가는 대화
 *
 * 한 번의 요청은 "대화 한 턴"이다. 서버는 꿈 원문도 대화 내용도 저장하지 않으므로, 화면이 이어서 보내 준다.
 *   처음   : { dream }
 *   이어서 : { dream, reply(사용자가 방금 한 말), step(직전 응답의 step), keep, gameCount, confirmToken }  ← 직전 응답 값을 그대로 돌려보낸다
 *
 * 처음 꿈을 들려줬을 때 (꿈에서 나온 번호 = 꿈 속 숫자 + 찾은 상징들의 번호, 개수 제한 없음)
 *   인사·감사·사용법·정책에 걸린 말(당첨 보장·구매 유도·절박한 요청) → 고정 문구 (AI 없음, 횟수 차감 없음)
 *   짧은 말·꿈과 거리가 있는 말 → AI 가 대화로 받는다. (횟수 1회. 정말 무관하면 AI 가 판단해 고정 문구, 횟수 돌려줌)
 *   번호 0개   "통계 번호를 만들어 볼까요?"                         → step ASK_STAT_NUMBERS (고정 문구)
 *   번호 1~3개 "나머지를 통계로 채워도 될까요?"                     → step ASK_FILL
 *   번호 4~5개 모자란 1~2개를 통계로 바로 채우고 확정/다시 질문     → step CONFIRM
 *   번호 6개   그대로 확정 질문                                     → step CONFIRM
 *   번호 7개 이상  몇 게임으로 나눌지 / 통계로 6개만 / 뺄 번호 질문 → step ASK_OVERFLOW
 * 이어서 사용자가 말로 답하면 (DreamReplyReader 가 뜻을 읽는다)
 *   "확정할게요"        → action=ISSUE 와 confirmToken 을 돌려주고, 화면이 기존 발급 확정(API-04)을 부른다. (고정 문구, 차감 없음)
 *   "다시 만들어 주세요" → 꿈에서 나온 번호는 그대로, 통계로 채운 번호만 새로 만든다.
 *   "네, 채워 주세요"   → 통계로 채운다.
 *   "3게임 / 3천원어치" → 꿈 번호를 게임들에 골고루 나눠 담고 모자란 자리는 통계로 채운다. (DreamGameSplitter)
 *   "통계로 6개만"      → 통계로 많이 나온 순 6개를 고른다. (DreamStatRanker)
 *   "7번이랑 9번 빼고"  → 그 번호를 빼고 다시 정리한다.
 *   질문과 상관없는 말   → 새 이야기로 보고 처음부터 다시 처리한다.
 *
 * 말투와 흐름
 *   풀이 앞뒤의 말은 AI 가 상황에 맞게 이어 쓴다. (DreamNarrator) 번호와 상징 이름은 서버가 넣고, AI 가 실패하거나 규칙을 어기면
 *   정해진 문구(DreamMessages)로 대신한다. 그래서 안내해야 할 내용이 빠지는 일은 없다.
 *
 * 무료 횟수
 *   AI 가 글을 쓴 턴은 무료 횟수를 1회 쓴다. 한 턴 안에서 AI 를 여러 번 불러도 1회만 쓴다. (AiSession)
 *   고정 문구로 답한 턴(인사, 감사, 짧은 꿈, 정책 차단, 확정, 거절)은 쓰지 않는다.
 *   턴 중간에 실패하면 되돌려 준다. (interpret 의 catch)
 *
 * 번호 만들기는 번호 생성 기능(GenerateService)이 한다. 꿈에서 나온 번호를 "포함 번호"로 고정하면 나머지만 통계로 채워지고
 * 임시 확정 토큰도 함께 나온다. 확정은 이 API 가 아니라 기존 발급 확정(API-04)이 처리한다.
 *
 * ⚠ 꿈 원문은 저장하지도, 로그에 남기지도, AI 에 보내지도 않는다. (AI 는 상징 이름과 DB 해석, 그리고 상황 설명만 받는다)
 * ⚠ 자료 출처 이름은 응답에 담지 않는다. 서비스 안내 화면에만 보여 준다.
 */
@Service
public class DreamService {

    static final int MIN_LENGTH = 5;
    static final int MAX_LENGTH = 300;
    static final String BASIS_NOTICE =
            "한국 전통 꿈풀이를 먼저 참고했고, 한국 자료가 없는 상징은 해외 자료를 참고했어요.";
    static final String NOTICE =
            "꿈 상징과 통계를 바탕으로 한 참고 정보이며 당첨을 보장하지 않습니다. 재미로 참고해 주세요.";

    /** 결과를 단정하거나 당첨 가능성을 높인다고 말하는 표현. 걸리면 AI 문장을 쓰지 않는다. (DreamNarrator 도 같이 쓴다) */
    static final Pattern FORBIDDEN = Pattern.compile(
            "반드시|확실히|틀림없|무조건|보장|100\\s*%|당첨\\s*(됩니다|돼요|될 거|될거|확률이 (높|올))|1등\\s*(됩니다|돼요|될 거)");

    private final DreamKnowledgeRepository repository;
    private final DreamSymbolMatcher matcher;
    private final DreamNumberExtractor numberExtractor;
    private final DreamNumberPicker numberPicker;
    private final DreamInterpretationSelector selector;
    private final DreamPromptBuilder promptBuilder;
    private final AiGateway gateway;
    private final GenerateService generateService;
    private final AiPolicyEngine policyEngine;
    private final DreamMessages messages;
    private final DreamReplyReader reader;
    private final DreamNarrator narrator;
    private final DreamStatRanker ranker;
    private final DreamChat chat;

    public DreamService(DreamKnowledgeRepository repository, DreamSymbolMatcher matcher,
                        DreamNumberExtractor numberExtractor, DreamNumberPicker numberPicker,
                        DreamInterpretationSelector selector, DreamPromptBuilder promptBuilder,
                        AiGateway gateway, GenerateService generateService, AiPolicyEngine policyEngine,
                        DreamMessages messages, DreamReplyReader reader, DreamNarrator narrator,
                        DreamStatRanker ranker, DreamChat chat) {
        this.repository = repository;
        this.matcher = matcher;
        this.numberExtractor = numberExtractor;
        this.numberPicker = numberPicker;
        this.selector = selector;
        this.promptBuilder = promptBuilder;
        this.gateway = gateway;
        this.generateService = generateService;
        this.policyEngine = policyEngine;
        this.messages = messages;
        this.reader = reader;
        this.narrator = narrator;
        this.ranker = ranker;
        this.chat = chat;
    }

    // ------------------------------------------------------------------ 한 턴

    /** 이번 턴에서 AI 를 쓸 때 한 번만 무료 횟수를 차감하기 위한 도구. 처음 필요할 때 열린다. */
    private final class Turn {
        private final GuestToken guest;
        private final String clientIp;
        private AiSession session;
        private boolean refunded;   // 돌려준 뒤에는 화면에 "횟수 사용"을 보여 주지 않는다

        Turn(GuestToken guest, String clientIp) {
            this.guest = guest;
            this.clientIp = clientIp;
        }

        AiSession session() {
            if (session == null) {
                session = gateway.open(AiFeature.DREAM, guest.value(), clientIp);   // 여기서 1회 차감
            }
            return session;
        }

        boolean charged() {
            return session != null && !refunded;
        }

        void refund() {
            if (session != null) {
                session.refund();   // 이미 돌려줬다면 아무 일도 하지 않는다
                refunded = true;
            }
        }
    }

    public DreamResponse interpret(DreamRequest request, GuestToken guest, String clientIp) {
        Turn turn = new Turn(guest, clientIp);
        try {
            return handle(request == null ? DreamRequest.first("") : request, turn);
        } catch (RuntimeException e) {
            turn.refund();   // 번호를 못 만들었거나 AI 가 실패한 것은 사용자 잘못이 아니다
            throw e;
        }
    }

    private DreamResponse handle(DreamRequest request, Turn turn) {
        String dream = request.dream() == null ? "" : request.dream().trim();
        String reply = request.reply() == null ? "" : request.reply().trim();
        if (dream.length() > MAX_LENGTH) {
            throw invalidLength(dream);
        }
        if (reply.length() > MAX_LENGTH) {
            throw invalidLength(reply);
        }
        if (!reply.isEmpty() && isQuestionStep(request.step())) {
            Optional<DreamResponse> answered = followUp(request, dream, reply, turn);
            if (answered.isPresent()) {
                return answered.get();
            }
            // 질문에 대한 답이 아니었다.
            //  - 꿈 상징도 꿈 속 숫자도 없는 말(잡담·"기억 안 나요" 등) → AI 가 대화로 받는다. 질문 단계와 버튼은 그대로 유지한다.
            //  - 그 밖의 말 → 사용자의 말을 새 이야기로 보고 처음부터 처리한다
            if (isPlainTalk(reply)) {
                return talk(reply, request.step(), turn);
            }
        }
        return fresh(reply.isEmpty() ? dream : reply, turn);
    }

    private static boolean isQuestionStep(String step) {
        return DreamResponse.STEP_CONFIRM.equals(step) || DreamResponse.STEP_ASK_FILL.equals(step)
                || DreamResponse.STEP_ASK_STAT_NUMBERS.equals(step) || DreamResponse.STEP_ASK_OVERFLOW.equals(step);
    }

    // ------------------------------------------------------------------ 새 이야기 (처음 풀이)

    private DreamResponse fresh(String text, Turn turn) {
        // 인사·감사·사용법은 짧아도 정상이므로 길이 검사보다 먼저 본다.
        Optional<AnalystSmallTalk.Kind> talk = messages.detectSmallTalk(text);
        if (talk.isPresent()) {
            DreamMessages.Talk t = messages.talk(talk.get());
            gateway.recordBlocked(AiFeature.DREAM, t.code());
            Reply r = Reply.fixed(DreamMatchResult.NO_MATCH, DreamResponse.STEP_TALK, t.message());
            r.policyCode = t.code();
            r.action = t.openGuide() ? DreamResponse.ACTION_OPEN_GUIDE : null;
            r.suggestions = DreamMessages.SUGGESTIONS;
            return respond(r, turn);
        }
        // 대화 우선: 짧은 말도 막지 않고 AI 가 대화로 받는다. (인사·감사·사용법은 위에서 고정 문구로 끝났다)
        if (text.isEmpty()) {
            gateway.recordBlocked(AiFeature.DREAM, DreamMessages.CODE_TOO_SHORT);   // 빈 입력은 AI 에게 보내지 않는다
            Reply r = Reply.fixed(DreamMatchResult.NO_MATCH, DreamResponse.STEP_NEED_MORE, messages.tooShort());
            r.policyCode = DreamMessages.CODE_TOO_SHORT;
            r.suggestions = DreamMessages.SUGGESTIONS;
            return respond(r, turn);
        }
        if (text.length() < MIN_LENGTH) {
            return talk(text, null, turn);
        }

        Material m = material(text);

        // 정책에 걸리면(당첨 보장 요구, 구매 유도, 절박한 요청, 꿈이 아닌 입력) AI 없이 고정 문구로 응답한다. 번호도 만들지 않는다.
        PolicyHit hit = policyEngine.check(PolicyContext.dream(text, !m.symbols().isEmpty())).orElse(null);
        // 꿈 표현이 없다는 키워드 판정(DREAM_OFF_TOPIC)은 바로 막지 않고 AI 가 대화로 받는다. 정말 무관한 말은 AI 가 판단해 고정 문구로 돌려보낸다.
        if (hit != null && DreamMessages.CODE_OFF_TOPIC.equals(hit.policyCode())) {
            return talk(text, null, turn);
        }
        if (hit != null) {
            gateway.recordBlocked(AiFeature.DREAM, hit.policyCode());
            boolean offTopic = DreamMessages.CODE_OFF_TOPIC.equals(hit.policyCode());
            Reply r = Reply.fixed(DreamMatchResult.NO_MATCH, DreamResponse.STEP_TALK,
                    offTopic ? messages.offTopic() : hit.message());
            r.policyCode = hit.policyCode();
            r.suggestions = offTopic ? DreamMessages.SUGGESTIONS : List.of();
            return respond(r, turn);
        }

        // 꿈에서 나온 번호가 하나도 없으면 "통계 번호를 만들어 볼까요?"를 묻는다.
        // 대화 우선: 앞의 말은 AI 가 상황에 맞게 받아 준다. (횟수 1회) AI 글을 못 쓰면 횟수를 돌려주고 고정 문구로 묻는다.
        if (m.base().isEmpty()) {
            String said = chat.clean(turn.session().call(chat.requestThenAsk(text)).text());
            if (said != null) {
                Reply r = Reply.fixed(DreamMatchResult.NO_MATCH, DreamResponse.STEP_ASK_STAT_NUMBERS, said);
                r.quickReplies = DreamMessages.REPLIES_STAT;
                r.suggestions = DreamMessages.SUGGESTIONS;
                return respond(r, turn);
            }
            turn.refund();
            gateway.recordBlocked(AiFeature.DREAM, DreamMessages.CODE_LOW_RELEVANCE);
            Reply r = Reply.fixed(DreamMatchResult.NO_MATCH, DreamResponse.STEP_ASK_STAT_NUMBERS, messages.askStatNumbers());
            r.policyCode = DreamMessages.CODE_LOW_RELEVANCE;
            r.quickReplies = DreamMessages.REPLIES_STAT;
            r.suggestions = DreamMessages.SUGGESTIONS;
            return respond(r, turn);
        }

        // 여기서 무료 횟수가 1회 차감된다. AI 가 실패하면 AiSession 이 자동으로 되돌려 준다.
        AiSession session = turn.session();
        String interpretation = null;
        List<DreamResponse.SymbolView> views = List.of();
        if (!m.symbols().isEmpty()) {
            Map<Long, List<Interpretation>> chosen = chooseInterpretations(text, m.symbols(),
                    repository.loadInterpretations(m.symbols().stream().map(MatchedSymbol::id).toList()));
            AiResponse ai = session.call(promptBuilder.build(m.symbols(), chosen));
            interpretation = sanitize(ai.text(), m.symbols(), chosen);
            views = symbolViews(m.symbols(), chosen);
        }
        Reply r = stateOf(m, m.base(), true, turn);
        r.interpretation = interpretation;
        r.symbols = views;
        return respond(r, turn);
    }

    /**
     * 꿈에서 나온 번호(base)의 개수에 따라 다음 대화를 정한다. (처음 풀이와, 번호를 뺀 뒤 다시 정리할 때 함께 쓴다)
     *
     * @param first true 면 처음 풀이의 첫마디, false 면 "번호를 빼고 정리했어요" 말투
     */
    private Reply stateOf(Material m, List<PickedNumber> base, boolean first, Turn turn) {
        String line = messages.symbolLine(m.symbols(), m.bySymbol(), base);
        int size = base.size();
        DreamMatchResult match = size >= DreamNumberPicker.TARGET ? DreamMatchResult.FULL_MATCH : DreamMatchResult.PARTIAL_MATCH;

        if (size > DreamNumberPicker.TARGET) {
            List<String> say = say(turn, Scene.OVERFLOW, size, line, messages.overflow(size, line), null);
            Reply r = Reply.asking(match, DreamResponse.STEP_ASK_OVERFLOW, say, DreamMessages.REPLIES_OVERFLOW);
            r.numbers = base;
            r.keep = new ArrayList<>(numbersOf(base));
            return r;
        }
        if (size <= 3) {
            List<String> say = say(turn, Scene.ASK_FILL, size, line, messages.askFill(line), null);
            Reply r = Reply.asking(match, DreamResponse.STEP_ASK_FILL, say, DreamMessages.REPLIES_FILL);
            r.numbers = base;
            r.keep = new ArrayList<>(numbersOf(base));
            return r;
        }
        // 6개면 그대로, 4~5개면 모자란 번호를 통계로 바로 채운다.
        boolean full = size == DreamNumberPicker.TARGET;
        Generated g = generate(List.of(numbersOf(base)), originOf(base), turn.guest);
        List<String> say;
        if (first) {
            say = full ? say(turn, Scene.FULL, 0, line, messages.openFull(), messages.confirmAskFull())
                    : say(turn, Scene.FILLED, 0, line, messages.openFilled(line), messages.confirmAskFilled());
        } else {
            say = full ? say(turn, Scene.REMOVED_FULL, 0, line, messages.openRemoved(false) + " " + messages.confirmAskFull())
                    : say(turn, Scene.REMOVED_FILLED, 0, line, messages.openRemoved(true) + " " + messages.askAfterFill());
        }
        return confirmReply(match, say, g);
    }

    // ------------------------------------------------------------------ 자유로운 대화

    /** 꿈 상징도, 꿈 속 숫자도 없는 말인지. (인사·감사는 제외) 이런 말은 새 꿈 이야기가 아니라 대화로 받는다. */
    private boolean isPlainTalk(String reply) {
        if (messages.detectSmallTalk(reply).isPresent()) {
            return false;
        }
        Material m = material(reply);
        return m.symbols().isEmpty() && m.base().isEmpty();
    }

    /**
     * AI 가 대화로 받는다. AI 가 쓴 글이므로 무료 횟수 1회를 쓴다.
     * AI 가 "무관한 이야기"라고 판단했거나 글이 규칙(숫자·단정 표현·길이)을 어기면 횟수를 돌려주고 고정 문구로 안내한다.
     *
     * @param step 직전에 물어본 질문 단계 (없으면 null). 있으면 같은 선택 버튼을 다시 보여 준다. 화면은 이 응답으로 대화 상태를 바꾸지 않는다.
     */
    private DreamResponse talk(String text, String step, Turn turn) {
        String said = chat.clean(turn.session().call(chat.request(text, step)).text());
        if (said == null) {
            turn.refund();
            gateway.recordBlocked(AiFeature.DREAM, DreamMessages.CODE_OFF_TOPIC);
            Reply r = Reply.fixed(DreamMatchResult.NO_MATCH, DreamResponse.STEP_TALK, messages.offTopic());
            r.policyCode = DreamMessages.CODE_OFF_TOPIC;
            r.quickReplies = quickRepliesOf(step);
            r.suggestions = r.quickReplies.isEmpty() ? DreamMessages.SUGGESTIONS : List.of();
            return respond(r, turn);
        }
        Reply r = Reply.fixed(DreamMatchResult.NO_MATCH, DreamResponse.STEP_TALK, said);
        r.quickReplies = quickRepliesOf(step);
        r.suggestions = r.quickReplies.isEmpty() ? DreamMessages.SUGGESTIONS : List.of();
        return respond(r, turn);
    }

    private static List<String> quickRepliesOf(String step) {
        if (step == null) {
            return List.of();
        }
        return switch (step) {
            case DreamResponse.STEP_CONFIRM -> DreamMessages.REPLIES_CONFIRM;
            case DreamResponse.STEP_ASK_FILL -> DreamMessages.REPLIES_FILL;
            case DreamResponse.STEP_ASK_STAT_NUMBERS -> DreamMessages.REPLIES_STAT;
            case DreamResponse.STEP_ASK_OVERFLOW -> DreamMessages.REPLIES_OVERFLOW;
            default -> List.of();
        };
    }

    // ------------------------------------------------------------------ 이어지는 대화

    /** 사용자의 말을 직전 질문에 대한 답으로 읽는다. 답이 아니면 비어 있고, 호출한 쪽이 새 이야기로 처리한다. */
    private Optional<DreamResponse> followUp(DreamRequest req, String dream, String reply, Turn turn) {
        String step = req.step();
        Material m = dream.length() >= MIN_LENGTH ? material(dream) : Material.EMPTY;
        Set<Integer> candidates = numbersOf(m.base());
        Reading reading = read(step, reply, candidates, turn);
        Intent intent = reading.intent();

        switch (step) {
            case DreamResponse.STEP_CONFIRM:
                if (intent == Intent.CONFIRM || intent == Intent.YES) {
                    return Optional.of(confirmed(req, m, turn));
                }
                if (intent == Intent.REGENERATE) {
                    return Optional.of(regenerate(req, m, turn));
                }
                break;
            case DreamResponse.STEP_ASK_FILL:
                if (intent == Intent.YES) {
                    List<PickedNumber> base = workingBase(m, req.keep());
                    return Optional.of(fillAfterConsent(m, base, turn));
                }
                break;
            case DreamResponse.STEP_ASK_STAT_NUMBERS:
                if (intent == Intent.YES) {
                    return Optional.of(statOnly(turn));
                }
                break;
            case DreamResponse.STEP_ASK_OVERFLOW:
                return overflowAnswer(req, m, reading, reply, turn);
            default:
                break;
        }
        if (intent == Intent.NO) {
            Reply r = Reply.fixed(DreamMatchResult.NO_MATCH, DreamResponse.STEP_TALK, messages.declined());
            r.suggestions = DreamMessages.SUGGESTIONS;
            return Optional.of(respond(r, turn));
        }
        return Optional.empty();
    }

    /** 단어·숫자로 읽고, 못 읽은 짧은 말만 AI 에게 읽힌다. (인사·감사는 읽힐 필요 없이 새 이야기로 넘어간다) */
    private Reading read(String step, String reply, Set<Integer> candidates, Turn turn) {
        Reading quick = reader.quick(step, reply, candidates);
        if (quick.intent() != Intent.UNCLEAR || messages.detectSmallTalk(reply).isPresent() || !reader.isShort(reply)) {
            return quick;
        }
        try {
            return reader.fromAi(turn.session().call(reader.aiRequest(step, reply)).text(), candidates);
        } catch (BusinessException e) {
            return quick;   // AI 가 읽지 못하면 못 알아들은 것으로 본다
        }
    }

    /** "확정할게요": 새로 만들지 않고, 화면이 API-04 로 확정하도록 알려 준다. 차감 없음 */
    private DreamResponse confirmed(DreamRequest req, Material m, Turn turn) {
        if (req.confirmToken() == null || req.confirmToken().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "확정할 번호를 찾을 수 없어요. 번호를 다시 만들어 주세요.",
                    "확정 요청인데 confirmToken 이 없음");
        }
        Reply r = Reply.fixed(matchOf(m.base()), DreamResponse.STEP_CONFIRMED, messages.confirmed());
        r.action = DreamResponse.ACTION_ISSUE;
        r.tokenOverride = req.confirmToken();
        r.keep = req.keep();
        r.gameCountOverride = req.gameCount();
        return respond(r, turn);
    }

    /** "다시 만들어 주세요": 꿈에서 나온 번호(keep)는 그대로 두고 통계로 채운 번호만 새로 만든다. 무료 횟수 1회 */
    private DreamResponse regenerate(DreamRequest req, Material m, Turn turn) {
        int games = gameCountOf(req);
        Set<Integer> pool = new LinkedHashSet<>(req.keep() == null ? List.of() : req.keep());
        if (!numbersOf(m.base()).containsAll(pool)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "꿈 내용을 확인할 수 없어요. 꿈 이야기를 다시 들려주세요.",
                    "keep 번호가 꿈에서 나온 번호가 아님");
        }
        List<Set<Integer>> perGame = perGame(pool, games);
        if (perGame.stream().allMatch(s -> s.size() >= DreamNumberPicker.TARGET)) {
            // 통계로 채운 번호가 없다. 새로 만들 것이 없으므로 차감하지 않는다.
            Reply r = Reply.fixed(matchOf(m.base()), DreamResponse.STEP_CONFIRM, messages.nothingToRegenerate());
            r.quickReplies = DreamMessages.REPLIES_CONFIRM_ONLY;
            r.tokenOverride = req.confirmToken();
            r.keep = req.keep();
            r.gameCountOverride = games;
            return respond(r, turn);
        }
        turn.session();   // 먼저 횟수를 확인하고 차감한다
        Generated g = generate(perGame, originOf(m.base()), turn.guest);
        String line = messages.symbolLine(m.symbols(), m.bySymbol(), m.base());
        List<String> say = pool.isEmpty()
                ? say(turn, Scene.STAT_ONLY, 0, line, messages.statOnly() + " " + messages.askAfterFill())
                : say(turn, Scene.REGENERATED, 0, line, messages.regenerated() + " " + messages.askAfterFill());
        return respond(confirmReply(matchOf(m.base()), say, g), turn);
    }

    /** "네, 채워 주세요": 꿈에서 나온 번호는 그대로 두고 모자란 번호를 통계로 채운다. */
    private DreamResponse fillAfterConsent(Material m, List<PickedNumber> base, Turn turn) {
        requireBase(base);
        turn.session();
        Generated g = generate(List.of(numbersOf(base)), originOf(m.base()), turn.guest);
        String line = messages.symbolLine(m.symbols(), m.bySymbol(), base);
        List<String> say = say(turn, Scene.FILLED_AFTER_CONSENT, 0, line, messages.askAfterFill());
        return respond(confirmReply(matchOf(m.base()), say, g), turn);
    }

    /** 꿈 번호가 없을 때 "네, 통계 번호 만들어 주세요": 통계만으로 한 게임을 만든다. */
    private DreamResponse statOnly(Turn turn) {
        turn.session();
        Generated g = generate(List.of(Set.of()), Map.of(), turn.guest);
        List<String> say = say(turn, Scene.STAT_ONLY, 0, "", messages.statOnly() + " " + messages.askAfterFill());
        return respond(confirmReply(DreamMatchResult.NO_MATCH, say, g), turn);
    }

    /** 6개를 넘을 때의 답: 게임 수 / 통계로 줄이기 / 뺄 번호 */
    private Optional<DreamResponse> overflowAnswer(DreamRequest req, Material m, Reading reading, String reply, Turn turn) {
        Intent intent = reading.intent();
        if (intent == Intent.NO) {
            Reply r = Reply.fixed(DreamMatchResult.NO_MATCH, DreamResponse.STEP_TALK, messages.declined());
            r.suggestions = DreamMessages.SUGGESTIONS;
            return Optional.of(respond(r, turn));
        }
        if (intent == Intent.UNCLEAR && !reader.isShort(reply)) {
            return Optional.empty();   // 긴 말은 새 꿈 이야기로 본다
        }
        List<PickedNumber> base = workingBase(m, req.keep());
        requireBase(base);
        String line = messages.symbolLine(m.symbols(), m.bySymbol(), base);
        Map<Integer, DreamNumberOrigin> origin = originOf(m.base());
        turn.session();

        if (intent == Intent.GAMES && reading.gameCount() != null && reading.gameCount() > 1) {
            int games = reading.gameCount();
            List<Set<Integer>> perGame = DreamGameSplitter.split(ranker.rank(numbersOf(base)), games, DreamNumberPicker.TARGET);
            Generated g = generate(perGame, origin, turn.guest);
            List<String> say = say(turn, Scene.MULTI, games, line, messages.multiGames(games) + " " + messages.confirmAskFull());
            return Optional.of(respond(confirmReply(matchOf(m.base()), say, g), turn));
        }
        if (intent == Intent.GAMES || intent == Intent.STATS) {
            // 1게임이거나 통계로 줄이기: 많이 나온 순으로 6개만 고른다.
            List<Integer> top = ranker.rank(numbersOf(base));
            Set<Integer> pool = new LinkedHashSet<>(top.subList(0, Math.min(DreamNumberPicker.TARGET, top.size())));
            Generated g = generate(List.of(pool), origin, turn.guest);
            List<String> say = say(turn, Scene.BY_STATS, 0, line, messages.pickedByStats() + " " + messages.confirmAskFull());
            return Optional.of(respond(confirmReply(matchOf(m.base()), say, g), turn));
        }
        if (intent == Intent.REMOVE) {
            List<PickedNumber> remaining = base.stream().filter(p -> !reading.remove().contains(p.number())).toList();
            if (remaining.isEmpty()) {
                List<String> say = say(turn, Scene.REMOVED_ALL, 0, line, messages.removedAll());
                Reply r = Reply.asking(matchOf(m.base()), DreamResponse.STEP_ASK_OVERFLOW, say, DreamMessages.REPLIES_OVERFLOW);
                r.numbers = base;
                r.keep = new ArrayList<>(numbersOf(base));
                return Optional.of(respond(r, turn));
            }
            return Optional.of(respond(stateOf(m, remaining, false, turn), turn));
        }
        // 짧은 말인데 무슨 뜻인지 모르겠다 → 다시 한 번 물어본다
        List<String> say = say(turn, Scene.OVERFLOW_UNCLEAR, 0, line, messages.overflowUnclear());
        Reply r = Reply.asking(matchOf(m.base()), DreamResponse.STEP_ASK_OVERFLOW, say, DreamMessages.REPLIES_OVERFLOW);
        r.numbers = base;
        r.keep = new ArrayList<>(numbersOf(base));
        return Optional.of(respond(r, turn));
    }

    // ------------------------------------------------------------------ 번호 만들기

    /** 번호 생성 결과와, 게임마다 번호에 출처(꿈 상징인지 통계인지)를 붙인 값 */
    private record Generated(GenerateResponse response, List<List<PickedNumber>> games) {
    }

    /**
     * 게임마다 꿈에서 나온 번호를 포함 번호로 고정하고 나머지는 통계로 채운다.
     * 포함 번호에 없던 번호는 통계로 채운 것이므로 출처를 STAT 으로 붙인다.
     */
    private Generated generate(List<Set<Integer>> perGame, Map<Integer, DreamNumberOrigin> origin, GuestToken guest) {
        GenerateResponse generated = perGame.size() == 1
                ? generateService.generateWithNumbers(guest, 1, new LinkedHashSet<>(perGame.get(0)), Set.of())
                : generateService.generateEachWithNumbers(guest, perGame);
        List<List<PickedNumber>> games = generated.games().stream()
                .map(game -> game.stream().sorted()
                        .map(n -> new PickedNumber(n, origin.getOrDefault(n, DreamNumberOrigin.STAT))).toList())
                .toList();
        return new Generated(generated, games);
    }

    /** 다시 만들 때 게임마다 포함할 번호. 한 게임이면 pool 전체, 여러 게임이면 통계 순서대로 골고루 나눈다. */
    private List<Set<Integer>> perGame(Set<Integer> pool, int games) {
        if (games == 1) {
            return List.of(pool);
        }
        if (pool.isEmpty()) {
            return java.util.Collections.nCopies(games, Set.<Integer>of());
        }
        return DreamGameSplitter.split(ranker.rank(pool), games, DreamNumberPicker.TARGET);
    }

    private Reply confirmReply(DreamMatchResult match, List<String> say, Generated g) {
        Reply r = new Reply(match, DreamResponse.STEP_CONFIRM, say.get(0));
        r.question = say.size() > 1 ? say.get(1) : null;
        r.generated = g.response();
        if (g.games().size() == 1) {
            r.numbers = g.games().get(0);
        } else {
            r.games = g.games();
        }
        r.keep = keepOf(g.games());
        r.gameCountOverride = g.games().size();
        // 통계로 채운 번호가 있을 때만 "다시 만들기"를 권한다
        boolean hasStat = g.games().stream().flatMap(List::stream).anyMatch(p -> p.origin() == DreamNumberOrigin.STAT);
        r.quickReplies = hasStat ? DreamMessages.REPLIES_CONFIRM : DreamMessages.REPLIES_CONFIRM_ONLY;
        return r;
    }

    // ------------------------------------------------------------------ AI 가 말하기

    /**
     * 상황에 맞는 말을 AI 가 쓰게 한다. 실패하거나 규칙을 어기면 fallback(정해진 문구)을 쓴다.
     * 두 줄짜리 상황은 fallback 도 두 개(첫마디, 질문)이고, 질문이 따로 없으면 두 번째를 null 로 둔다.
     */
    private List<String> say(Turn turn, Scene scene, int count, String symbolLine, String... fallback) {
        List<String> narrated = narrator.narrate(turn.session(), scene, count, symbolLine);
        return narrated != null ? narrated : Arrays.asList(fallback);
    }

    // ------------------------------------------------------------------ 응답 만들기

    /** 응답에 담을 값을 모으는 상자. (필드가 많아서 생성자 인자 대신 쓴다) */
    private static final class Reply {
        final DreamMatchResult match;
        final String step;
        String policyCode;
        final String message;
        String question;
        List<DreamResponse.SymbolView> symbols = List.of();
        String interpretation;
        List<PickedNumber> numbers = List.of();
        List<List<PickedNumber>> games = List.of();
        List<Integer> keep;
        Integer gameCountOverride;
        GenerateResponse generated;
        String tokenOverride;
        String action;
        List<String> quickReplies = List.of();
        List<String> suggestions = List.of();

        Reply(DreamMatchResult match, String step, String message) {
            this.match = match;
            this.step = step;
            this.message = message;
        }

        /** AI 를 부르지 않는 고정 응답 */
        static Reply fixed(DreamMatchResult match, String step, String message) {
            return new Reply(match, step, message);
        }

        /** 사용자의 말을 기다리는 질문 응답 (say 의 첫 줄이 message, 둘째 줄이 question) */
        static Reply asking(DreamMatchResult match, String step, List<String> say, List<String> quickReplies) {
            Reply r = new Reply(match, step, say.get(0));
            r.question = say.size() > 1 ? say.get(1) : null;
            r.quickReplies = quickReplies;
            return r;
        }
    }

    private DreamResponse respond(Reply r, Turn turn) {
        GenerateResponse g = r.generated;
        return new DreamResponse(r.match.name(), r.step, r.policyCode, r.message, r.question, r.symbols, r.interpretation,
                numberViews(r.numbers), r.games.stream().map(DreamService::numberViews).toList(),
                r.keep, r.gameCountOverride,
                g != null ? g.confirmToken() : r.tokenOverride, g == null ? null : g.ttlMinutes(),
                g == null ? null : g.targetRound(), r.action, r.quickReplies, r.suggestions, turn.charged(),
                BASIS_NOTICE, NOTICE, remaining(turn));
    }

    private Integer remaining(Turn turn) {
        AiUsageSnapshot snapshot = gateway.usage(turn.guest.value(), turn.clientIp, false);
        return snapshot.unlimited() ? null : snapshot.remainingToday();
    }

    // ------------------------------------------------------------------ 보조

    /** 꿈에서 찾은 상징과, 꿈에서 나온 모든 번호 (꿈 속 숫자 + 상징 번호) */
    private record Material(List<MatchedSymbol> symbols, List<List<SymbolNumber>> bySymbol, List<PickedNumber> base) {
        static final Material EMPTY = new Material(List.of(), List.of(), List.of());
    }

    private Material material(String dream) {
        List<SymbolEntry> entries = repository.loadActiveSymbols();
        List<MatchedSymbol> symbols = matcher.match(dream, entries);
        List<Integer> dreamNumbers = numberExtractor.extract(dream);
        List<Long> ids = symbols.stream().map(MatchedSymbol::id).toList();
        List<List<SymbolNumber>> bySymbol = ids.isEmpty()
                ? List.of() : numbersInSymbolOrder(symbols, repository.loadNumbers(ids));
        return new Material(symbols, bySymbol, numberPicker.pick(dreamNumbers, bySymbol));
    }

    /** 지금 대화에서 쓰는 번호. keep 이 있으면 그 번호만(뺀 번호는 다시 살아나지 않는다), 없으면 꿈에서 나온 전부 */
    private List<PickedNumber> workingBase(Material m, List<Integer> keep) {
        if (keep == null || keep.isEmpty()) {
            return m.base();
        }
        if (!numbersOf(m.base()).containsAll(keep)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "꿈 내용을 확인할 수 없어요. 꿈 이야기를 다시 들려주세요.",
                    "keep 번호가 꿈에서 나온 번호가 아님");
        }
        return m.base().stream().filter(p -> keep.contains(p.number())).toList();
    }

    private static void requireBase(List<PickedNumber> base) {
        if (base.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "꿈 내용을 확인할 수 없어요. 꿈 이야기를 다시 들려주세요.",
                    "이어지는 요청인데 꿈에서 나온 번호가 없음");
        }
    }

    private static int gameCountOf(DreamRequest req) {
        Integer n = req.gameCount();
        if (n == null) {
            return 1;
        }
        if (n < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "게임 수를 확인할 수 없어요.", "gameCount=" + n);
        }
        return n;
    }

    private static DreamMatchResult matchOf(List<PickedNumber> base) {
        if (base.isEmpty()) {
            return DreamMatchResult.NO_MATCH;
        }
        return base.size() >= DreamNumberPicker.TARGET ? DreamMatchResult.FULL_MATCH : DreamMatchResult.PARTIAL_MATCH;
    }

    private static Set<Integer> numbersOf(List<PickedNumber> picked) {
        Set<Integer> set = new LinkedHashSet<>();
        picked.forEach(p -> set.add(p.number()));
        return set;
    }

    private static Map<Integer, DreamNumberOrigin> originOf(List<PickedNumber> picked) {
        Map<Integer, DreamNumberOrigin> origin = new HashMap<>();
        picked.forEach(p -> origin.put(p.number(), p.origin()));
        return origin;
    }

    /** 결과 번호 중 통계가 아닌(꿈에서 나온) 번호 모음. 사용자가 말로 답할 때 화면이 그대로 돌려보낸다. */
    private static List<Integer> keepOf(List<List<PickedNumber>> games) {
        Set<Integer> keep = new TreeSet<>();
        for (List<PickedNumber> game : games) {
            for (PickedNumber p : game) {
                if (p.origin() != DreamNumberOrigin.STAT) {
                    keep.add(p.number());
                }
            }
        }
        return new ArrayList<>(keep);
    }

    private static BusinessException invalidLength(String text) {
        // 꿈 내용 자체는 오류 상세에도 남기지 않고 길이만 남긴다.
        return new BusinessException(ErrorCode.INVALID_INPUT,
                "꿈 내용은 " + MIN_LENGTH + "자 이상 " + MAX_LENGTH + "자 이하로 입력해 주세요.",
                "꿈 내용 길이 오류: " + text.length());
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
            list.sort(Comparator.comparingInt(n -> n.origin() == DreamNumberOrigin.SERVICE ? 1 : 0));
            result.add(list);
        }
        return result;
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

    private static List<DreamResponse.SymbolView> symbolViews(List<MatchedSymbol> symbols,
                                                              Map<Long, List<Interpretation>> chosen) {
        List<DreamResponse.SymbolView> views = new ArrayList<>();
        for (MatchedSymbol s : symbols) {
            List<DreamResponse.InterpretationView> items = new ArrayList<>();
            for (Interpretation i : chosen.getOrDefault(s.id(), List.of())) {
                items.add(new DreamResponse.InterpretationView(i.situation(), i.summary(), i.polarity(),
                        i.originRegion()));
            }
            views.add(new DreamResponse.SymbolView(s.keyword(), items));
        }
        return views;
    }

    private static List<DreamResponse.NumberView> numberViews(List<PickedNumber> picked) {
        return picked.stream().map(p -> new DreamResponse.NumberView(p.number(), p.origin().name())).toList();
    }
}
