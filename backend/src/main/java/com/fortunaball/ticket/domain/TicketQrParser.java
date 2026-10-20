package com.fortunaball.ticket.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로또 용지 QR 글자를 풀어서 회차와 번호를 꺼낸다. DB·스프링·인터넷 없이 글자만 다루는 순수 클래스.
 * 📚 배운 내용: 정규식(Pattern, Matcher)
 *
 * QR 글자 형식 (실제 용지 제 1239회로 확인, 2026-10-20)
 *   http://qr.dhlottery.co.kr/?v=1239 m030614163442 m010305091442 ... 106368072612810027
 *                                └회차┘└─ 게임 한 줄 = 선택방식 1글자 + 번호 6개(2자리씩) ─┘└ 용지 식별값 ┘
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 주소(qr.dhlottery.co.kr)는 글자로만 읽는다. 절대 접속하지 않는다. (외부 연결·요금·보안 위험 없음)
 *  - 허용하는 주소는 dhlottery.co.kr 이거나 그 하위 주소(qr., m., www. 등)뿐이다. (evildhlottery.co.kr 같은 비슷한 주소는 거절)
 *  - 한 용지에는 최대 5줄(A~E)이다. 0줄이거나 6줄 이상이면 거절한다.
 *  - 번호는 1~45, 한 줄 안에서 중복이 없어야 한다. 순서가 뒤섞여 있어도 오름차순으로 정렬해서 돌려준다.
 *  - 사용자가 보내는 값이라 길이 제한(MAX_LENGTH)을 먼저 확인한다.
 */
public final class TicketQrParser {

    /** 이보다 긴 글자는 읽어 보지도 않고 거절 (실제 QR 은 100자 안팎) */
    static final int MAX_LENGTH = 300;
    static final int MAX_GAMES = 5;

    private static final String ALLOWED_HOST = "dhlottery.co.kr";

    /** 주소 부분: http(s)://호스트/?v=값 */
    private static final Pattern URL = Pattern.compile("^https?://([A-Za-z0-9.-]+)/\\?v=([0-9a-z]+)$");
    /** v 값: 회차(1~4자리) + 게임(1~5줄, 각 영문자 1 + 숫자 12) + 용지 식별값(선택) */
    private static final Pattern VALUE = Pattern.compile("^(\\d{1,4})((?:[qms]\\d{12}){1,5})(\\d*)$");
    private static final Pattern GAME = Pattern.compile("([qms])(\\d{12})");

    private TicketQrParser() {
    }

    public static ParsedTicket parse(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidTicketQrException("QR 글자가 비어 있음");
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidTicketQrException("QR 글자가 너무 김: length=" + trimmed.length());
        }

        Matcher url = URL.matcher(trimmed);
        if (!url.matches()) {
            throw new InvalidTicketQrException("로또 용지 QR 주소 형식이 아님");
        }
        String host = url.group(1).toLowerCase(Locale.ROOT);
        if (!host.equals(ALLOWED_HOST) && !host.endsWith("." + ALLOWED_HOST)) {
            throw new InvalidTicketQrException("허용되지 않는 QR 주소: host=" + host);
        }

        Matcher value = VALUE.matcher(url.group(2));
        if (!value.matches()) {
            throw new InvalidTicketQrException("회차·게임 형식이 맞지 않음");
        }
        int round = Integer.parseInt(value.group(1));
        if (round < 1) {
            throw new InvalidTicketQrException("회차가 올바르지 않음: round=" + round);
        }

        List<TicketGame> games = new ArrayList<>();
        Matcher g = GAME.matcher(value.group(2));
        while (g.find()) {
            games.add(parseGame(g.group(1).charAt(0), g.group(2), games.size() + 1));
        }
        if (games.isEmpty() || games.size() > MAX_GAMES) {      // 정규식이 이미 막지만 한 번 더 확인
            throw new InvalidTicketQrException("게임 수가 올바르지 않음: count=" + games.size());
        }
        return new ParsedTicket(round, List.copyOf(games), value.group(3));
    }

    /** 12자리 숫자 → 번호 6개 (1~45, 중복 없음) */
    private static TicketGame parseGame(char kindCode, String digits, int lineNo) {
        GameKind kind = GameKind.ofCode(kindCode);
        if (kind == null) {
            throw new InvalidTicketQrException("알 수 없는 선택 방식 글자: line=" + lineNo);
        }
        Set<Integer> unique = new HashSet<>();
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int n = Integer.parseInt(digits.substring(i * 2, i * 2 + 2));
            if (n < 1 || n > 45) {
                throw new InvalidTicketQrException("번호가 1~45 범위를 벗어남: line=" + lineNo);
            }
            if (!unique.add(n)) {
                throw new InvalidTicketQrException("한 줄 안에 같은 번호가 있음: line=" + lineNo);
            }
            numbers.add(n);
        }
        numbers.sort(Integer::compareTo);
        return new TicketGame(kind, List.copyOf(numbers));
    }
}
