-- AI 예외처리 정책 시드 (ai_input_policy / ai_policy_keyword)
-- 여러 번 실행해도 같은 결과가 되도록 정책은 policy_code 기준으로 덮어쓰고, 키워드는 지운 뒤 다시 넣는다.
--
-- 판별 규칙 (PolicyEngine, 코드 쪽에서 구현)
--  * 질문 문자열에서 공백을 모두 제거한 뒤 keyword(공백 없이 저장)가 포함되는지 본다. (match_type=CONTAINS)
--  * 우선순위(priority) 오름차순으로 검사해 처음 걸린 정책의 response_message 를 고정 응답한다. AI 호출·횟수 차감 없음.
--  * LOCATION_REQUIRED 는 위치(위도·경도)가 요청에 없을 때만 적용한다.
--  * OFF_TOPIC_MAP 은 예외적으로 "허용 키워드" 목록이다. 위치가 함께 온 지도 문맥에서 키워드가 하나도 없으면 차단한다.
--  * OFF_TOPIC_ANALYST 도 "허용 키워드" 목록이다. 지도가 아닌 애널리스트 질문에서 번호 생성·회차·발급코드 같은 로또 입력도 없고 키워드도 없으면 차단한다.
--  * DREAM_OFF_TOPIC 도 "허용 키워드" 목록이다. 상징이 없고 꿈 표현(꿈·꿨 등)도 없으면 꿈이 아닌 입력으로 보고 차단한다.
--  * DREAM_LOW_RELEVANCE 는 관리자 화면 표시용이다. 꿈풀이 코드는 아직 상수 문구를 쓴다.

-- ========== 정책 ==========
INSERT INTO ai_input_policy (policy_code, policy_name, feature_scope, policy_type, action_type, priority, response_message, is_active) VALUES
('DESPERATE_REQUEST', '과몰입·절박한 요청', 'ALL', 'DESPERATE_REQUEST', 'FIXED_RESPONSE', 10,
 '로또는 당첨을 보장할 수 없는 무작위 추첨입니다.\n경제적 문제 해결 수단으로 로또를 이용하는 것은 권장하지 않습니다.\n저희 서비스는 참고용 통계 정보만 제공할 수 있습니다.\n도박 문제로 힘드시다면 상담전화 1336으로 상담받으실 수 있습니다.', 1),
('GUARANTEE_REQUEST', '당첨 보장 요구', 'ALL', 'GUARANTEE_REQUEST', 'FIXED_RESPONSE', 20,
 '저희 서비스는 역대 로또 데이터를 분석해 참고용 번호 조합을 추천합니다.\n로또 추첨은 무작위로 이루어지므로 당첨을 보장할 수 없습니다.\n제공되는 조합은 통계 기반 참고 정보로만 이용해주세요.', 1),
('PURCHASE_PUSH', '구매 유도 요청', 'ALL', 'PURCHASE_PUSH', 'FIXED_RESPONSE', 30,
 '저희 서비스는 로또 구매를 권장하거나 강요하지 않습니다.\n제공되는 번호는 통계 기반 참고 조합이며, 최종 선택은 사용자 본인의 판단에 따라 결정해주세요.', 1),
('LOCATION_REQUIRED', '위치 꺼짐 상태의 내 위치 질문', 'ANALYST', 'LOCATION_REQUIRED', 'FIXED_RESPONSE', 40,
 '내 주변 판매점은 위치 사용을 켜시면 찾아 드릴 수 있어요. 지도 위의 위치 스위치를 켜 주세요. 위치는 저장되지 않아요. 지역 이름을 알려 주셔도 바로 찾아 드릴게요!', 1),
('OFF_TOPIC_MAP', '지도 AI 주제 이탈', 'ANALYST', 'OFF_TOPIC', 'FIXED_RESPONSE', 50,
 '지도 AI는 로또 판매점, 명당, 당첨 이력에 대한 질문만 도와드릴 수 있어요. 다른 질문은 답변하기 어려워요.', 1),
('OFF_TOPIC_ANALYST', '애널리스트 주제 이탈', 'ANALYST', 'OFF_TOPIC', 'FIXED_RESPONSE', 55,
 '번호 생성과 무관한 주제의 이야기는 죄송하지만 처리할 수 없어요.', 1),
('DREAM_LOW_RELEVANCE', '꿈풀이 관련성 부족', 'DREAM', 'DREAM_LOW_RELEVANCE', 'FIXED_RESPONSE', 60,
 '입력하신 꿈은 로또 꿈풀이와 직접 연결할 상징이 많지 않습니다.\n그래도 일부 키워드와 통계 데이터를 바탕으로 재미로 참고할 수 있는 번호 조합을 만들어드릴 수 있습니다.', 1),
('DREAM_OFF_TOPIC', '꿈풀이 주제 이탈', 'DREAM', 'OFF_TOPIC', 'FIXED_RESPONSE', 70,
 '꿈풀이는 꿈 내용을 적어 주실 때 도와드릴 수 있어요.\n어젯밤 꾼 꿈을 5자 이상 300자 이하로 적어 주세요. (예: 큰 돼지가 집으로 들어오는 꿈)', 1)
