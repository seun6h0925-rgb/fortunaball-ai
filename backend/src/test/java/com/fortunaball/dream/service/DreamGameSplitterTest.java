package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DreamGameSplitterTest {

    @Test
    void 번호_9개를_3게임에_돌아가며_한_장씩_나눠_준다() {
        // 통계로 많이 나온 순 a~i = 1~9
        List<Set<Integer>> games = DreamGameSplitter.split(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), 3, 6);

        assertEquals(List.of(Set.of(1, 4, 7), Set.of(2, 5, 8), Set.of(3, 6, 9)), games);
    }

    @Test
    void 번호가_게임보다_적으면_처음부터_다시_돌아가며_모든_게임에_꿈_번호가_들어간다() {
        List<Set<Integer>> games = DreamGameSplitter.split(List.of(10, 20), 3, 6);

        assertEquals(List.of(Set.of(10), Set.of(20), Set.of(10)), games);
    }

    @Test
    void 한_게임에_6개를_넘게_담지_않는다() {
        List<Set<Integer>> one = DreamGameSplitter.split(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 1, 6);
        assertEquals(Set.of(1, 2, 3, 4, 5, 6), one.get(0));

        List<Set<Integer>> two = DreamGameSplitter.split(List.of(1, 2, 3, 4, 5, 6, 7), 2, 6);
        assertEquals(Set.of(1, 3, 5, 7), two.get(0));
        assertEquals(Set.of(2, 4, 6), two.get(1));
        two.forEach(g -> assertTrue(g.size() <= 6));
    }

    @Test
    void 번호가_없으면_빈_게임들이_나온다() {
        List<Set<Integer>> games = DreamGameSplitter.split(List.of(), 2, 6);

        assertEquals(2, games.size());
        games.forEach(g -> assertTrue(g.isEmpty()));
    }
}
