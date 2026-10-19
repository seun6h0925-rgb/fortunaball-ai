package com.fortunaball.stat.dto;

import com.fortunaball.stat.domain.PatternCode;

/**
 * 패턴 하나의 계산 결과. 화면에는 "최근 {period}회 중 {hitCount}회 해당"으로 보인다.
 *  - period    : 요청한 기간(20·50·100)
 *  - windowSize: 실제로 계산에 쓴 회차 수 (저장된 회차가 기간보다 적을 때만 period 보다 작다)
 *  - hitCount  : 그 안에서 패턴에 해당한 회차 수
 */
public record PatternResult(PatternCode code, int period, int windowSize, int hitCount) {
}
