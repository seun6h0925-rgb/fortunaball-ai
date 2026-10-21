package com.fortunaball.admin.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AdminQueryTest {

    @Test
    void 쪽_번호와_크기를_보정한다() {
        assertEquals(0, AdminQuery.page(null));
        assertEquals(0, AdminQuery.page(-1));
        assertEquals(3, AdminQuery.page(3));
        assertEquals(20, AdminQuery.size(null));
        assertEquals(20, AdminQuery.size(0));
        assertEquals(100, AdminQuery.size(1000));
        assertEquals(50, AdminQuery.size(50));
    }

    @Test
    void 기간은_양끝_날짜를_포함하고_끝은_다음날_0시까지다() {
        AdminQuery.Range range = AdminQuery.range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), 30);

        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(), range.start());
        assertEquals(LocalDate.of(2026, 10, 4).atStartOfDay(), range.endExclusive());
    }

    @Test
    void 시작만_안_보내면_끝_날짜에서_기본_일수만큼_거슬러_올라간다() {
        AdminQuery.Range range = AdminQuery.range(null, LocalDate.of(2026, 10, 10), 7);

        assertEquals(LocalDate.of(2026, 10, 4).atStartOfDay(), range.start());
    }

    @Test
    void 시작이_끝보다_늦으면_오류다() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> AdminQuery.range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1), 7));

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }
}
