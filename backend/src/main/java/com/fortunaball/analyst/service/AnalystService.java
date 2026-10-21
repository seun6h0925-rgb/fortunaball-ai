package com.fortunaball.analyst.service;

import com.fortunaball.ai.AiGateway;
import com.fortunaball.ai.AiSession;
import com.fortunaball.ai.policy.AiPolicyEngine;
import com.fortunaball.ai.policy.PolicyContext;
import com.fortunaball.ai.policy.PolicyHit;
import com.fortunaball.ai.usage.AiFeature;
import com.fortunaball.ai.usage.AiUsageSnapshot;
import com.fortunaball.analyst.domain.AnalystEvidence;
import com.fortunaball.analyst.domain.AskContext;
import com.fortunaball.analyst.domain.ChatTurn;
import com.fortunaball.analyst.domain.EvidenceItem;
import com.fortunaball.analyst.domain.ParsedQuestion;
import com.fortunaball.analyst.domain.QuestionType;
import com.fortunaball.analyst.dto.AnalystRequest;
import com.fortunaball.analyst.dto.AnalystResponse;
import com.fortunaball.analyst.service.AnalystAgent.Outcome;
import com.fortunaball.analyst.service.AnalystSmallTalk.Kind;
import com.fortunaball.analyst.service.AnalystSmallTalk.Reply;
import com.fortunaball.analyst.service.AnalystSmallTalk.Scene;
import com.fortunaball.analyst.service.AnalystTools.ToolResult;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.generate.dto.GenerateResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * AI 로또 애널리스트 (API-07) 처리 순서
 *
 *  1. 입력 검사 (질문 1~300자, context 는 비움/MAP, 위치는 위도·경도가 함께 있어야 함, 이전 대화 정리)
 *  2. 규칙 정책 검사 (당첨 보장·구매 유도·절박한 요청·위치 꺼짐) → 걸리면 고정 응답. AI 호출·차감 없음
 *     ⚠ 주제 이탈 키워드(OFF_TOPIC_ANALYST, OFF_TOPIC_MAP)에 걸려도 여기서 막지 않는다. 인사·잡담인지 아닌지는 AI 가 판단한다.
 *  2-1. 딱 봐도 단순한 인사·감사·사용법 질문("안녕", "고마워", "사용법 알려줘")은 고정 문구로 응답. AI 호출·차감 없음
 *  3. 무료 횟수 1회 차감 → AnalystAgent: AI 가 질문과 대화를 보고 조회 도구를 골라 쓰며 답한다
 *     - 가벼운 인사·잡담이면 AI 가 짧게 받아 주고 횟수를 돌려준다
 *     - 도와드릴 수 없는 이야기라고 AI 가 판단하면 고정 문구(3개 번갈아)로 응답하고 횟수를 돌려준다
 *     - 답을 못 만들면 횟수를 돌려주고 다시 물어보라고 안내한다
 *     - AI 호출이 실패하면 AiSession 이 횟수를 돌려준다
 *  4. 답변 검사 (길이·단정 표현). 통과 못 하면 조회한 근거로 만든 문장을 쓴다
 *
 * ⚠ 질문·이전 대화·위치 값은 저장하지도, 로그에 남기지도 않는다. (이전 대화는 화면이 요청마다 보내 준다)
 *    관리자 면제는 관리자 로그인을 만든 뒤에 연결한다. (지금은 모두 일반 사용자로 처리)
 */
@Service
public class AnalystService {

    static final int MAX_QUESTION_LENGTH = 300;
    static final int MAX_HISTORY = 6;
    static final int MAX_HISTORY_CONTENT = 800;
    static final String CONTEXT_MAP = "MAP";
    static final String POLICY_OFF_TOPIC = "OFF_TOPIC_ANALYST";
    static final String POLICY_OFF_TOPIC_MAP = "OFF_TOPIC_MAP";
    static final String POLICY_SMALL_TALK_AI = "SMALL_TALK_AI";
    static final int MAX_SMALL_TALK_CHARS = 150;
    static final String NOTICE = "통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.";

    private final QuestionParser parser;
    private final AiPolicyEngine policyEngine;
    private final AnalystAgent agent;
    private final AnalystAnswerGuard answerGuard;
    private final AiGateway gateway;
    private final AnalystSmallTalk smallTalk = new AnalystSmallTalk();   // 계산만 하는 클래스라서 직접 만든다

    public AnalystService(QuestionParser parser, AiPolicyEngine policyEngine, AnalystAgent agent,
                          AnalystAnswerGuard answerGuard, AiGateway gateway) {
        this.parser = parser;
        this.policyEngine = policyEngine;
        this.agent = agent;
        this.answerGuard = answerGuard;
        this.gateway = gateway;
    }

