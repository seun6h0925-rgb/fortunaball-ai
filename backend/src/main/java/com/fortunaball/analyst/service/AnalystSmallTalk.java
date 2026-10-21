package com.fortunaball.analyst.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 애널리스트의 고정 문구 모음 (인사·감사·사용법·로또와 무관한 이야기).
 *
 * 어떤 말을 고정 문구로 답하는가 (AI 호출·횟수 차감 없음)
 *  - "안녕", "고마워"처럼 딱 봐도 단순한 말만 코드가 직접 알아본다. (공백·문장부호를 뺀 글이 아래 목록과 똑같을 때)
 *  - "사용법", "뭘 할 수 있어" 같은 사용법 질문은 단어가 들어 있으면 알아본다. (회차·번호 같은 로또 입력이 없을 때만)
 *  - 그 밖의 말(인사가 섞였거나 애매한 말)은 여기서 판단하지 않는다. AI 가 판단한다.
 *
 * 같은 상황의 문구는 3개를 돌아가며 쓴다. (같은 말만 반복하는 느낌을 줄이기 위해서다)
 * 문구는 사용자와 확정한 내용이다. 바꿀 때는 먼저 확인받는다. (docs: AI_문구_정리)
 */
public class AnalystSmallTalk {

    /** 알아본 말의 종류 */
    public enum Kind { GREETING, THANKS, USAGE }

    /** 어느 화면에서 열린 애널리스트인지. 화면마다 문구가 다르다. (꿈풀이 문구는 꿈풀이 쪽에서 따로 쓴다) */
    public enum Scene { GENERAL, MAP }

    /** 화면에 보여 줄 고정 답. action 은 버튼 종류(없으면 null), suggestions 는 추천 질문 */
    public record Reply(String message, List<String> suggestions, String action) {
    }

    /** 버튼 종류: 서비스 사용 방법 화면 열기 (자동으로 열지 않고 버튼으로만 보여 준다) */
    public static final String ACTION_OPEN_GUIDE = "OPEN_GUIDE";

    public static final String CODE_GREETING = "SMALL_TALK_GREETING";
    public static final String CODE_THANKS = "SMALL_TALK_THANKS";
    public static final String CODE_USAGE = "USAGE_GUIDE";

    private static final Set<String> GREETINGS = Set.of(
            "안녕", "안녕하세요", "안녕하십니까", "안뇽", "안녕하셔요", "하이", "헬로", "hi", "hello", "hey",
            "반가워", "반가워요", "반갑습니다", "처음뵙겠습니다", "여보세요");

    private static final Set<String> THANKS = Set.of(
            "고마워", "고마워요", "고맙습니다", "감사", "감사해요", "감사합니다", "감사드려요", "땡큐", "thanks", "thankyou",
            "도움됐어", "도움됐어요", "도움이됐어", "도움이됐어요", "고마워요도움됐어요");

    private static final List<String> USAGE_WORDS = List.of(
            "사용법", "사용방법", "이용방법", "어떻게써", "어떻게사용", "어떻게이용", "뭘할수있", "뭐할수있", "무엇을할수",
            "뭘도와", "뭐도와", "도움말", "무슨기능", "어떤기능");

    // ------------------------------------------------------------------ 문구 (3개씩 돌려 쓴다)

    private static final String[] GENERAL_FIRST = {
            "안녕하세요! 저는 로또 데이터를 함께 살펴보는 AI 애널리스트예요. 회차별 당첨번호, 번호 통계, 번호 만들기까지 편하게 물어보세요.",
            "반갑습니다! 로또 통계를 쉽게 풀어 드리고 번호도 함께 만들어 드리는 AI 애널리스트예요. 궁금한 것부터 편하게 말씀해 주세요.",
            "안녕하세요, 만나서 반가워요! 당첨번호나 통계, 번호 만들기 중에 어떤 게 궁금하세요?"};
    private static final String[] GENERAL_MID = {
            "도움이 되셨다니 정말 기뻐요! 이어서 궁금한 게 있으면 편하게 물어보세요.",
            "언제든지요! 다른 회차나 번호가 궁금하시면 이어서 말씀해 주세요.",
            "감사합니다! 더 살펴보고 싶은 게 있으시면 말씀해 주세요. 바로 찾아 드릴게요."};
    private static final String[] GENERAL_OFF = {
            "아쉽지만 그건 제가 잘 알지 못하는 이야기예요. 대신 로또 통계나 번호 만들기는 자신 있어요. 한번 물어봐 주세요!",
            "그 부분은 제가 도와드리기 어려워요. 로또 이야기라면 언제든 환영이에요. 어떤 게 궁금하세요?",
            "죄송해요, 그 질문은 제 전문 분야가 아니에요. 로또 통계와 번호 이야기로는 열심히 도와드릴게요!"};
    private static final String GENERAL_USAGE =
            "저는 이런 걸 도와드려요! ① 회차별 당첨번호와 당첨금 조회 ② 번호 통계와 패턴 설명 ③ 통계를 바탕으로 번호 만들기. "
                    + "궁금한 걸 그냥 말로 물어보시면 돼요. 자세한 사용 방법은 아래 버튼에서 볼 수 있어요.";
    private static final List<String> GENERAL_SUGGESTIONS = List.of(
            "이번 주 번호 만들어줘", "최근 회차 1등 당첨금은?", "가장 많이 나온 번호는?");

