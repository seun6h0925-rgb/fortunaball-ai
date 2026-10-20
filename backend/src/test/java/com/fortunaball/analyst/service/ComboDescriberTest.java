package com.fortunaball.analyst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.analyst.domain.EvidenceItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComboDescriberTest {

    private static String valueOf(List<EvidenceItem> items, String label) {
        return items.stream().filter(i -> i.label().equals(label)).findFirst().orElseThrow().value();
    }

    @Test
    void 번호_구조를_사실_그대로_풀어_쓴다() {
        List<EvidenceItem> items = ComboDescriber.describe(List.of(44, 3, 14, 22, 35, 38), Map.of(3, 9, 14, 11), 100, "");

        assertEquals("3, 14, 22, 35, 38, 44", valueOf(items, "번호"));
        assertEquals("홀수 2개 · 짝수 4개", valueOf(items, "홀짝"));
        assertEquals("저(1~22) 3개 · 고(23~45) 3개", valueOf(items, "저고"));
        assertEquals("156", valueOf(items, "합계"));
        assertEquals("이웃한 숫자 쌍 0개", valueOf(items, "연속수"));
        assertEquals("1~10 1개 · 11~20 1개 · 21~30 1개 · 31~40 2개 · 41~45 1개", valueOf(items, "구간"));
        assertTrue(valueOf(items, "최근 100회 출현").startsWith("3번 9회, 14번 11회, 22번 0회"));
    }

    @Test
    void 연속수를_센다() {
        List<EvidenceItem> items = ComboDescriber.describe(List.of(1, 2, 3, 10, 20, 30), null, 0, "");
        assertEquals("이웃한 숫자 쌍 2개", valueOf(items, "연속수"));
    }

    @Test
    void 출현_횟수가_없으면_그_줄을_만들지_않고_이름_앞말을_붙인다() {
        List<EvidenceItem> items = ComboDescriber.describe(List.of(1, 2, 3, 4, 5, 6), null, 0, "게임 1 ");
        assertEquals(6, items.size());
        assertEquals("게임 1 번호", items.get(0).label());
    }
}
