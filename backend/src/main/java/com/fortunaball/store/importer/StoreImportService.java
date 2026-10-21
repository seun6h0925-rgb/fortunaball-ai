package com.fortunaball.store.importer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 판매점 당첨 CSV → DB 가져오기 (처음 한 번 넣는 도구).
 *
 *  1) 판매점            → lotto_store          (판매점 번호 shop_code 기준으로 없으면 넣고, 있으면 고친다)
 *  2) 회차별 당첨 이력  → store_winning_history (CSV 의 회차 범위를 지우고 다시 넣는다)
 *  3) 판매점별 집계     → store_win_summary     (판매점당 1행. 있으면 고친다)
 *  전체가 한 트랜잭션이다. 도중에 하나라도 실패하면 아무것도 저장되지 않는다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 몇 번을 다시 실행해도 결과가 같다 (판매점·집계는 덮어쓰고, 이력은 회차 범위를 지우고 다시 넣기 때문).
 *  - 폐점 판매점도 넣는다 (is_closed = 1). 당첨 이력과 집계는 그대로 보관하고,
 *    지도·검색·AI 답변에서만 is_closed = 0 인 판매점만 쓴다.
 *  - store_winning_history.round_no 는 draw_round 를 참조한다(FK). CSV 의 회차가 draw_round 에 없으면
 *    시작 전에 어느 회차가 없는지 알려 주고 멈춘다 (넣다가 중간에 FK 오류가 나는 것보다 추적하기 쉽다).
 *  - source_base_date(자료 기준일)는 CSV 의 마지막 회차의 추첨일로 넣는다. 화면의 "데이터 기준일" 표시에 쓴다.
 *  - 좌표는 CSV 에 이미 있으므로 geocode_status 는 SUCCESS 로 넣는다 (좌표 변환 API 는 이번에 쓰지 않는다).
 *  - 한 줄씩 넣으면 느리므로 1000건씩 묶어서(batch) 넣는다.
 */
@Service
public class StoreImportService {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbc;

    public StoreImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 가져오기 결과 요약 (로그에 남긴다) */
    public record Result(int stores, int closedStores, int histories, int summaries,
                         int minRound, int maxRound, LocalDate baseDate) { }

    @Transactional
    public Result importFrom(Path csvFile) throws IOException {
        List<StoreCsvRow> rows = StoreCsvParser.parse(csvFile);
        if (rows.isEmpty()) {
            throw new IllegalStateException("CSV 에 데이터가 없습니다: " + csvFile);
        }
        return importRows(rows);
    }

    /** CSV 읽기와 DB 넣기를 나눠 둔 것 (읽기는 파일 없이도 확인할 수 있게). 관리자 업로드 반영(API-50)도 이 메서드를 쓴다. 호출하는 쪽에 트랜잭션이 있어야 한다. */
    public Result importRows(List<StoreCsvRow> rows) {
        int minRound = rows.stream().mapToInt(StoreCsvRow::round).min().orElseThrow();
        int maxRound = rows.stream().mapToInt(StoreCsvRow::round).max().orElseThrow();

        checkRoundsExist(rows, minRound, maxRound);
        LocalDate baseDate = jdbc.query("SELECT draw_date FROM draw_round WHERE round_no = ?",
                rs -> rs.next() ? rs.getDate(1).toLocalDate() : null, maxRound);

        // 1) 판매점 (판매점 번호마다 한 곳. CSV 에서 같은 판매점은 값이 모두 같다)
        Map<String, StoreCsvRow> stores = new LinkedHashMap<>();
        for (StoreCsvRow row : rows) {
            stores.putIfAbsent(row.shopCode(), row);
        }
        upsertStores(new ArrayList<>(stores.values()), baseDate);
        Map<String, Long> storeIds = loadStoreIds();

        // 2) 회차별 당첨 이력 (CSV 의 회차 범위는 지우고 다시 넣는다)
        jdbc.update("DELETE FROM store_winning_history WHERE round_no BETWEEN ? AND ?", minRound, maxRound);
        insertHistories(rows, storeIds);

        // 3) 판매점별 집계
        Map<String, StoreWinSummaryCalculator.Summary> summaries = StoreWinSummaryCalculator.calculate(rows);
        upsertSummaries(summaries, storeIds, baseDate);

        int closed = (int) stores.values().stream().filter(StoreCsvRow::closed).count();
        return new Result(stores.size(), closed, rows.size(), summaries.size(), minRound, maxRound, baseDate);
    }

