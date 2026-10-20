package com.fortunaball.analyst.domain;

/** 근거 한 줄. 화면의 "분석 근거 보기"와 AI 에게 줄 재료로 함께 쓴다. 예) label="홀짝", value="홀수 3개 · 짝수 3개" */
public record EvidenceItem(String label, String value) {
}
