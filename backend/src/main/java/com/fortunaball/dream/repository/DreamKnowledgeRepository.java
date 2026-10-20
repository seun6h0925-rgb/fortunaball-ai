package com.fortunaball.dream.repository;

import com.fortunaball.dream.domain.DreamKnowledge.Interpretation;
import com.fortunaball.dream.domain.DreamKnowledge.SourceRef;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolEntry;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolNumber;
import com.fortunaball.dream.domain.DreamNumberOrigin;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 꿈 지식베이스 조회 (읽기 전용). 사용 테이블:
 * dream_symbol, dream_symbol_alias, dream_interpretation, dream_symbol_number, dream_symbol_source, dream_source
 *
 * 📚 배운 내용: JdbcTemplate (SQL 을 직접 쓰고 결과를 행 단위로 읽는 방식)
 *
 * ⚠ 사용자가 쓴 꿈 내용은 이 클래스로 넘어오지 않는다. 여기서는 상징 번호(id)로만 조회한다.
 */
@Repository
public class DreamKnowledgeRepository {

    private final JdbcTemplate jdbc;

    public DreamKnowledgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 사용 중인(is_active) 상징과 별칭 전체. 79개 안팎이라 매 요청마다 읽어도 부담이 작다. */
    public List<SymbolEntry> loadActiveSymbols() {
        Map<Long, String> keywords = new LinkedHashMap<>();
        jdbc.query("SELECT id, symbol_keyword FROM dream_symbol WHERE is_active = 1 ORDER BY id",
                rs -> {
                    keywords.put(rs.getLong("id"), rs.getString("symbol_keyword"));
                });
        Map<Long, List<String>> aliases = new LinkedHashMap<>();
        jdbc.query("SELECT a.dream_symbol_id, a.alias_keyword FROM dream_symbol_alias a "
                        + "JOIN dream_symbol s ON s.id = a.dream_symbol_id WHERE s.is_active = 1 ORDER BY a.id",
                rs -> {
                    aliases.computeIfAbsent(rs.getLong("dream_symbol_id"), k -> new ArrayList<>())
                            .add(rs.getString("alias_keyword"));
                });
        List<SymbolEntry> result = new ArrayList<>();
        keywords.forEach((id, keyword) -> result.add(new SymbolEntry(id, keyword, aliases.getOrDefault(id, List.of()))));
        return result;
    }

    public List<Interpretation> loadInterpretations(Collection<Long> symbolIds) {
        if (symbolIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT i.dream_symbol_id, i.situation_keyword, i.interpretation_summary, i.polarity, "
                + "i.confidence, i.origin_region, src.source_name "
                + "FROM dream_interpretation i LEFT JOIN dream_source src ON src.id = i.dream_source_id "
                + "WHERE i.dream_symbol_id IN (" + placeholders(symbolIds.size()) + ") ORDER BY i.id";
        return jdbc.query(sql, (rs, n) -> new Interpretation(
                rs.getLong("dream_symbol_id"), rs.getString("situation_keyword"),
                rs.getString("interpretation_summary"), rs.getString("polarity"), rs.getString("confidence"),
                rs.getString("origin_region"), rs.getString("source_name")), symbolIds.toArray());
    }

    public List<SymbolNumber> loadNumbers(Collection<Long> symbolIds) {
        if (symbolIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT dream_symbol_id, number_value, number_origin FROM dream_symbol_number "
                + "WHERE dream_symbol_id IN (" + placeholders(symbolIds.size()) + ") ORDER BY id";
        return jdbc.query(sql, (rs, n) -> new SymbolNumber(rs.getLong("dream_symbol_id"), rs.getInt("number_value"),
                DreamNumberOrigin.fromDb(rs.getString("number_origin"))), symbolIds.toArray());
    }

    public List<SourceRef> loadSources(Collection<Long> symbolIds) {
        if (symbolIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT ss.dream_symbol_id, src.source_name, src.source_type, src.trust_level "
                + "FROM dream_symbol_source ss JOIN dream_source src ON src.id = ss.dream_source_id "
                + "WHERE ss.dream_symbol_id IN (" + placeholders(symbolIds.size()) + ") ORDER BY src.id";
        return jdbc.query(sql, (rs, n) -> new SourceRef(rs.getLong("dream_symbol_id"), rs.getString("source_name"),
                rs.getString("source_type"), rs.getString("trust_level")), symbolIds.toArray());
    }

    /** IN (?, ?, ?) 의 물음표 자리. 값은 항상 물음표로 넘기고 문자열에 직접 붙이지 않는다(SQL 주입 방지). */
    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }
}
