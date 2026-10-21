package com.fortunaball.dream.service;

import com.fortunaball.ai.AiSession;
import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiRole;
import com.fortunaball.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 꿈풀이의 "대화" 부분을 AI 가 상황에 맞게 자연스럽게 이어 말하게 한다.
 * (풀이 앞의 첫마디, 풀이 뒤의 질문, 이어지는 대화의 답)
 *
 * 정해진 문구(DreamMessages)와의 관계
 *  - 평소에는 AI 가 상황과 사실(상징·번호는 {상징} 자리표시로만 전달)을 보고 그때그때 다르게 말한다.
 *  - AI 가 실패하거나, 단정 표현·길이·필수 내용 규칙을 어기면 정해진 문구(DreamMessages)로 대신한다.
 *    그래서 AI 가 어떻게 답하든 안내해야 할 내용(질문, 다시 만들면 1회 차감)이 빠지는 일은 없다.
 *
 * ⚠ 번호와 상징 이름은 AI 가 쓰지 않는다. 서버가 {상징} 자리에 실제 내용을 넣는다.
 * ⚠ 꿈 원문은 AI 에 보내지 않는다. 상황 설명과 개수만 보낸다.
 */
@Component
public class DreamNarrator {

    private static final Logger log = LoggerFactory.getLogger(DreamNarrator.class);

    /** 말하는 상황. 필수로 들어가야 할 내용이 상황마다 다르다. */
    public enum Scene {
        /** 처음 풀이: 번호 6개 완성 (첫마디 + 확정 질문) */
        FULL(2, true, false),
        /** 처음 풀이: 4~5개라 통계로 채움 (첫마디 + 확정/다시 질문, 1회 차감 안내) */
        FILLED(2, true, true),
        /** 처음 풀이: 3개 이하라 통계로 채워도 되는지 묻기 (첫마디 + 동의 질문) */
        ASK_FILL(2, true, false),
        /** 처음 풀이: 6개를 넘어 몇 게임으로 나눌지 묻기 (첫마디 + 질문) */
        OVERFLOW(2, true, false),
        /** 6개 넘을 때 답을 못 알아들어 다시 묻기 */
        OVERFLOW_UNCLEAR(1, false, false),
        /** 뺄 번호를 알려 줬는데 남는 번호가 없어 다시 묻기 */
        REMOVED_ALL(1, false, false),
        /** 여러 게임으로 나눠 담음 → 확정 질문 */
        MULTI(1, false, false),
        /** 통계로 6개를 골라 줌 → 확정 질문 */
        BY_STATS(1, false, false),
        /** 뺄 번호를 뺐더니 6개가 됨 → 확정 질문 */
        REMOVED_FULL(1, false, false),
        /** 뺄 번호를 뺐더니 모자라서 통계로 채움 → 확정/다시 질문, 1회 차감 안내 */
        REMOVED_FILLED(1, false, true),
        /** 동의에 따라 통계로 채움 → 확정/다시 질문, 1회 차감 안내 */
        FILLED_AFTER_CONSENT(1, false, true),
        /** 다시 만들었음 → 확정/다시 질문, 1회 차감 안내 */
        REGENERATED(1, false, true),
        /** 통계 번호만으로 만들었음 → 확정/다시 질문, 1회 차감 안내 */
        STAT_ONLY(1, false, true);

        final int parts;
        final boolean needsSymbol;
        final boolean needsChargeNotice;

        Scene(int parts, boolean needsSymbol, boolean needsChargeNotice) {
            this.parts = parts;
            this.needsSymbol = needsSymbol;
            this.needsChargeNotice = needsChargeNotice;
        }
    }

    static final String SYMBOL_SLOT = DreamMessages.SYMBOL_SLOT;
    private static final int MAX_CHARS = 320;
    private static final int MAX_OUTPUT_TOKENS = 900;   // 모델이 답 전에 생각하는 데 토큰을 써서, 작게 잡으면 "빈 응답(length)"으로 실패한다. 풀이(900)와 같게 둔다

