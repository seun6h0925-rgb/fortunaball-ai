package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DreamNumberExtractorTest {

    private final DreamNumberExtractor extractor = new DreamNumberExtractor();

    @Test
    void 꿈에_적힌_1에서_45_숫자를_순서대로_뽑는다() {
        assertEquals(List.of(7, 23, 44), extractor.extract("7번 버스를 타고 23층에서 44마리를 봤어요"));
    }

    @Test
    void 범위_밖_숫자와_중복은_버린다() {
        assertEquals(List.of(3, 45), extractor.extract("0도 46도 99도 3이 두 번 3 그리고 45"));
    }

    @Test
    void 세_자리_이상_숫자의_일부는_뽑지_않는다() {
        assertTrue(extractor.extract("2026년 100명이 왔어요").isEmpty());
    }

    @Test
    void 최대_여섯_개까지만() {
        assertEquals(6, extractor.extract("1 2 3 4 5 6 7 8 9").size());
    }

    @Test
    void 숫자가_없으면_빈_목록() {
        assertTrue(extractor.extract("숫자 없는 꿈").isEmpty());
        assertTrue(extractor.extract(null).isEmpty());
    }
}
