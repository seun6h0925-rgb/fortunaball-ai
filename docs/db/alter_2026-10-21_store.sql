-- =====================================================================
-- DB 변경 기록 (2026-10-21) — 판매점 지도(7번) 준비
-- schema.sql 로 새로 DB 를 만들었다면, 그 뒤에 이 파일을 한 번 실행한다. (이미 실행한 DB 에서는 다시 실행하지 말 것)
-- 정식 반영: 문서 정리 때 schema.sql 과 DB 설계서에도 같은 내용을 적는다.
--
-- 바꾼 이유
--  1) lotto_store.shop_code : 동행복권 판매점 번호. 판매점을 구분하는 기준값(중복 불가)
--  2) lotto_store.is_closed : 폐점 여부. 1이면 지도·검색·AI 답변에서 제외 (당첨 이력·집계는 보관)
--  3) 이름+도로명 주소의 중복 금지를 풀었다 : 같은 자리에서 가게가 바뀐 20곳(번호는 다름)이 있어서.
--     중복은 shop_code 가 막아 주므로 검색용 색인만 남긴다.
--  4) store_winning_history.win_type : 자료에 자동·수동 구분이 없으면 NULL 허용 (2등 대부분)
-- =====================================================================

ALTER TABLE lotto_store
  ADD COLUMN shop_code VARCHAR(20) NULL COMMENT '[추가 2026-10-21] 동행복권 판매점 번호' AFTER id,
  ADD COLUMN is_closed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '[추가 2026-10-21] 폐점 여부. 1이면 지도·검색·AI 답변에서 제외',
  ADD UNIQUE KEY uk_lotto_store_shop_code (shop_code);

ALTER TABLE store_winning_history
  MODIFY win_type VARCHAR(20) NULL COMMENT 'AUTO / MANUAL / SEMI_AUTO. 자료에 구분이 없으면 NULL',
  DROP CHECK ck_store_winning_history_type,
  ADD CONSTRAINT ck_store_winning_history_type CHECK (win_type IS NULL OR win_type IN ('AUTO','MANUAL','SEMI_AUTO'));

ALTER TABLE lotto_store
  DROP INDEX uk_lotto_store_name_road,
  ADD KEY idx_lotto_store_name_road (store_name, road_address);