    public AnalystResponse answer(AnalystRequest request, GuestToken guest, String clientIp) {
        // 1) 입력 검사
        String question = validateQuestion(request);
        boolean mapContext = validateContext(request.context());
        boolean hasLocation = validateLocation(request.lat(), request.lng());
        List<ChatTurn> history = cleanHistory(request.history());

        // 2) 규칙 정책 검사
        ParsedQuestion parsed = parser.parse(question, mapContext);
        boolean hasLottoInput = parsed.hasLottoInput() || !history.isEmpty();
        Scene scene = mapContext ? Scene.MAP : Scene.GENERAL;
        PolicyHit hit = policyEngine.check(PolicyContext.analyst(question, mapContext, hasLocation, hasLottoInput)).orElse(null);
        // 주제 이탈 키워드는 AI 가 판단하게 한다. ("안녕" 같은 인사도 이 키워드에는 걸리기 때문이다)
        if (hit != null && !isOffTopicKeywordHit(hit)) {
            return fixed(parsed.type().name(), hit.policyCode(), hit.message(), guest, clientIp, List.of(), null);
        }

        // 2-1) 딱 봐도 단순한 인사·감사·사용법 질문
        Kind kind = smallTalk.detect(question, parsed.hasLottoInput()).orElse(null);
        if (kind != null) {
            Reply reply = smallTalk.reply(kind, scene, history.isEmpty());
            return fixed("GENERAL", AnalystSmallTalk.codeOf(kind), reply.message(), guest, clientIp,
                    reply.suggestions(), reply.action());
        }

        // 3) 여기서 무료 횟수가 1회 차감된다. AI 호출이 실패하면 AiSession 이 자동으로 환불한다.
        AiSession session = gateway.open(AiFeature.ANALYST, guest.value(), clientIp);
        AnalystAgent.Result result = agent.run(session, history, question, guest, clientIp, askContext(mapContext, request));

        if (result.outcome() == Outcome.SMALL_TALK) {
            session.refund();   // 인사·잡담은 횟수를 쓰지 않는다
            String talk = result.text() == null ? "" : result.text().trim();
            if (!talk.isEmpty() && talk.length() <= MAX_SMALL_TALK_CHARS) {
                // AI 가 쓴 문장이라 정책 코드만 남기고 그대로 보여 준다. 너무 길면 고정 문구로 바꾼다.
                gateway.recordBlocked(AiFeature.ANALYST, POLICY_SMALL_TALK_AI);
                return new AnalystResponse("GENERAL", POLICY_SMALL_TALK_AI, talk, null, null, null, List.of(), NOTICE,
                        remaining(guest, clientIp), null, smallTalk.suggestions(scene), null, false);
            }
            return offTopic(scene, guest, clientIp);
        }
        if (result.outcome() == Outcome.OFF_TOPIC) {
            session.refund();   // AI 가 도와드릴 수 없다고 판단한 요청은 사용자 잘못이 아니라서 횟수를 돌려준다
            return offTopic(scene, guest, clientIp);
        }

        AnalystEvidence evidence = merge(result.okResults());
        String text;
        boolean charged = true;
        if (result.outcome() == Outcome.ANSWER) {
            text = answerGuard.sanitize(result.text(), evidence, result.generated() != null);
        } else {
            session.refund();   // 답을 만들지 못한 것도 사용자 잘못이 아니다
            charged = false;
            text = answerGuard.sanitize(null, evidence, result.generated() != null);
            if (AnalystAnswerGuard.NO_EVIDENCE_FALLBACK.equals(text)) {
                text += AnalystAnswerGuard.REFUND_NOTE;   // 횟수를 돌려준 경우에만 안내한다
            }
        }

        GenerateResponse generated = result.generated();
        return new AnalystResponse(typeOf(generated, evidence), null, null, text, generated,
                evidence == null ? null : new AnalystResponse.Evidence(evidence.basis(), evidence.items()),
                evidence == null ? List.of() : evidence.references(), NOTICE, remaining(guest, clientIp),
                historyText(text, generated), List.of(), null, charged);
    }

    // ------------------------------------------------------------------ 보조

    /** 화면(지도 여부)과 위치. 위치는 AI 에게 보내지 않고 내 주변 조회 도구가 서버 안에서만 쓴다. */
    private static AskContext askContext(boolean mapContext, AnalystRequest request) {
        boolean located = request.lat() != null && request.lng() != null;
        return new AskContext(mapContext, located ? new AskContext.Location(request.lat(), request.lng()) : null);
    }

    /** 고정 응답. AI 를 부르지 않았거나 횟수를 돌려준 경우라서 charged 는 항상 false 다. */
    private AnalystResponse fixed(String type, String policyCode, String message, GuestToken guest, String clientIp,
                                  List<String> suggestions, String action) {
        gateway.recordBlocked(AiFeature.ANALYST, policyCode);
        return new AnalystResponse(type, policyCode, message, null, null, null, List.of(), NOTICE,
                remaining(guest, clientIp), null, suggestions, action, false);
    }

