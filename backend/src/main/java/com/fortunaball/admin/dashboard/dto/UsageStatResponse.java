package com.fortunaball.admin.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 서비스 이용 통계 (API-61). 방문자 수는 1차 범위에서 제외했다.
 *  - issueCount: 기간 내 번호 발급 확정 건수
 *  - aiUseCount: 기간 내 AI 이용 횟수 (정상 응답한 횟수)
 *  - features: 기능별 AI 이용 (애널리스트·꿈풀이)
 *  - popular: 이용이 많은 기능 순위
 *  - daily: 날짜별 추이 (이용이 없는 날도 0으로 채운다)
 */
public record UsageStatResponse(LocalDate from, LocalDate to, long issueCount, long aiUseCount,
                                List<FeatureUsage> features, List<PopularFeature> popular, List<DailyUsage> daily) {

    /** success: 정상 응답, failed: AI 호출 실패, blocked: 정책·한도로 막음 */
    public record FeatureUsage(String feature, String label, long success, long failed, long blocked) {
    }

    public record PopularFeature(int rank, String name, long count) {
    }

    public record DailyUsage(LocalDate date, long issues, long aiUses) {
    }
}
