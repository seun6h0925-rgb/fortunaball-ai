package com.fortunaball.stat.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 통계 패턴 조회 응답 (API-44).
 *  - targetRound : 지금 번호 생성의 대상 회차 (예: 1247)
 *  - basisRound  : 통계를 계산한 기준 회차 = 이미 결과가 저장된 가장 최근 회차 (예: 1246)
 *  - status      : 통계 상태 코드 (PREPARING / WAITING / CONFIRMED), statusLabel 은 화면 표시용 (사전 분석 / 결과 대기 / 확정)
 */
public record PatternStatResponse(
        int targetRound,
        int basisRound,
        String status,
        String statusLabel,
        int period,
        LocalDateTime generatedAt,
        List<PatternCardResponse> patterns
) {

    /**
     * 카드마다 구성 예시 번호를 붙인 새 응답을 만든다. (record 는 값을 바꿀 수 없어서 새로 만든다)
     * @param examples 패턴 코드(HOT_NUMBER 등) → 예시 번호 6개. 없는 패턴은 예시 없이 그대로 둔다.
     */
    public PatternStatResponse withExamples(Map<String, List<Integer>> examples) {
        List<PatternCardResponse> withEx = patterns.stream()
                .map(c -> new PatternCardResponse(c.code(), c.name(), c.description(), c.period(), c.hitCount(),
                        examples.get(c.code())))
                .toList();
        return new PatternStatResponse(targetRound, basisRound, status, statusLabel, period, generatedAt, withEx);
    }
}
