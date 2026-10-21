package com.fortunaball.admin.dashboard.service;

import com.fortunaball.admin.common.AdminQuery;
import com.fortunaball.admin.dashboard.dto.UsageStatResponse;
import com.fortunaball.admin.dashboard.dto.UsageStatResponse.DailyUsage;
import com.fortunaball.admin.dashboard.dto.UsageStatResponse.FeatureUsage;
import com.fortunaball.admin.dashboard.dto.UsageStatResponse.PopularFeature;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서비스 이용 통계 (API-61). 읽기만 한다. 기간을 안 보내면 최근 30일이다. (날짜는 서울 시간 기준, 양 끝 날짜 포함)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - "번호 발급"은 번호를 확정해서 발급코드가 만들어진 건수다. (임시 생성은 저장하지 않으므로 세지 않는다)
 *  - "AI 이용 횟수"는 정상 응답(SUCCESS)만 센다. 실패·차단은 기능별 표에 따로 보여 준다.
 */
@Service
public class AdminUsageStatService {

    static final int DEFAULT_DAYS = 30;
    static final String ANALYST = "ANALYST";
    static final String DREAM = "DREAM";

    private final JdbcTemplate jdbc;

    public AdminUsageStatService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public UsageStatResponse usage(LocalDate from, LocalDate to) {
        AdminQuery.Range range = AdminQuery.range(from, to, DEFAULT_DAYS);
        Object[] period = {range.start(), range.endExclusive()};

        Long issues = jdbc.queryForObject(
                "SELECT COUNT(*) FROM number_issue WHERE created_at >= ? AND created_at < ?", Long.class, period);

        // 기능·상태별 AI 기록 수
        Map<String, Long> aiCounts = new HashMap<>();
        jdbc.query("SELECT feature_type, request_status, COUNT(*) AS cnt FROM ai_usage_log"
                        + " WHERE created_at >= ? AND created_at < ? GROUP BY feature_type, request_status",
                rs -> {
                    aiCounts.put(rs.getString("feature_type") + "/" + rs.getString("request_status"), rs.getLong("cnt"));
                }, period);

        Map<LocalDate, Long> issuesByDay = new HashMap<>();
        jdbc.query("SELECT DATE(created_at) AS d, COUNT(*) AS cnt FROM number_issue"
                        + " WHERE created_at >= ? AND created_at < ? GROUP BY DATE(created_at)",
                rs -> {
                    issuesByDay.put(rs.getDate("d").toLocalDate(), rs.getLong("cnt"));
                }, period);
        Map<LocalDate, Long> aiByDay = new HashMap<>();
        jdbc.query("SELECT DATE(created_at) AS d, COUNT(*) AS cnt FROM ai_usage_log"
                        + " WHERE created_at >= ? AND created_at < ? AND request_status = 'SUCCESS'"
                        + " GROUP BY DATE(created_at)",
                rs -> {
                    aiByDay.put(rs.getDate("d").toLocalDate(), rs.getLong("cnt"));
                }, period);

        LocalDate start = range.start().toLocalDate();
        LocalDate end = range.endExclusive().toLocalDate().minusDays(1);
        long issueCount = issues == null ? 0 : issues;
        FeatureUsage analyst = feature(aiCounts, ANALYST, "AI 로또 애널리스트");
        FeatureUsage dream = feature(aiCounts, DREAM, "AI 로또 꿈풀이");

        return new UsageStatResponse(start, end, issueCount, analyst.success() + dream.success(),
                List.of(analyst, dream), popular(issueCount, analyst.success(), dream.success()),
                daily(start, end, issuesByDay, aiByDay));
    }

    // ------------------------------------------------------------------ 계산 (DB 와 분리해서 테스트한다)

    static FeatureUsage feature(Map<String, Long> counts, String feature, String label) {
        return new FeatureUsage(feature, label, counts.getOrDefault(feature + "/SUCCESS", 0L),
                counts.getOrDefault(feature + "/FAILED", 0L), counts.getOrDefault(feature + "/BLOCKED", 0L));
    }

    /** 이용 횟수가 많은 순서. 같으면 번호 발급 → 애널리스트 → 꿈풀이 순으로 둔다. */
    static List<PopularFeature> popular(long issueCount, long analystSuccess, long dreamSuccess) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(List.of(
                Map.entry("번호 발급", issueCount),
                Map.entry("AI 로또 애널리스트", analystSuccess),
                Map.entry("AI 로또 꿈풀이", dreamSuccess)));
        entries.sort(Comparator.comparingLong((Map.Entry<String, Long> e) -> e.getValue()).reversed());
        List<PopularFeature> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            result.add(new PopularFeature(i + 1, entries.get(i).getKey(), entries.get(i).getValue()));
        }
        return result;
    }

    static List<DailyUsage> daily(LocalDate start, LocalDate end, Map<LocalDate, Long> issuesByDay,
                                  Map<LocalDate, Long> aiByDay) {
        List<DailyUsage> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            days.add(new DailyUsage(d, issuesByDay.getOrDefault(d, 0L), aiByDay.getOrDefault(d, 0L)));
        }
        return days;
    }
}
