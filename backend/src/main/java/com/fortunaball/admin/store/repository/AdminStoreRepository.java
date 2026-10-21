package com.fortunaball.admin.store.repository;

import com.fortunaball.admin.common.AdminJdbc;
import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.store.dto.AdminStoreItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 관리자 판매점 목록(API-53)과 수정(API-31)이 쓰는 DB 접근. 사용자 입력은 전부 바인딩한다. */
@Repository
public class AdminStoreRepository {

    private static final String COLUMNS = "id, shop_code, store_name, road_address, jibun_address, sido, sigungu, "
            + "latitude, longitude, geocode_status, is_closed";
    private static final RowMapper<AdminStoreItem> MAPPER = (rs, i) -> new AdminStoreItem(rs.getLong("id"),
            rs.getString("shop_code"), rs.getString("store_name"), rs.getString("road_address"),
            rs.getString("jibun_address"), rs.getString("sido"), rs.getString("sigungu"),
            rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getString("geocode_status"),
            rs.getBoolean("is_closed"));

    private final JdbcTemplate jdbc;

    public AdminStoreRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AdminPage<AdminStoreItem> list(String sido, String sigungu, String keyword, String geocodeStatus,
                                          Boolean closed, int page, int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("FROM lotto_store WHERE 1 = 1");
        if (sido != null) {
            where.append(" AND sido = ?");
            args.add(sido);
        }
        if (sigungu != null) {
            where.append(" AND sigungu = ?");
            args.add(sigungu);
        }
        if (keyword != null) {
            where.append(" AND (store_name LIKE ? OR road_address LIKE ? OR jibun_address LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (geocodeStatus != null) {
            where.append(" AND geocode_status = ?");
            args.add(geocodeStatus);
        }
        if (closed != null) {
            where.append(" AND is_closed = ?");
            args.add(closed);
        }
        return AdminJdbc.page(jdbc, COLUMNS, where.toString(), args, "id ASC", page, size, MAPPER);
    }

    public Optional<AdminStoreItem> find(long storeId) {
        List<AdminStoreItem> rows = jdbc.query("SELECT " + COLUMNS + " FROM lotto_store WHERE id = ?", MAPPER, storeId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 값을 모두 덮어쓴다. (바뀌지 않는 항목은 서비스가 기존 값을 그대로 넘긴다) 좌표가 바뀌었으면 좌표 변환일자를 갱신한다. */
    public void update(long storeId, String name, String road, String jibun, String sido, String sigungu,
                       BigDecimal lat, BigDecimal lon, String geocodeStatus, boolean coordsChanged, LocalDateTime now) {
        jdbc.update("UPDATE lotto_store SET store_name = ?, road_address = ?, jibun_address = ?, sido = ?, "
                        + "sigungu = ?, latitude = ?, longitude = ?, geocode_status = ?, "
                        + "geocoded_at = CASE WHEN ? THEN ? ELSE geocoded_at END WHERE id = ?",
                name, road, jibun, sido, sigungu, lat, lon, geocodeStatus, coordsChanged, now, storeId);
    }
}
