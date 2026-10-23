package com.fortunaball.admin.record.service;

import com.fortunaball.admin.common.AdminJdbc;
import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.common.AdminQuery;
import com.fortunaball.admin.record.dto.AdminWinningRecordItem;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우리 서비스 당첨 기록 관리 목록 (API-26). 읽기만 한다. 공개 여부 변경은 API-27(AdminWinningRecordService)이 한다.
 * 낙첨(등수 없음)과 검증 실패는 "당첨 기록"이 아니므로 뺀다.
 */
@Service
public class AdminWinningRecordQueryService {

    private final JdbcTemplate jdbc;

    public AdminWinningRecordQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public AdminPage<AdminWinningRecordItem> list(Integer roundNo, Integer rank, Boolean publicVisible,
                                                  Integer page, Integer size) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                FROM winning_verification v
                JOIN issue_number i ON i.id = v.issue_number_id
                JOIN number_issue n ON n.id = i.number_issue_id
                WHERE v.result_status = 'VERIFIED' AND v.winning_rank IS NOT NULL
                """);
        if (roundNo != null) {
            where.append(" AND v.round_no = ?");
            args.add(roundNo);
        }
        if (rank != null) {
            where.append(" AND v.winning_rank = ?");
            args.add(rank);
        }
        if (publicVisible != null) {
            where.append(" AND v.is_public = ?");
            args.add(publicVisible ? 1 : 0);
        }
        return AdminJdbc.page(jdbc,
                "v.id, v.round_no, v.winning_rank, v.match_count, v.bonus_matched, v.is_public, v.verified_at,"
                        + " n.issue_code, n.guest_token, n.created_at AS issued_at, i.number_1, i.number_2, i.number_3, i.number_4, i.number_5, i.number_6",
                where.toString(), args, "v.verified_at DESC, v.id DESC", AdminQuery.page(page), AdminQuery.size(size),
                (rs, idx) -> new AdminWinningRecordItem(rs.getLong("id"), rs.getInt("round_no"),
                        rs.getInt("winning_rank"), rs.getInt("match_count"), rs.getBoolean("bonus_matched"),
                        rs.getBoolean("is_public"), rs.getString("issue_code"),
                        rs.getString("guest_token"), AdminJdbc.time(rs, "issued_at"),
                        List.of(rs.getInt("number_1"), rs.getInt("number_2"), rs.getInt("number_3"),
                                rs.getInt("number_4"), rs.getInt("number_5"), rs.getInt("number_6")),
                        AdminJdbc.time(rs, "verified_at")));
    }
}
