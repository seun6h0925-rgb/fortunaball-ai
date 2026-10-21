package com.fortunaball.admin.dream.repository;

import com.fortunaball.admin.common.AdminJdbc;
import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.dream.dto.DreamHistoryItem;
import com.fortunaball.admin.dream.dto.DreamNumberItem;
import com.fortunaball.admin.dream.dto.DreamSourceItem;
import com.fortunaball.admin.dream.dto.DreamSymbolItem;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 관리자 꿈 상징(API-33·34·57)이 쓰는 DB 접근. 사용 테이블: dream_symbol, dream_symbol_alias, dream_symbol_number,
 * dream_source, dream_symbol_source, admin_action_log(읽기만). 사용자 입력은 전부 바인딩한다.
 * (SQL 글자에는 코드에 적은 고정 글자와 "?" 개수만 쓴다)
 */
@Repository
public class AdminDreamRepository {

    static final int HISTORY_LIMIT = 10;
    private static final String COLUMNS = "s.id, s.symbol_keyword, s.category, s.description, s.is_active, s.updated_at";

    private final JdbcTemplate jdbc;

    public AdminDreamRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------ 조회

    /** keyword: 상징 이름 또는 별칭에 포함, source: 출처 이름에 포함. null 이면 조건을 걸지 않는다. */
    public AdminPage<DreamSymbolItem> page(String keyword, String source, Boolean active, int page, int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder from = new StringBuilder("FROM dream_symbol s WHERE 1 = 1");
        if (keyword != null) {
            String like = like(keyword);
            from.append(" AND (s.symbol_keyword LIKE ? ESCAPE '!' OR EXISTS (SELECT 1 FROM dream_symbol_alias a "
                    + "WHERE a.dream_symbol_id = s.id AND a.alias_keyword LIKE ? ESCAPE '!'))");
            args.add(like);
            args.add(like);
        }
        if (source != null) {
            from.append(" AND EXISTS (SELECT 1 FROM dream_symbol_source l JOIN dream_source o ON o.id = l.dream_source_id "
                    + "WHERE l.dream_symbol_id = s.id AND o.source_name LIKE ? ESCAPE '!')");
            args.add(like(source));
        }
        if (active != null) {
            from.append(" AND s.is_active = ?");
            args.add(active);
        }
        AdminPage<Base> base = AdminJdbc.page(jdbc, COLUMNS, from.toString(), args, "s.id", page, size,
                (rs, i) -> new Base(rs.getLong("id"), rs.getString("symbol_keyword"), rs.getString("category"),
                        rs.getString("description"), rs.getBoolean("is_active"), AdminJdbc.time(rs, "updated_at")));
        List<DreamSymbolItem> items = assemble(base.items());
        return new AdminPage<>(items, base.page(), base.size(), base.totalCount(), base.hasNext());
    }

