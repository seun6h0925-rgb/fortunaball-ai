-- 당첨 보장 요구 키워드 추가 (2026-10-22). seed_ai_policy.sql 과 같은 내용이며, 이미 쌓인 DB 에 이것만 넣을 때 쓴다.
INSERT INTO ai_policy_keyword (ai_input_policy_id, keyword, match_type, is_active)
SELECT p.id, w.keyword, 'CONTAINS', 1
  FROM ai_input_policy p
  JOIN (
    SELECT '무조건1등' AS keyword UNION ALL SELECT '1등번호달라' UNION ALL SELECT '1등번호줘' UNION ALL SELECT '1등확정'
    UNION ALL SELECT '반드시당첨' UNION ALL SELECT '무조건되는번호' UNION ALL SELECT '무조건맞는번호'
  ) w
 WHERE p.policy_code = 'GUARANTEE_REQUEST'
   AND NOT EXISTS (SELECT 1 FROM ai_policy_keyword k WHERE k.ai_input_policy_id = p.id AND k.keyword = w.keyword);
SELECT COUNT(*) AS guarantee_keywords FROM ai_policy_keyword k JOIN ai_input_policy p ON p.id = k.ai_input_policy_id WHERE p.policy_code = 'GUARANTEE_REQUEST';
