package com.fortunaball.generate.service;

import com.fortunaball.generate.domain.Combo;
import com.fortunaball.generate.domain.GenerationTicket;
import com.fortunaball.stat.domain.PatternCode;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationStoreTest {

    private static final String GUEST_A = "guest-aaaaaaaaaaaaaaaa";
    private static final String GUEST_B = "guest-bbbbbbbbbbbbbbbb";

    /** 테스트에서 직접 움직이는 시계 (나노초) */
    private final AtomicLong nanos = new AtomicLong();
    private GenerationStore store;

    private final List<Combo> games = List.of(Combo.of(List.of(1, 2, 3, 4, 5, 6)));

    @BeforeEach
    void setUp() {
        Ticker ticker = nanos::get;
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return Instant.EPOCH.plusNanos(nanos.get()); }
        };
        store = new GenerationStore(Duration.ofMinutes(30), ticker, clock);
    }

    private void passMinutes(long minutes) {
        nanos.addAndGet(TimeUnit.MINUTES.toNanos(minutes));
    }

    private GenerationTicket issue(String guest) {
        return store.issue(guest, 1247, 1246, PatternCode.BALANCE_MIX, games);
    }

    @Test
    void 저장하면_토큰으로_다시_읽는다() {
        GenerationTicket t = issue(GUEST_A);
        assertEquals(43, t.confirmToken().length());
        assertEquals(Duration.ofMinutes(30), Duration.between(t.createdAt(), t.expiresAt()));
        GenerationTicket found = store.find(t.confirmToken(), GUEST_A).orElseThrow();
        assertEquals(1247, found.targetRound());
        assertEquals(games, found.games());
    }

    @Test
    void 만료_시간이_지나면_사라진다() {
        GenerationTicket t = issue(GUEST_A);
        passMinutes(29);
        assertTrue(store.find(t.confirmToken(), GUEST_A).isPresent());
        passMinutes(2);   // 합계 31분
        assertTrue(store.find(t.confirmToken(), GUEST_A).isEmpty());
        assertTrue(store.consume(t.confirmToken(), GUEST_A).isEmpty());
    }

    @Test
    void 다시_생성하면_이전_결과는_버려진다() {
        GenerationTicket first = issue(GUEST_A);
        GenerationTicket second = issue(GUEST_A);
        assertNotEquals(first.confirmToken(), second.confirmToken());
        assertTrue(store.find(first.confirmToken(), GUEST_A).isEmpty());
        assertTrue(store.find(second.confirmToken(), GUEST_A).isPresent());
    }

    @Test
    void 다른_게스트의_결과는_영향받지도_읽히지도_않는다() {
        GenerationTicket a = issue(GUEST_A);
        GenerationTicket b = issue(GUEST_B);
        assertTrue(store.find(a.confirmToken(), GUEST_A).isPresent());      // B 가 만들어도 A 것은 그대로
        assertTrue(store.find(a.confirmToken(), GUEST_B).isEmpty());        // 남의 토큰으로는 못 읽는다
        assertTrue(store.consume(a.confirmToken(), GUEST_B).isEmpty());
        assertTrue(store.find(a.confirmToken(), GUEST_A).isPresent());      // 남이 시도해도 지워지지 않는다
        assertTrue(store.find(b.confirmToken(), GUEST_B).isPresent());
    }

    @Test
    void 확정용으로_꺼내면_한번만_쓸_수_있다() {
        GenerationTicket t = issue(GUEST_A);
        assertTrue(store.consume(t.confirmToken(), GUEST_A).isPresent());
        assertTrue(store.consume(t.confirmToken(), GUEST_A).isEmpty());
        assertTrue(store.find(t.confirmToken(), GUEST_A).isEmpty());
    }

    @Test
    void 확정한_뒤_다시_생성해도_문제없다() {
        GenerationTicket t = issue(GUEST_A);
        store.consume(t.confirmToken(), GUEST_A);
        GenerationTicket next = issue(GUEST_A);
        assertTrue(store.find(next.confirmToken(), GUEST_A).isPresent());
    }

    @Test
    void 토큰은_매번_다르고_없는_토큰은_빈_값() {
        Set<String> tokens = Set.of(issue(GUEST_A).confirmToken(), issue(GUEST_A).confirmToken(), issue(GUEST_A).confirmToken());
        assertEquals(3, tokens.size());
        assertTrue(store.find("없는토큰", GUEST_A).isEmpty());
        assertTrue(store.find(null, GUEST_A).isEmpty());
    }
}
