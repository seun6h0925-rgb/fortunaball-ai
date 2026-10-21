package com.fortunaball.store.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 루키 명당 판정: 최근 1년 1등 1회 이상 또는 2등 5회 이상. */
class RookieRuleTest {

    private final RookieRule rule = new RookieRule(1, 5);

    @Test
    void 일등_한_번이면_루키() {
        assertTrue(rule.isRookie(1, 0));
    }

    @Test
    void 이등_다섯_번이면_루키() {
        assertTrue(rule.isRookie(0, 5));
    }

    @Test
    void 이등_네_번까지는_루키가_아니다() {
        assertFalse(rule.isRookie(0, 4));
    }

    @Test
    void 최근_당첨이_없으면_루키가_아니다() {
        assertFalse(rule.isRookie(0, 0));
    }

    @Test
    void 두_조건_중_하나만_맞아도_된다() {
        assertTrue(rule.isRookie(3, 0));
        assertTrue(rule.isRookie(0, 9));
    }
}
