package com.fortunaball.ticket.domain;

/**
 * 로또 용지 한 줄(게임)의 선택 방식. QR 글자에서는 번호 앞의 영문자 한 글자로 표시된다.
 *
 * ⚠ 확인 상태
 *  - m(수동): 실제 용지(제 1239회, A~E 5줄 모두 수동)의 QR 로 확인했다.
 *  - q(자동), s(반자동): 알려진 형식으로 가정한 것이다. 자동 용지를 구하면 한 번 더 확인한다. (2026-10-20)
 */
public enum GameKind {
    AUTO('q', "자동"),
    MANUAL('m', "수동"),
    SEMI_AUTO('s', "반자동");

    private final char code;
    private final String label;

    GameKind(char code, String label) {
        this.code = code;
        this.label = label;
    }

    public char code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** QR 글자의 영문자 → 선택 방식. 모르는 글자면 null */
    public static GameKind ofCode(char code) {
        for (GameKind k : values()) {
            if (k.code == code) {
                return k;
            }
        }
        return null;
    }
}
