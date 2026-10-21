package com.fortunaball.dream.service;

import com.fortunaball.dream.domain.DreamKnowledge.MatchedSymbol;
import com.fortunaball.dream.domain.DreamKnowledge.PickedNumber;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolNumber;
import com.fortunaball.dream.domain.DreamNumberOrigin;
import com.fortunaball.analyst.service.AnalystSmallTalk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 꿈풀이의 고정 문구 모음. (AI 호출·횟수 차감 없이 돌려주는 말과, 결과별 첫마디)
 * 문구는 사용자와 확정한 내용이다(docs: AI_문구 수정안 v2). 바꿀 때는 먼저 확인받는다.
 * 같은 상황의 문구는 여러 개를 돌아가며 쓴다. (같은 말만 반복하는 느낌을 줄이기 위해서다)
 *
 * 인사·감사·사용법 단어를 알아보는 규칙은 애널리스트와 똑같아서 AnalystSmallTalk.detect 를 그대로 쓴다.
 * (문구만 꿈풀이용으로 따로 둔다)
 */
@Component
public class DreamMessages {

    /** 결과 문구 안에서 실제 상징과 번호로 바뀌는 자리 */
    static final String SYMBOL_SLOT = "{상징}";

    public static final String CODE_GREETING = AnalystSmallTalk.CODE_GREETING;
    public static final String CODE_THANKS = AnalystSmallTalk.CODE_THANKS;
    public static final String CODE_USAGE = AnalystSmallTalk.CODE_USAGE;
    public static final String CODE_TOO_SHORT = "DREAM_TOO_SHORT";
    public static final String CODE_LOW_RELEVANCE = "DREAM_LOW_RELEVANCE";
    public static final String CODE_OFF_TOPIC = "DREAM_OFF_TOPIC";

    private static final String[] GREETING = {
            "안녕하세요! 저는 꿈 속 상징을 풀어 드리는 AI 꿈풀이예요. 어젯밤 꿈이 기억나시나요? 편하게 들려주세요.",
            "반갑습니다! 기억나는 꿈 장면을 알려 주시면 어떤 상징인지 풀어 드릴게요.",
            "안녕하세요, 만나서 반가워요! 오늘은 어떤 꿈을 꾸셨는지 들려주세요."};
    private static final String[] THANKS = {
            "재미있게 보셨다니 기뻐요! 다른 꿈도 궁금하시면 들려주세요.",
            "언제든지요! 또 기억나는 꿈이 있으시면 말씀해 주세요.",
            "감사합니다! 다른 장면이 떠오르시면 이어서 알려 주세요."};
    private static final String[] OFF_TOPIC = {
            "아쉽지만 그건 제가 도와드리기 어려워요. 꿈 이야기라면 언제든 들려주세요!",
            "그 부분은 제 전문 분야가 아니에요. 꿈 속 상징 풀이라면 자신 있어요. 어떤 꿈이었는지 들려주실래요?",
            "죄송해요, 그 질문은 답하기 어려워요. 꿈 이야기를 들려주시면 정성껏 풀어 드릴게요!"};
    private static final String USAGE =
            "꿈 내용을 편하게 들려주세요. 예를 들면 '돼지가 집에 들어오는 꿈을 꿨어요'처럼요! "
                    + "꿈 속 상징을 풀이해 드리고, 그 상징에서 나온 번호를 정리해 드려요. 번호가 모자라면 통계로 채워도 되는지 여쭤볼게요. "
                    + "마음에 드시면 '확정할게요', 다시 만들고 싶으시면 '다시 만들어 주세요'라고 말씀해 주세요. "
                    + "꿈풀이는 재미와 참고를 위한 풀이라서 당첨을 알려 드리지는 않아요. 자세한 사용 방법은 아래에서 볼 수 있어요.";
    private static final String[] TOO_SHORT = {
            "꿈 내용이 조금 짧아요. 어떤 장면이 나왔는지 한두 가지만 더 알려 주시겠어요?",
            "조금만 더 자세히 들려주시면 풀이해 드릴 수 있어요. 기억나는 장면을 더 적어 주세요!"};

