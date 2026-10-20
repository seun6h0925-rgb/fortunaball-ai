package com.fortunaball.ai.usage;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 한도 검사를 통과한 요청 1건의 "차감 영수증". AI 호출이 실패했을 때 무료 횟수를 되돌리려고 쓴다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 차감한 "그 날짜"의 횟수에서 되돌린다. (자정을 넘겨 호출이 끝나도 새 날짜의 횟수를 건드리지 않게)
 *  - 한 영수증으로는 한 번만 되돌린다. (같은 요청에서 AI 를 두 번 불러 둘 다 실패해도 횟수는 1만 돌려준다)
 *  - 관리자나 차감이 없었던 요청은 되돌릴 것이 없다. (none())
 *  - 식별값(guest_token, IP)이 들어 있으므로 로그에 찍지 않는다. toString() 을 마스킹했다.
 */
public final class AiPermit {

    private static final AiPermit NONE = new AiPermit(false, null, null, null);

    private final boolean consumed;
    private final LocalDate date;
    private final String guestKey;
    private final String clientIp;
    private final AtomicBoolean refunded = new AtomicBoolean(false);

    AiPermit(boolean consumed, LocalDate date, String guestKey, String clientIp) {
        this.consumed = consumed;
        this.date = date;
        this.guestKey = guestKey;
        this.clientIp = clientIp;
    }

    /** 차감한 것이 없는 영수증 (관리자) */
    public static AiPermit none() {
        return NONE;
    }

    boolean consumed() { return consumed; }
    LocalDate date() { return date; }
    String guestKey() { return guestKey; }
    String clientIp() { return clientIp; }

    /** 처음 부른 한 번만 true. (되돌리기를 한 번만 하게 하는 장치) */
    boolean markRefunded() {
        return refunded.compareAndSet(false, true);
    }

    @Override
    public String toString() {
        return "AiPermit[consumed=" + consumed + ", date=" + date + "]";
    }
}
