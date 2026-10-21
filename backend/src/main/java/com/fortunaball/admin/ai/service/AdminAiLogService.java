package com.fortunaball.admin.ai.service;

import com.fortunaball.admin.ai.dto.AiLogItem;
import com.fortunaball.admin.ai.dto.AiLogResponse;
import com.fortunaball.admin.ai.dto.AiLogSummary;
import com.fortunaball.admin.common.AdminJdbc;
import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.common.AdminQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 사용량·실패 로그 조회 (API-32). 읽기만 한다. 기간을 안 보내면 최근 7일이다.
 * 요약(summary)은 기능 구분(featureType)·상태(status) 필터와 상관없이 "기간 전체"를 센다.
 * (정책 코드별 건수를 항상 전체 기준으로 보려는 것)
 */
@Service
public class AdminAiLogService {

    static final int DEFAULT_DAYS = 7;

    private final JdbcTemplate jdbc;
    private final AiLimitStatusProvider limitStatusProvider;

    public AdminAiLogService(JdbcTemplate jdbc, AiLimitStatusProvider limitStatusProvider) {
        this.jdbc = jdbc;
        this.limitStatusProvider = limitStatusProvider;
    }

    @Transactional(readOnly = true)
    public AiLogResponse logs(String featureType, String status, LocalDate from, LocalDate to,
                              Integer page, Integer size) {
        AdminQuery.Range range = AdminQuery.range(from, to, DEFAULT_DAYS);

        List<Object> args = new ArrayList<>(List.of(range.start(), range.endExclusive()));
        StringBuilder where = new StringBuilder("FROM ai_usage_log WHERE created_at >= ? AND created_at < ?");
        if (featureType != null && !featureType.isBlank()) {
            where.append(" AND feature_type = ?");
            args.add(featureType.trim());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND request_status = ?");
            args.add(status.trim());
        }
        AdminPage<AiLogItem> logs = AdminJdbc.page(jdbc,
                "id, feature_type, request_status, model_name, policy_code, input_token_count, output_token_count,"
                        + " response_time_ms, estimated_cost, error_message, created_at",
                where.toString(), args, "created_at DESC, id DESC", AdminQuery.page(page), AdminQuery.size(size),
                (rs, i) -> new AiLogItem(rs.getLong("id"), rs.getString("feature_type"),
                        rs.getString("request_status"), rs.getString("model_name"), rs.getString("policy_code"),
                        rs.getInt("input_token_count"), rs.getInt("output_token_count"),
                        AdminJdbc.intOrNull(rs, "response_time_ms"), rs.getBigDecimal("estimated_cost"),
                        rs.getString("error_message"), AdminJdbc.time(rs, "created_at")));

        return new AiLogResponse(logs, summary(range), limitStatusProvider.current());
    }

    private AiLogSummary summary(AdminQuery.Range range) {
        Object[] period = {range.start(), range.endExclusive()};
        String base = " FROM ai_usage_log WHERE created_at >= ? AND created_at < ?";

        Long calls = jdbc.queryForObject("SELECT COUNT(*)" + base + " AND model_name IS NOT NULL", Long.class, period);
        Long failed = jdbc.queryForObject("SELECT COUNT(*)" + base + " AND request_status = 'FAILED'", Long.class, period);
        Long blocked = jdbc.queryForObject("SELECT COUNT(*)" + base + " AND request_status = 'BLOCKED'", Long.class, period);
        BigDecimal cost = jdbc.queryForObject("SELECT COALESCE(SUM(estimated_cost), 0)" + base, BigDecimal.class, period);
        List<AiLogSummary.PolicyCount> policies = jdbc.query(
                "SELECT policy_code, COUNT(*) AS cnt" + base
                        + " AND policy_code IS NOT NULL GROUP BY policy_code ORDER BY cnt DESC, policy_code",
                (rs, i) -> new AiLogSummary.PolicyCount(rs.getString("policy_code"), rs.getLong("cnt")), period);

        return new AiLogSummary(nz(calls), nz(failed), nz(blocked), cost == null ? BigDecimal.ZERO : cost, policies);
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }
}