    /** ① 꿈 상징 번호가 6개 모두 채워졌을 때 */
    private static final String[] OPEN_FULL = {
            "와, 정말 길한 꿈을 꾸셨어요! 상징이 가득한 꿈이네요. 풀이를 들려드릴게요.",
            "상징이 풍성한 꿈이에요! 좋은 기운이 느껴져요. 하나씩 풀어 드릴게요.",
            "상징이 아주 많은 꿈이에요! 어떤 의미인지 살펴볼게요."};
    /** ② 4~5개일 때: 모자란 1~2개는 통계로 바로 채웠다 */
    private static final String[] OPEN_FILLED = {
            "상징이 꽤 많은 꿈이에요! 찾은 상징은 " + SYMBOL_SLOT + "이에요. 모자란 번호는 통계를 분석해서 채워 드렸어요.",
            "좋은 상징이 여러 개 보여요. " + SYMBOL_SLOT + ". 나머지 번호는 통계로 분석해서 더했어요.",
            "풍성한 꿈이에요! " + SYMBOL_SLOT + ". 부족한 번호는 통계를 바탕으로 채웠어요."};
    static final String CONFIRM_ASK_FILLED =
            "꿈 상징 번호는 그대로 두고 통계로 채운 번호만 새로 만들 수도 있어요. 이 번호로 확정하시겠어요, 아니면 다시 만들어 드릴까요? "
                    + "다시 만들면 무료 횟수가 1회 사용돼요.";
    static final String CONFIRM_ASK_FULL = "이 번호로 확정하시겠어요? '확정할게요'라고 말씀해 주시면 바로 확정해 드릴게요.";
    /** ③ 3개 이하일 때: 먼저 동의를 구한다 */
    private static final String[] ASK_FILL = {
            "꿈에서 찾은 상징은 " + SYMBOL_SLOT + "이에요. 나머지 번호는 통계로 분석해서 채워도 괜찮을까요?",
            "상징이 많진 않지만 " + SYMBOL_SLOT + "을 찾았어요. 모자란 번호는 통계를 바탕으로 채워 드려도 될까요?",
            "아쉽게도 상징은 " + SYMBOL_SLOT + "뿐이에요. 나머지는 통계로 채워서 완성해 드릴까요?"};
    /** 동의해서 번호가 채워진 뒤 */
    static final String CONFIRM_ASK_AFTER_FILL =
            "이 번호로 확정하시겠어요, 아니면 통계 번호만 다시 만들어 드릴까요? 다시 만들면 무료 횟수가 1회 사용돼요.";
    /** ④ 상징이 하나도 없을 때 */
    private static final String[] ASK_STAT = {
            "꿈 이야기 잘 들었어요. 특별한 상징은 찾지 못했지만, 대신 통계를 바탕으로 번호를 만들어 드릴까요?",
            "이번 꿈에서는 뚜렷한 상징이 보이지 않아요. 그래도 괜찮아요! 통계 번호로 대신 만들어 드릴까요?",
            "꿈 이야기 감사해요! 상징은 찾지 못했지만, 통계로 분석한 번호는 만들어 드릴 수 있어요. 해 보시겠어요?"};
    /** 상징 번호가 6개를 넘을 때: {n} = 번호 개수, {상징} = 어느 상징에서 어떤 번호가 나왔는지 */
    private static final String[] OVERFLOW = {
            "와, 상징 번호가 {n}개나 나왔어요! {상징}. 한 게임에는 번호 6개를 담을 수 있어서요, 몇 게임으로 나눠 담을까요? "
                    + "'3게임'이나 '3천원어치'처럼 편하게 말씀해 주세요. 통계로 6개만 골라 드릴 수도 있고, 빼고 싶은 번호를 알려 주셔도 돼요.",
            "상징 번호가 {n}개 나왔어요. {상징}. 6개씩 담는 거라 한 게임에 다 넣기는 어려워요. 몇 게임으로 나눠 드릴까요? "
                    + "게임 수나 금액을 말씀해 주세요. 통계로 6개만 고르거나 빼고 싶은 번호를 말씀해 주셔도 좋아요."};
    static final String OVERFLOW_UNCLEAR =
            "음, 제가 잘 이해하지 못했어요. 몇 게임으로 나눠 담을지 말씀해 주시거나, 통계로 6개만 고를지, 빼고 싶은 번호가 있는지 알려 주시겠어요?";
    static final String REMOVED_ALL = "그 번호를 빼면 남는 번호가 없어요. 어떤 번호를 뺄지 다시 알려 주시겠어요?";
    static final String MULTI_GAMES = "{n}게임으로 나눠 담았어요! 꿈 상징 번호가 게임마다 골고루 들어가고, 모자란 자리는 통계로 채웠어요.";
    static final String PICKED_BY_STATS = "통계에서 많이 나온 순으로 상징 번호 6개를 골랐어요.";
    static final String REMOVED_FULL = "알려 주신 번호는 빼고 정리했어요.";
    static final String REMOVED_FILLED = "알려 주신 번호는 빼고 정리했어요. 모자란 번호는 통계를 분석해서 채워 드렸어요.";
    static final String STAT_ONLY = "통계를 바탕으로 번호를 만들었어요.";
    static final String REGENERATED = "꿈 상징 번호는 그대로 두고 통계로 채운 번호만 새로 만들었어요.";
    static final String NOTHING_TO_REGENERATE =
            "이 번호는 모두 꿈 상징에서 나온 번호라서 새로 만들 통계 번호가 없어요. 이 번호로 확정하시겠어요?";
    static final String DECLINED = "알겠어요! 다른 꿈 이야기도 편하게 들려주세요.";
    static final String CONFIRMED = "좋아요, 이 번호로 확정해 드릴게요!";

