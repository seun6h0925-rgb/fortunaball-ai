package com.fortunaball.generate.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComboTest {

    @Test
    void 순서가_섞여도_오름차순으로_담고_두자리_키를_만든다() {
        Combo c = new Combo(List.of(41, 3, 27, 8, 33, 18));
        assertEquals(List.of(3, 8, 18, 27, 33, 41), c.numbers());
        assertEquals("03-08-18-27-33-41", c.key());
        assertEquals(17, c.key().length());
    }

    @Test
    void 같은_번호_조합은_서로_같다() {
        assertEquals(new Combo(List.of(1, 2, 3, 4, 5, 6)), new Combo(List.of(6, 5, 4, 3, 2, 1)));
    }

    @Test
    void 잘못된_번호는_만들_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> new Combo(List.of(1, 2, 3, 4, 5)));          // 5개
        assertThrows(IllegalArgumentException.class, () -> new Combo(List.of(1, 1, 2, 3, 4, 5)));       // 중복
        assertThrows(IllegalArgumentException.class, () -> new Combo(List.of(0, 1, 2, 3, 4, 5)));       // 범위
        assertThrows(IllegalArgumentException.class, () -> new Combo(List.of(1, 2, 3, 4, 5, 46)));      // 범위
    }
}
