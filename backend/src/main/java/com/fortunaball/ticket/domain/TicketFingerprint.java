package com.fortunaball.ticket.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 용지 한 장을 구별하는 "지문"(SHA-256 해시, 64자)을 만든다. (같은 용지를 두 번 등록하는 것을 막는 데 쓴다)
 *
 * ⚠ 일련번호 원문은 DB·로그 어디에도 남기지 않는다. 해시는 되돌릴 수 없어서, 저장해도 원래 값을 알 수 없다.
 * ⚠ 해시 입력은 [회차 + 일련번호 + 번호들] 이다. 선택 방식 글자(q/m/s)는 넣지 않는다. (같은 용지를 다르게 읽어도 같은 지문이 되도록)
 *    입력 모양(PREFIX 의 v1)을 바꾸면 이미 저장된 해시와 맞지 않게 되므로, 바꿀 때는 새 버전(v2)으로 올리고 기존 데이터를 어떻게 할지 정한다.
 */
public final class TicketFingerprint {

    private static final String PREFIX = "fortuna-ticket-v1";

    private TicketFingerprint() {
    }

    /** @throws IllegalArgumentException 일련번호가 비어 있으면 (용지를 구별할 수 없다) */
    public static String of(ParsedTicket ticket) {
        if (ticket.serial() == null || ticket.serial().isBlank()) {
            throw new IllegalArgumentException("용지 식별값(일련번호)이 비어 있습니다.");
        }
        String games = ticket.games().stream()
                .map(g -> g.numbers().stream().map(String::valueOf).collect(Collectors.joining(",")))
                .collect(Collectors.joining(";"));
        String input = PREFIX + "|" + ticket.roundNo() + "|" + ticket.serial() + "|" + games;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);   // 자바에 항상 있는 알고리즘이라 실제로는 나지 않는다
        }
    }
}
