package com.fortunaball.generate.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 번호 생성 응답
 *
 * @param confirmToken 임시 확정 토큰. 발급 확정(API-04) 때 이 값으로 번호를 꺼낸다. 번호 내용은 서버가 들고 있다.
 * @param ttlMinutes   토큰 유효 시간(분)
 * @param expiresAt    토큰 만료 시각 (UTC, 예: 2026-10-19T07:30:00Z)
 * @param games        생성된 번호 조합들 (각각 오름차순 6개)
 * @param patternCode  선택한 패턴 코드 (없으면 null)
 * @param patternName  패턴 한글 이름 (없으면 null)
 * @param targetRound  대상 회차 (서버가 현재 시각으로 계산한 값)
 * @param drawDate     대상 회차 추첨 예정일
 * @param basisRound   통계 기준 회차 (이 회차까지의 당첨 결과로 계산)
 */
public record GenerateResponse(
        String confirmToken,
        int ttlMinutes,
        Instant expiresAt,
        List<List<Integer>> games,
        String patternCode,
        String patternName,
        int targetRound,
        LocalDate drawDate,
        int basisRound) {
}
