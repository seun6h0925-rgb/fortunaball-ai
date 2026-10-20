package com.fortunaball.dream.dto;

import java.util.List;

/**
 * AI 꿈풀이 응답 (API-08)
 *
 *  matchResult           FULL_MATCH / PARTIAL_MATCH / NO_MATCH
 *  policyCode            고정 응답일 때만 값이 있다 (DREAM_LOW_RELEVANCE). 이때는 AI 를 부르지 않고 횟수도 차감하지 않는다.
 *  message               고정 응답 문구 (고정 응답일 때만)
 *  suggestStatGeneration true 면 화면이 "통계 번호 만들어 보기" 선택지를 보여준다
 *  symbols               꿈에서 찾은 상징과 근거 해석 ("꿈풀이 근거 보기"는 이 값을 보여주므로 AI 를 다시 부르지 않는다)
 *  interpretation        AI 가 쉬운 문장으로 풀어 쓴 해석 (고정 응답이면 null)
 *  numbers               추천 번호 (오름차순) 와 번호 출처
 *  sources               상징에 연결된 출처 (화면 출처 표기용)
 *  basisNotice           해석 기준 안내 (한국 전통 우선)
 *  notice                참고용 고지
 *  remainingToday        오늘 남은 무료 AI 횟수 (관리자는 null)
 */
public record DreamResponse(String matchResult, String policyCode, String message, boolean suggestStatGeneration,
                            List<SymbolView> symbols, String interpretation, List<NumberView> numbers,
                            List<SourceView> sources, String basisNotice, String notice, Integer remainingToday) {

    public record SymbolView(String keyword, List<InterpretationView> interpretations) {
    }

    public record InterpretationView(String situation, String summary, String polarity, String region,
                                     String sourceName) {
    }

    public record NumberView(int number, String origin) {
    }

    public record SourceView(String name, String type, String trustLevel) {
    }
}
