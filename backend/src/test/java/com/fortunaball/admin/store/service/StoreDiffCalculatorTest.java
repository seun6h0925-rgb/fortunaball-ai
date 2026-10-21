package com.fortunaball.admin.store.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.admin.store.service.StoreDiffCalculator.Diff;
import com.fortunaball.admin.store.service.StoreDiffCalculator.ExistingStore;
import com.fortunaball.store.importer.StoreAddressParser;
import com.fortunaball.store.importer.StoreCsvRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreDiffCalculatorTest {

    private static StoreCsvRow row(int round, int rank, String code, String name, boolean closed, String lat, String winType) {
        return new StoreCsvRow(round, rank, code, name, closed, "서울", "서울 중구 퇴계로 1", "서울 중구 장충동 1",
                winType, new BigDecimal(lat), new BigDecimal("127.000000"));
    }

    private static ExistingStore existing(long id, String code, String name, boolean closed, String lat) {
        StoreAddressParser.Region region = StoreAddressParser.parse("서울", "서울 중구 퇴계로 1", "서울 중구 장충동 1");
        return new ExistingStore(id, name, "서울 중구 퇴계로 1", "서울 중구 장충동 1", region.sido(), region.sigungu(),
                new BigDecimal(lat), new BigDecimal("127.0000000"), closed);
    }

    @Test
    void DB에_없는_판매점은_신규로_센다() {
        List<StoreCsvRow> rows = List.of(row(1246, 1, "A", "가게A", false, "37.5", "AUTO"));
        Diff d = StoreDiffCalculator.compute(rows, Map.of(), Map.of());
        assertEquals(1, d.summary().newStores());
        assertEquals(0, d.summary().changedStores());
        assertEquals("NEW", d.items().get(0).kind());
        assertEquals("가게A", d.items().get(0).after().get("판매점명"));
        assertTrue(d.items().get(0).before().isEmpty());
    }

    @Test
    void 좌표_자릿수만_다르면_변경으로_보지_않는다() {
        List<StoreCsvRow> rows = List.of(row(1246, 1, "A", "가게A", false, "37.500000", "AUTO"));
        Map<String, ExistingStore> ex = Map.of("A", existing(1, "A", "가게A", false, "37.5000000"));
        Diff d = StoreDiffCalculator.compute(rows, ex, Map.of());
        assertEquals(0, d.summary().newStores());
        assertEquals(0, d.summary().changedStores());
        assertEquals(1, d.summary().unchangedStores());
        assertTrue(d.items().isEmpty());
    }

    @Test
    void 이름과_폐점이_다르면_바뀐_항목만_전후로_담는다() {
        List<StoreCsvRow> rows = List.of(row(1246, 1, "A", "새이름", true, "37.5", "AUTO"));
        Map<String, ExistingStore> ex = Map.of("A", existing(1, "A", "옛이름", false, "37.5"));
        Diff d = StoreDiffCalculator.compute(rows, ex, Map.of());
        assertEquals(1, d.summary().changedStores());
        var item = d.items().get(0);
        assertEquals("CHANGED", item.kind());
        assertEquals(Map.of("판매점명", "옛이름", "폐점여부", "false"), item.before());
        assertEquals(Map.of("판매점명", "새이름", "폐점여부", "true"), item.after());
    }

    @Test
    void 같은_판매점이_여러_줄이어도_판매점은_한_번만_센다() {
        List<StoreCsvRow> rows = List.of(row(1246, 1, "A", "가게A", false, "37.5", "AUTO"),
                row(1245, 2, "A", "가게A", false, "37.5", null));
        Diff d = StoreDiffCalculator.compute(rows, Map.of(), Map.of());
        assertEquals(1, d.summary().newStores());
    }

    @Test
    void 당첨_이력은_개수로_견준다() {
        // 파일: A 가 1246회 1등 자동을 2번, B 가 1245회 2등 1번
        List<StoreCsvRow> rows = List.of(row(1246, 1, "A", "가게A", false, "37.5", "AUTO"),
                row(1246, 1, "A", "가게A", false, "37.5", "AUTO"), row(1245, 2, "B", "가게B", false, "37.5", null));
        // DB: A 1246회 1등 자동 1번(→ 1건 새로 생김), C 1245회 2등 1번(→ 파일에 없어서 지워짐)
        Map<String, Integer> db = Map.of(
                StoreDiffCalculator.historyKey("A", 1246, 1, "AUTO"), 1,
                StoreDiffCalculator.historyKey("C", 1245, 2, null), 1);
        Diff d = StoreDiffCalculator.compute(rows, Map.of(), db);
        assertEquals(2, d.summary().newHistories());       // A 1건 + B 1건
        assertEquals(1, d.summary().removedHistories());   // C 1건
        assertEquals(0, d.summary().deletedStores());
    }
}
