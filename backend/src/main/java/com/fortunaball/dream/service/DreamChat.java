package com.fortunaball.dream.service;

import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiRole;
import com.fortunaball.analyst.domain.ChatTurn;
import java.util.List;
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
    /** 이야기 중인 꿈이 있는데 사용자가 "새 꿈 이야기"를 시작했다고 AI 가 판단했을 때 쓰는 표시 */
    static final String NEW_MARK = "[NEW_DREAM]";
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
            7. [이야기 중인 꿈]이 주어지면 그 꿈과 [이전 대화]를 이어서 답하세요. 사용자가 "그 꿈", "위에 꿈", "방금 번호"라고 하면 이 꿈을 가리킵니다.
               꿈에 대한 질문은 [서비스가 찾은 자료] 안에서만 답하고, 자료에 없는 내용을 지어내지 마세요. 처음 들은 척하거나 꿈을 다시 물어보지 마세요.
               번호가 맞는지 물으면 숫자 없이 "꿈 상징에서 나온 번호가 있다/없어서 통계로 만들었다" 정도로 설명하세요.
            8. [이야기 중인 꿈]이 있는데 사용자가 완전히 새로운 꿈 이야기를 들려주는 것이면 다른 글 없이 [NEW_DREAM] 만 쓰세요.
            """;

    /**
     * @param step    직전에 서비스가 물어본 질문 단계 (없으면 null)
     * @param context 이야기 중인 꿈과 서비스가 찾은 자료를 글로 만든 것 (없으면 빈 글). DreamService 가 만든다.
     * @param history 꿈에 대해 주고받은 최근 대화 (없으면 빈 목록)
     */
    public AiRequest request(String text, String step, String context, List<ChatTurn> history) {
        String question = step == null ? "(없음)" : DreamReplyReader.questionOf(step);
        StringBuilder input = new StringBuilder();
        if (context != null && !context.isBlank()) {
            input.append(context).append('\n');
        }
        if (history != null && !history.isEmpty()) {
            input.append("[이전 대화]\n");
            for (ChatTurn turn : history) {
                input.append(turn.isUser() ? "사용자: " : "도우미: ").append(turn.content()).append('\n');
            }
            input.append('\n');
        }
        input.append("서비스가 직전에 물은 질문: ").append(question).append("\n사용자의 말: ").append(text);
        return new AiRequest(AiRole.ANSWER, SYSTEM, input.toString(), 800);   // 생각에 쓰는 토큰까지 감안해 넉넉히 둔다
    }

    /** @param step 직전에 서비스가 물어본 질문 단계 (없으면 null) */
    public AiRequest request(String text, String step) {
        return request(text, step, "", List.of());
    }

    /** AI 가 "새 꿈 이야기"라고 표시했는지 */
    public boolean isNewDream(String aiText) {
        return aiText != null && aiText.contains(NEW_MARK);
    }

    /** 꿈 표현은 있지만 상징·번호가 없을 때: 이 답 바로 아래에 "통계 번호를 만들어 볼까요?" 버튼이 붙는다. */
    public AiRequest requestThenAsk(String text) {
        String input = "이 답 아래에 화면이 '통계로 번호를 만들어 볼까요?' 선택 버튼을 함께 보여 줍니다. "
                + "꿈이 기억나지 않거나 특별한 상징이 없어도 통계로 번호를 만들 수 있다고 자연스럽게 안내하세요.\n사용자의 말: " + text;
        return new AiRequest(AiRole.ANSWER, SYSTEM, input, 800);
    }

    /** 쓸 수 있는 글이면 다듬어 돌려주고, 아니면 null (호출한 쪽이 고정 문구로 대신한다) */
    public String clean(String aiText) {
        String text = aiText == null ? "" : aiText.trim();
        if (text.isEmpty() || text.contains(OFF_MARK) || text.contains(NEW_MARK) || text.length() > MAX_CHARS) {
            return null;
        }
        if (text.chars().anyMatch(Character::isDigit) || DreamService.FORBIDDEN.matcher(text).find()) {
            return null;
        }
        return text;
    }
}
