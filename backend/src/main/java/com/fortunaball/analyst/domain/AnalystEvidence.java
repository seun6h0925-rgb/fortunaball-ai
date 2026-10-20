package com.fortunaball.analyst.domain;

import java.util.List;

/**
 * 질문에 답하려고 서버가 모은 근거 데이터. 숫자는 모두 서버가 계산한 값이고, AI 는 이 값을 쉬운 문장으로 풀어 쓰기만 한다.
 *
 *  type        질문 유형
 *  basis       근거의 기준 안내. 예) "1245회까지 최근 100회 기준"
 *  items       근거 항목들 (화면 "분석 근거 보기"에 그대로 보여 준다)
 *  references  참고 데이터 출처. 예) "동행복권 회차별 당첨번호"
 *  ready       false 면 아직 근거를 모을 수 없는 질문이다. (예: 판매점 데이터가 없는 지도 질문)
 */
public record AnalystEvidence(QuestionType type, String basis, List<EvidenceItem> items, List<String> references,
                              boolean ready) {

    /** AI 에게 줄 재료 문장. 한 줄에 항목 하나. (질문 원문은 포함하지 않는다) */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        if (basis != null && !basis.isBlank()) {
            sb.append("기준: ").append(basis).append('\n');
        }
        for (EvidenceItem item : items) {
            sb.append("- ").append(item.label()).append(": ").append(item.value()).append('\n');
        }
        return sb.toString();
    }
}