    /** CSV 의 회차가 draw_round 에 모두 있는지 확인. 없으면 어느 회차인지 알려 주고 멈춘다. */
    private void checkRoundsExist(List<StoreCsvRow> rows, int minRound, int maxRound) {
        Set<Integer> have = new HashSet<>(jdbc.queryForList(
                "SELECT round_no FROM draw_round WHERE round_no BETWEEN ? AND ?", Integer.class, minRound, maxRound));
        Set<Integer> missing = new TreeSet<>();
        for (StoreCsvRow row : rows) {
            if (!have.contains(row.round())) {
                missing.add(row.round());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("draw_round 에 없는 회차가 있어 가져올 수 없습니다 (회차 수집을 먼저 하세요). 없는 회차: "
                    + missing.stream().limit(20).toList() + (missing.size() > 20 ? " 외 " + (missing.size() - 20) + "개" : ""));
        }
    }

    private void upsertStores(List<StoreCsvRow> stores, LocalDate baseDate) {
        String sql = "INSERT INTO lotto_store (shop_code, store_name, road_address, jibun_address, sido, sigungu, "
                + "latitude, longitude, geocode_status, source_base_date, is_closed) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?, ?) "
                + "ON DUPLICATE KEY UPDATE store_name = VALUES(store_name), road_address = VALUES(road_address), "
                + "jibun_address = VALUES(jibun_address), sido = VALUES(sido), sigungu = VALUES(sigungu), "
                + "latitude = VALUES(latitude), longitude = VALUES(longitude), geocode_status = 'SUCCESS', "
                + "source_base_date = VALUES(source_base_date), is_closed = VALUES(is_closed)";
        jdbc.batchUpdate(sql, stores, BATCH_SIZE, (ps, s) -> {
            StoreAddressParser.Region region = StoreAddressParser.parse(s.region(), s.roadAddress(), s.jibunAddress());
            ps.setString(1, s.shopCode());
            ps.setString(2, s.storeName());
            ps.setString(3, blankToNull(s.roadAddress()));
            ps.setString(4, blankToNull(s.jibunAddress()));
            ps.setString(5, region.sido());
            ps.setString(6, region.sigungu());
            ps.setBigDecimal(7, s.latitude());
            ps.setBigDecimal(8, s.longitude());
            ps.setObject(9, baseDate);
            ps.setBoolean(10, s.closed());
        });
    }

    private Map<String, Long> loadStoreIds() {
        Map<String, Long> ids = new LinkedHashMap<>();
        jdbc.query("SELECT id, shop_code FROM lotto_store WHERE shop_code IS NOT NULL",
                rs -> {
                    ids.put(rs.getString("shop_code"), rs.getLong("id"));
                });
        return ids;
    }

    private void insertHistories(List<StoreCsvRow> rows, Map<String, Long> storeIds) {
        String sql = "INSERT INTO store_winning_history (lotto_store_id, round_no, winning_rank, win_type) VALUES (?, ?, ?, ?)";
        jdbc.batchUpdate(sql, rows, BATCH_SIZE, (ps, r) -> {
            ps.setLong(1, storeIds.get(r.shopCode()));
            ps.setInt(2, r.round());
            ps.setInt(3, r.rank());
            ps.setString(4, r.winType());   // 구분이 없으면 null
        });
    }

    private void upsertSummaries(Map<String, StoreWinSummaryCalculator.Summary> summaries,
                                 Map<String, Long> storeIds, LocalDate baseDate) {
        String sql = "INSERT INTO store_win_summary (lotto_store_id, first_auto_count, first_manual_count, "
                + "first_semi_count, second_count, match_status, source_base_date) VALUES (?, ?, ?, ?, ?, 'MATCHED', ?) "
                + "ON DUPLICATE KEY UPDATE first_auto_count = VALUES(first_auto_count), "
                + "first_manual_count = VALUES(first_manual_count), first_semi_count = VALUES(first_semi_count), "
                + "second_count = VALUES(second_count), match_status = 'MATCHED', source_base_date = VALUES(source_base_date)";
        List<Map.Entry<String, StoreWinSummaryCalculator.Summary>> entries = new ArrayList<>(summaries.entrySet());
        jdbc.batchUpdate(sql, entries, BATCH_SIZE, (ps, e) -> {
            StoreWinSummaryCalculator.Summary s = e.getValue();
            ps.setLong(1, storeIds.get(e.getKey()));
            ps.setInt(2, s.firstAuto());
            ps.setInt(3, s.firstManual());
            ps.setInt(4, s.firstSemi());
            ps.setInt(5, s.second());
            ps.setObject(6, baseDate);
        });
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