    /** 사용자가 글을 쓰지 않고 누를 수 있는 답 칩. 누르면 이 문구가 그대로 사용자의 말로 전송된다. */
    public static final List<String> REPLIES_CONFIRM = List.of("확정할게요", "다시 만들어 주세요");
    public static final List<String> REPLIES_CONFIRM_ONLY = List.of("확정할게요");
    public static final List<String> REPLIES_FILL = List.of("네, 채워 주세요", "다른 꿈 이야기할래요");
    public static final List<String> REPLIES_STAT = List.of("네, 통계 번호 만들어 주세요", "다른 꿈 이야기할래요");
    public static final List<String> REPLIES_OVERFLOW = List.of("3게임으로 나눠 주세요", "통계로 6개만 골라 주세요");

    public static final List<String> SUGGESTIONS = List.of(
            "돼지가 나오는 꿈을 꿨어요", "물에 빠지는 꿈은 무슨 뜻이에요?", "조상님이 나오는 꿈을 꿨어요");

    /** 인사·감사·사용법 문구와 버튼이 들어 있는 고정 답 */
    public record Talk(String code, String message, boolean openGuide) {
    }

    /** 문구를 돌려 쓰는 순번. 서버가 켜져 있는 동안만 유지하면 충분하다. (저장하지 않는다) */
    private final AtomicInteger turn = new AtomicInteger();
    private final AnalystSmallTalk detector = new AnalystSmallTalk();

    // ------------------------------------------------------------------ 말 알아보기 / 고정 답

    /** 인사·감사·사용법이면 그 종류를 돌려준다. (딱 봐도 단순한 말만. 그 밖의 말은 비어 있다) */
    public Optional<AnalystSmallTalk.Kind> detectSmallTalk(String text) {
        return detector.detect(text, false);
    }

    public Talk talk(AnalystSmallTalk.Kind kind) {
        return switch (kind) {
            case GREETING -> new Talk(CODE_GREETING, pick(GREETING), false);
            case THANKS -> new Talk(CODE_THANKS, pick(THANKS), false);
            case USAGE -> new Talk(CODE_USAGE, USAGE, true);
        };
    }

    public String offTopic() {
        return pick(OFF_TOPIC);
    }

    public String tooShort() {
        return pick(TOO_SHORT);
    }

    // ------------------------------------------------------------------ 결과별 첫마디

    public String openFull() {
        return pick(OPEN_FULL);
    }

