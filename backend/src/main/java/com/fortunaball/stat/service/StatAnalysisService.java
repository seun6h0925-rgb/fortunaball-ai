package com.fortunaball.stat.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.StatPeriod;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.dto.DrawNumbers;
import com.fortunaball.stat.dto.NumberStatResponse;
import com.fortunaball.stat.dto.StatSummaryResponse;
import com.fortunaball.stat.repository.StatRoundSnapshotRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 통계 요약(API-09)과 번호별 통계(API-10)를 만든다.
 * 📚 배운 내용: @Service, @Transactional(readOnly = true)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 이 두 API 는 저장된 통계 표(lotto_statistics)를 쓰지 않고, 요청할 때마다 draw_round 를 읽어 계산한다.
 *    (회차 이력은 천 줄대로 작고 계산도 가벼워서 따로 저장·갱신하는 것보다 안정적이고 관리가 쉽다. 느려지면 캐시를 붙인다)
 *  - 기준 회차(basisRound)를 안 주면 "저장된 가장 최근 회차"다. 그 회차 이하의 이력만 계산에 쓴다.
 *  - 상태(준비 중·확정): 기준 회차가 최신이면 지금 대상 회차의 스냅샷 상태를 따르고, 지난 회차를 기준으로 보면 항상 확정이다.
 */
@Service
public class StatAnalysisService {

    /** 이력을 전부 읽을 때 쓰는 큰 크기 (회차가 이만큼 쌓일 일은 없다) */
    private static final int ALL = 100_000;

    private final DrawRoundRepository drawRepo;
    private final RoundPolicy roundPolicy;
    private final StatRoundSnapshotRepository snapshotRepo;

    public StatAnalysisService(DrawRoundRepository drawRepo, RoundPolicy roundPolicy,
                               StatRoundSnapshotRepository snapshotRepo) {
        this.drawRepo = drawRepo;
        this.roundPolicy = roundPolicy;
        this.snapshotRepo = snapshotRepo;
    }

    /** API-09 @param basisRound 기준 회차(null 이면 최신) */
    @Transactional(readOnly = true)
    public StatSummaryResponse summary(StatPeriod period, Integer basisRound) {
        int latest = latestRound();
        int basis = resolveBasis(basisRound, latest);
        List<DrawNumbers> history = history(basis);

        String status = null;
        String label = null;
        if (basis < latest) {
            status = "CONFIRMED";
            label = "확정";
        } else {
            StatRoundSnapshot snap = snapshotRepo.findByTargetRoundNo(roundPolicy.targetRound()).orElse(null);
            if (snap != null) {
                status = snap.getStatus().name();
                label = snap.getStatus().label();
            }
        }
        return SummaryCalculator.summarize(history, period, status, label);
    }

    /**
     * API-10. 구간은 (fromRound, toRound) 를 둘 다 주면 그 범위, 아니면 period 로 정한다. (둘 중 하나만 주면 400)
     * @param toRound 구간 끝 = 기준 회차(null 이면 최신)
     */
    @Transactional(readOnly = true)
    public NumberStatResponse numbers(StatPeriod period, Integer fromRound, Integer toRound) {
        if ((fromRound == null) != (toRound == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회차 범위는 시작과 끝을 함께 입력해 주세요.",
                    "fromRound=" + fromRound + ", toRound=" + toRound);
        }
        int latest = latestRound();
        int to = toRound == null ? latest : resolveBasis(toRound, latest);
        List<DrawNumbers> history = history(to);

        int from;
        if (fromRound != null) {
            if (fromRound < 1 || fromRound > to) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "회차 범위를 확인해 주세요.",
                        "fromRound=" + fromRound + ", toRound=" + to);
            }
            from = fromRound;
        } else if (period.isAll()) {
            from = history.get(0).roundNo();
        } else {
            from = history.get(Math.max(0, history.size() - period.rounds())).roundNo();
        }

        int roundCount = (int) history.stream().filter(d -> d.roundNo() >= from).count();
        if (roundCount == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해당 범위에 회차 데이터가 없어요.",
                    "범위 데이터 없음: " + from + "~" + to);
        }
        List<NumberStatResponse.Item> items = NumberStatCalculator.calculate(history, from, to).stream()
                .map(s -> new NumberStatResponse.Item(s.number(), s.count(), s.lastRound(), s.absentRounds()))
                .toList();
        return new NumberStatResponse(to, from, to, roundCount, items);
    }

    // ---------- 도우미 ----------

    private int latestRound() {
        return drawRepo.findTopByOrderByRoundNoDesc().map(DrawRound::getRoundNo).orElseThrow(
                () -> new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "통계를 준비하고 있어요. 잠시 후 다시 시도해 주세요.",
                        "draw_round 비어 있음"));
    }

    private static int resolveBasis(Integer basisRound, int latest) {
        if (basisRound == null) {
            return latest;
        }
        if (basisRound < 1 || basisRound > latest) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회차를 확인해 주세요.",
                    "basisRound=" + basisRound + ", latest=" + latest);
        }
        return basisRound;
    }

    /** 기준 회차 이하의 모든 회차를 오래된 순으로 */
    private List<DrawNumbers> history(int basis) {
        List<DrawRound> desc = drawRepo.findByRoundNoLessThanEqualOrderByRoundNoDesc(basis, PageRequest.of(0, ALL));
        List<DrawNumbers> list = new ArrayList<>(desc.size());
        for (DrawRound r : desc) {
            list.add(new DrawNumbers(r.getRoundNo(), r.getNumbers()));
        }
        Collections.reverse(list);
        if (list.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해당 회차 데이터가 없어요.", "history 비어 있음: basis=" + basis);
        }
        return list;
    }
}
