package com.fortunaball.analyst.dto;

import com.fortunaball.analyst.domain.EvidenceItem;
import com.fortunaball.generate.dto.GenerateResponse;
import java.util.List;

/**
 * AI 로또 애널리스트 응답 (API-07)
 *
 *  questionType   무엇을 했는지. 번호를 만들었으면 NUMBER_GENERATION, 조회한 자료가 있으면 그 종류
 *                 (ROUND_ANALYSIS, STAT_ANALYSIS, NUMBER_ANALYSIS, ISSUE_ANALYSIS),
 *                 조회 없이 답했으면 GENERAL, 지도 질문이면 MAP_STORE
 *  policyCode     고정 응답일 때만 값이 있다. (정책 코드, 지도 데이터 준비 전이면 MAP_DATA_PENDING)
 *  message        고정 응답 문구. 고정 응답이 아니면 null
 *  answer         AI 답변 (500자 이하, 한 번에 응답). 고정 응답이면 null
 *  generated      번호를 만들었을 때 만든 번호와 임시 확정 토큰. 아니면 null (번호 생성 화면과 같은 형식)
 *  evidence       조회한 근거. 화면의 "분석 근거 보기"에 쓴다. 조회하지 않았으면 null
 *  references     참고 데이터 출처
 *  notice         고정 안내 문구
 *  remainingToday 오늘 남은 무료 횟수 (제한 없음이면 null)
 *  historyText    다음 질문 때 history 에 assistant 내용으로 그대로 보내는 글. (답변 + 만든 번호)
 */
public record AnalystResponse(String questionType, String policyCode, String message, String answer,
                              GenerateResponse generated, Evidence evidence, List<String> references,
                              String notice, Integer remainingToday, String historyText) {

    public record Evidence(String basis, List<EvidenceItem> items) {
    }
}
