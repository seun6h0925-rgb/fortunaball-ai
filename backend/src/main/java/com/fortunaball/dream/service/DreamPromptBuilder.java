package com.fortunaball.dream.service;

import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiRole;
import com.fortunaball.dream.domain.DreamKnowledge.Interpretation;
import com.fortunaball.dream.domain.DreamKnowledge.MatchedSymbol;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 꿈풀이 AI 요청문을 만든다.
 *
 * ⚠ 사용자가 쓴 꿈 원문은 AI 에 보내지 않는다. 서버가 찾은 상징 이름과 DB 해석만 보낸다.
 *   (원문 미저장 원칙, 비용 절감, 꿈 속에 "이전 지시 무시" 같은 문장을 넣어 AI 를 속이는 시도 차단)
 * AI 는 이 자료를 쉬운 문장으로 풀어 쓰기만 한다. 번호는 만들지도 말하지도 않는다.
 */
@Component
public class DreamPromptBuilder {

    static final int MAX_OUTPUT_TOKENS = 900;
    static final int MAX_ANSWER_CHARS = 500;

    private static final String SYSTEM = """
            당신은 로또 꿈풀이 서비스의 설명 도우미입니다. 아래 '해석 자료'에 적힌 내용만 쉬운 한국어로 풀어서 설명하세요.
            규칙:
            1. 자료에 없는 의미, 숫자, 번호를 만들지 마세요. 번호는 언급하지 마세요.
            2. "당첨됩니다", "반드시", "확실히", "보장"처럼 결과를 단정하거나 당첨 가능성을 높인다고 말하지 마세요.
            3. 의료, 법률, 투자에 대한 조언이나 판단을 하지 마세요.
            4. 자료 앞에 [한국 전통]이라고 적힌 것을 먼저 설명하고, [해외 자료]는 "해외 자료에서는"이라고 구분해서 말하세요.
            5. 모든 해석은 "~일 수 있어요", "~로 읽어요"처럼 가능성으로 표현하세요.
            6. 해요체로 500자 이내로 쓰고, 마지막 문장은 재미로 참고하라는 취지로 끝내세요.
            """;

    public AiRequest build(List<MatchedSymbol> symbols, Map<Long, List<Interpretation>> bySymbol) {
        StringBuilder user = new StringBuilder("해석 자료:\n");
        for (MatchedSymbol s : symbols) {
            user.append("- 상징: ").append(s.keyword()).append('\n');
            for (Interpretation i : bySymbol.getOrDefault(s.id(), List.of())) {
                user.append("  ").append(i.isKorean() ? "[한국 전통] " : "[해외 자료] ")
                        .append(i.situation()).append(": ").append(i.summary()).append('\n');
            }
        }
        return new AiRequest(AiRole.ANSWER, SYSTEM, user.toString(), MAX_OUTPUT_TOKENS);
    }
}
