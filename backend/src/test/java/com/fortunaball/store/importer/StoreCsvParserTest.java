package com.fortunaball.store.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 판매점 CSV 읽기 규칙 확인 (파일 없이 문자열로 확인한다). */
class StoreCsvParserTest {

    private static final String HEADER =
            "draw,rank,shop_id,shop_name,status,region,road_addr,road_addr_short,detail_addr,jibun_addr,full_addr,tel,auto_manual,lat,lon\n";

    @Test
    void BOM과_공백을_정리해서_읽는다() {
        String csv = "﻿" + HEADER
                + "262,2,11101278,샌드빈(SANDBEAN),정상,서울,서울 중구 퇴계로56길 57,,1층,서울 중구 장충동2가  9-2,서울 중구 퇴계로56길 57 1층,02-2277-8956,,37.561344,127.00559\n"
                + "262,2,11190127,노다지,폐점, ,   ,,,서울 중랑구 면목동  74-35,   ,02-434-3020,,37.589488,127.091767\n";

        List<StoreCsvRow> rows = StoreCsvParser.parseText(csv);

        assertEquals(2, rows.size());
        StoreCsvRow open = rows.get(0);
        assertEquals(262, open.round());
        assertEquals(2, open.rank());
        assertEquals("11101278", open.shopCode());
        assertEquals("샌드빈(SANDBEAN)", open.storeName());
        assertFalse(open.closed());
        assertEquals("서울", open.region());
        assertEquals("서울 중구 퇴계로56길 57", open.roadAddress());
        assertNull(open.winType());
        assertEquals(new BigDecimal("37.561344"), open.latitude());

        StoreCsvRow closed = rows.get(1);
        assertTrue(closed.closed());
        assertEquals("", closed.region());
        assertEquals("", closed.roadAddress());
    }

    @Test
    void 당첨_구분_한글을_DB_값으로_바꾼다() {
        String csv = HEADER
                + "300,1,1,가게,정상,서울,서울 중구 a길 1,,,,,,자동,37.5,127.0\n"
                + "300,1,2,가게2,정상,서울,서울 중구 b길 1,,,,,,수동,37.5,127.0\n"
                + "300,1,3,가게3,정상,서울,서울 중구 c길 1,,,,,,반자동,37.5,127.0\n";

        List<StoreCsvRow> rows = StoreCsvParser.parseText(csv);

        assertEquals("AUTO", rows.get(0).winType());
        assertEquals("MANUAL", rows.get(1).winType());
        assertEquals("SEMI_AUTO", rows.get(2).winType());
    }

    @Test
    void 따옴표_안의_쉼표는_칸을_나누지_않는다() {
        String csv = HEADER + "300,2,1,\"가게, 본점\",정상,서울,서울 중구 a길 1,,,,,,,37.5,127.0\n";

        List<StoreCsvRow> rows = StoreCsvParser.parseText(csv);

        assertEquals("가게, 본점", rows.get(0).storeName());
    }

    @Test
    void 필요한_열이_없으면_바로_오류() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> StoreCsvParser.parseText("draw,rank\n1,1\n"));
        assertTrue(e.getMessage().contains("필요한 열"));
    }

    @Test
    void 잘못된_줄은_몇_번째_줄인지_알려_준다() {
        String csv = HEADER + "300,3,1,가게,정상,서울,서울 중구 a길 1,,,,,,,37.5,127.0\n";

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> StoreCsvParser.parseText(csv));

        assertTrue(e.getMessage().contains("2번째 줄"), e.getMessage());
    }

    @Test
    void 알_수_없는_당첨_구분은_오류() {
        String csv = HEADER + "300,1,1,가게,정상,서울,서울 중구 a길 1,,,,,,복권,37.5,127.0\n";

        assertThrows(IllegalArgumentException.class, () -> StoreCsvParser.parseText(csv));
    }
}
