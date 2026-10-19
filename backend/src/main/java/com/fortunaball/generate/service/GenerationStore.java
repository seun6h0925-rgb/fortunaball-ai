package com.fortunaball.generate.service;

import com.fortunaball.generate.domain.Combo;
import com.fortunaball.generate.domain.GenerationTicket;
import com.fortunaball.stat.domain.PatternCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 번호 생성 임시 결과 보관소. (DB 를 쓰지 않고 서버 메모리에 TTL 동안만 보관)
 *
 *  - issue()   : 새 임시 결과를 저장하고 확정 토큰을 만든다. 같은 게스트의 이전 임시 결과는 버린다. ("다시 생성하면 이전 번호는 확정 불가")
 *  - find()    : 확정 전에 내용을 읽는다 (게스트가 다르면 못 읽는다)
 *  - consume() : 확정할 때 꺼내면서 지운다 (한 번 쓰면 다시 못 쓴다 → 같은 토큰으로 두 번 확정 방지)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 서버를 다시 켜면 임시 결과는 모두 사라진다. (확정 전 번호이므로 허용하는 정책)
 *  - 서버가 여러 대로 늘어나면 메모리가 서로 달라서 쓸 수 없다. 그때는 Redis 같은 공용 저장소로 바꾼다. (지금은 서버 1대 전제)
 *  - 확정 토큰은 추측할 수 없게 SecureRandom 으로 만든다. 그래도 게스트 토큰이 같은 사람의 것인지 항상 함께 확인한다.
 *  - 보관 개수 상한(MAX_TICKETS)은 메모리 보호용이다. 넘으면 오래된 것부터 밀려난다.
 *
 * 📚 배운 내용: @Component, 생성자 주입 / Caffeine 은 학원에서 쓴 Spring Cache 의 실제 저장소 중 하나
 */
@Component
@EnableConfigurationProperties(GenerationProperties.class)
public class GenerationStore {

    /** 메모리 보호용 최대 보관 개수 (게스트 1명당 1건이므로 동시 사용자 수 기준) */
    static final int MAX_TICKETS = 20_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Duration ttl;
    private final Clock clock;
    /** 확정 토큰 → 임시 결과 */
    private final Cache<String, GenerationTicket> tickets;
    /** 게스트 토큰 → 그 게스트의 가장 최근 확정 토큰 (다시 생성하면 이전 것을 지우기 위한 색인) */
    private final Cache<String, String> latestByGuest;

    @Autowired
    public GenerationStore(GenerationProperties props) {
        this(Duration.ofMinutes(props.ttlMinutes()), Ticker.systemTicker(), Clock.systemUTC());
    }

    /** 테스트용: 시간을 직접 움직일 수 있는 Ticker/Clock 을 넣는다 */
    GenerationStore(Duration ttl, Ticker ticker, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
        this.tickets = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(MAX_TICKETS).ticker(ticker).build();
        this.latestByGuest = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(MAX_TICKETS).ticker(ticker).build();
    }

    /** 새 임시 결과를 저장한다. 같은 게스트의 이전 임시 결과는 폐기한다. */
    public synchronized GenerationTicket issue(String guestToken, int targetRound, int basisRound,
                                               PatternCode patternCode, List<Combo> games) {
        String previous = latestByGuest.getIfPresent(guestToken);
        if (previous != null) {
            tickets.invalidate(previous);
        }
        Instant now = clock.instant();
        GenerationTicket ticket = new GenerationTicket(newToken(), guestToken, targetRound, basisRound,
                patternCode, games, now, now.plus(ttl));
        tickets.put(ticket.confirmToken(), ticket);
        latestByGuest.put(guestToken, ticket.confirmToken());
        return ticket;
    }

    /** 내용을 읽기만 한다. 없거나 만료됐거나 다른 게스트의 것이면 빈 값. */
    public Optional<GenerationTicket> find(String confirmToken, String guestToken) {
        if (confirmToken == null) {
            return Optional.empty();
        }
        GenerationTicket ticket = tickets.getIfPresent(confirmToken);
        if (ticket == null || !ticket.guestToken().equals(guestToken)) {
            return Optional.empty();
        }
        return Optional.of(ticket);
    }

    /** 확정용: 꺼내면서 지운다. 같은 토큰으로 두 번째 호출하면 빈 값. 다른 게스트가 부르면 지우지 않고 빈 값. */
    public synchronized Optional<GenerationTicket> consume(String confirmToken, String guestToken) {
        Optional<GenerationTicket> found = find(confirmToken, guestToken);
        found.ifPresent(t -> {
            tickets.invalidate(t.confirmToken());
            latestByGuest.invalidate(t.guestToken());
        });
        return found;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);   // 43글자, 영문·숫자·-·_
    }
}
