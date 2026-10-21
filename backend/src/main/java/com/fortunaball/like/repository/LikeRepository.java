package com.fortunaball.like.repository;

import java.sql.Date;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 좋아요 DB 접근. 사용 테이블: service_like_total(전체 수 1행), service_like_daily(하루·사람별 누른 횟수).
 * 사용자 입력은 전부 바인딩한다. (표는 LikeSchemaInitializer 가 없으면 만든다)
 */
@Repository
public class LikeRepository {

    private final JdbcTemplate jdbc;

    public LikeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 서비스 전체 좋아요 수. 아직 아무도 안 눌렀으면 0. */
    public long total() {
        Long v = jdbc.query("SELECT total_count FROM service_like_total WHERE id = 1",
                rs -> rs.next() ? rs.getLong(1) : 0L);
        return v == null ? 0L : v;
    }

    /** 전체 수를 1 올리고 올린 뒤의 값을 돌려준다. (행이 없으면 1로 만든다) */
    public long incrementTotal() {
        jdbc.update("INSERT INTO service_like_total (id, total_count) VALUES (1, 1) "
                + "ON DUPLICATE KEY UPDATE total_count = total_count + 1");
        return total();
    }

    /** 그 날짜·그 사람(actorKey)이 오늘 누른 횟수. */
    public int todayCount(LocalDate date, String actorKey) {
        Integer v = jdbc.query("SELECT click_count FROM service_like_daily WHERE like_date = ? AND actor_key = ?",
                rs -> rs.next() ? rs.getInt(1) : 0, Date.valueOf(date), actorKey);
        return v == null ? 0 : v;
    }

    /**
     * 한도(limit) 안이면 횟수를 1 올리고 true, 한도에 닿았으면 false. (limit 은 1 이상)
     * 먼저 올리기를 시도하고, 행이 없으면 새로 넣는다. 동시에 눌러도 한도를 넘지 않는다.
     */
    public boolean tryIncrement(LocalDate date, String actorKey, int limit) {
        int updated = jdbc.update("UPDATE service_like_daily SET click_count = click_count + 1 "
                + "WHERE like_date = ? AND actor_key = ? AND click_count < ?", Date.valueOf(date), actorKey, limit);
        if (updated > 0) {
            return true;
        }
        int inserted = jdbc.update("INSERT IGNORE INTO service_like_daily (like_date, actor_key, click_count) "
                + "VALUES (?, ?, 1)", Date.valueOf(date), actorKey);
        return inserted > 0;
    }

    /** 오래된 하루 기록을 지운다. (전체 수는 service_like_total 에 따로 있어 영향 없음) */
    public int deleteBefore(LocalDate date) {
        return jdbc.update("DELETE FROM service_like_daily WHERE like_date < ?", Date.valueOf(date));
    }
}
