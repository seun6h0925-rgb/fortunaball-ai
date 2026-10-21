package com.fortunaball.store.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 판매점별 당첨 횟수 집계 규칙 확인. */
class StoreWinSummaryCalculatorTest {

    private static StoreCsvRow row(int round, int rank, String shop, String winType) {
        return new StoreCsvRow(round, rank, shop, "가게" + shop, false, "서울", "서울 중구 a길 1", "",
                winType, new BigDecimal("37.5"), new BigDecimal("127.0"));
    }

    @Test
    void 일등은_구분별로_이등은_합쳐서_센다() {
        List<StoreCsvRow> rows = List.of(
                row(300, 1, "A", "AUTO"),
                row(310, 1, "A", "AUTO"),
                row(320, 1, "A", "MANUAL"),
                row(330, 1, "A", "SEMI_AUTO"),
                row(340, 2, "A", null),
                row(341, 2, "A", "AUTO"),
                row(300, 2, "B", null));

        Map<String, StoreWinSummaryCalculator.Summary> result = StoreWinSummaryCalculator.calculate(rows);

        assertEquals(new StoreWinSummaryCalculator.Summary(2, 1, 1, 2), result.get("A"));
        assertEquals(new StoreWinSummaryCalculator.Summary(0, 0, 0, 1), result.get("B"));
    }

    @Test
    void 같은_회차에_같은_판매점_줄이_여러_번이면_그만큼_센다() {
        List<StoreCsvRow> rows = List.of(row(300, 2, "A", null), row(300, 2, "A", null), row(300, 2, "A", null));

        Map<String, StoreWinSummaryCalculator.Summary> result = StoreWinSummaryCalculator.calculate(rows);

        assertEquals(3, result.get("A").second());
    }

    @Test
    void 일등인데_구분이_없으면_오류() {
        List<StoreCsvRow> rows = List.of(row(300, 1, "A", null));

        assertThrows(IllegalArgumentException.class, () -> StoreWinSummaryCalculator.calculate(rows));
    }
}
