package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.dream.domain.DreamKnowledge.PickedNumber;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolNumber;
import com.fortunaball.dream.domain.DreamNumberOrigin;
import java.util.List;
import org.junit.jupiter.api.Test;

class DreamNumberPickerTest {

    private final DreamNumberPicker picker = new DreamNumberPicker();

    private static SymbolNumber n(long sym, int number, DreamNumberOrigin o) {
        return new SymbolNumber(sym, number, o);
    }

    private static List<Integer> numbers(List<PickedNumber> p) {
        return p.stream().map(PickedNumber::number).toList();
    }

    @Test
    void 꿈_속_숫자를_가장_먼저_쓴다() {
        List<PickedNumber> r = picker.pick(List.of(29),
                List.of(List.of(n(1, 3, DreamNumberOrigin.SERVICE))), false, List.of());
        assertEquals(List.of(3, 29), numbers(r));
        assertEquals(DreamNumberOrigin.DREAM_TEXT, r.get(1).origin());
    }

    @Test
    void 상징_번호는_상징_순서대로_한_개씩_번갈아_고른다() {
        List<SymbolNumber> a = List.of(n(1, 1, DreamNumberOrigin.SMORFIA), n(1, 2, DreamNumberOrigin.SERVICE),
                n(1, 3, DreamNumberOrigin.SERVICE), n(1, 4, DreamNumberOrigin.SERVICE));
        List<SymbolNumber> b = List.of(n(2, 11, DreamNumberOrigin.CHARADA), n(2, 12, DreamNumberOrigin.SERVICE),
                n(2, 13, DreamNumberOrigin.SERVICE), n(2, 14, DreamNumberOrigin.SERVICE));
        List<PickedNumber> r = picker.pick(List.of(), List.of(a, b), false, List.of());
        // 1, 11, 2, 12, 3, 13 이 뽑히고 정렬되어 나온다
        assertEquals(List.of(1, 2, 3, 11, 12, 13), numbers(r));
        assertTrue(picker.isComplete(r));
    }

    @Test
    void 번호가_모자라면_동의한_경우에만_통계로_보완한다() {
        List<List<SymbolNumber>> one = List.of(List.of(n(1, 3, DreamNumberOrigin.SERVICE), n(1, 8, DreamNumberOrigin.SERVICE)));
        List<Integer> stat = List.of(3, 10, 20, 30, 40, 5);

        List<PickedNumber> without = picker.pick(List.of(), one, false, stat);
        assertEquals(List.of(3, 8), numbers(without));
        assertFalse(picker.isComplete(without));

        List<PickedNumber> with = picker.pick(List.of(), one, true, stat);
        assertEquals(6, with.size());
        assertEquals(List.of(3, 8, 10, 20, 30, 40), numbers(with));   // 이미 쓴 3 은 건너뛴다
        assertEquals(4, with.stream().filter(p -> p.origin() == DreamNumberOrigin.STAT).count());
        assertFalse(picker.isComplete(with));                          // 통계로 채웠으므로 FULL 아님
    }

    @Test
    void 같은_번호는_한_번만_쓴다() {
        List<PickedNumber> r = picker.pick(List.of(3),
                List.of(List.of(n(1, 3, DreamNumberOrigin.SERVICE), n(1, 5, DreamNumberOrigin.SERVICE))), false, List.of());
        assertEquals(List.of(3, 5), numbers(r));
    }

    @Test
    void 같은_입력이면_항상_같은_결과() {
        List<List<SymbolNumber>> in = List.of(List.of(n(1, 9, DreamNumberOrigin.SERVICE), n(1, 4, DreamNumberOrigin.SERVICE)));
        assertEquals(picker.pick(List.of(), in, false, List.of()), picker.pick(List.of(), in, false, List.of()));
    }
}
