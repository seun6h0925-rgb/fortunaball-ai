package com.fortunaball.ticket.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 용지 당첨 확인 결과 (API-63)
 *
 * 아직 당첨번호가 없는 회차(drawn = false)에는 당첨번호·등수가 모두 null 이고 message 에 안내 문구가 들어간다.
 * 이 경우는 오류가 아니라 정상 응답이다. (다음 회차 용지를 미리 사 둔 사용자가 흔히 만나는 상황)
 *
 * @param roundNo        용지에 적힌 회차
 * @param drawn          그 회차의 당첨번호가 있는지
 * @param drawDate       추첨일 (당첨번호가 없으면 null)
 * @param winningNumbers 당첨번호 6개 (없으면 null)
 * @param bonusNumber    보너스 번호 (없으면 null)
 * @param bestRank       이 용지에서 가장 높은 등수 1~5 (낙첨이거나 당첨번호가 없으면 null)
 * @param bestRankLabel  "1등"~"5등" / "낙첨" (당첨번호가 없으면 null)
 * @param message        화면에 보여줄 안내 문구
 * @param games          용지의 줄별 결과 (A줄부터)
 */
public record TicketCheckResponse(
        int roundNo,
        boolean drawn,
        LocalDate drawDate,
        List<Integer> winningNumbers,
        Integer bonusNumber,
        Integer bestRank,
        String bestRankLabel,
        String message,
        List<Game> games) {

    /**
     * @param line           줄 이름 "A"~"E"
     * @param kindLabel      "자동" / "수동" / "반자동"
     * @param numbers        용지에 적힌 번호 6개
     * @param matchCount     맞힌 개수 (당첨번호가 없으면 null)
     * @param bonusMatched   보너스 번호 일치 여부 (없으면 null)
     * @param rank           1~5등, 낙첨이면 null
     * @param rankLabel      "1등"~"5등" / "낙첨" (당첨번호가 없으면 null)
     * @param matchedNumbers 당첨번호와 겹친 번호 (화면 강조용, 없으면 null)
     */
    public record Game(
            String line,
            String kindLabel,
            List<Integer> numbers,
            Integer matchCount,
            Boolean bonusMatched,
            Integer rank,
            String rankLabel,
            List<Integer> matchedNumbers) {
    }
}
