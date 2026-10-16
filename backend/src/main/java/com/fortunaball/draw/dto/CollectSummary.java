package com.fortunaball.draw.dto;

import java.util.List;

/**
 * 전체 수집(1회~최신 회차)이 끝난 뒤의 결과 요약. 로그에 남기고, 나중에 관리자 화면(API-23)에서도 보여줄 수 있다.
 *
 * @param saved          새로 저장한 회차 수
 * @param skipped        이미 있어서 건너뛴 회차 수
 * @param notAggregated  2~5등 집계 전이라 저장하지 않은 회차 수
 * @param failedRounds   실패한 회차 번호들 (원인은 로그의 "[회차수집]" 줄에 있다)
 * @param lastRound      마지막으로 확인한 회차 번호
 * @param stopReason     끝난 이유 (정상 완료 / 연속 실패로 중단 등)
 */
public record CollectSummary(int saved, int skipped, int notAggregated,
                             List<Integer> failedRounds, int lastRound, String stopReason) { }
