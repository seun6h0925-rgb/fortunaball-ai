package com.fortunaball.admin.log.service;

import com.fortunaball.admin.common.AdminJdbc;
import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.common.AdminQuery;
import com.fortunaball.admin.log.dto.ErrorLogItem;
import com.fortunaball.admin.log.dto.SchedulerLogItem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오류 로그(API-38)와 스케줄러 실행 로그(API-39) 조회. 읽기만 한다.
 * 기간을 안 보내면 최근 30일, 쪽 크기는 기본 20(최대 100)이다.
 */
@Service
public class AdminLogQueryService {

    static final int DEFAULT_DAYS = 30;
    /** RUNNING 이 이 시간 넘게 남아 있으면 중단된 것으로 본다. */
    public static final long STALE_HOURS = 1;

    private final JdbcTemplate jdbc;

    public AdminLogQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public AdminPage<ErrorLogItem> errors(String errorType, LocalDate from, LocalDate to, Integer page, Integer size) {
        AdminQuery.Range range = AdminQuery.range(from, to, DEFAULT_DAYS);
        List<Object> args = new ArrayList<>(List.of(range.start(), range.endExclusive()));
        StringBuilder where = new StringBuilder("FROM api_error_log WHERE created_at >= ? AND created_at < ?");
        if (errorType != null && !errorType.isBlank()) {
            where.append(" AND error_type = ?");
            args.add(errorType.trim());
        }
        return AdminJdbc.page(jdbc,
                "id, api_name, error_type, request_url, http_status, error_message, created_at",
                where.toString(), args, "created_at DESC, id DESC", AdminQuery.page(page), AdminQuery.size(size),
                (rs, i) -> new ErrorLogItem(rs.getLong("id"), rs.getString("api_name"), rs.getString("error_type"),
                        rs.getString("request_url"), AdminJdbc.intOrNull(rs, "http_status"),
                        rs.getString("error_message"), AdminJdbc.time(rs, "created_at")));
    }

    @Transactional(readOnly = true)
    public AdminPage<SchedulerLogItem> schedulerLogs(String jobName, LocalDate from, LocalDate to,
                                                     Integer page, Integer size) {
        AdminQuery.Range range = AdminQuery.range(from, to, DEFAULT_DAYS);
        List<Object> args = new ArrayList<>(List.of(range.start(), range.endExclusive()));
        StringBuilder where = new StringBuilder("FROM scheduler_log WHERE started_at >= ? AND started_at < ?");
        if (jobName != null && !jobName.isBlank()) {
            where.append(" AND job_name = ?");
            args.add(jobName.trim());
        }
        LocalDateTime staleBefore = LocalDateTime.now(AdminQuery.ZONE).minusHours(STALE_HOURS);
        return AdminJdbc.page(jdbc,
                "id, job_name, job_status, target_round_no, started_at, ended_at, processed_count, error_message",
                where.toString(), args, "started_at DESC, id DESC", AdminQuery.page(page), AdminQuery.size(size),
                (rs, i) -> new SchedulerLogItem(rs.getLong("id"), rs.getString("job_name"),
                        rs.getString("job_status"), AdminJdbc.intOrNull(rs, "target_round_no"),
                        AdminJdbc.time(rs, "started_at"), AdminJdbc.time(rs, "ended_at"),
                        AdminJdbc.intOrNull(rs, "processed_count"), rs.getString("error_message"),
                        isInterrupted(rs.getString("job_status"), AdminJdbc.time(rs, "started_at"), staleBefore)));
    }

    /** RUNNING 인데 시작한 지 오래됐으면 중단된 작업이다. */
    public static boolean isInterrupted(String status, LocalDateTime startedAt, LocalDateTime staleBefore) {
        return "RUNNING".equals(status) && startedAt != null && startedAt.isBefore(staleBefore);
    }
}
