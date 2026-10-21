package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fortunaball.stat.service.StatAnalysisService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DreamStatRankerTest {

    @Test
    void 통계를_읽지_못하면_작은_번호_순으로_돌려주고_꿈풀이를_멈추지_않는다() {
        StatAnalysisService statService = mock(StatAnalysisService.class);
        when(statService.numbers(any(), any(), any())).thenThrow(new IllegalStateException("통계 오류"));

        List<Integer> ranked = new DreamStatRanker(statService).rank(Set.of(35, 7, 22, 3));

        assertEquals(List.of(3, 7, 22, 35), ranked);
    }
}
