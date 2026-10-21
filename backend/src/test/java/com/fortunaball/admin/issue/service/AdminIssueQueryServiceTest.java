package com.fortunaball.admin.issue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.admin.issue.dto.UnverifiedIssueItem;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdminIssueQueryServiceTest {

    @Test
    void 게스트_식별값은_앞_6글자만_보여_준다() {
        assertEquals("guest-…", AdminIssueQueryService.maskGuestToken("guest-dddddddddddddddd"));
        assertEquals("abc", AdminIssueQueryService.maskGuestToken("abc"));
        assertEquals(null, AdminIssueQueryService.maskGuestToken(null));
    }

    @Test
    void 실패한_게임이_있으면_검증_실패로_본다() {
        UnverifiedIssueItem item = AdminIssueQueryService.toUnverified(1L, "ABC", 1100, 5, 3, 2,
                LocalDateTime.of(2026, 10, 20, 12, 0));

        assertEquals("ERROR", item.status());
        assertEquals("검증 실패", item.statusLabel());
        assertTrue(item.reason().contains("2개"));
    }

    @Test
    void 실패가_없으면_검증_대기로_본다() {
        UnverifiedIssueItem item = AdminIssueQueryService.toUnverified(1L, "ABC", 1100, 5, 3, 0,
                LocalDateTime.of(2026, 10, 20, 12, 0));

        assertEquals("PENDING", item.status());
        assertEquals("검증 대기", item.statusLabel());
        assertTrue(item.reason().contains("3/5"));
    }
}
