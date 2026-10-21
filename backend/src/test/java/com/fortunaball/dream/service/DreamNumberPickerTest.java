package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void 꿈_속_숫자도_함께_모은다() {
        List<PickedNumber> r = picker.pick(List.of(29),
                List.of(List.of(n(1, 3, DreamNumberOrigin.SERVICE))));
        assertEquals(List.of(3, 29), numbers(r));
        assertEquals(DreamNumberOrigin.DREAM_TEXT, r.get(1).origin());
        assertEquals(DreamNumberOrigin.SERVICE, r.get(0).origin());
    }

    @Test
    void 상징_번호는_개수_제한_없이_모두_모은다() {
        List<SymbolNumber> a = List.of(n(1, 1, DreamNumberOrigin.SMORFIA), n(1, 2, DreamNumberOrigin.SERVICE),
                n(1, 3, DreamNumberOrigin.SERVICE), n(1, 4, DreamNumberOrigin.SERVICE));
        List<SymbolNumber> b = List.of(n(2, 11, DreamNumberOrigin.CHARADA), n(2, 12, DreamNumberOrigin.SERVICE),
                n(2, 13, DreamNumberOrigin.SERVICE), n(2, 14, DreamNumberOrigin.SERVICE));
        List<PickedNumber> r = picker.pick(List.of(), List.of(a, b));
        // 6개로 자르지 않는다. 6개를 넘으면 DreamService 가 사용자에게 어떻게 할지 묻는다.
        assertEquals(List.of(1, 2, 3, 4, 11, 12, 13, 14), numbers(r));
    }

    @Test
    void 같은_번호는_한_번만_쓰고_꿈_속_숫자의_출처가_앞선다() {
        List<PickedNumber> r = picker.pick(List.of(3),
                List.of(List.of(n(1, 3, DreamNumberOrigin.SERVICE), n(1, 5, DreamNumberOrigin.SERVICE))));
        assertEquals(List.of(3, 5), numbers(r));
        assertEquals(DreamNumberOrigin.DREAM_TEXT, r.get(0).origin());
    }

    @Test
    void 번호가_없으면_빈_목록() {
        assertEquals(List.of(), picker.pick(List.of(), List.of()));
    }

    @Test
    void 같은_입력이면_항상_같은_결과() {
        List<List<SymbolNumber>> in = List.of(List.of(n(1, 9, DreamNumberOrigin.SERVICE), n(1, 4, DreamNumberOrigin.SERVICE)));
        assertEquals(picker.pick(List.of(), in), picker.pick(List.of(), in));
    }
}
