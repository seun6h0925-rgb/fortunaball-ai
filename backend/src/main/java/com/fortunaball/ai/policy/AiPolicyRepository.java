package com.fortunaball.ai.policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * AI 예외처리 정책 조회 (읽기 전용). 사용 테이블: ai_input_policy, ai_policy_keyword
 * 지금은 AI 호출 없이 고정 응답하는 정책(action_type = FIXED_RESPONSE)만 읽는다.
 *
 * 📚 배운 내용: JdbcTemplate
 */
@Repository
public class AiPolicyRepository {

    private final JdbcTemplate jdbc;

    public AiPolicyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AiPolicy> loadActive() {
        Map<Long, List<String>> keywords = new LinkedHashMap<>();
        jdbc.query("SELECT ai_input_policy_id, keyword FROM ai_policy_keyword WHERE is_active = 1 ORDER BY id",
                rs -> {
                    keywords.computeIfAbsent(rs.getLong("ai_input_policy_id"), k -> new ArrayList<>())
                            .add(rs.getString("keyword"));
                });
        return jdbc.query("SELECT id, policy_code, policy_type, feature_scope, priority, response_message "
                        + "FROM ai_input_policy WHERE is_active = 1 AND action_type = 'FIXED_RESPONSE' "
                        + "ORDER BY priority, id",
                (rs, n) -> new AiPolicy(rs.getString("policy_code"), rs.getString("policy_type"),
                        rs.getString("feature_scope"), rs.getInt("priority"), rs.getString("response_message"),
                        keywords.getOrDefault(rs.getLong("id"), List.of())));
    }
}
