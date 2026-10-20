package com.fortunaball.analyst.domain;

/**
 * 애널리스트 질문 유형. 서버가 규칙으로 정하고, 유형에 따라 어떤 근거 데이터를 모을지가 달라진다.
 *
 *  NUMBER_GENERATION  번호를 만들어 달라는 질문 (게임 수·포함·제외 번호를 함께 읽는다)
 *  ISSUE_ANALYSIS     발급코드가 들어 있는 질문 (내가 발급받은 번호 분석)
 *  NUMBER_ANALYSIS    번호 6개를 직접 적고 분석을 요청한 질문
 *  ROUND_ANALYSIS     특정 회차(예: 1245회)에 대한 질문
 *  STAT_ANALYSIS      그 밖의 통계 질문 (최근 자주 나온 번호 등)
 *  MAP_STORE          지도 화면에서 온 판매점·명당·당첨 이력 질문
 */
public enum QuestionType {
    NUMBER_GENERATION,
    ISSUE_ANALYSIS,
    NUMBER_ANALYSIS,
    ROUND_ANALYSIS,
    STAT_ANALYSIS,
    MAP_STORE
}
