package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.dream.domain.DreamKnowledge.MatchedSymbol;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class DreamSymbolMatcherTest {

    private final DreamSymbolMatcher matcher = new DreamSymbolMatcher();

    private static final List<SymbolEntry> ENTRIES = List.of(
            new SymbolEntry(1, "돼지", List.of("새끼돼지")),
            new SymbolEntry(2, "용", List.of("용꿈")),
            new SymbolEntry(3, "말", List.of()),
            new SymbolEntry(4, "물", List.of("맑은 물")),
            new SymbolEntry(5, "새", List.of()),
            new SymbolEntry(6, "이빨", List.of("이가 빠짐")));

    @Test
    void 두_글자_이상_상징은_글_안에_들어_있으면_찾는다() {
        List<MatchedSymbol> r = matcher.match("어젯밤 새끼돼지가 집에 들어오는 꿈을 꿨어요", ENTRIES);
        assertEquals(1, r.size());
        assertEquals("돼지", r.get(0).keyword());
    }

    @Test
    void 한_글자_상징은_조사가_붙은_독립된_말일_때만_찾는다() {
        assertEquals(1, matcher.match("하늘을 나는 용이 나왔어요", ENTRIES).size());
        // "말하다", "새로운", "물건"처럼 다른 낱말의 일부는 찾지 않는다
        assertTrue(matcher.match("새로운 사람과 말했는데 물건을 받았어요", ENTRIES).isEmpty());
    }

    @Test
    void 앞글자가_한글이면_한_글자_상징으로_보지_않는다() {
        assertTrue(matcher.match("구렁이용사가 나왔어요 그리고 조용히 갔어요", ENTRIES).stream()
                .noneMatch(s -> s.keyword().equals("용")));
    }

    @Test
    void 꿈에_먼저_나온_순서로_돌려주고_같은_상징은_한_번만_센다() {
        List<MatchedSymbol> r = matcher.match("맑은 물에서 돼지를 보았고 또 돼지가 왔다", ENTRIES);
        assertEquals(List.of("물", "돼지"), r.stream().map(MatchedSymbol::keyword).toList());
    }

    @Test
    void 최대_세_개까지만_쓴다() {
        List<MatchedSymbol> r = matcher.match("돼지와 용꿈 그리고 이가 빠짐 그리고 맑은 물", ENTRIES);
        assertEquals(DreamSymbolMatcher.MAX_SYMBOLS, r.size());
    }

    @Test
    void 빈_글이나_널은_빈_목록() {
        assertTrue(matcher.match(null, ENTRIES).isEmpty());
        assertTrue(matcher.match("   ", ENTRIES).isEmpty());
    }
}
