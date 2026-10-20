package com.fortunaball.ticket.domain;

import java.util.List;

/**
 * 용지 QR 글자를 풀어서 얻은 내용.
 *
 * @param roundNo 용지에 적힌 회차
 * @param games   게임 목록 (용지 위에서 아래 순서, A줄부터. 1~5줄)
 * @param serial  게임 뒤에 붙은 용지 식별값 (TR 번호 + 일련번호). 없으면 빈 문자열.
 *                번호 계산에는 쓰지 않고, 같은 용지 중복 확인에 쓸 수 있다. (⚠ 개인 식별이 가능한 값이라 로그에 남기지 않는다)
 */
public record ParsedTicket(int roundNo, List<TicketGame> games, String serial) {

    public int gameCount() {
        return games.size();
    }
}