    private static final String SYSTEM = """
            당신은 로또 꿈풀이 서비스의 친근한 상담원입니다. 사용자와 대화하듯 아래 '상황'에 맞는 말을 써 주세요.
            규칙:
            1. 해요체로 밝고 친절하게, 앞뒤 대화가 자연스럽게 이어지는 말투로 쓰세요. 안내문이나 목록처럼 딱딱하게 쓰지 마세요.
            2. 번호와 상징 이름은 직접 쓰지 마세요. 필요한 자리에는 {상징} 이라고만 쓰세요. (서버가 실제 내용으로 바꿉니다)
            3. "당첨됩니다", "반드시", "확실히", "보장"처럼 결과를 단정하거나 당첨 가능성을 높인다고 말하지 마세요.
            4. '버튼', '클릭', '누르세요'라는 말은 쓰지 마세요. 사용자는 말로 편하게 답합니다.
            5. '상황'에 적힌 꼭 들어가야 할 내용은 모두 자연스럽게 포함하세요.
            6. 출력 형식을 정확히 지키세요. 다른 설명이나 따옴표는 쓰지 마세요.
            """;

    /**
     * 상황에 맞는 말을 만든다. 규칙을 어기거나 실패하면 null (호출한 쪽이 정해진 문구를 쓴다).
     *
     * @param count 상황 설명에 쓸 개수 (상징 번호 개수 또는 게임 수). 필요 없으면 0
     * @return 상황이 요구하는 개수(1 또는 2)의 문장들. {상징} 은 symbolLine 으로 바뀐 상태
     */
    public List<String> narrate(AiSession session, Scene scene, int count, String symbolLine) {
        try {
            String raw = session.call(new AiRequest(AiRole.ANSWER, SYSTEM, situation(scene, count), MAX_OUTPUT_TOKENS)).text();
            return validated(scene, raw, symbolLine);
        } catch (BusinessException e) {
            log.warn("꿈풀이 대화 문장을 만들지 못해 정해진 문구를 씁니다: {}", e.getErrorCode());
            return null;
        }
    }

