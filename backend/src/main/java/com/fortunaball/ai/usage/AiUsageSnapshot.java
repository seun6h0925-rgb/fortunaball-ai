package com.fortunaball.ai.usage;

import java.time.LocalDateTime;

/**
 * 한 사용자의 "오늘 AI 무료 이용 현황". (화면 오른쪽 위의 남은 횟수 표시용)
 *
 * @param unlimited      관리자처럼 제한이 없으면 true. 이때 나머지 숫자는 의미가 없다.
 * @param dailyFreeLimit 하루 무료 횟수 (사용자 기준 한도)
 * @param usedToday      오늘 사용자 본인이 쓴 횟수 (한도를 넘긴 시도는 한도까지만 보여 준다)
 * @param remainingToday 오늘 남은 횟수. 사용자 한도와 접속지 한도 중 더 먼저 바닥나는 쪽 기준이라 0 이상이다.
 *                       (그래서 usedToday + remainingToday 가 한도와 다를 수 있다. 접속지를 여럿이 쓰는 경우)
 * @param resetAt        횟수가 다시 채워지는 시각 (다음 날 서울 0시)
 */
public record AiUsageSnapshot(boolean unlimited, int dailyFreeLimit, int usedToday, int remainingToday,
                              LocalDateTime resetAt) {
}
