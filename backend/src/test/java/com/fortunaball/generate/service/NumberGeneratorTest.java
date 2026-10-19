package com.fortunaball.generate.service;

import com.fortunaball.generate.domain.Combo;
import com.fortunaball.generate.domain.PatternUnavailableException;
import com.fortunaball.generate.dto.PatternCondition;
import com.fortunaball.stat.domain.PatternCode;
import com.fortunaball.stat.dto.DrawNumbers;
import com.fortunaball.stat.service.PatternCalculator;
import com.fortunaball.stat.service.PatternRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberGeneratorTest {

    private final NumberGenerator generator = new NumberGenerator();
    private final PatternCalculator calculator = new PatternCalculator();
    private List<DrawNumbers> history;
    private DrawProfile profile;

    @BeforeEach
    void setUp() {
        history = ComboScorerTest.randomHistory(120, 11);
        profile = DrawProfile.from(history.subList(20, 120));
    }

    private PatternCondition condition(PatternCode code) {
        Set<Integer> hot = calculator.hotNumbers(history.subList(20, 120));
        Set<Integer> sleep = calculator.sleepNumbers(history);
        return new PatternCondition(code, hot, sleep);
    }

    private RandomGenerator rng(long seed) {
        return new java.util.Random(seed);
    }

    private boolean satisfies(PatternCode code, Combo c, PatternCondition cond) {
        List<Integer> n = c.numbers();
        switch (code) {
            case HOT_NUMBER:
                return PatternRule.isHotNumber(n, cond.hotNumbers());
            case WINTER_SLEEP:
                return n.stream().anyMatch(cond.sleepNumbers()::contains);
            case BALANCE_MIX:
                return PatternRule.isBalanceMix(n);
            case HIGH_LOW_MIX:
                return PatternRule.isHighLowMix(n);
            case CENTER_ZONE:
                return PatternRule.isCenterZone(n);
            case ALL_SECTION_SPREAD:
                return PatternRule.isAllSectionSpread(n);
            default:
                return false;
        }
    }

    @Test
    void 모든_패턴이_조건을_만족하는_서로_다른_조합을_만든다() {
        for (PatternCode code : PatternCode.values()) {
            PatternCondition cond = condition(code);
            List<Combo> games = generator.generate(cond, 30, profile, Set.of(), rng(1));
            assertEquals(30, games.size(), code.name());
            assertEquals(30, games.stream().map(Combo::key).distinct().count(), code + ": 같은 조합이 나옴");
            for (Combo c : games) {
                assertTrue(satisfies(code, c, cond), code + " 조건 위반: " + c.key());
            }
        }
    }

    @Test
    void 패턴_없이도_만든다() {
        List<Combo> games = generator.generate(PatternCondition.none(), 50, profile, Set.of(), rng(2));
        assertEquals(50, games.size());
        assertEquals(50, games.stream().map(Combo::key).distinct().count());
    }

    @Test
    void 제외한_조합은_나오지_않는다() {
        PatternCondition cond = condition(PatternCode.BALANCE_MIX);
        List<Combo> first = generator.generate(cond, 40, profile, Set.of(), rng(3));
        Set<String> excluded = new HashSet<>();
        first.forEach(c -> excluded.add(c.key()));

        List<Combo> second = generator.generate(cond, 40, profile, excluded, rng(3));   // 같은 난수여도 제외한 것은 안 나온다
        for (Combo c : second) {
            assertFalse(excluded.contains(c.key()), "제외한 조합이 나옴: " + c.key());
        }
    }

    @Test
    void 같은_씨앗이면_같은_결과다() {
        PatternCondition cond = condition(PatternCode.HOT_NUMBER);
        List<Combo> a = generator.generate(cond, 10, profile, Set.of(), rng(99));
        List<Combo> b = generator.generate(cond, 10, profile, Set.of(), rng(99));
        assertEquals(a, b);
    }

    @Test
    void 쏠림_조합은_거의_나오지_않는다() {
        List<Combo> games = generator.generate(condition(PatternCode.BALANCE_MIX), 50, profile, Set.of(), rng(5));
        long skew = games.stream().filter(c -> ComboScorer.penalty(c) > 0).count();
        assertEquals(0, skew, "쏠림 감점 대상이 섞임");
    }

    @Test
    void 핫넘버가_부족하거나_겨울잠_번호가_없으면_패턴을_완화하지_않고_예외를_던진다() {
        PatternCondition noHot = new PatternCondition(PatternCode.HOT_NUMBER, Set.of(1, 2), Set.of());
        assertThrows(PatternUnavailableException.class,
                () -> generator.generate(noHot, 1, profile, Set.of(), rng(1)));

        PatternCondition noSleep = new PatternCondition(PatternCode.WINTER_SLEEP, Set.of(), Set.of());
        assertThrows(PatternUnavailableException.class,
                () -> generator.generate(noSleep, 1, profile, Set.of(), rng(1)));
    }

    @Test
    void 게임_수가_1보다_작으면_입력_오류() {
        assertThrows(IllegalArgumentException.class,
                () -> generator.generate(PatternCondition.none(), 0, profile, Set.of(), rng(1)));
    }
}
