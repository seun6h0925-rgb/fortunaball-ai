package com.fortunaball.admin.dashboard.service;

import com.fortunaball.admin.ai.service.AiLimitStatusProvider;
import com.fortunaball.admin.common.AdminJdbc;
import com.fortunaball.admin.common.AdminQuery;
import com.fortunaball.admin.dashboard.dto.AdminDashboardResponse;
import com.fortunaball.admin.dashboard.dto.AdminDashboardResponse.ErrorStatus;
import com.fortunaball.admin.dashboard.dto.AdminDashboardResponse.IssueCounts;
import com.fortunaball.admin.dashboard.dto.AdminDashboardResponse.LatestRound;
import com.fortunaball.admin.dashboard.dto.AdminDashboardResponse.SchedulerStatus;
import com.fortunaball.admin.dashboard.dto.AdminDashboardResponse.TypeCount;
import com.fortunaball.admin.log.service.AdminLogQueryService;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.stat.domain.SnapshotStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 대시보드 (API-22). 읽기만 한다. 날짜 기준은 서울 시간이다.
 * 한 화면에 여러 숫자를 모으는 곳이라, 각 숫자의 의미는 AdminDashboardResponse 주석에 적어 두었다.
 */
@Service
public class AdminDashboardService {

    private final JdbcTemplate jdbc;
    private final AiLimitStatusProvider aiLimitStatusProvider;
    private final DrawRoundRepository drawRoundRepository;

    public AdminDashboardService(JdbcTemplate jdbc, AiLimitStatusProvider aiLimitStatusProvider,
                                 DrawRoundRepository drawRoundRepository) {
        this.jdbc = jdbc;
        this.aiLimitStatusProvider = aiLimitStatusProvider;
        this.drawRoundRepository = drawRoundRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        LocalDateTime todayStart = AdminQuery.today().atStartOfDay();
        LocalDateTime monthStart = AdminQuery.today().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(AdminQuery.ZONE);

        IssueCounts issues = new IssueCounts(
                count("SELECT COUNT(*) FROM number_issue WHERE created_at >= ?", todayStart),
                count("SELECT COUNT(*) FROM number_issue WHERE created_at >= ?", monthStart),
                count("SELECT COUNT(*) FROM number_issue"));

        long aiFailedToday = count(
                "SELECT COUNT(*) FROM ai_usage_log WHERE request_status = 'FAILED' AND created_at >= ?", todayStart);

        List<TypeCount> byType = jdbc.query(
                "SELECT error_type, COUNT(*) AS cnt FROM api_error_log WHERE created_at >= ?"
                        + " GROUP BY error_type ORDER BY cnt DESC, error_type",
                (rs, i) -> new TypeCount(rs.getString("error_type"), rs.getLong("cnt")), now.minusHours(24));
        ErrorStatus errors = new ErrorStatus(byType.stream().mapToLong(TypeCount::count).sum(), byType);

        return new AdminDashboardResponse(issues, aiLimitStatusProvider.current(), aiFailedToday, errors,
                latestRound(), schedulerStatuses(now));
    }

    private LatestRound latestRound() {
        Optional<DrawRound> latest = drawRoundRepository.findTopByOrderByRoundNoDesc();
        if (latest.isEmpty()) {
            return new LatestRound(null, null, null, null, null);
        }
        int next = latest.get().getRoundNo() + 1;
        List<String> statuses = jdbc.query("SELECT status FROM stat_round_snapshot WHERE target_round_no = ?",
                (rs, i) -> rs.getString("status"), next);
        SnapshotStatus stat = statuses.isEmpty() ? null : SnapshotStatus.valueOf(statuses.get(0));
        return new LatestRound(latest.get().getRoundNo(), latest.get().getDrawDate(), next,
                stat == null ? null : stat.name(), stat == null ? null : stat.label());
    }

    /** 작업 이름마다 가장 최근에 시작한 실행 한 줄씩 */
    private List<SchedulerStatus> schedulerStatuses(LocalDateTime now) {
        LocalDateTime staleBefore = now.minusHours(AdminLogQueryService.STALE_HOURS);
        return jdbc.query("""
                SELECT s.job_name, s.job_status, s.started_at, s.ended_at, s.processed_count, s.error_message
                  FROM scheduler_log s
                  JOIN (SELECT job_name, MAX(started_at) AS last_started FROM scheduler_log GROUP BY job_name) x
                    ON x.job_name = s.job_name AND x.last_started = s.started_at
                 ORDER BY s.job_name
                """,
                (rs, i) -> new SchedulerStatus(rs.getString("job_name"), rs.getString("job_status"),
                        AdminJdbc.time(rs, "started_at"), AdminJdbc.time(rs, "ended_at"),
                        AdminJdbc.intOrNull(rs, "processed_count"), rs.getString("error_message"),
                        AdminLogQueryService.isInterrupted(rs.getString("job_status"),
                                AdminJdbc.time(rs, "started_at"), staleBefore)));
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
