package com.fortunaball.ticket.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 용지 지문: 같은 용지는 같은 값, 다른 용지는 다른 값, 일련번호 원문은 들어 있지 않다. (가짜 값 사용) */
class TicketFingerprintTest {

    private static ParsedTicket ticket(int round, String serial, GameKind kind, Integer... nums) {
        return new ParsedTicket(round, List.of(new TicketGame(kind, List.of(nums))), serial);
    }

    @Test
    void 같은_용지는_같은_지문이다() {
        assertEquals(TicketFingerprint.of(ticket(1239, "100000000112345678", GameKind.MANUAL, 1, 2, 3, 4, 5, 6)),
                TicketFingerprint.of(ticket(1239, "100000000112345678", GameKind.MANUAL, 1, 2, 3, 4, 5, 6)));
    }

    @Test
    void 선택방식_글자가_달라도_같은_용지면_같은_지문이다() {
        assertEquals(TicketFingerprint.of(ticket(1239, "100000000112345678", GameKind.MANUAL, 1, 2, 3, 4, 5, 6)),
                TicketFingerprint.of(ticket(1239, "100000000112345678", GameKind.AUTO, 1, 2, 3, 4, 5, 6)));
    }

    @Test
    void 회차_일련번호_번호가_다르면_다른_지문이다() {
        String base = TicketFingerprint.of(ticket(1239, "100000000112345678", GameKind.MANUAL, 1, 2, 3, 4, 5, 6));
        assertNotEquals(base, TicketFingerprint.of(ticket(1240, "100000000112345678", GameKind.MANUAL, 1, 2, 3, 4, 5, 6)));
        assertNotEquals(base, TicketFingerprint.of(ticket(1239, "100000000112345679", GameKind.MANUAL, 1, 2, 3, 4, 5, 6)));
        assertNotEquals(base, TicketFingerprint.of(ticket(1239, "100000000112345678", GameKind.MANUAL, 1, 2, 3, 4, 5, 7)));
    }

    @Test
    void 지문은_64자_16진수이고_일련번호_원문을_담지_않는다() {
        String h = TicketFingerprint.of(ticket(1239, "100000000112345678", GameKind.MANUAL, 1, 2, 3, 4, 5, 6));
        assertEquals(64, h.length());
        assertTrue(h.matches("^[0-9a-f]{64}$"));
        assertTrue(!h.contains("100000000112345678"));
    }

    @Test
    void 일련번호가_비어_있으면_거절한다() {
        assertThrows(IllegalArgumentException.class,
                () -> TicketFingerprint.of(ticket(1239, "", GameKind.MANUAL, 1, 2, 3, 4, 5, 6)));
        assertThrows(IllegalArgumentException.class,
                () -> TicketFingerprint.of(ticket(1239, null, GameKind.MANUAL, 1, 2, 3, 4, 5, 6)));
    }
}
