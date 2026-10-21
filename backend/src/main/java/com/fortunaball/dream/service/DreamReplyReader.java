package com.fortunaball.dream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiRole;
import com.fortunaball.dream.dto.DreamResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 꿈풀이가 물어본 말에 사용자가 글로 답했을 때, 무슨 뜻인지 읽는다.
 * ("확정할게요", "네 채워 주세요", "3게임만 할게, 3천원어치", "7번이랑 9번 빼고 해주세요" ...)
 *
 * 읽는 순서
 *  1) quick(): 코드가 단어와 숫자로 바로 알아본다. (AI 호출 없음, 돈 0원)
 *  2) 못 알아봤고 짧은 말이면 AI 에게 읽게 한다. (aiRequest / fromAi)
 *  3) 그래도 모르면 UNCLEAR. 서버는 그 말을 새 꿈 이야기로 보거나(대부분), 다시 한 번 물어본다.
 *
 * 알아보는 규칙은 지금 어떤 질문 중이었는지(step)에 따라 다르다.
 *  CONFIRM          확정 / 다시 만들기
 *  ASK_FILL         채워 주세요(예) / 아니요
 *  ASK_STAT_NUMBERS 만들어 주세요(예) / 아니요
 *  ASK_OVERFLOW     게임 수(금액) / 통계로 줄이기 / 뺄 번호
 * 긴 말은 단순한 대답으로 보지 않는다. (꿈 이야기 안에 "네", "다시" 같은 글자가 들어 있을 수 있기 때문이다)
 */
@Component
public class DreamReplyReader {

    public enum Intent { CONFIRM, REGENERATE, YES, NO, GAMES, STATS, REMOVE, UNCLEAR }

    /** 읽은 결과. gameCount 는 GAMES 일 때만, remove 는 REMOVE 일 때만 의미가 있다. */
    public record Reading(Intent intent, Integer gameCount, Set<Integer> remove) {
        static Reading of(Intent intent) {
            return new Reading(intent, null, Set.of());
        }
    }

    /** 이 글자 수(공백·문장부호 뺀 길이) 이하의 짧은 말만 "네"·"확정" 같은 단순한 대답으로 본다 */
    private static final int SHORT_REPLY = 14;
    /** 한 게임 값 (1게임 = 1,000원). 금액으로 말하면 이 값으로 나눠 게임 수를 구한다. */
    private static final int WON_PER_GAME = 1000;

    private static final List<String> CONFIRM_WORDS = List.of("확정", "그대로", "이대로", "이걸로", "그걸로", "오케이", "ok", "좋아", "좋습니다", "완료");
    private static final List<String> REGENERATE_WORDS = List.of("다시", "새로", "바꿔", "재생성", "다른걸", "다른번호");
    private static final List<String> YES_WORDS = List.of("네", "예", "응", "그래", "좋아", "채워", "해줘", "해주세요", "오케이", "ok", "부탁", "괜찮", "만들어", "할게", "해볼게", "해볼래");
    private static final List<String> NO_WORDS = List.of("아니", "싫", "안할", "다른꿈", "그만", "됐어", "괜찮아요말고");

