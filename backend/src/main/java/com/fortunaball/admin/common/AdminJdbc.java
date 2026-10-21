package com.fortunaball.admin.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 관리자 조회용 SQL 도우미. 목록(쪽 나누기)과 값 읽기를 한곳에 모았다.
 *
 * ⚠ 보안: fromWhere 와 orderBy 는 "코드에 적은 고정 글자"만 넣는다. 사용자 입력은 반드시 args 로 바인딩한다.
 *    (사용자 입력을 SQL 글자에 이어 붙이면 SQL 주입 위험이 생긴다)
 */
public final class AdminJdbc {

    private AdminJdbc() {
    }

    /**
     * @param columns   SELECT 뒤에 올 열 목록
     * @param fromWhere "FROM ... WHERE ..." 부분 (? 자리는 args 순서대로)
     * @param orderBy   ORDER BY 뒤에 올 글자
     */
    public static <T> AdminPage<T> page(JdbcTemplate jdbc, String columns, String fromWhere, List<Object> args,
                                        String orderBy, int page, int size, RowMapper<T> mapper) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + fromWhere, Long.class, args.toArray());
        long totalCount = total == null ? 0 : total;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<T> items = jdbc.query(
                "SELECT " + columns + " " + fromWhere + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                mapper, pageArgs.toArray());
        return new AdminPage<>(items, page, size, totalCount, (long) (page + 1) * size < totalCount);
    }

    public static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    public static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
