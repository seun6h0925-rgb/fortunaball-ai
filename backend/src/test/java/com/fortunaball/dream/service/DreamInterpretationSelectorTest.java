package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.dream.domain.DreamKnowledge.Interpretation;
import java.util.List;
import org.junit.jupiter.api.Test;

class DreamInterpretationSelectorTest {

    private final DreamInterpretationSelector selector = new DreamInterpretationSelector();

    private static Interpretation i(String situation, String region) {
        return new Interpretation(1, situation, situation + " 해석", "POSITIVE", "LOW", region, null);
    }

    @Test
    void 한국_해석이_있으면_한국_해석만_쓴다() {
        List<Interpretation> r = selector.select("꿈 내용", List.of(i("가", "GLOBAL"), i("나", "KR"), i("다", "GLOBAL")));
        assertEquals(1, r.size());
        assertTrue(r.get(0).isKorean());
    }

    @Test
    void 한국_해석이_없으면_해외_해석을_쓴다_기본_두_개() {
        List<Interpretation> r = selector.select("꿈 내용", List.of(i("가", "GLOBAL"), i("나", "GLOBAL"), i("다", "GLOBAL")));
        assertEquals(2, r.size());
    }

    @Test
    void 꿈에_상황_단어가_있으면_그_해석을_먼저_쓴다() {
        List<Interpretation> r = selector.select("돼지가 집에 들어옴을 봤다",
                List.of(i("내쫓음", "KR"), i("집에 들어옴", "KR"), i("사라짐", "KR")));
        assertEquals(1, r.size());
        assertEquals("집에 들어옴", r.get(0).situation());
    }

    @Test
    void 해석이_없으면_빈_목록() {
        assertTrue(selector.select("꿈", List.of()).isEmpty());
    }
}
