package com.fortunaball.ai.usage;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 사용 기록 엔티티의 보호 규칙 확인: 오류 사유 길이 제한 */
class AiUsageLogTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 11, 0);

    @Test
    void 오류_사유는_500자까지만_저장한다() {
        AiUsageLog log = AiUsageLog.failed(AiFeature.DREAM, "m", 10, "가".repeat(900), NOW);

        assertEquals(500, log.getErrorMessage().length());
    }

    @Test
    void 오류_사유가_없어도_된다() {
        AiUsageLog log = AiUsageLog.failed(AiFeature.DREAM, "m", 10, null, NOW);

        assertNull(log.getErrorMessage());
    }

    @Test
    void 응답_시간이_매우_커도_정수_범위로_맞춘다() {
        AiUsageLog log = AiUsageLog.success(AiFeature.DREAM, "m", 1, 1, Long.MAX_VALUE, null, NOW);

        assertEquals(Integer.MAX_VALUE, log.getResponseTimeMs());
    }
}
