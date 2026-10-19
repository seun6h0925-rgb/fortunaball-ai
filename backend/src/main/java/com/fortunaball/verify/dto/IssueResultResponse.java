package com.fortunaball.verify.dto;

import java.util.List;

/**
 * 당첨 검증 결과 (API-06)
 *
 * 추첨 전(drawn = false)에는 당첨번호와 게임별 결과가 모두 null 이고, 검증 상태는 PENDING("검증 대기")이다.
 *
 * @param issueCode          발급 코드
 * @param targetRound        대상 회차
 * @param drawn              대상 회차의 추첨 결과가 있는지
 * @param winningNumbers     당첨번호 6개 (추첨 전이면 null)
 * @param bonusNumber        보너스 번호 (추첨 전이면 null)
 * @param verificationStatus PENDING / VERIFIED
 * @param verificationLabel  "검증 대기" / "검증 완료"
 * @param bestRank           이 발급 건에서 가장 높은 등수 (1~5, 낙첨이거나 추첨 전이면 null)
 * @param bestRankLabel      "1등"~"5등" / "낙첨" (추첨 전이면 null)
 * @param games              게임별 결과 (게임 순서대로)
 */
public record IssueResultResponse(
        String issueCode,
        int targetRound,
        boolean drawn,
        List<Integer> winningNumbers,
        Integer bonusNumber,
        String verificationStatus,
        String verificationLabel,
        Integer bestRank,
        String bestRankLabel,
        List<Game> games) {

    /**
     * @param matchCount     맞힌 개수 (추첨 전이면 null)
     * @param bonusMatched   보너스 번호 일치 여부 (추첨 전이면 null)
     * @param rank           1~5등, 낙첨이면 null
     * @param rankLabel      "1등"~"5등" / "낙첨" (추첨 전이면 null)
     * @param matchedNumbers 당첨번호와 겹친 번호 (화면에서 강조 표시용, 추첨 전이면 null)
     */
    public record Game(
            int gameOrder,
            List<Integer> numbers,
            Integer matchCount,
            Boolean bonusMatched,
            Integer rank,
            String rankLabel,
            List<Integer> matchedNumbers) {
    }
}