    /** 도와드릴 수 없는 이야기에 대한 고정 문구 (화면마다 3개씩 번갈아) */
    private AnalystResponse offTopic(Scene scene, GuestToken guest, String clientIp) {
        Reply reply = smallTalk.offTopic(scene);
        return fixed(QuestionType.STAT_ANALYSIS.name(), POLICY_OFF_TOPIC, reply.message(), guest, clientIp,
                reply.suggestions(), null);
    }

    private static boolean isOffTopicKeywordHit(PolicyHit hit) {
        return POLICY_OFF_TOPIC.equals(hit.policyCode()) || POLICY_OFF_TOPIC_MAP.equals(hit.policyCode());
    }

    private Integer remaining(GuestToken guest, String clientIp) {
        AiUsageSnapshot snapshot = gateway.usage(guest.value(), clientIp, false);
        return snapshot.unlimited() ? null : snapshot.remainingToday();
    }

    /** 조회한 근거들을 하나로 합친다. (화면의 "분석 근거 보기"용) 조회한 것이 없으면 null */
    static AnalystEvidence merge(List<ToolResult> results) {
        if (results.isEmpty()) {
            return null;
        }
        List<String> bases = new ArrayList<>();
        List<EvidenceItem> items = new ArrayList<>();
        Set<String> references = new LinkedHashSet<>();
        QuestionType type = results.get(0).evidence().type();
        for (ToolResult r : results) {
            AnalystEvidence e = r.evidence();
            if (e.basis() != null && !e.basis().isBlank() && !bases.contains(e.basis())) {
                bases.add(e.basis());
            }
            items.addAll(e.items());
            references.addAll(e.references());
        }
        return new AnalystEvidence(type, String.join(" / ", bases), items, new ArrayList<>(references), true);
    }

    private static String typeOf(GenerateResponse generated, AnalystEvidence evidence) {
        if (generated != null) {
            return QuestionType.NUMBER_GENERATION.name();
        }
        return evidence == null ? "GENERAL" : evidence.type().name();
    }

    /** 다음 질문 때 화면이 history 로 되돌려 보낼 글. 만든 번호도 함께 담아야 "그 번호"를 알아들을 수 있다. */
    static String historyText(String answer, GenerateResponse generated) {
        if (generated == null) {
            return answer;
        }
        StringBuilder sb = new StringBuilder(answer).append("\n[만든 번호]");
        for (int i = 0; i < generated.games().size(); i++) {
            sb.append(" 게임").append(i + 1).append(": ")
                    .append(String.join(", ", generated.games().get(i).stream().map(String::valueOf).toList())).append(';');
        }
        return sb.toString();
    }

    private static String validateQuestion(AnalystRequest request) {
        String question = request == null || request.question() == null ? "" : request.question().trim();
        if (question.isEmpty() || question.length() > MAX_QUESTION_LENGTH) {
            // 질문 내용은 오류 상세에도 남기지 않고 길이만 남긴다.
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "질문은 1자 이상 " + MAX_QUESTION_LENGTH + "자 이하로 입력해 주세요.",
                    "질문 길이 오류: " + question.length());
        }
        return question;
    }

    private static boolean validateContext(String context) {
        if (context == null || context.isBlank()) {
            return false;
        }
        if (CONTEXT_MAP.equalsIgnoreCase(context.trim())) {
            return true;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 형식이 올바르지 않아요.", "context 값 오류");
    }

    /** 위도·경도는 둘 다 있거나 둘 다 없어야 하고 범위 안이어야 한다. 값 자체는 어디에도 남기지 않는다. */
    private static boolean validateLocation(Double lat, Double lng) {
        if (lat == null && lng == null) {
            return false;
        }
        boolean valid = lat != null && lng != null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "위치 정보가 올바르지 않아요.", "위도·경도 형식 오류");
        }
        return true;
    }

    /** 이전 대화를 정리한다: 최근 MAX_HISTORY 개만, 너무 긴 글은 자르고, 빈 글은 버린다. 역할이 이상하면 입력 오류. */
    static List<ChatTurn> cleanHistory(List<ChatTurn> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ChatTurn> cleaned = new ArrayList<>();
        for (ChatTurn turn : raw) {
            if (turn == null || turn.content() == null || turn.content().isBlank()) {
                continue;
            }
            if (!ChatTurn.USER.equals(turn.role()) && !ChatTurn.ASSISTANT.equals(turn.role())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 형식이 올바르지 않아요.", "history role 값 오류");
            }
            String content = turn.content().trim();
            if (content.length() > MAX_HISTORY_CONTENT) {
                content = content.substring(0, MAX_HISTORY_CONTENT);
            }
            cleaned.add(new ChatTurn(turn.role(), content));
        }
        int from = Math.max(0, cleaned.size() - MAX_HISTORY);
        return List.copyOf(cleaned.subList(from, cleaned.size()));
    }
}
