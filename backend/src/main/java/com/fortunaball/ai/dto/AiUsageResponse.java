package com.fortunaball.ai.dto;

import com.fortunaball.ai.usage.AiUsageSnapshot;

import java.time.LocalDateTime;

/**
 * AI 이용 현황 응답 (GET /api/ai/usage). AI 화면 오른쪽 위의 "오늘 남은 횟수" 표시에 쓴다.
 *
 * @param unlimited      제한 없음(관리자)이면 true. 이때는 숫자를 보여 주지 말고 "제한 없음"으로 표시한다.
 * @param dailyFreeLimit 하루 무료 횟수
 * @param usedToday      오늘 쓴 횟수
 * @param remainingToday 오늘 남은 횟수
 * @param resetAt        횟수가 다시 채워지는 시각 (다음 날 0시, 서울 시간)
 */
public record AiUsageResponse(boolean unlimited, int dailyFreeLimit, int usedToday, int remainingToday,
                              LocalDateTime resetAt) {

    public static AiUsageResponse from(AiUsageSnapshot snapshot) {
        return new AiUsageResponse(snapshot.unlimited(), snapshot.dailyFreeLimit(), snapshot.usedToday(),
                snapshot.remainingToday(), snapshot.resetAt());
    }
}