    /** @param symbolLine symbolLine() 이 만든 "[돼지 → 4·32, 용 → 8]" 모양의 글 */
    public String openFilled(String symbolLine) {
        return pick(OPEN_FILLED).replace(SYMBOL_SLOT, symbolLine);
    }

    /** 풀이 뒤, 버튼 바로 위에 보여 주는 확정 질문 (통계로 채운 번호가 있는 경우) */
    public String confirmAskFilled() {
        return CONFIRM_ASK_FILLED;
    }

    /** 꿈 상징 번호만으로 6개가 채워진 경우의 확정 질문 */
    public String confirmAskFull() {
        return CONFIRM_ASK_FULL;
    }

    public String askFill(String symbolLine) {
        return pick(ASK_FILL).replace(SYMBOL_SLOT, symbolLine);
    }

    public String askAfterFill() {
        return CONFIRM_ASK_AFTER_FILL;
    }

    public String openRemoved(boolean filled) {
        return filled ? REMOVED_FILLED : REMOVED_FULL;
    }

    public String overflow(int count, String symbolLine) {
        return pick(OVERFLOW).replace("{n}", String.valueOf(count)).replace(SYMBOL_SLOT, symbolLine);
    }

    public String overflowUnclear() {
        return OVERFLOW_UNCLEAR;
    }

    public String removedAll() {
        return REMOVED_ALL;
    }

    public String multiGames(int games) {
        return MULTI_GAMES.replace("{n}", String.valueOf(games));
    }

    public String pickedByStats() {
        return PICKED_BY_STATS;
    }

    public String statOnly() {
        return STAT_ONLY;
    }

    public String regenerated() {
        return REGENERATED;
    }

    public String nothingToRegenerate() {
        return NOTHING_TO_REGENERATE;
    }

    public String declined() {
        return DECLINED;
    }

    public String confirmed() {
        return CONFIRMED;
    }

    public String askStatNumbers() {
        return pick(ASK_STAT);
    }

    // ------------------------------------------------------------------ 상징 → 번호 한 줄

    /**
     * 어느 상징에서 어떤 번호가 나왔는지 한 줄로 만든다. 예: "[돼지 → 4·32, 용 → 8, 꿈 속 숫자 → 17]"
     *
     * @param symbols  꿈에서 찾은 상징 (등장 순서)
     * @param bySymbol 상징 순서대로 정렬된 상징별 번호 목록
     * @param base     꿈에서 나온 번호 (DreamNumberPicker 가 고른 값. 통계 번호는 아직 없다)
     */
    public String symbolLine(List<MatchedSymbol> symbols, List<List<SymbolNumber>> bySymbol, List<PickedNumber> base) {
        Map<Integer, DreamNumberOrigin> pickedOrigin = new HashMap<>();
        for (PickedNumber p : base) {
            pickedOrigin.put(p.number(), p.origin());
        }
        Set<Integer> claimed = new HashSet<>();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < symbols.size() && i < bySymbol.size(); i++) {
            List<Integer> mine = new ArrayList<>();
            for (SymbolNumber sn : bySymbol.get(i)) {
                // 꿈 속에 직접 적힌 숫자와 겹치는 번호는 "꿈 속 숫자"로 보여 주므로 여기서는 건너뛴다
                if (pickedOrigin.get(sn.number()) == sn.origin() && claimed.add(sn.number())) {
                    mine.add(sn.number());
                }
            }
            if (!mine.isEmpty()) {
                parts.add(symbols.get(i).keyword() + " → " + join(mine));
            }
        }
        List<Integer> fromText = base.stream().filter(p -> p.origin() == DreamNumberOrigin.DREAM_TEXT)
                .map(PickedNumber::number).toList();
        if (!fromText.isEmpty()) {
            parts.add("꿈 속 숫자 → " + join(fromText));
        }
        return "[" + String.join(", ", parts) + "]";
    }

    private static String join(List<Integer> numbers) {
        return numbers.stream().map(String::valueOf).collect(Collectors.joining("·"));
    }

    private String pick(String[] variants) {
        return variants[Math.floorMod(turn.getAndIncrement(), variants.length)];
    }
}
