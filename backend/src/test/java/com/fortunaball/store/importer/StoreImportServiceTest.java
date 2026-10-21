package com.fortunaball.store.importer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** 가져오기 전 확인 규칙 (DB 없이 JdbcTemplate 을 가짜로 바꿔서 확인한다). */
class StoreImportServiceTest {

    @Test
    void draw_round_에_없는_회차가_있으면_저장_전에_어느_회차인지_알려_주고_멈춘다() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);   // 회차 조회는 기본값(빈 목록)을 돌려준다 → 모든 회차가 "없음"
        StoreImportService service = new StoreImportService(jdbc);
        List<StoreCsvRow> rows = List.of(new StoreCsvRow(300, 1, "A", "가게", false, "서울", "서울 중구 a길 1", "",
                "AUTO", new BigDecimal("37.5"), new BigDecimal("127.0")));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.importRows(rows));

        assertTrue(e.getMessage().contains("300"), e.getMessage());
    }
}
