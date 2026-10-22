package com.fortunaball.stat.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.stat.dto.RegionStatResponse;
import com.fortunaball.stat.dto.RegionStatResponse.RegionItem;
import com.fortunaball.stat.repository.RegionStatRepository;
import com.fortunaball.stat.repository.RegionStatRepository.RegionRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 지역별 당첨 통계 (API-41). 회차 범위의 시도별 1·2등 당첨 건수와, 바로 앞 같은 길이 구간 대비 1등 증감률을 만든다.
 * 📚 배운 내용: @Service, @Transactional(readOnly = true)
 *
 * ⚠ 범위를 안 주면 최근 20회(최신 회차 포함). 한 번에 최대 200회까지 허용한다. (당첨금 추이 API-11 과 같은 규칙)
 * ⚠ 비교 구간(바로 앞 같은 길이)이 판매점 자료 시작 회차보다 앞서면 비교하지 않는다 → previousAvailable=false.
 */
@Service
public class RegionStatService {

    static final int DEFAULT_ROUNDS = 20;
    static final int MAX_ROUNDS = 200;

    private final DrawRoundRepository drawRepo;
    private final RegionStatRepository regionRepo;

    public RegionStatService(DrawRoundRepository drawRepo, RegionStatRepository regionRepo) {
        this.drawRepo = drawRepo;
        this.regionRepo = regionRepo;
    }

    @Transactional(readOnly = true)
    public RegionStatResponse regions(Integer fromRound, Integer toRound) {
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

        int length = to - from + 1;
        int prevTo = from - 1;
        int prevFrom = from - length;
        Integer dataFrom = regionRepo.firstHistoryRound();
        // 앞 구간이 1회차보다 앞이거나 자료 시작 회차보다 앞서면 비교하지 않는다
        boolean previousAvailable = dataFrom != null && prevFrom >= 1 && prevFrom >= dataFrom;

        Map<String, RegionRow> current = new HashMap<>();
        regionRepo.sumByRegion(from, to).forEach(r -> current.put(r.sido(), r));
        Map<String, RegionRow> previous = new HashMap<>();
        if (previousAvailable) {
            regionRepo.sumByRegion(prevFrom, prevTo).forEach(r -> previous.put(r.sido(), r));
        }

        List<RegionItem> items = current.values().stream()
                .map(r -> {
                    int prevFirst = previous.containsKey(r.sido()) ? previous.get(r.sido()).firstCount() : 0;
                    return new RegionItem(r.sido(), r.storeCount(), r.firstCount(), r.secondCount(),
                            prevFirst, changeRate(previousAvailable, r.firstCount(), prevFirst));
                })
                .sorted(Comparator.comparingInt(RegionItem::firstCount).reversed().thenComparing(RegionItem::sido))
                .toList();
        return new RegionStatResponse(from, to, previousAvailable ? prevFrom : 0, previousAvailable ? prevTo : 0,
                previousAvailable, dataFrom, items);
    }

    /** 증감률(%, 소수 첫째 자리). 비교 불가이거나 앞 구간이 0건이면 null */
    static Double changeRate(boolean previousAvailable, int current, int previous) {
        if (!previousAvailable || previous <= 0) {
            return null;
        }
        return Math.round((current - previous) * 1000.0 / previous) / 10.0;
    }
}
