package com.fortunaball.common.guest;

import com.fortunaball.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** GuestToken 형식 검사 시험 (스프링 없이 빠르게 실행됨) */
class GuestTokenTest {

    @Test
    void UUID_형식은_통과한다() {
        String uuid = UUID.randomUUID().toString();
        assertEquals(uuid, GuestToken.of(uuid).value());
    }

    @Test
    void 앞뒤_공백은_잘라서_통과한다() {
        String uuid = UUID.randomUUID().toString();
        assertEquals(uuid, GuestToken.of("  " + uuid + " ").value());
    }

    @Test
    void 없거나_빈값이면_실패한다() {
        assertThrows(BusinessException.class, () -> GuestToken.of(null));
        assertThrows(BusinessException.class, () -> GuestToken.of(""));
        assertThrows(BusinessException.class, () -> GuestToken.of("   "));
    }

    @Test
    void 너무_짧거나_길면_실패한다() {
        assertThrows(BusinessException.class, () -> GuestToken.of("abc"));
        assertThrows(BusinessException.class, () -> GuestToken.of("a".repeat(129)));
    }

    @Test
    void 허용되지_않는_문자가_있으면_실패한다() {
        assertThrows(BusinessException.class, () -> GuestToken.of("abcd efgh ijkl mnop"));
        assertThrows(BusinessException.class, () -> GuestToken.of("abcdefghijklmnop'; --"));
    }

    @Test
    void 문자열로_찍어도_값이_가려진다() {
        String uuid = UUID.randomUUID().toString();
        assertFalse(GuestToken.of(uuid).toString().contains(uuid));
    }
}
