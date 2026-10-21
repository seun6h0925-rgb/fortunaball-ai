package com.fortunaball.dream.dto;

import java.util.List;

/**
 * AI 꿈풀이 응답 (API-08)
 *
 *  matchResult     FULL_MATCH(꿈에서 나온 번호가 6개) / PARTIAL_MATCH(6개가 아님) / NO_MATCH(번호가 하나도 없음)
 *  step            서버가 지금 어떤 이야기 중인지 (아래 STEP_ 상수). 사용자가 답하면 이 값을 그대로 돌려보낸다.
 *  policyCode      고정 응답일 때만 값이 있다. 이때는 AI 를 부르지 않고 횟수도 차감하지 않는다.
 *  message         화면에 먼저 보여 줄 말 (결과 첫마디, 동의 질문, 인사 등). 항상 있다.
 *  question        풀이 뒤에 보여 줄 질문 (예: "이 번호로 확정하시겠어요?"). 없으면 null
 *  symbols         꿈에서 찾은 상징과 근거 해석 ("꿈풀이 근거 보기"는 이 값을 보여주므로 AI 를 다시 부르지 않는다)
 *  interpretation  AI 가 쉬운 문장으로 풀어 쓴 해석 (처음 풀이일 때만. 이어지는 응답에는 null)
 *  numbers         추천 번호 한 게임 (오름차순) 과 번호 출처. 출처가 STAT 이면 "통계", 그 밖에는 "꿈 상징"으로 표시한다.
 *  games           여러 게임으로 나눠 담았을 때의 게임별 번호. 한 게임이면 빈 목록
 *  keep            지금 쓰고 있는 꿈에서 나온 번호들. 사용자가 말로 답할 때 request.keep 으로 그대로 돌려보낸다.
 *  gameCount       게임 수. 사용자가 말로 답할 때 request.gameCount 로 그대로 돌려보낸다.
 *  confirmToken    확정용 임시 확정 토큰 (기존 발급 확정 API-04 에 그대로 보낸다). 번호가 완성되지 않았으면 null
 *  tokenTtlMinutes 토큰 유효 시간(분)
 *  targetRound     대상 회차
 *  action          화면이 해야 할 일. ISSUE(사용자가 말로 확정했으니 confirmToken 으로 API-04 를 부른다) /
 *                  OPEN_GUIDE(사용 방법 화면 보기 링크를 보여 준다) / 없으면 null
 *  quickReplies    사용자가 글을 쓰지 않고 누를 수 있는 답 칩. 누르면 그 문구를 reply 로 그대로 보낸다. 없으면 빈 목록
 *  suggestions     추천 질문 (인사·잡담·너무 짧을 때 등)
 *  charged         이번 요청이 무료 횟수를 1회 썼는지
 *  basisNotice     해석 기준 안내 (한국 전통 우선)
 *  notice          참고용 고지
 *  remainingToday  오늘 남은 무료 AI 횟수 (관리자는 null)
 *
 * 출처(자료 이름)는 응답에 담지 않는다. 출처는 서비스 안내 화면에만 보여 준다.
 */
public record DreamResponse(String matchResult, String step, String policyCode, String message, String question,
                            List<SymbolView> symbols, String interpretation, List<NumberView> numbers,
                            List<List<NumberView>> games, List<Integer> keep, Integer gameCount,
                            String confirmToken, Integer tokenTtlMinutes,
                            Integer targetRound, String action, List<String> quickReplies, List<String> suggestions,
                            boolean charged, String basisNotice, String notice, Integer remainingToday) {

    /** 인사·감사·사용법·꿈과 무관한 이야기·당첨 보장 요구·거절처럼 풀이 없이 말만 돌려주는 경우 */
    public static final String STEP_TALK = "TALK";
    /** 꿈 내용이 너무 짧아서 더 적어 달라고 요청 */
    public static final String STEP_NEED_MORE = "NEED_MORE";
    /** 꿈 번호가 하나도 없어서 "통계 번호를 만들어 볼까요?"를 묻는 중 */
    public static final String STEP_ASK_STAT_NUMBERS = "ASK_STAT_NUMBERS";
    /** 꿈 번호가 3개 이하라서 "나머지를 통계로 채워도 될까요?"를 묻는 중 */
    public static final String STEP_ASK_FILL = "ASK_FILL";
    /** 꿈 번호가 6개를 넘어서 "몇 게임으로 나눌지 / 통계로 줄일지 / 뺄 번호가 있는지"를 묻는 중 */
    public static final String STEP_ASK_OVERFLOW = "ASK_OVERFLOW";
    /** 번호가 완성돼서 확정 토큰이 있다. "확정할게요" / "다시 만들어 주세요"를 기다린다 */
    public static final String STEP_CONFIRM = "CONFIRM";
    /** 사용자가 말로 확정했다. 화면이 action=ISSUE 로 확정 처리를 한다 */
    public static final String STEP_CONFIRMED = "CONFIRMED";

    public static final String ACTION_ISSUE = "ISSUE";
    public static final String ACTION_OPEN_GUIDE = "OPEN_GUIDE";

    public record SymbolView(String keyword, List<InterpretationView> interpretations) {
    }

    public record InterpretationView(String situation, String summary, String polarity, String region) {
    }

    public record NumberView(int number, String origin) {
    }
}
