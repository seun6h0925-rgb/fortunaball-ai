package com.fortunaball.admin.store.repository;

import com.fortunaball.admin.common.AdminJdbc;
import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.common.AdminQuery;
import com.fortunaball.admin.store.dto.StoreUploadRow;
import com.fortunaball.admin.store.service.StoreDiffCalculator.ExistingStore;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 판매점 파일 업로드(API-30, 49~52)가 쓰는 DB 접근 모음. SQL 은 모두 이 파일 안에 있다.
 *
 * ⚠ 보안: SQL 글자에는 코드에 적은 고정 글자와 "?" 만 이어 붙인다. 사용자 입력은 전부 바인딩한다.
 * ⚠ lockUpload 는 SELECT ... FOR UPDATE 라서 호출한 쪽에 트랜잭션이 있어야 의미가 있다.
 *    (같은 업로드를 두 번 반영·취소하려는 동시 요청을 한 줄로 세우기 위해 쓴다)
 */
@Repository
public class StoreUploadRepository {

    private static final int IN_CHUNK = 500;
    private static final String UPLOAD_COLUMNS = "id, upload_type, file_name, base_date, total_rows, new_rows, "
            + "changed_rows, status, admin_user_id, error_message, uploaded_at, applied_at";

    private final JdbcTemplate jdbc;

    public StoreUploadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ 비교용 읽기

    /** 판매점 번호가 있는 판매점 전체 (판매점 번호 → 값) */
    public Map<String, ExistingStore> loadExistingStores() {
        Map<String, ExistingStore> map = new LinkedHashMap<>();
        jdbc.query("SELECT id, shop_code, store_name, road_address, jibun_address, sido, sigungu, latitude, longitude, "
                + "is_closed FROM lotto_store WHERE shop_code IS NOT NULL", rs -> {
            map.put(rs.getString("shop_code"), new ExistingStore(rs.getLong("id"), rs.getString("store_name"),
                    rs.getString("road_address"), rs.getString("jibun_address"), rs.getString("sido"),
                    rs.getString("sigungu"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
                    rs.getBoolean("is_closed")));
        });
        return map;
    }

    /** 회차 범위 안의 당첨 이력 개수 (열쇠는 StoreDiffCalculator.historyKey 와 같은 모양) */
    public Map<String, Integer> loadHistoryCounts(int minRound, int maxRound) {
        Map<String, Integer> map = new LinkedHashMap<>();
        jdbc.query("SELECT s.shop_code, h.round_no, h.winning_rank, h.win_type, COUNT(*) AS cnt "
                + "FROM store_winning_history h JOIN lotto_store s ON s.id = h.lotto_store_id "
                + "WHERE h.round_no BETWEEN ? AND ? AND s.shop_code IS NOT NULL "
                + "GROUP BY s.shop_code, h.round_no, h.winning_rank, h.win_type", rs -> {
            String key = com.fortunaball.admin.store.service.StoreDiffCalculator.historyKey(rs.getString("shop_code"),
                    rs.getInt("round_no"), rs.getInt("winning_rank"), rs.getString("win_type"));
            map.put(key, rs.getInt("cnt"));
        }, minRound, maxRound);
        return map;
    }

    /** 범위 안에서 draw_round 에 실제로 있는 회차 번호들 */
    public Set<Integer> findExistingRounds(int minRound, int maxRound) {
        return new HashSet<>(jdbc.queryForList("SELECT round_no FROM draw_round WHERE round_no BETWEEN ? AND ?",
                Integer.class, minRound, maxRound));
    }

    public LocalDate drawDate(int roundNo) {
        return jdbc.query("SELECT draw_date FROM draw_round WHERE round_no = ?",
                rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null, roundNo);
    }

    public int countGeocodePending() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lotto_store WHERE geocode_status <> 'SUCCESS'",
                Integer.class);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------ 업로드 이력

