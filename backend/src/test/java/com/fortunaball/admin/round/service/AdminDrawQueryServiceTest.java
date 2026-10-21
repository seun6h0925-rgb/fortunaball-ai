package com.fortunaball.admin.round.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.admin.round.dto.AdminDrawItem;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.stat.domain.SnapshotStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminDrawQueryServiceTest {

    private static DrawRound round(int no) {
        return new DrawRound(no, LocalDate.of(2026, 10, 17), List.of(1, 2, 3, 4, 5, 6), 7, "TEST");
    }

    @Test
    void 범위의_모든_회차가_최신순으로_나오고_없는_회차는_누락으로_표시한다() {
        List<AdminDrawItem> items = AdminDrawQueryService.build(1100, 1102,
                Map.of(1100, round(1100), 1102, round(1102)),
                Map.of(1100, 5, 1102, 3),
                Map.of(1102, SnapshotStatus.CONFIRMED));

        assertEquals(List.of(1102, 1101, 1100), items.stream().map(AdminDrawItem::roundNo).toList());

        AdminDrawItem latest = items.get(0);
        assertFalse(latest.missing());
        assertEquals("INCOMPLETE", latest.prizeStatus());
        assertEquals(3, latest.prizeRanksCollected());
        assertEquals("CONFIRMED", latest.statStatus());
        assertEquals("확정", latest.statLabel());

        AdminDrawItem missing = items.get(1);
        assertTrue(missing.missing());
        assertNull(missing.drawDate());
        assertNull(missing.prizeStatus());

        assertEquals("COMPLETE", items.get(2).prizeStatus());
        assertNull(items.get(2).statStatus());
    }
}
