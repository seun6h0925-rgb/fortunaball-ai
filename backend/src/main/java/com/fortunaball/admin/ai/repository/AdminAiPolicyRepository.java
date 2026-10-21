package com.fortunaball.admin.ai.repository;

import com.fortunaball.admin.ai.dto.AiPolicyItem;
import com.fortunaball.admin.ai.dto.AiPolicyKeywordItem;
import com.fortunaball.admin.common.AdminJdbc;
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
 * 관리자 AI 입력 정책(API-54~56)이 쓰는 DB 접근. 사용 테이블: ai_input_policy, ai_policy_keyword.
 * 사용자 입력은 전부 바인딩한다. (SQL 글자에는 코드에 적은 고정 글자만 쓴다)
 */
@Repository
public class AdminAiPolicyRepository {

    private static final String POLICY_COLUMNS = "id, policy_code, policy_name, feature_scope, policy_type, "
            + "action_type, priority, response_message, is_active, updated_at";

    private final JdbcTemplate jdbc;

    public AdminAiPolicyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** scope / active 가 null 이면 그 조건은 걸지 않는다. 우선순위 순(같으면 번호 순). */
    public List<AiPolicyItem> list(String featureScope, Boolean active) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT " + POLICY_COLUMNS + " FROM ai_input_policy WHERE 1 = 1");
        if (featureScope != null) {
            sql.append(" AND feature_scope = ?");
            args.add(featureScope);
        }
        if (active != null) {
            sql.append(" AND is_active = ?");
            args.add(active);
        }
        sql.append(" ORDER BY priority, id");
        Map<Long, List<AiPolicyKeywordItem>> keywords = loadKeywords();
        return jdbc.query(sql.toString(), (rs, i) -> toItem(rs, keywords), args.toArray());
    }

    public Optional<AiPolicyItem> find(long policyId) {
        Map<Long, List<AiPolicyKeywordItem>> keywords = loadKeywords();
        List<AiPolicyItem> rows = jdbc.query("SELECT " + POLICY_COLUMNS + " FROM ai_input_policy WHERE id = ?",
                (rs, i) -> toItem(rs, keywords), policyId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Map<Long, List<AiPolicyKeywordItem>> loadKeywords() {
        Map<Long, List<AiPolicyKeywordItem>> map = new LinkedHashMap<>();
        jdbc.query("SELECT id, ai_input_policy_id, keyword, match_type, is_active FROM ai_policy_keyword ORDER BY id",
                rs -> {
                    map.computeIfAbsent(rs.getLong("ai_input_policy_id"), k -> new ArrayList<>())
                            .add(new AiPolicyKeywordItem(rs.getLong("id"), rs.getString("keyword"),
                                    rs.getString("match_type"), rs.getBoolean("is_active")));
                });
        return map;
    }

    private static AiPolicyItem toItem(java.sql.ResultSet rs, Map<Long, List<AiPolicyKeywordItem>> keywords)
            throws java.sql.SQLException {
        long id = rs.getLong("id");
        return new AiPolicyItem(id, rs.getString("policy_code"), rs.getString("policy_name"),
                rs.getString("feature_scope"), rs.getString("policy_type"), rs.getString("action_type"),
                rs.getInt("priority"), rs.getString("response_message"), rs.getBoolean("is_active"),
                keywords.getOrDefault(id, List.of()), AdminJdbc.time(rs, "updated_at"));
    }

    public long insertPolicy(String code, String name, String scope, String type, String action, int priority,
                             String message) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ai_input_policy (policy_code, policy_name, feature_scope, policy_type, action_type, "
                            + "priority, response_message, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, scope);
            ps.setString(4, type);
            ps.setString(5, action);
            ps.setInt(6, priority);
            ps.setString(7, message);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("정책 번호를 받지 못했습니다.");
        }
        return key.longValue();
    }

    public void updatePolicy(long policyId, String name, int priority, String message, boolean active) {
        jdbc.update("UPDATE ai_input_policy SET policy_name = ?, priority = ?, response_message = ?, is_active = ? "
                + "WHERE id = ?", name, priority, message, active, policyId);
    }

    public void insertKeyword(long policyId, String keyword, String matchType, boolean active) {
        jdbc.update("INSERT INTO ai_policy_keyword (ai_input_policy_id, keyword, match_type, is_active) "
                + "VALUES (?, ?, ?, ?)", policyId, keyword, matchType, active);
    }

    public void updateKeyword(long keywordId, String matchType, boolean active) {
        jdbc.update("UPDATE ai_policy_keyword SET match_type = ?, is_active = ? WHERE id = ?",
                matchType, active, keywordId);
    }

    public void deleteKeyword(long keywordId) {
        jdbc.update("DELETE FROM ai_policy_keyword WHERE id = ?", keywordId);
    }
}