ON DUPLICATE KEY UPDATE
  policy_name = VALUES(policy_name), feature_scope = VALUES(feature_scope), policy_type = VALUES(policy_type),
  action_type = VALUES(action_type), priority = VALUES(priority), response_message = VALUES(response_message),
  is_active = VALUES(is_active);

-- ========== 키워드 (공백 없이 저장) ==========
DELETE k FROM ai_policy_keyword k
  JOIN ai_input_policy p ON p.id = k.ai_input_policy_id
 WHERE p.policy_code IN ('DESPERATE_REQUEST','GUARANTEE_REQUEST','PURCHASE_PUSH','LOCATION_REQUIRED','OFF_TOPIC_MAP','OFF_TOPIC_ANALYST','DREAM_OFF_TOPIC');

INSERT INTO ai_policy_keyword (ai_input_policy_id, keyword, match_type, is_active)
SELECT p.id, w.keyword, 'CONTAINS', 1
  FROM ai_input_policy p
  JOIN (
    SELECT 'DESPERATE_REQUEST' AS code, '빚이' AS keyword UNION ALL
    SELECT 'DESPERATE_REQUEST', '빚때문' UNION ALL
    SELECT 'DESPERATE_REQUEST', '빚갚' UNION ALL
    SELECT 'DESPERATE_REQUEST', '대출' UNION ALL
    SELECT 'DESPERATE_REQUEST', '파산' UNION ALL
    SELECT 'DESPERATE_REQUEST', '생활비' UNION ALL
    SELECT 'DESPERATE_REQUEST', '절박' UNION ALL
    SELECT 'DESPERATE_REQUEST', '인생역전해야' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '당첨번호알려' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '당첨번호줘' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '1등되는번호' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '1등번호알려' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '확률높은번호' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '무조건당첨' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '당첨보장' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '당첨확정' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '꼭당첨' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '무조건1등' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '1등번호달라' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '1등번호줘' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '1등확정' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '반드시당첨' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '무조건되는번호' UNION ALL
    SELECT 'GUARANTEE_REQUEST', '무조건맞는번호' UNION ALL
    SELECT 'PURCHASE_PUSH', '사도돼' UNION ALL
    SELECT 'PURCHASE_PUSH', '사도되' UNION ALL
    SELECT 'PURCHASE_PUSH', '사야돼' UNION ALL
    SELECT 'PURCHASE_PUSH', '사야되' UNION ALL
    SELECT 'PURCHASE_PUSH', '살까' UNION ALL
    SELECT 'PURCHASE_PUSH', '구매할까' UNION ALL
    SELECT 'PURCHASE_PUSH', '무조건구매' UNION ALL
    SELECT 'PURCHASE_PUSH', '무조건사' UNION ALL
    SELECT 'LOCATION_REQUIRED', '내위치' UNION ALL
    SELECT 'LOCATION_REQUIRED', '내주변' UNION ALL
    SELECT 'LOCATION_REQUIRED', '근처' UNION ALL
    SELECT 'LOCATION_REQUIRED', '가까운' UNION ALL
    SELECT 'OFF_TOPIC_MAP', '판매점' UNION ALL
    SELECT 'OFF_TOPIC_MAP', '명당' UNION ALL
    SELECT 'OFF_TOPIC_MAP', '당첨' UNION ALL
    SELECT 'OFF_TOPIC_MAP', '1등' UNION ALL
    SELECT 'OFF_TOPIC_MAP', '2등' UNION ALL
    SELECT 'OFF_TOPIC_MAP', '복권방' UNION ALL
    SELECT 'OFF_TOPIC_MAP', '복권' UNION ALL
    SELECT 'OFF_TOPIC_MAP', '로또' UNION ALL
    SELECT 'OFF_TOPIC_MAP', '루키' UNION ALL
    SELECT 'OFF_TOPIC_MAP', '자동' UNION ALL
    SELECT 'OFF_TOPIC_MAP', '수동' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '로또' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '번호' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '당첨' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '통계' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '회차' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '추첨' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '발급' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '조합' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '핫넘버' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '미출현' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '홀짝' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '합계' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '연속' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '구간' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '보너스' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '1등' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '2등' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '판매점' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '명당' UNION ALL
    SELECT 'OFF_TOPIC_ANALYST', '분석' UNION ALL
    SELECT 'DREAM_OFF_TOPIC', '꿈' UNION ALL
    SELECT 'DREAM_OFF_TOPIC', '꿨' UNION ALL
    SELECT 'DREAM_OFF_TOPIC', '꾼' UNION ALL
    SELECT 'DREAM_OFF_TOPIC', '꾸었' UNION ALL
    SELECT 'DREAM_OFF_TOPIC', '꾸는'
  ) w ON w.code = p.policy_code;

-- 확인용
SELECT p.policy_code, p.priority, COUNT(k.id) AS keyword_count
  FROM ai_input_policy p LEFT JOIN ai_policy_keyword k ON k.ai_input_policy_id = p.id
 GROUP BY p.id ORDER BY p.priority;
