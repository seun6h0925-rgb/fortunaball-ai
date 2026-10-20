package com.fortunaball.generate.service;

import com.fortunaball.generate.domain.Combo;
import com.fortunaball.generate.dto.PatternCondition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AI 애널리스트용: 포함·제외 번호가 어떤 경우에도 지켜지는지 확인한다. */
class NumberGeneratorConstraintTest {

    private final NumberGenerator generator = new NumberGenerator();
    private final DrawProfile profile = DrawProfile.from(ComboScorerTest.randomHistory(100, 21));

    @Test
    void 포함_번호는_모든_게임에_들어간다() {
        PatternCondition cond = PatternCondition.withNumbers(Set.of(7, 13), Set.of());
        List<Combo> games = generator.generate(cond, 20, profile, Set.of(), new Random(1));
        assertEquals(20, games.size());
        games.forEach(g -> assertTrue(g.numbers().containsAll(List.of(7, 13)), g.toString()));
    }

    @Test
    void 제외_번호는_어느_게임에도_나오지_않는다() {
        Set<Integer> exclude = Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Combo> games = generator.generate(PatternCondition.withNumbers(Set.of(), exclude), 30, profile, Set.of(), new Random(2));
        games.forEach(g -> g.numbers().forEach(n -> assertFalse(exclude.contains(n), g.toString())));
    }

    @Test
    void 포함과_제외를_함께_지킨다() {
        List<Combo> games = generator.generate(PatternCondition.withNumbers(Set.of(44), Set.of(45, 1)), 20, profile, Set.of(), new Random(3));
        games.forEach(g -> {
            assertTrue(g.numbers().contains(44));
            assertFalse(g.numbers().contains(45) || g.numbers().contains(1));
        });
    }

    @Test
    void 포함_번호가_6개면_그_조합_하나만_나온다() {
        List<Combo> games = generator.generate(PatternCondition.withNumbers(Set.of(1, 2, 3, 4, 5, 6), Set.of()), 1, profile, Set.of(), new Random(4));
        assertEquals(List.of(1, 2, 3, 4, 5, 6), games.get(0).numbers());
    }

    @Test
    void 만들_수_없는_조건이면_예외를_던진다() {
        // 포함이 7개: 조합을 만들 수 없다
        assertThrows(RuntimeException.class, () ->
                generator.generate(PatternCondition.withNumbers(Set.of(1, 2, 3, 4, 5, 6, 7), Set.of()), 1, profile, Set.of(), new Random(5)));
    }

    @Test
    void 기존_3개_값_생성자는_포함_제외가_비어_있다() {
        PatternCondition cond = new PatternCondition(null, Set.of(), Set.of());
        assertTrue(cond.includeNumbers().isEmpty());
        assertTrue(cond.excludeNumbers().isEmpty());
    }
}
