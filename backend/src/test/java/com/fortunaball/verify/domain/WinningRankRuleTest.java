package com.fortunaball.verify.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WinningRankRuleTest {

    // 당첨번호 1,2,3,4,5,6 / 보너스 7
    private static final List<Integer> WIN = List.of(1, 2, 3, 4, 5, 6);
    private static final int BONUS = 7;

    private MatchResult eval(Integer... issued) {
        return WinningRankRule.evaluate(List.of(issued), WIN, BONUS);
    }

    @Test
    void 여섯개_맞으면_1등() {
        MatchResult r = eval(1, 2, 3, 4, 5, 6);
        assertEquals(6, r.matchCount());
        assertEquals(1, r.rank());
    }

    @Test
    void 다섯개와_보너스_맞으면_2등() {
        MatchResult r = eval(1, 2, 3, 4, 5, 7);
        assertEquals(5, r.matchCount());
        assertTrue(r.bonusMatched());
        assertEquals(2, r.rank());
    }

    @Test
    void 다섯개만_맞으면_3등() {
        MatchResult r = eval(1, 2, 3, 4, 5, 40);
        assertEquals(5, r.matchCount());
        assertFalse(r.bonusMatched());
        assertEquals(3, r.rank());
    }

    @Test
    void 네개_맞으면_4등_보너스가_맞아도_4등() {
        assertEquals(4, eval(1, 2, 3, 4, 30, 40).rank());
        assertEquals(4, eval(1, 2, 3, 4, 7, 40).rank());
    }

    @Test
    void 세개_맞으면_5등() {
        assertEquals(5, eval(1, 2, 3, 30, 40, 41).rank());
    }

    @Test
    void 두개_이하면_낙첨_등수는_null() {
        MatchResult r = eval(1, 2, 30, 31, 40, 41);
        assertEquals(2, r.matchCount());
        assertNull(r.rank());
        assertFalse(r.isWinning());
        assertNull(eval(30, 31, 32, 33, 34, 35).rank());
    }

    @Test
    void 번호가_여섯개가_아니면_예외() {
        assertThrows(IllegalArgumentException.class,
                () -> WinningRankRule.evaluate(List.of(1, 2, 3), WIN, BONUS));
    }
}