    private static final Pattern GAMES = Pattern.compile("(\\d+)\\s*(게임|장|판|세트)");
    private static final Pattern WON_CHEON = Pattern.compile("(\\d+)\\s*천\\s*원");
    private static final Pattern WON_MAN = Pattern.compile("(\\d+)\\s*만\\s*원");
    private static final Pattern WON_PLAIN = Pattern.compile("(\\d{4,})\\s*원");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,2}");

    private final ObjectMapper mapper;

    public DreamReplyReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 단순한 대답으로 볼 만큼 짧은 말인지 (AI 에게 읽힐지 정할 때 쓴다) */
    public boolean isShort(String reply) {
        return reply != null && squeeze(reply).length() <= SHORT_REPLY;
    }

    // ------------------------------------------------------------------ 1) 단어·숫자로 바로 읽기

    /**
     * @param candidates 꿈에서 나온 번호 (뺄 번호는 이 안에 있는 것만 인정한다)
     */
    public Reading quick(String step, String reply, Set<Integer> candidates) {
        String raw = reply == null ? "" : reply.trim();
        String text = squeeze(raw);
        if (text.isEmpty() || step == null) {
            return Reading.of(Intent.UNCLEAR);
        }
        return switch (step) {
            case DreamResponse.STEP_CONFIRM -> confirmStep(text);
            case DreamResponse.STEP_ASK_FILL, DreamResponse.STEP_ASK_STAT_NUMBERS -> yesNo(text);
            case DreamResponse.STEP_ASK_OVERFLOW -> overflowStep(raw, text, candidates);
            default -> Reading.of(Intent.UNCLEAR);
        };
    }

    private Reading confirmStep(String text) {
        if (text.length() > SHORT_REPLY) {
            return Reading.of(Intent.UNCLEAR);
        }
        // "다시 확정할게요" 같은 말은 다시 만들기로 본다. (다시 만들기를 먼저 본다)
        if (containsAny(text, REGENERATE_WORDS)) {
            return Reading.of(Intent.REGENERATE);
        }
        if (containsAny(text, CONFIRM_WORDS) || containsAny(text, YES_WORDS)) {
            return Reading.of(Intent.CONFIRM);
        }
        return Reading.of(Intent.UNCLEAR);
    }

    private Reading yesNo(String text) {
        if (text.length() > SHORT_REPLY) {
            return Reading.of(Intent.UNCLEAR);
        }
        // "아니요"를 먼저 본다. ("아니요 괜찮아요"의 '괜찮'이 예 로 읽히지 않게)
        if (containsAny(text, NO_WORDS)) {
            return Reading.of(Intent.NO);
        }
        if (containsAny(text, YES_WORDS)) {
            return Reading.of(Intent.YES);
        }
        return Reading.of(Intent.UNCLEAR);
    }

    private Reading overflowStep(String raw, String text, Set<Integer> candidates) {
        Integer games = gameCount(raw, text);
        if (games != null) {
            return new Reading(Intent.GAMES, games, Set.of());
        }
        // 뺄 번호: "7번이랑 9번 빼고", "12 제외" 처럼 빼라는 말과 번호가 함께 있을 때
        if (containsAny(text, List.of("빼", "제외", "지워", "말고"))) {
            Set<Integer> remove = new HashSet<>();
            Matcher m = NUMBER.matcher(raw);
            while (m.find()) {
                int n = Integer.parseInt(m.group());
                if (candidates.contains(n)) {
                    remove.add(n);
                }
            }
            if (!remove.isEmpty()) {
                return new Reading(Intent.REMOVE, null, remove);
            }
        }
        if (text.contains("통계") && containsAny(text, List.of("줄", "골라", "뽑", "추려", "6개", "여섯", "알아서"))) {
            return Reading.of(Intent.STATS);
        }
        return Reading.of(Intent.UNCLEAR);
    }

    /** "3게임", "3천원어치", "5000원", "1만원", 숫자만 "3" 에서 게임 수를 읽는다. 못 읽으면 null */
    static Integer gameCount(String raw, String squeezed) {
        Matcher m = GAMES.matcher(raw);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        m = WON_CHEON.matcher(raw);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        m = WON_MAN.matcher(raw);
        if (m.find()) {
            return Integer.parseInt(m.group(1)) * 10;
        }
        m = WON_PLAIN.matcher(raw);
        if (m.find()) {
            return Integer.parseInt(m.group(1)) / WON_PER_GAME;
        }
        // 숫자 하나만 답한 경우 ("3") 는 "몇 게임으로 나눌까요?"에 대한 답으로 본다
        if (squeezed.matches("\\d{1,2}")) {
            return Integer.parseInt(squeezed);
        }
        return null;
    }

    // ------------------------------------------------------------------ 2) 못 알아봤을 때만 AI 가 읽기

    private static final String SYSTEM = """
            당신은 로또 꿈풀이 서비스에서 사용자의 짧은 답을 읽는 도우미입니다.
            사용자는 직전에 아래 '질문'을 받았고, 그 질문에 답했습니다. 답을 읽어 아래 JSON 하나로만 답하세요. 설명이나 다른 글은 쓰지 마세요.
            {"intent":"confirm|regenerate|yes|no|games|stats|remove|other","gameCount":숫자 또는 null,"remove":[번호,...]}
            의미:
            - confirm: 지금 번호로 확정하겠다는 뜻
            - regenerate: 번호를 다시 만들어 달라는 뜻
            - yes / no: 질문에 대한 긍정 / 거절
            - games: 몇 게임으로 나눌지 게임 수나 금액으로 답함. 1,000원은 1게임입니다. (예: "삼천원어치"는 gameCount 3, "세 게임"은 3)
            - stats: 통계로 6개만 골라 달라는 뜻
            - remove: 특정 번호를 빼 달라는 뜻. remove 에 그 번호들
            - other: 위 어디에도 해당하지 않는 말 (새 꿈 이야기, 인사 등)
            """;

    /** 직전에 어떤 질문을 했는지 AI 에게 알려 주는 문장 */
    private static String questionOf(String step) {
        return switch (step) {
            case DreamResponse.STEP_CONFIRM -> "이 번호로 확정할까요, 아니면 통계로 채운 번호만 다시 만들까요?";
            case DreamResponse.STEP_ASK_FILL -> "나머지 번호를 통계로 채워도 괜찮을까요?";
            case DreamResponse.STEP_ASK_STAT_NUMBERS -> "대신 통계를 바탕으로 번호를 만들어 드릴까요?";
            case DreamResponse.STEP_ASK_OVERFLOW -> "상징 번호가 6개를 넘는데, 몇 게임으로 나눌까요? 통계로 6개만 고를 수도 있고 뺄 번호를 알려 줘도 됩니다.";
            default -> "(질문 없음)";
        };
    }

    public AiRequest aiRequest(String step, String reply) {
        return new AiRequest(AiRole.CLASSIFIER, SYSTEM, "질문: " + questionOf(step) + "\n사용자의 답: " + reply, 600);   // 생각에 쓰는 토큰까지 감안해 넉넉히 둔다 (작으면 빈 응답으로 실패)
    }

    /** AI 가 돌려준 글을 읽는다. 깨졌거나 이상하면 UNCLEAR */
    public Reading fromAi(String aiText, Set<Integer> candidates) {
        String text = aiText == null ? "" : aiText.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Reading.of(Intent.UNCLEAR);
        }
        try {
            JsonNode node = mapper.readTree(text.substring(start, end + 1));
            switch (node.path("intent").asText("")) {
                case "confirm":
                    return Reading.of(Intent.CONFIRM);
                case "regenerate":
                    return Reading.of(Intent.REGENERATE);
                case "yes":
                    return Reading.of(Intent.YES);
                case "no":
                    return Reading.of(Intent.NO);
                case "games": {
                    int games = node.path("gameCount").asInt(0);
                    return games >= 1 ? new Reading(Intent.GAMES, games, Set.of()) : Reading.of(Intent.UNCLEAR);
                }
                case "stats":
                    return Reading.of(Intent.STATS);
                case "remove": {
                    Set<Integer> remove = new HashSet<>();
                    node.path("remove").forEach(n -> {
                        if (candidates.contains(n.asInt())) {
                            remove.add(n.asInt());
                        }
                    });
                    return remove.isEmpty() ? Reading.of(Intent.UNCLEAR) : new Reading(Intent.REMOVE, null, remove);
                }
                default:
                    return Reading.of(Intent.UNCLEAR);
            }
        } catch (Exception e) {
            return Reading.of(Intent.UNCLEAR);
        }
    }

    // ------------------------------------------------------------------ 보조

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().anyMatch(text::contains);
    }

    /** 한글·영문·숫자만 남기고 소문자로 바꾼다. ("확정할게요!!" → "확정할게요") */
    static String squeeze(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
