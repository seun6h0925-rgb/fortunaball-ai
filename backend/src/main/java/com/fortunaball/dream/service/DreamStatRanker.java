package com.fortunaball.dream.service;

import com.fortunaball.stat.domain.StatPeriod;
import com.fortunaball.stat.dto.NumberStatResponse;
import com.fortunaball.stat.service.StatAnalysisService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 번호들을 통계로 줄 세운다. (최근 100회에 많이 나온 순, 같으면 작은 번호 먼저)
 * 꿈 상징 번호가 6개를 넘을 때 "통계로 6개만 골라 주세요"와 "여러 게임에 나눠 담기"의 순서를 정하는 데 쓴다.
 * 통계를 읽지 못하면 작은 번호 순으로 돌려준다. (꿈풀이가 통계 오류로 멈추지 않게 하려는 것)
 */
@Component
public class DreamStatRanker {

    private static final Logger log = LoggerFactory.getLogger(DreamStatRanker.class);

    private final StatAnalysisService statService;

    public DreamStatRanker(StatAnalysisService statService) {
        this.statService = statService;
    }

    public List<Integer> rank(Collection<Integer> numbers) {
        Map<Integer, Integer> counts = new HashMap<>();
        try {
            NumberStatResponse stat = statService.numbers(StatPeriod.parse("100"), null, null);
            stat.items().forEach(item -> counts.put(item.number(), item.count()));
        } catch (RuntimeException e) {
            log.warn("꿈풀이 통계 순서를 가져오지 못했습니다: {}", e.getClass().getSimpleName());
        }
        List<Integer> ranked = new ArrayList<>(numbers);
        ranked.sort(Comparator.<Integer>comparingInt(n -> counts.getOrDefault(n, 0)).reversed().thenComparingInt(n -> n));
        return ranked;
    }
}