    public Optional<DreamSymbolItem> find(long id) {
        List<Base> rows = jdbc.query("SELECT " + COLUMNS + " FROM dream_symbol s WHERE s.id = ?",
                (rs, i) -> new Base(rs.getLong("id"), rs.getString("symbol_keyword"), rs.getString("category"),
                        rs.getString("description"), rs.getBoolean("is_active"), AdminJdbc.time(rs, "updated_at")), id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(assemble(rows).get(0));
    }

    public boolean sourceExists(long sourceId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM dream_source WHERE id = ?", Integer.class, sourceId);
        return n != null && n > 0;
    }

    private record Base(long id, String keyword, String category, String description, boolean active,
                        java.time.LocalDateTime updatedAt) {
    }

    private List<DreamSymbolItem> assemble(List<Base> bases) {
        if (bases.isEmpty()) {
            return List.of();
        }
        List<Long> ids = bases.stream().map(Base::id).toList();
        String in = String.join(",", ids.stream().map(x -> "?").toList());
        Object[] idArgs = ids.toArray();

        Map<Long, List<String>> aliases = new LinkedHashMap<>();
        jdbc.query("SELECT dream_symbol_id, alias_keyword FROM dream_symbol_alias WHERE dream_symbol_id IN (" + in
                + ") ORDER BY id", rs -> {
            aliases.computeIfAbsent(rs.getLong("dream_symbol_id"), k -> new ArrayList<>())
                    .add(rs.getString("alias_keyword"));
        }, idArgs);

        Map<Long, List<DreamNumberItem>> numbers = new LinkedHashMap<>();
        jdbc.query("SELECT dream_symbol_id, number_value, number_origin FROM dream_symbol_number "
                + "WHERE dream_symbol_id IN (" + in + ") ORDER BY number_value", rs -> {
            numbers.computeIfAbsent(rs.getLong("dream_symbol_id"), k -> new ArrayList<>())
                    .add(new DreamNumberItem(rs.getInt("number_value"), rs.getString("number_origin")));
        }, idArgs);

        Map<Long, List<DreamSourceItem>> sources = new LinkedHashMap<>();
        jdbc.query("SELECT l.dream_symbol_id, o.id, o.source_name, o.source_type, o.source_url, o.trust_level, l.memo "
                + "FROM dream_symbol_source l JOIN dream_source o ON o.id = l.dream_source_id "
                + "WHERE l.dream_symbol_id IN (" + in + ") ORDER BY l.id", rs -> {
            sources.computeIfAbsent(rs.getLong("dream_symbol_id"), k -> new ArrayList<>())
                    .add(new DreamSourceItem(rs.getLong("id"), rs.getString("source_name"), rs.getString("source_type"),
                            rs.getString("source_url"), rs.getString("trust_level"), rs.getString("memo")));
        }, idArgs);

        Map<Long, List<DreamHistoryItem>> history = new LinkedHashMap<>();
        jdbc.query("SELECT id, admin_user_id, action_type, target_id, action_detail, created_at FROM admin_action_log "
                + "WHERE target_table = 'dream_symbol' AND target_id IN (" + in + ") ORDER BY id DESC", rs -> {
            List<DreamHistoryItem> list = history.computeIfAbsent(rs.getLong("target_id"), k -> new ArrayList<>());
            if (list.size() < HISTORY_LIMIT) {
                list.add(new DreamHistoryItem(rs.getLong("id"), rs.getLong("admin_user_id"),
                        rs.getString("action_type"), rs.getString("action_detail"), AdminJdbc.time(rs, "created_at")));
            }
        }, idArgs);

        List<DreamSymbolItem> result = new ArrayList<>();
        for (Base b : bases) {
            result.add(new DreamSymbolItem(b.id(), b.keyword(), b.category(), b.description(), b.active(),
                    aliases.getOrDefault(b.id(), List.of()), numbers.getOrDefault(b.id(), List.of()),
                    sources.getOrDefault(b.id(), List.of()), history.getOrDefault(b.id(), List.of()), b.updatedAt()));
        }
        return result;
    }

    /** LIKE 검색어: 특별 글자(% _ !)를 글자 그대로 찾도록 막고 앞뒤에 %를 붙인다. */
    static String like(String raw) {
        return "%" + raw.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    // ------------------------------------------------------------ 쓰기

    public long insertSymbol(String keyword, String category, String description) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO dream_symbol (symbol_keyword, category, description, is_active) VALUES (?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, keyword);
            ps.setString(2, category);
            ps.setString(3, description);
            return ps;
        }, keys);
        return keyOf(keys);
    }

    public void updateSymbol(long id, String description, boolean active) {
        jdbc.update("UPDATE dream_symbol SET description = ?, is_active = ? WHERE id = ?", description, active, id);
    }

    public void insertAlias(long symbolId, String alias) {
        jdbc.update("INSERT INTO dream_symbol_alias (dream_symbol_id, alias_keyword, alias_type) VALUES (?, ?, '표현')",
                symbolId, alias);
    }

    public void deleteAlias(long symbolId, String alias) {
        jdbc.update("DELETE FROM dream_symbol_alias WHERE dream_symbol_id = ? AND alias_keyword = ?", symbolId, alias);
    }

    public void insertNumber(long symbolId, int number, String origin) {
        jdbc.update("INSERT INTO dream_symbol_number (dream_symbol_id, number_value, number_origin) VALUES (?, ?, ?)",
                symbolId, number, origin);
    }

    public void updateNumberOrigin(long symbolId, int number, String origin) {
        jdbc.update("UPDATE dream_symbol_number SET number_origin = ? WHERE dream_symbol_id = ? AND number_value = ?",
                origin, symbolId, number);
    }

    public void deleteNumber(long symbolId, int number) {
        jdbc.update("DELETE FROM dream_symbol_number WHERE dream_symbol_id = ? AND number_value = ?", symbolId, number);
    }

    public long insertSource(String name, String type, String url, String trust, String memo) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO dream_source (source_name, source_type, source_url, trust_level, memo) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, type);
            ps.setString(3, url);
            ps.setString(4, trust);
            ps.setString(5, memo);
            return ps;
        }, keys);
        return keyOf(keys);
    }

    public void insertLink(long symbolId, long sourceId, String memo) {
        jdbc.update("INSERT INTO dream_symbol_source (dream_symbol_id, dream_source_id, memo) VALUES (?, ?, ?)",
                symbolId, sourceId, memo);
    }

    public void updateLinkMemo(long symbolId, long sourceId, String memo) {
        jdbc.update("UPDATE dream_symbol_source SET memo = ? WHERE dream_symbol_id = ? AND dream_source_id = ?",
                memo, symbolId, sourceId);
    }

    public void deleteLink(long symbolId, long sourceId) {
        jdbc.update("DELETE FROM dream_symbol_source WHERE dream_symbol_id = ? AND dream_source_id = ?", symbolId, sourceId);
    }

    private static long keyOf(KeyHolder keys) {
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("저장한 행의 번호를 받지 못했습니다.");
        }
        return key.longValue();
    }
}