    public long insertUpload(String uploadType, String fileName, LocalDate baseDate, int totalRows, int newRows,
                             int changedRows, String status, long adminUserId) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO store_data_upload (upload_type, file_name, base_date, total_rows, new_rows, "
                            + "changed_rows, status, admin_user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, uploadType);
            ps.setString(2, fileName);
            ps.setDate(3, baseDate == null ? null : Date.valueOf(baseDate));
            ps.setInt(4, totalRows);
            ps.setInt(5, newRows);
            ps.setInt(6, changedRows);
            ps.setString(7, status);
            ps.setLong(8, adminUserId);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("업로드 번호를 받지 못했습니다.");
        }
        return key.longValue();
    }

    public Optional<StoreUploadRow> find(long uploadId) {
        return query("SELECT " + UPLOAD_COLUMNS + " FROM store_data_upload WHERE id = ?", uploadId);
    }

    /** 행을 잠그고 읽는다. 반드시 트랜잭션 안에서 부른다. */
    public Optional<StoreUploadRow> lockUpload(long uploadId) {
        return query("SELECT " + UPLOAD_COLUMNS + " FROM store_data_upload WHERE id = ? FOR UPDATE", uploadId);
    }

    private Optional<StoreUploadRow> query(String sql, long uploadId) {
        List<StoreUploadRow> rows = jdbc.query(sql, (rs, i) -> mapUpload(rs), uploadId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void markApplied(long uploadId, LocalDateTime now) {
        jdbc.update("UPDATE store_data_upload SET status = 'APPLIED', applied_at = ? WHERE id = ?", now, uploadId);
    }

    public void markCanceled(long uploadId) {
        jdbc.update("UPDATE store_data_upload SET status = 'CANCELED' WHERE id = ?", uploadId);
    }

    public void markFailed(long uploadId, String message) {
        jdbc.update("UPDATE store_data_upload SET status = 'FAILED', error_message = ? WHERE id = ?", message, uploadId);
    }

    public AdminPage<StoreUploadRow> history(LocalDateTime start, LocalDateTime endExclusive, String status,
                                             int page, int size) {
        List<Object> args = new ArrayList<>(List.of(start, endExclusive));
        StringBuilder where = new StringBuilder("FROM store_data_upload WHERE uploaded_at >= ? AND uploaded_at < ?");
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status);
        }
        return AdminJdbc.page(jdbc, UPLOAD_COLUMNS, where.toString(), args, "uploaded_at DESC, id DESC",
                AdminQuery.page(page), AdminQuery.size(size), (rs, i) -> mapUpload(rs));
    }

    private static StoreUploadRow mapUpload(ResultSet rs) throws SQLException {
        Date base = rs.getDate("base_date");
        return new StoreUploadRow(rs.getLong("id"), rs.getString("upload_type"), rs.getString("file_name"),
                base == null ? null : base.toLocalDate(), rs.getInt("total_rows"), rs.getInt("new_rows"),
                rs.getInt("changed_rows"), rs.getString("status"), rs.getLong("admin_user_id"),
                rs.getString("error_message"), AdminJdbc.time(rs, "uploaded_at"), AdminJdbc.time(rs, "applied_at"));
    }

    // ------------------------------------------------------------------ 반영 뒤 표시

    /** 새로 생긴 판매점에 "처음 확인된 업로드"를 적는다. (이미 적힌 판매점은 건드리지 않는다) */
    public void stampFirstSeen(long uploadId, Collection<String> shopCodes, LocalDateTime now) {
        for (List<String> chunk : chunks(shopCodes)) {
            List<Object> args = new ArrayList<>();
            args.add(uploadId);
            args.add(now);
            args.addAll(chunk);
            jdbc.update("UPDATE lotto_store SET first_seen_upload_id = ?, first_seen_at = ? "
                    + "WHERE first_seen_upload_id IS NULL AND shop_code IN (" + marks(chunk.size()) + ")", args.toArray());
        }
    }

    /** 이번 파일에 들어 있던 판매점의 집계에 "반영한 업로드"를 적는다. */
    public void stampSummaryUpload(long uploadId, Collection<String> shopCodes) {
        for (List<String> chunk : chunks(shopCodes)) {
            List<Object> args = new ArrayList<>();
            args.add(uploadId);
            args.addAll(chunk);
            jdbc.update("UPDATE store_win_summary w JOIN lotto_store s ON s.id = w.lotto_store_id "
                    + "SET w.upload_id = ? WHERE s.shop_code IN (" + marks(chunk.size()) + ")", args.toArray());
        }
    }

    private static List<List<String>> chunks(Collection<String> codes) {
        List<String> all = new ArrayList<>(codes);
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < all.size(); i += IN_CHUNK) {
            result.add(all.subList(i, Math.min(all.size(), i + IN_CHUNK)));
        }
        return result;
    }

    /** "?, ?, ?" 모양 글자 (개수만 사용자 입력과 무관하게 코드가 정한다) */
    private static String marks(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }
}
