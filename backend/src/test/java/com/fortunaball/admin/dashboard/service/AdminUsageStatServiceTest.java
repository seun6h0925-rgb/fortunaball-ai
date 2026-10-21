package com.fortunaball.admin.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fortunaball.admin.dashboard.dto.UsageStatResponse.DailyUsage;
import com.fortunaball.admin.dashboard.dto.UsageStatResponse.FeatureUsage;
import com.fortunaball.admin.dashboard.dto.UsageStatResponse.PopularFeature;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminUsageStatServiceTest {

    @Test
    void 기능별_이용은_성공_실패_차단을_따로_센다() {
        FeatureUsage usage = AdminUsageStatService.feature(
                Map.of("DREAM/SUCCESS", 7L, "DREAM/FAILED", 1L, "ANALYST/SUCCESS", 99L), "DREAM", "AI 로또 꿈풀이");

        assertEquals(7, usage.success());
        assertEquals(1, usage.failed());
        assertEquals(0, usage.blocked());
    }

    @Test
    void 인기_기능은_이용_횟수가_많은_순서다() {
        List<PopularFeature> popular = AdminUsageStatService.popular(10, 30, 20);

        assertEquals("AI 로또 애널리스트", popular.get(0).name());
        assertEquals(1, popular.get(0).rank());
        assertEquals("AI 로또 꿈풀이", popular.get(1).name());
        assertEquals("번호 발급", popular.get(2).name());
    }

    @Test
    void 횟수가_같으면_번호_발급_애널리스트_꿈풀이_순서를_지킨다() {
        List<PopularFeature> popular = AdminUsageStatService.popular(5, 5, 5);

        assertEquals("번호 발급", popular.get(0).name());
        assertEquals("AI 로또 애널리스트", popular.get(1).name());
        assertEquals("AI 로또 꿈풀이", popular.get(2).name());
    }

    @Test
    void 날짜별_추이는_이용이_없는_날도_0으로_채운다() {
        List<DailyUsage> daily = AdminUsageStatService.daily(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3),
                Map.of(LocalDate.of(2026, 10, 2), 4L), Map.of(LocalDate.of(2026, 10, 3), 9L));

        assertEquals(3, daily.size());
        assertEquals(0, daily.get(0).issues());
        assertEquals(4, daily.get(1).issues());
        assertEquals(9, daily.get(2).aiUses());
    }
}
