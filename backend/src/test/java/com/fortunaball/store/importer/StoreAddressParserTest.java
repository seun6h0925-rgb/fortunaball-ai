package com.fortunaball.store.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** 시도·시군구 뽑기 규칙 확인. */
class StoreAddressParserTest {

    @Test
    void 도로명_주소의_둘째_단어가_구면_시군구로_쓴다() {
        StoreAddressParser.Region r = StoreAddressParser.parse("서울", "서울 중구 퇴계로56길 57", "");

        assertEquals("서울", r.sido());
        assertEquals("중구", r.sigungu());
    }

    @Test
    void 구가_있는_시는_시를_시군구로_쓴다() {
        StoreAddressParser.Region r = StoreAddressParser.parse("경기", "경기 수원시 영통구 광교로 1", "");

        assertEquals("수원시", r.sigungu());
    }

    @Test
    void 세종처럼_시군구가_없으면_null() {
        StoreAddressParser.Region r = StoreAddressParser.parse("세종", "세종   조치원로 9-1", "");

        assertEquals("세종", r.sido());
        assertNull(r.sigungu());
    }

    @Test
    void 전남광주는_자료의_이름을_그대로_쓴다() {
        StoreAddressParser.Region r = StoreAddressParser.parse("전남광주", "전남광주 광양시 인덕로 1082", "");

        assertEquals("전남광주", r.sido());
        assertEquals("광양시", r.sigungu());
    }

    @Test
    void 도로명이_없으면_지번_주소로_뽑고_지역이_비면_첫_단어를_시도로_쓴다() {
        StoreAddressParser.Region r = StoreAddressParser.parse(" ", "   ", "서울 중랑구 면목동  74-35");

        assertEquals("서울", r.sido());
        assertEquals("중랑구", r.sigungu());
    }

    @Test
    void 주소가_모두_비면_둘_다_null() {
        StoreAddressParser.Region r = StoreAddressParser.parse("", "", "");

        assertNull(r.sido());
        assertNull(r.sigungu());
    }
}
