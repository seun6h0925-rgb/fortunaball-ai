package com.fortunaball.issue.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationStatusTest {

    @Test
    void 검증_결과가_없거나_일부만_있으면_대기() {
        assertEquals(VerificationStatus.PENDING, VerificationStatus.of(3, 0, 0));
        assertEquals(VerificationStatus.PENDING, VerificationStatus.of(3, 2, 0));
        assertEquals(VerificationStatus.PENDING, VerificationStatus.of(0, 0, 0));
    }

    @Test
    void 모든_게임이_검증되면_완료() {
        assertEquals(VerificationStatus.VERIFIED, VerificationStatus.of(3, 3, 0));
    }

    @Test
    void 하나라도_오류면_실패() {
        assertEquals(VerificationStatus.ERROR, VerificationStatus.of(3, 2, 1));
        assertEquals(VerificationStatus.ERROR, VerificationStatus.of(3, 0, 3));
    }

    @Test
    void 화면에_보일_이름() {
        assertEquals("검증 대기", VerificationStatus.PENDING.label());
        assertEquals("검증 완료", VerificationStatus.VERIFIED.label());
        assertEquals("검증 실패", VerificationStatus.ERROR.label());
    }
}
