package com.fortunaball.issue.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 발급 확정 응답
 *
 * @param issueCode     발급 코드 (내 기록 조회·당첨 확인 때 쓰는 값. 12자)
 * @param targetRound   대상 회차
 * @param drawDate      대상 회차 추첨 예정일
 * @param gameCount     확정된 게임 수
 * @param patternCode   생성 때 고른 패턴 (없으면 null)
 * @param patternName   패턴 한글 이름 (없으면 null)
 * @param games         확정된 번호들 (교체된 게임은 replaced=true)
 * @param replacedGames 교체된 게임의 이전 번호/새 번호. 없으면 빈 목록
 */
public record IssueConfirmResponse(
        String issueCode,
        int targetRound,
        LocalDate drawDate,
        int gameCount,
        String patternCode,
        String patternName,
        List<IssuedGame> games,
        List<ReplacedGame> replacedGames) {

    /** gameOrder 는 1부터 시작 */
    public record IssuedGame(int gameOrder, List<Integer> numbers, boolean replaced) {
    }

    /** 이미 같은 회차에서 3번 발급된 조합이라 새 번호로 바꾼 게임 */
    public record ReplacedGame(int gameOrder, List<Integer> before, List<Integer> after) {
    }
}
