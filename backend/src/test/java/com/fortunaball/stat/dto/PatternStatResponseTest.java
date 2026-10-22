package com.fortunaball.stat.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 패턴 카드에 구성 예시 번호를 붙이는 동작 (API-44) */
class PatternStatResponseTest {

    private PatternStatResponse base() {
        return new PatternStatResponse(1247, 1246, "PREPARING", "사전 분석", 100, LocalDateTime.of(2026, 10, 22, 10, 0),
                List.of(new PatternCardResponse("HOT_NUMBER", "핫넘버", "설명", 100, 62),
                        new PatternCardResponse("WINTER_SLEEP", "겨울잠 번호", "설명", 100, 48)));
    }

    @Test
    void 예시가_있는_패턴에만_번호가_붙고_나머지는_null이다() {
        PatternStatResponse r = base().withExamples(Map.of("HOT_NUMBER", List.of(3, 14, 22, 35, 38, 41)));

        assertThat(r.patterns().get(0).exampleNumbers()).containsExactly(3, 14, 22, 35, 38, 41);
        assertThat(r.patterns().get(1).exampleNumbers()).isNull();
    }

    @Test
    void 예시를_붙여도_기존_값은_그대로다() {
        PatternStatResponse r = base().withExamples(Map.of());

        assertThat(r.targetRound()).isEqualTo(1247);
        assertThat(r.basisRound()).isEqualTo(1246);
        assertThat(r.patterns().get(0).hitCount()).isEqualTo(62);
        assertThat(r.patterns().get(1).name()).isEqualTo("겨울잠 번호");
    }
}
