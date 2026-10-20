package com.fortunaball.stat.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.domain.DrawPrize;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawPrizeRepository;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.stat.dto.PrizeTrendResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 당첨금 추이 (API-11). 회차 범위의 회차별 등수별 당첨금·당첨자 수와 등수별 평균을 만든다.
 * 📚 배운 내용: @Service, @Transactional(readOnly = true)
 *
 * ⚠ 범위를 안 주면 최근 20회(최신 회차 포함). 한 번에 최대 200회까지만 허용한다. (응답이 너무 커지는 것을 막으려는 상한)
 * ⚠ 평균은 "당첨금 정보가 있는 회차"만으로 계산한다. (수집이 안 된 회차를 0으로 세면 평균이 낮아져 틀린다)
 */
@Service
public class PrizeTrendService {

    static final int DEFAULT_ROUNDS = 20;
    static final int MAX_ROUNDS = 200;

    private final DrawRoundRepository drawRepo;
    private final DrawPrizeRepository prizeRepo;

    public PrizeTrendService(DrawRoundRepository drawRepo, DrawPrizeRepository prizeRepo) {
        this.drawRepo = drawRepo;
        this.prizeRepo = prizeRepo;
    }

    @Transactional(readOnly = true)
    public PrizeTrendResponse prizes(Integer fromRound, Integer toRound) {
        if ((fromRound == null) != (toRound == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회차 범위는 시작과 끝을 함께 입력해 주세요.",
                    "fromRound=" + fromRound + ", toRound=" + toRound);
        }
        int to;
        int from;
        if (toRound == null) {
            to = drawRepo.findTopByOrderByRoundNoDesc().map(DrawRound::getRoundNo).orElseThrow(
                    () -> new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "통계를 준비하고 있어요. 잠시 후 다시 시도해 주세요.",
                            "draw_round 비어 있음"));
            from = Math.max(1, to - DEFAULT_ROUNDS + 1);
        } else {
            from = fromRound;
            to = toRound;
            if (from < 1 || from > to) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "회차 범위를 확인해 주세요.",
                        "fromRound=" + from + ", toRound=" + to);
            }
            if (to - from + 1 > MAX_ROUNDS) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "회차 범위는 최대 " + MAX_ROUNDS + "회까지 조회할 수 있어요.",
                        "범위 초과: " + from + "~" + to);
            }
        }

        List<DrawRound> rounds = drawRepo.findByRoundNoBetweenOrderByRoundNoAsc(from, to);
        Map<Integer, List<PrizeTrendResponse.Prize>> byRound = new HashMap<>();
        // 등수별 합계 [당첨자 수 합, 당첨금 합, 회차 수]
        long[][] sums = new long[6][3];
        for (DrawPrize p : prizeRepo.findByRoundNoBetweenOrderByRoundNoAscWinningRankAsc(from, to)) {
            byRound.computeIfAbsent(p.getRoundNo(), k -> new ArrayList<>())
                    .add(new PrizeTrendResponse.Prize(p.getWinningRank(), p.getPrizeAmount(), p.getWinnerCount()));
            long[] s = sums[p.getWinningRank()];
            s[0] += p.getWinnerCount();
            s[1] += p.getPrizeAmount();
            s[2]++;
        }

        List<PrizeTrendResponse.RoundPrizes> list = rounds.stream()
                .map(r -> new PrizeTrendResponse.RoundPrizes(r.getRoundNo(), r.getDrawDate(),
                        byRound.getOrDefault(r.getRoundNo(), List.of())))
                .toList();
        List<PrizeTrendResponse.RankAverage> averages = new ArrayList<>();
        for (int rank = 1; rank <= 5; rank++) {
            long[] s = sums[rank];
            if (s[2] > 0) {
                averages.add(new PrizeTrendResponse.RankAverage(rank,
                        Math.round(s[0] * 10.0 / s[2]) / 10.0, Math.round((double) s[1] / s[2])));
            }
        }
        return new PrizeTrendResponse(from, to, list.size(), list, averages);
    }
}
