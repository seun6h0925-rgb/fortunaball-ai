package com.fortunaball.ticket.domain;

import java.util.List;

/**
 * 용지의 한 줄(A, B, C ...)
 *
 * @param kind    선택 방식 (자동/수동/반자동)
 * @param numbers 번호 6개 (오름차순으로 정렬해 둔다, 1~45, 중복 없음)
 */
public record TicketGame(GameKind kind, List<Integer> numbers) {
}
