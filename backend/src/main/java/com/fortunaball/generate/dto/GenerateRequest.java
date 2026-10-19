package com.fortunaball.generate.dto;

/**
 * 번호 생성 요청 (POST /api/numbers/generate 의 본문)
 *
 * @param gameCount   만들 게임 수. 1 ~ 설정한 최대값(기본 50). 비어 있으면 오류.
 * @param patternCode 패턴 코드(HOT_NUMBER 등). 비우면 패턴 없이 통계 검증만 거쳐 만든다.
 *
 * ⚠ 포함할 번호 / 제외할 번호는 받지 않는다. (정책: 번호는 서버 알고리즘이 만든다)
 *    숫자가 아닌 값이 오면 JSON 변환 단계에서 400(COMMON-001)으로 거절된다.
 */
public record GenerateRequest(Integer gameCount, String patternCode) {
}
