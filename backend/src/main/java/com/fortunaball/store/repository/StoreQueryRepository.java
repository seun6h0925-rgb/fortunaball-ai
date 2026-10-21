package com.fortunaball.store.repository;

import com.fortunaball.store.dto.StoreWinHistory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 판매점 조회용 저장소 (테이블 lotto_store + store_win_summary + store_winning_history).
 * 조회 전용이라 JdbcTemplate 으로 필요한 칸만 직접 읽는다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 모든 조회는 "폐점이 아니고(is_closed = 0) 좌표가 있는" 판매점만 대상이다.
 *    폐점 판매점의 당첨 이력과 집계는 DB 에 보관되지만 지도·검색·AI 답변에는 나오지 않는다.
 *  - 검색어는 LIKE 로 찾는다. 사용자가 쓴 % 와 _ 는 일반 글자로 취급하도록 앞에 \ 를 붙인다 (escapeLike).
 *  - 위치(위도·경도)는 조회 범위를 만드는 데만 쓰이고, 어디에도 기록하지 않는다.
 *  - 총 당첨 횟수 정렬: 1등(자동+수동+반자동) + 2등. 같으면 판매점 번호(id) 순.
 */
@Repository
public class StoreQueryRepository {

    /** 판매점 한 줄 (집계 포함). 서비스에서 StoreItem 으로 바꾼다. */
    public record StoreRow(long id, String name, String roadAddress, String jibunAddress, String sido, String sigungu,
                           double latitude, double longitude,
                           int firstAuto, int firstManual, int firstSemi, int second) { }

    /** 한 지역 조회 결과 한 쪽 + 전체 건수 */
    public record StorePage(List<StoreRow> rows, long total) { }

    /** 최근 기간 안의 판매점별 횟수 (루키 명당 판정용) */
    public record RecentCount(int first, int second) { }

    private static final String SELECT_STORE = "SELECT s.id, s.store_name, s.road_address, s.jibun_address, s.sido, s.sigungu, "
            + "s.latitude, s.longitude, "
            + "COALESCE(w.first_auto_count, 0), COALESCE(w.first_manual_count, 0), "
            + "COALESCE(w.first_semi_count, 0), COALESCE(w.second_count, 0) "
            + "FROM lotto_store s LEFT JOIN store_win_summary w ON w.lotto_store_id = s.id "
            + "WHERE s.is_closed = 0 AND s.latitude IS NOT NULL AND s.longitude IS NOT NULL";

    private static final String TOTAL_WINS_ORDER = " ORDER BY (COALESCE(w.first_auto_count, 0) + COALESCE(w.first_manual_count, 0) "
            + "+ COALESCE(w.first_semi_count, 0) + COALESCE(w.second_count, 0)) DESC, s.id";

    private final JdbcTemplate jdbc;

    public StoreQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 지역·검색어 조회 (총 당첨 횟수 많은 순). 조건이 비어 있으면 그 조건은 쓰지 않는다. */
    public StorePage search(String sido, String sigungu, String keyword, int page, int size) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (sido != null) {
            where.append(" AND s.sido = ?");
            args.add(sido);
        }
        if (sigungu != null) {
            where.append(" AND s.sigungu = ?");
            args.add(sigungu);
        }
        if (keyword != null) {
            where.append(" AND (s.store_name LIKE ? OR s.road_address LIKE ? OR s.jibun_address LIKE ?)");
            String like = "%" + escapeLike(keyword) + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM lotto_store s WHERE s.is_closed = 0 AND s.latitude IS NOT NULL AND s.longitude IS NOT NULL"
                        + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<StoreRow> rows = jdbc.query(SELECT_STORE + where + TOTAL_WINS_ORDER + " LIMIT ? OFFSET ?",
                (rs, i) -> toRow(rs), pageArgs.toArray());
        return new StorePage(rows, total == null ? 0 : total);
    }

    /** 네모 범위 안의 판매점 (내 주변 조회의 후보. 정확한 거리 계산은 서비스에서 한다). */
    public List<StoreRow> findInBox(double minLat, double maxLat, double minLng, double maxLng) {
        return jdbc.query(SELECT_STORE + " AND s.latitude BETWEEN ? AND ? AND s.longitude BETWEEN ? AND ?",
                (rs, i) -> toRow(rs), minLat, maxLat, minLng, maxLng);
    }

    /** 판매점 한 곳 (폐점이거나 없으면 비어 있음) */
    public Optional<StoreRow> findOpenById(long storeId) {
        List<StoreRow> rows = jdbc.query(SELECT_STORE + " AND s.id = ?", (rs, i) -> toRow(rs), storeId);
        return rows.stream().findFirst();
    }

    /** 판매점별 "기준일 이후" 1등·2등 횟수. 기준일 이후 당첨이 없는 판매점은 결과에 없다. */
    public Map<Long, RecentCount> recentCounts(Collection<Long> storeIds, LocalDate afterDate) {
        Map<Long, RecentCount> result = new HashMap<>();
        if (storeIds.isEmpty() || afterDate == null) {
            return result;
        }
        String placeholders = String.join(",", storeIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(afterDate);
        args.addAll(storeIds);
        jdbc.query("SELECT h.lotto_store_id, SUM(h.winning_rank = 1), SUM(h.winning_rank = 2) "
                        + "FROM store_winning_history h JOIN draw_round d ON d.round_no = h.round_no "
                        + "WHERE d.draw_date > ? AND h.lotto_store_id IN (" + placeholders + ") "
                        + "GROUP BY h.lotto_store_id",
                rs -> {
                    result.put(rs.getLong(1), new RecentCount(rs.getInt(2), rs.getInt(3)));
                }, args.toArray());
        return result;
    }

    /** 판매점의 회차별 당첨 이력 (최근 회차부터) */
    public List<StoreWinHistory> history(long storeId) {
        return jdbc.query("SELECT h.round_no, d.draw_date, h.winning_rank, h.win_type "
                        + "FROM store_winning_history h JOIN draw_round d ON d.round_no = h.round_no "
                        + "WHERE h.lotto_store_id = ? ORDER BY h.round_no DESC, h.winning_rank",
                (rs, i) -> new StoreWinHistory(rs.getInt(1), rs.getDate(2).toLocalDate(), rs.getInt(3), rs.getString(4)),
                storeId);
    }

    /** 최신 확정 회차의 추첨일 (데이터 기준일, 루키 명당의 기준일). 회차가 없으면 null. */
    public LocalDate latestDrawDate() {
        return jdbc.query("SELECT draw_date FROM draw_round ORDER BY round_no DESC LIMIT 1",
                rs -> rs.next() ? rs.getDate(1).toLocalDate() : null);
    }

    /** 당첨 횟수를 센 첫 회차 ("262회 이후 기준" 표시용). 이력이 없으면 null. */
    public Integer firstStatsRound() {
        return jdbc.query("SELECT MIN(round_no) FROM store_winning_history",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    int v = rs.getInt(1);
                    return rs.wasNull() ? null : v;
                });
    }

    private static StoreRow toRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoreRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getDouble(7), rs.getDouble(8),
                rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getInt(12));
    }

    /** LIKE 검색에서 % 와 _ 와 \ 를 일반 글자로 취급하게 만든다. */
    static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
