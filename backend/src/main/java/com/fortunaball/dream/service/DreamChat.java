package com.fortunaball.dream.service;

import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiRole;
import org.springframework.stereotype.Component;

/**
 * 꿈풀이의 자유로운 대화 (인사·감사가 아닌 짧은 말, 꿈과 조금 거리가 있는 말, "꿈이 기억 안 나요" 같은 말)
 *
 * 대화 우선: 정해진 문구로 막지 않고 AI 가 짧게 받아 준다. 대신 이 글도 AI 가 쓴 글이므로 무료 횟수 1회를 쓴다.
 * 정말 관계없는 이야기(또는 부적절한 이야기)는 AI 가 [OFF_TOPIC] 한 마디로 알려 주고,
 * 서버는 횟수를 돌려준 뒤 고정 문구(DreamMessages.offTopic)로 안내한다. (민감한 주제는 OpenAI 가 먼저 걸러 준다)
 *
 * 지켜야 할 선 (AI 글이 어기면 그 글을 쓰지 않고 위와 같이 고정 문구로 대신한다)
 *  - 번호는 AI 가 말하지 않는다. (번호는 서비스가 정해진 자료로만 만든다) → 숫자가 들어 있으면 쓰지 않는다.
 *  - 당첨 보장·확률 상승·단정 표현 금지 (DreamService.FORBIDDEN)
 *  - 너무 긴 글 금지
 */
@Component
public class DreamChat {

    /** AI 가 "도와드릴 수 없는 이야기"라고 판단했을 때 쓰는 표시 */
    static final String OFF_MARK = "[OFF_TOPIC]";
    static final int MAX_CHARS = 300;

    private static final String SYSTEM = """
            당신은 로또 서비스 '포르투나볼 AI'의 꿈풀이 도우미입니다. 사용자와 자연스럽게 대화하세요.
            규칙:
            1. 한국어 존댓말로 1~3문장만 짧게 답하세요.
            2. 사용자가 꿈 이야기를 하려는 흐름이면 어떤 꿈이었는지 편하게 들려 달라고 이어 가세요. 꿈이 기억나지 않는다고 하면 부담 없이 받아 주고, 통계로 번호를 만들어 볼 수 있다고 안내하세요.
            3. 조금 벗어난 이야기는 짧게 받아 주고, 꿈 이야기로 부드럽게 돌아오도록 이끄세요.
            4. 로또 번호나 숫자를 직접 말하지 마세요. 번호는 서비스가 정해진 자료로 따로 만듭니다.
            5. 당첨을 보장하거나 확률이 오른다고 말하지 마세요. 구매를 권하지도 마세요.
            6. 꿈풀이·로또와 전혀 관계없는 이야기이거나 정치·성적·불법 등 부적절한 이야기면 다른 글 없이 [OFF_TOPIC] 만 쓰세요.
            """;

    /** @param step 직전에 서비스가 물어본 질문 단계 (없으면 null) */
    public AiRequest request(String text, String step) {
        String question = step == null ? "(없음)" : DreamReplyReader.questionOf(step);
        String input = "서비스가 직전에 물은 질문: " + question + "\n사용자의 말: " + text;
        return new AiRequest(AiRole.ANSWER, SYSTEM, input, 800);   // 생각에 쓰는 토큰까지 감안해 넉넉히 둔다
    }

    /** 쓸 수 있는 글이면 다듬어 돌려주고, 아니면 null (호출한 쪽이 고정 문구로 대신한다) */
    public String clean(String aiText) {
        String text = aiText == null ? "" : aiText.trim();
        if (text.isEmpty() || text.contains(OFF_MARK) || text.length() > MAX_CHARS) {
            return null;
        }
        if (text.chars().anyMatch(Character::isDigit) || DreamService.FORBIDDEN.matcher(text).find()) {
            return null;
        }
        return text;
    }
}