    private static final String[] MAP_FIRST = {
            "안녕하세요! 저는 로또 당첨 판매점을 찾아 드리는 AI 애널리스트예요. 궁금한 지역이나 판매점 이름을 알려 주세요.",
            "반갑습니다! 어느 지역의 판매점이 궁금하세요? 당첨 이력이 많은 곳도 알려 드릴게요.",
            "안녕하세요, 만나서 반가워요! 찾고 싶은 동네를 말씀해 주시면 당첨 이력이 있는 판매점을 안내해 드릴게요."};
    private static final String[] MAP_MID = {
            "도움이 되셨다니 기뻐요! 다른 지역도 궁금하시면 말씀해 주세요.",
            "언제든지요! 또 찾고 싶은 판매점이 있으시면 알려 주세요.",
            "감사합니다! 다른 동네나 판매점도 바로 찾아 드릴게요."};
    private static final String[] MAP_OFF = {
            "아쉽지만 그건 제가 도와드리기 어려워요. 판매점과 당첨 이력이라면 자신 있어요. 어느 지역이 궁금하세요?",
            "그 부분은 제 전문 분야가 아니에요. 판매점 찾기라면 언제든 도와드릴게요!",
            "죄송해요, 그 질문은 답하기 어려워요. 찾고 싶은 판매점이 있으시면 말씀해 주세요."};
    private static final String MAP_USAGE =
            "지역이나 판매점 이름을 말씀해 주시면 당첨 이력이 있는 판매점을 알려 드려요. "
                    + "지도의 \"내 주변\" 기능은 화면에서 위치 사용을 켜시면 쓸 수 있어요. 자세한 사용 방법은 아래 버튼에서 볼 수 있어요.";
    private static final List<String> MAP_SUGGESTIONS = List.of(
            "서울 강남구에서 1등이 많이 나온 판매점은?", "최근 1년 안에 당첨된 판매점은?", "루키 명당이 뭐예요?");

    /** 문구를 돌려 쓰는 순번. 서버가 켜져 있는 동안만 유지하면 충분하다. (저장하지 않는다) */
    private final AtomicInteger turn = new AtomicInteger();

    // ------------------------------------------------------------------ 알아보기

    /**
     * 고정 문구로 답할 말인지 본다. 아니면 비어 있고, 그때는 AI 가 판단한다.
     *
     * @param hasLottoInput 질문에 회차·번호·발급코드처럼 로또와 직접 관련된 입력이 있는지 (있으면 사용법 질문으로 보지 않는다)
     */
    public Optional<Kind> detect(String question, boolean hasLottoInput) {
        String text = squeeze(question);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (GREETINGS.contains(text)) {
            return Optional.of(Kind.GREETING);
        }
        if (THANKS.contains(text)) {
            return Optional.of(Kind.THANKS);
        }
        if (!hasLottoInput && USAGE_WORDS.stream().anyMatch(text::contains)) {
            return Optional.of(Kind.USAGE);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ 답 고르기

    /**
     * @param firstTurn 대화의 첫 질문인지 (이전 대화가 없으면 true). 첫 인사와 대화 중 인사·감사가 다르다.
     */
    public Reply reply(Kind kind, Scene scene, boolean firstTurn) {
        boolean map = scene == Scene.MAP;
        List<String> suggestions = map ? MAP_SUGGESTIONS : GENERAL_SUGGESTIONS;
        return switch (kind) {
            case GREETING -> new Reply(pick(map ? (firstTurn ? MAP_FIRST : MAP_MID) : (firstTurn ? GENERAL_FIRST : GENERAL_MID)),
                    suggestions, null);
            case THANKS -> new Reply(pick(map ? MAP_MID : GENERAL_MID), suggestions, null);
            case USAGE -> new Reply(map ? MAP_USAGE : GENERAL_USAGE, suggestions, ACTION_OPEN_GUIDE);
        };
    }

    /** AI 가 "도와드릴 수 없는 이야기"라고 판단했을 때 쓰는 문구 */
    public Reply offTopic(Scene scene) {
        boolean map = scene == Scene.MAP;
        return new Reply(pick(map ? MAP_OFF : GENERAL_OFF), map ? MAP_SUGGESTIONS : GENERAL_SUGGESTIONS, null);
    }

    /** 이 종류의 말에 붙일 정책 코드 (사용 기록에 남는다) */
    public static String codeOf(Kind kind) {
        return switch (kind) {
            case GREETING -> CODE_GREETING;
            case THANKS -> CODE_THANKS;
            case USAGE -> CODE_USAGE;
        };
    }

    /** 이 화면에서 쓰는 추천 질문 (AI 가 답한 뒤에도 같은 질문을 붙일 수 있다) */
    public List<String> suggestions(Scene scene) {
        return scene == Scene.MAP ? MAP_SUGGESTIONS : GENERAL_SUGGESTIONS;
    }

    private String pick(String[] variants) {
        return variants[Math.floorMod(turn.getAndIncrement(), variants.length)];
    }

    /** 한글·영문·숫자만 남기고 소문자로 바꾼다. ("안녕하세요!!" → "안녕하세요", "Hi ~" → "hi") */
    static String squeeze(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
