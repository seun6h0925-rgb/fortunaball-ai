package com.fortunaball.analyst.domain;

import java.util.List;

/**
 * 질문 문장에서 규칙으로 뽑아낸 값. (질문 원문은 담지 않는다. 저장·로그 금지)
 *
 *  type          질문 유형
 *  gameCount     "3게임"처럼 말한 게임 수 (말하지 않았으면 null. 번호 생성에서 null 이면 서비스가 기본값을 쓴다)
 *  include       꼭 넣어 달라고 한 번호 (1~45, 중복 없음)
 *  exclude       빼 달라고 한 번호 (1~45, 중복 없음)
 *  issueCode     질문에 적힌 발급코드 (대문자로 바꾼 값, 없으면 null)
 *  roundNo       "1245회차"처럼 말한 회차 (없으면 null)
 *  periodRounds  "최근 20회"처럼 말한 분석 기간 (없으면 null)
 *  givenNumbers  직접 적은 번호 6개 (없으면 빈 목록)
 */
public record ParsedQuestion(QuestionType type, Integer gameCount, List<Integer> include, List<Integer> exclude,
                             String issueCode, Integer roundNo, Integer periodRounds, List<Integer> givenNumbers) {

    /** 번호 생성·발급코드·직접 적은 번호처럼 로또와 직접 관련된 입력이 있는지 (주제 이탈 판별에 쓴다) */
    public boolean hasLottoInput() {
        return type == QuestionType.NUMBER_GENERATION || issueCode != null || roundNo != null || !givenNumbers.isEmpty()
                || !include.isEmpty() || !exclude.isEmpty() || gameCount != null;
    }
}