    List<String> validated(Scene scene, String raw, String symbolLine) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String line : raw.trim().split("\\R")) {
            if (!line.isBlank()) {
                parts.add(line.trim());
            }
        }
        if (parts.size() != scene.parts) {
            return null;
        }
        String all = String.join(" ", parts);
        if (all.length() > MAX_CHARS || DreamService.FORBIDDEN.matcher(all).find() || all.contains("버튼") || all.contains("클릭")) {
            return null;
        }
        if (scene.needsSymbol && !all.contains(SYMBOL_SLOT)) {
            return null;
        }
        // 다시 만들면 1회 사용된다는 안내가 빠졌으면 정해진 문구로 대신한다. (사용자가 모르고 차감되면 안 된다)
        if (scene.needsChargeNotice && !parts.get(parts.size() - 1).contains("1회")) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            result.add(part.replace(SYMBOL_SLOT, symbolLine == null ? "" : symbolLine));
        }
        return result;
    }

    private static String situation(Scene scene, int count) {
        String format = scene.parts == 2
                ? "출력 형식: 정확히 두 줄. 첫 줄은 풀이 앞에 붙일 첫마디(1~2문장), 둘째 줄은 풀이 뒤에 붙일 질문(1~2문장)."
                : "출력 형식: 한 단락(2~3문장, 150자 이내).";
        String body = switch (scene) {
            case FULL -> "사용자가 들려준 꿈에서 나온 상징 번호만으로 한 게임(6개)이 완성됐습니다. 첫마디는 길한 꿈이라고 밝게 반응하며 {상징}에서 번호를 정리했다고 말하세요. "
                    + "질문은 이 번호로 확정할지 묻고, 확정하려면 '확정할게요'라고 말씀해 주시면 된다고 자연스럽게 알려 주세요.";
            case FILLED -> "꿈에서 나온 상징 번호가 4~5개라서 모자란 번호는 통계를 분석해 채웠습니다. 첫마디는 찾은 상징 {상징}을 말하고 모자란 번호는 통계로 채웠다고 알려 주세요. "
                    + "질문은 이 번호로 확정할지, 꿈 상징 번호는 그대로 두고 통계로 채운 번호만 다시 만들지 묻고, 다시 만들면 무료 횟수가 1회 사용된다고 꼭 알려 주세요.";
            case ASK_FILL -> "꿈에서 나온 상징 번호가 " + count + "개뿐이라 한 게임(6개)을 채우려면 나머지를 통계로 채워야 합니다. 첫마디는 찾은 상징 {상징}을 말하세요. "
                    + "질문은 나머지 번호를 통계로 분석해 채워도 괜찮은지 묻고, 괜찮으면 '네'라고, 아니면 다른 꿈 이야기를 해도 된다고 자연스럽게 말하세요.";
            case OVERFLOW -> "꿈에서 나온 상징 번호가 " + count + "개나 돼서 한 게임에 담을 수 있는 6개를 넘습니다. 첫마디는 상징 번호가 많이 나왔다고 놀라워하며 {상징}을 말하세요. "
                    + "질문은 몇 게임으로 나눠 담을지 물어보세요. '3게임'이나 '3천원어치'처럼 편하게 말하면 된다고 예를 들고, 통계로 6개만 골라 드릴 수도 있고 빼고 싶은 번호를 알려 줘도 된다고 함께 알려 주세요.";
            case OVERFLOW_UNCLEAR -> "사용자의 답을 잘 이해하지 못했습니다. 부드럽게 못 알아들었다고 하고, 몇 게임으로 나눌지 / 통계로 6개만 고를지 / 빼고 싶은 번호가 있는지 중에서 말해 달라고 다시 물어보세요.";
            case REMOVED_ALL -> "사용자가 알려 준 번호를 모두 빼면 남는 번호가 없습니다. 부드럽게 알리고 어떤 번호를 뺄지 다시 알려 달라고 하세요.";
            case MULTI -> count + "게임으로 꿈 상징 번호를 나눠 담았습니다. 꿈 상징 번호가 게임마다 골고루 들어갔고 모자란 자리는 통계로 채웠다고 알리고, 이 번호로 확정할지 묻고 '확정할게요'라고 하면 된다고 알려 주세요.";
            case BY_STATS -> "통계에서 많이 나온 순으로 상징 번호 6개를 골랐습니다. 그렇게 골랐다고 알리고 이 번호로 확정할지 묻고 '확정할게요'라고 하면 된다고 알려 주세요.";
            case REMOVED_FULL -> "사용자가 빼 달라고 한 번호를 빼고 나니 꿈 상징 번호 6개로 한 게임이 완성됐습니다. 그렇게 정리했다고 알리고 이 번호로 확정할지 묻고 '확정할게요'라고 하면 된다고 알려 주세요.";
            case REMOVED_FILLED -> "사용자가 빼 달라고 한 번호를 빼고 나니 번호가 모자라서 통계로 채웠습니다. 그렇게 정리했다고 알리고, 이 번호로 확정할지 / 통계로 채운 번호만 다시 만들지 묻고, 다시 만들면 무료 횟수가 1회 사용된다고 꼭 알려 주세요.";
            case FILLED_AFTER_CONSENT -> "사용자가 동의해서 모자란 번호를 통계로 채웠습니다. 채웠다고 알리고, 이 번호로 확정할지 / 통계 번호만 다시 만들지 묻고, 다시 만들면 무료 횟수가 1회 사용된다고 꼭 알려 주세요.";
            case REGENERATED -> "통계로 채운 번호만 새로 만들었습니다(꿈 상징 번호는 그대로). 새로 만들었다고 알리고, 이 번호로 확정할지 / 또 다시 만들지 묻고, 다시 만들면 무료 횟수가 1회 사용된다고 꼭 알려 주세요.";
            case STAT_ONLY -> "통계를 바탕으로 번호 한 게임을 만들었습니다. 만들었다고 알리고, 이 번호로 확정할지 / 다시 만들지 묻고, 다시 만들면 무료 횟수가 1회 사용된다고 꼭 알려 주세요.";
        };
        return "상황: " + body + "\n" + format;
    }
}
