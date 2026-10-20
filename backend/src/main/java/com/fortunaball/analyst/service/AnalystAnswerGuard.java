package com.fortunaball.analyst.service;

import com.fortunaball.analyst.domain.AnalystEvidence;
import com.fortunaball.analyst.domain.EvidenceItem;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * AI 가 쓴 답변을 화면에 내보내기 전에 검사한다.
 * 비었거나, 500자를 넘거나, 단정·구매 유도 표현이 있으면 AI 문장을 버리고 서버가 가진 근거로 만든 문장을 쓴다.
 * (꿈풀이의 DreamService.sanitize 와 같은 방식)
 */
@Component
public class AnalystAnswerGuard {

    /** 결과를 단정하거나 당첨 가능성을 높인다고 말하는 표현, 구매를 권하는 표현 */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "반드시|확실히|틀림없|무조건|보장\\s*(합니다|해요|해 드|할 수 있|됩니다|돼요)|100\\s*%|당첨\\s*(됩니다|돼요|될 거|될거|확률이 (높|올))|1등\\s*(됩니다|돼요|될 거)"
                    + "|구매\\s*(하세요|해보세요|해 보세요|를 권)|사세요|사보세요|사 보세요");

    static final String TAIL = " 통계를 바탕으로 AI가 분석한 내용이지만 당첨을 보장할 수는 없어요.";
    static final String NO_EVIDENCE_FALLBACK = "답변을 만들지 못했어요. 질문을 조금 바꿔서 다시 물어봐 주세요.";

    /** AI 문장이 쓸 만하면 그대로, 아니면 근거로 만든 대체 문장. evidence 가 없으면(조회 없이 답한 경우) 다시 물어보라는 안내 */
    public String sanitize(String aiText, AnalystEvidence evidence) {
        return sanitize(aiText, evidence, false);
    }

    /** @param generated 번호를 만들어 드린 답변이면 true. 대체 문장 끝에 "보장할 수 없다"는 마무리 문장을 붙인다. (일반 대화에는 붙이지 않는다) */
    public String sanitize(String aiText, AnalystEvidence evidence, boolean generated) {
        String text = aiText == null ? "" : aiText.trim();
        if (!generated) {
            // 번호를 만들지 않은 답변에는 마무리 문장을 붙이지 않는다. AI 가 지침을 어기고 붙였다면 서버가 지운다.
            text = text.replace(TAIL.trim(), "").trim();
        }
        boolean bad = text.isEmpty() || text.length() > AnalystPromptBuilder.MAX_ANSWER_CHARS
                || FORBIDDEN.matcher(text).find();
        if (!bad) {
            return text;
        }
        return evidence == null || evidence.items().isEmpty() ? NO_EVIDENCE_FALLBACK : fallbackText(evidence, generated);
    }

    /** 기준과 근거 항목을 앞에서부터 넣다가, 꼬리 문장이 들어갈 자리만 남기고 멈춘다. (항목 중간에서 자르지 않는다) */
    String fallbackText(AnalystEvidence evidence, boolean withTail) {
        int limit = AnalystPromptBuilder.MAX_ANSWER_CHARS - (withTail ? TAIL.length() : 0);
        StringBuilder sb = new StringBuilder();
        if (evidence.basis() != null && !evidence.basis().isBlank()) {
            sb.append(evidence.basis()).append(" 기준이에요.");
        }
        for (EvidenceItem item : evidence.items()) {
            String piece = (sb.length() > 0 ? " " : "") + item.label() + " " + item.value() + ".";
            if (sb.length() + piece.length() > limit) {
                break;
            }
            sb.append(piece);
        }
        return withTail ? sb.append(TAIL).toString() : sb.toString();
    }
}
