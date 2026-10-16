-- =====================================================================
-- 포르투나볼 AI  DB 스키마 (MySQL 8.0 이상)
-- 기준 문서: 포르투나볼_DB설계서_2026-10-16.md (테이블 27개)
-- DB명: fortuna_ball / 문자셋: utf8mb4
--
-- 작성 기준
--  * 생성 순서는 FK 참조 순서를 따른다 (참조되는 테이블을 먼저 만든다).
--  * 범위·순서가 정해진 값은 CHECK 제약으로 막는다 (MySQL 8.0.16 이상에서 동작).
--  * [추가 2026-10-16] 표시는 이번에 새로 넣은 테이블·컬럼이다.
--  * 번호 3회 제한(같은 게스트·회차·조합)은 UNIQUE가 아니라 서비스 로직으로 검사한다.
--  * number_issue.target_round_no, stat_round_snapshot.target_round_no 는
--    아직 추첨되지 않은 회차를 가리킬 수 있어 FK로 묶지 않는다.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS fortuna_ball
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE fortuna_ball;

SET NAMES utf8mb4;

-- =====================================================================
-- A. 운영 기반 (관리자, 파일)
-- =====================================================================

-- 관리자 계정
CREATE TABLE admin_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '관리자 고유번호',
  login_id      VARCHAR(50)  NOT NULL COMMENT '관리자 아이디',
  password      VARCHAR(255) NOT NULL COMMENT '암호화된 비밀번호 (평문 저장 금지)',
  role          VARCHAR(30)  NOT NULL COMMENT '관리자 권한',
  last_login_at DATETIME     NULL     COMMENT '마지막 로그인 시간',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_user_login_id (login_id)
) ENGINE=InnoDB COMMENT='관리자 계정';

-- 업로드 파일 (익명 당첨 인증 사진)
CREATE TABLE file_upload (
  id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '업로드 파일 고유번호',
  original_file_name VARCHAR(255) NOT NULL COMMENT '원본 파일명',
  stored_file_name   VARCHAR(255) NOT NULL COMMENT '서버 저장 파일명',
  file_path          VARCHAR(500) NOT NULL COMMENT '비공개 저장 경로',
  file_size          BIGINT       NOT NULL COMMENT '파일 크기(byte)',
  file_extension     VARCHAR(10)  NOT NULL COMMENT '확장자',
  content_type       VARCHAR(100) NOT NULL COMMENT '콘텐츠 유형(MIME)',
  upload_status      VARCHAR(20)  NOT NULL COMMENT '업로드 상태',
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '업로드일자',
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_upload_stored_name (stored_file_name)
) ENGINE=InnoDB COMMENT='업로드 파일';

-- [추가 2026-10-16] 판매점 데이터 업로드 이력
CREATE TABLE store_data_upload (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '업로드 이력 고유번호',
  upload_type   VARCHAR(30)  NOT NULL COMMENT 'STORE_LIST / FIRST_AUTO / FIRST_SECOND_EXCEL',
  file_name     VARCHAR(255) NOT NULL COMMENT '원본 파일명',
  base_date     DATE         NULL     COMMENT '자료 기준일',
  total_rows    INT          NOT NULL DEFAULT 0 COMMENT '전체 행 수',
  new_rows      INT          NOT NULL DEFAULT 0 COMMENT '새로 생긴 행 수',
  changed_rows  INT          NOT NULL DEFAULT 0 COMMENT '바뀐 행 수',
  status        VARCHAR(20)  NOT NULL COMMENT 'STAGED / APPLIED / FAILED / CANCELED',
  admin_user_id BIGINT       NOT NULL COMMENT '업로드 관리자',
  error_message TEXT         NULL     COMMENT '실패 사유',
  uploaded_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '업로드일자',
  applied_at    DATETIME     NULL     COMMENT '반영일자. 반영 전에는 NULL',
  PRIMARY KEY (id),
  KEY idx_store_data_upload_type_status (upload_type, status),
  CONSTRAINT fk_store_data_upload_admin FOREIGN KEY (admin_user_id) REFERENCES admin_user (id)
) ENGINE=InnoDB COMMENT='판매점 데이터 업로드 이력';

-- =====================================================================
-- B. 회차 데이터 (동행복권 공식 API, 추첨 완료 회차만 저장)
-- =====================================================================

CREATE TABLE draw_round (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '회차 데이터 고유번호',
  round_no     INT         NOT NULL COMMENT '로또 회차 번호',
  draw_date    DATE        NOT NULL COMMENT '추첨일',
  number_1     INT         NOT NULL COMMENT '당첨번호 1',
  number_2     INT         NOT NULL COMMENT '당첨번호 2',
  number_3     INT         NOT NULL COMMENT '당첨번호 3',
  number_4     INT         NOT NULL COMMENT '당첨번호 4',
  number_5     INT         NOT NULL COMMENT '당첨번호 5',
  number_6     INT         NOT NULL COMMENT '당첨번호 6',
  bonus_number INT         NOT NULL COMMENT '보너스 번호',
  data_source  VARCHAR(50) NOT NULL COMMENT '데이터 출처',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_draw_round_round_no (round_no),
  CONSTRAINT ck_draw_round_numbers CHECK (
    number_1 BETWEEN 1 AND 45 AND number_2 BETWEEN 1 AND 45 AND number_3 BETWEEN 1 AND 45 AND
    number_4 BETWEEN 1 AND 45 AND number_5 BETWEEN 1 AND 45 AND number_6 BETWEEN 1 AND 45 AND
    bonus_number BETWEEN 1 AND 45)
) ENGINE=InnoDB COMMENT='회차별 당첨번호';

CREATE TABLE draw_prize (
  id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '당첨금 데이터 고유번호',
  round_no     INT      NOT NULL COMMENT '회차 번호',
  winning_rank INT      NOT NULL COMMENT '당첨 등수 (1~5)',
  prize_amount BIGINT   NOT NULL COMMENT '해당 등수 1인 당첨금',
  winner_count INT      NOT NULL COMMENT '해당 등수 당첨자 수',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_draw_prize_round_rank (round_no, winning_rank),
  CONSTRAINT fk_draw_prize_round FOREIGN KEY (round_no) REFERENCES draw_round (round_no),
  CONSTRAINT ck_draw_prize_rank CHECK (winning_rank BETWEEN 1 AND 5)
) ENGINE=InnoDB COMMENT='회차별 등수별 당첨금';

-- =====================================================================
-- C. 번호 발급 / 당첨 검증
-- =====================================================================

-- 발급 기록 (발급 확정 시 issue_number 와 같은 트랜잭션으로 저장)
CREATE TABLE number_issue (
  id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '발급 기록 고유번호',
  issue_code             VARCHAR(30)  NOT NULL COMMENT '발급코드 (10자 이상, 추측하기 어려운 값)',
  guest_token            VARCHAR(100) NOT NULL COMMENT '게스트 식별값',
  target_round_no        INT          NOT NULL COMMENT '대상 회차 번호. 미래 회차 발급을 위해 FK로 묶지 않는다.',
  game_count             INT          NOT NULL COMMENT '생성 게임 수',
  issue_type             VARCHAR(30)  NOT NULL COMMENT '번호 생성 방식',
  generation_basis_type  VARCHAR(30)  NULL COMMENT '[추가 2026-10-16] 생성 근거 유형. 예: STAT_PATTERN. 쓰지 않으면 NULL',
  generation_basis_value VARCHAR(50)  NULL COMMENT '[추가 2026-10-16] 생성 근거값. 예: pattern_code(HOT_NUMBER 등)',
  created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급일자',
  PRIMARY KEY (id),
  UNIQUE KEY uk_number_issue_code (issue_code),
  KEY idx_number_issue_guest_round (guest_token, target_round_no),
  CONSTRAINT ck_number_issue_game_count CHECK (game_count >= 1)
) ENGINE=InnoDB COMMENT='번호 발급 기록';

CREATE TABLE issue_number (
  id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '발급 번호 고유번호',
  number_issue_id BIGINT      NOT NULL COMMENT '발급 기록 고유번호',
  game_order      INT         NOT NULL COMMENT '게임 순서',
  combo_key       VARCHAR(20) NOT NULL COMMENT '오름차순 번호를 연결한 중복 검사용 키',
  number_1        INT         NOT NULL COMMENT '발급번호 1',
  number_2        INT         NOT NULL COMMENT '발급번호 2',
  number_3        INT         NOT NULL COMMENT '발급번호 3',
  number_4        INT         NOT NULL COMMENT '발급번호 4',
  number_5        INT         NOT NULL COMMENT '발급번호 5',
  number_6        INT         NOT NULL COMMENT '발급번호 6',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일자',
  PRIMARY KEY (id),
  UNIQUE KEY uk_issue_number_order (number_issue_id, game_order),
  KEY idx_issue_number_issue_combo (number_issue_id, combo_key),
  CONSTRAINT fk_issue_number_issue FOREIGN KEY (number_issue_id) REFERENCES number_issue (id),
  -- 번호는 1~45, 오름차순으로 저장한다
  CONSTRAINT ck_issue_number_asc CHECK (
    number_1 BETWEEN 1 AND 45 AND number_6 BETWEEN 1 AND 45 AND
    number_1 < number_2 AND number_2 < number_3 AND number_3 < number_4 AND
    number_4 < number_5 AND number_5 < number_6)
) ENGINE=InnoDB COMMENT='발급 번호 조합';

CREATE TABLE winning_verification (
  id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '검증 결과 고유번호',
  issue_number_id BIGINT      NOT NULL COMMENT '발급 번호 고유번호. 번호 조합 1개당 검증 결과 1개',
  round_no        INT         NOT NULL COMMENT '검증 회차 번호',
  match_count     INT         NOT NULL COMMENT '당첨번호 일치 개수',
  bonus_matched   TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '보너스 번호 일치 여부',
  winning_rank    INT         NULL     COMMENT '당첨 등수. 낙첨이면 NULL',
  result_status   VARCHAR(20) NOT NULL COMMENT '검증 상태',
  verified_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '검증일자',
  PRIMARY KEY (id),
  UNIQUE KEY uk_winning_verification_issue_number (issue_number_id),
  KEY idx_winning_verification_round (round_no),
  CONSTRAINT fk_winning_verification_issue_number FOREIGN KEY (issue_number_id) REFERENCES issue_number (id),
  CONSTRAINT fk_winning_verification_round FOREIGN KEY (round_no) REFERENCES draw_round (round_no),
  CONSTRAINT ck_winning_verification_match CHECK (match_count BETWEEN 0 AND 6),
  CONSTRAINT ck_winning_verification_rank CHECK (winning_rank IS NULL OR winning_rank BETWEEN 1 AND 5)
) ENGINE=InnoDB COMMENT='당첨 검증 결과';

-- =====================================================================
-- D. 통계
-- =====================================================================

CREATE TABLE lotto_statistics (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '통계 결과 고유번호',
  base_round_no   INT          NOT NULL COMMENT '기준 회차 번호',
  stat_type       VARCHAR(30)  NOT NULL COMMENT '통계 유형',
  stat_key        VARCHAR(50)  NOT NULL COMMENT '통계 기준값',
  stat_value      VARCHAR(255) NOT NULL COMMENT '통계 결과값',
  calculated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '계산일자',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lotto_statistics (base_round_no, stat_type, stat_key),
  CONSTRAINT fk_lotto_statistics_round FOREIGN KEY (base_round_no) REFERENCES draw_round (round_no)
) ENGINE=InnoDB COMMENT='통계 결과';

-- [추가 2026-10-16] 회차별 통계 상태 (준비 중 → 결과 대기 → 확정)
CREATE TABLE stat_round_snapshot (
  id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '회차별 통계 고유번호',
  target_round_no INT         NOT NULL COMMENT '통계가 보여 줄 대상 회차. 미래 회차일 수 있어 FK로 묶지 않는다.',
  basis_round_no  INT         NOT NULL COMMENT '계산에 포함된 마지막 회차',
  status          VARCHAR(20) NOT NULL COMMENT 'PREPARING(준비 중) / WAITING(결과 대기) / CONFIRMED(확정)',
  generated_at    DATETIME    NOT NULL COMMENT '통계 생성일자',
  confirmed_at    DATETIME    NULL     COMMENT '확정일자. 확정 전에는 NULL',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stat_round_snapshot_target (target_round_no),
  CONSTRAINT fk_stat_round_snapshot_basis FOREIGN KEY (basis_round_no) REFERENCES draw_round (round_no),
  CONSTRAINT ck_stat_round_snapshot_status CHECK (status IN ('PREPARING','WAITING','CONFIRMED'))
) ENGINE=InnoDB COMMENT='회차별 통계 상태';

-- [추가 2026-10-16] 통계 패턴 카드 (회차별 6개 패턴)
CREATE TABLE stat_pattern_card (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '패턴 카드 고유번호',
  snapshot_id      BIGINT       NOT NULL COMMENT '회차별 통계 고유번호',
  pattern_code     VARCHAR(30)  NOT NULL COMMENT 'HOT_NUMBER / WINTER_SLEEP / BALANCE_MIX / HIGH_LOW_MIX / CENTER_ZONE / ALL_SECTION_SPREAD',
  pattern_name     VARCHAR(50)  NOT NULL COMMENT '패턴 이름',
  description      VARCHAR(255) NOT NULL COMMENT '사실 위주의 설명 문구',
  period_rounds    INT          NOT NULL DEFAULT 100 COMMENT '해당 횟수 계산 기간(회)',
  hit_count        INT          NOT NULL COMMENT '기간 안에서 패턴에 해당한 회차 수',
  condition_params JSON         NULL     COMMENT '번호 생성에 쓰는 조건값',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stat_pattern_card (snapshot_id, pattern_code),
  CONSTRAINT fk_stat_pattern_card_snapshot FOREIGN KEY (snapshot_id) REFERENCES stat_round_snapshot (id),
  CONSTRAINT ck_stat_pattern_card_hit CHECK (hit_count >= 0)
) ENGINE=InnoDB COMMENT='통계 패턴 카드';

-- =====================================================================
-- E. 판매점
-- =====================================================================

CREATE TABLE lotto_store (
  id                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '판매점 고유번호',
  store_name           VARCHAR(100)  NOT NULL COMMENT '판매점명',
  road_address         VARCHAR(200)  NULL     COMMENT '도로명 주소',
  jibun_address        VARCHAR(200)  NULL     COMMENT '지번 주소',
  sido                 VARCHAR(30)   NULL     COMMENT '시도',
  sigungu              VARCHAR(50)   NULL     COMMENT '시군구',
  latitude             DECIMAL(10,7) NULL     COMMENT '위도. 지오코딩 전까지 NULL',
  longitude            DECIMAL(10,7) NULL     COMMENT '경도. 지오코딩 전까지 NULL',
  store_type           VARCHAR(20)   NULL     COMMENT '판매점 유형. 공공데이터 유형 코드값(코드 의미는 확인 필요)',
  geocode_status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '[추가 2026-10-16] PENDING / SUCCESS / FAILED. 좌표 변환 상태',
  geocoded_at          DATETIME      NULL     COMMENT '[추가 2026-10-16] 좌표 변환일자',
  source_base_date     DATE          NULL     COMMENT '[추가 2026-10-16] 공공데이터 기준일',
  first_seen_upload_id BIGINT        NULL     COMMENT '[추가 2026-10-16] 처음 확인된 업로드. 루키 명당 판정에 사용',
  first_seen_at        DATETIME      NULL     COMMENT '[추가 2026-10-16] 처음 확인된 일자',
  created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종수정일',
  PRIMARY KEY (id),
  -- 같은 판매점이 파일에서 반복될 수 있어 판매점명 + 도로명주소를 중복 방지 기준으로 쓴다
  UNIQUE KEY uk_lotto_store_name_road (store_name, road_address),
  KEY idx_lotto_store_region (sido, sigungu),
  KEY idx_lotto_store_geo (latitude, longitude),
  KEY idx_lotto_store_geocode_status (geocode_status),
  CONSTRAINT fk_lotto_store_first_upload FOREIGN KEY (first_seen_upload_id) REFERENCES store_data_upload (id),
  CONSTRAINT ck_lotto_store_geocode_status CHECK (geocode_status IN ('PENDING','SUCCESS','FAILED'))
) ENGINE=InnoDB COMMENT='로또 판매점';

-- 회차별 판매점 당첨 이력 (회차별 자료가 있을 때 사용. 비어 있을 수 있다)
CREATE TABLE store_winning_history (
  id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '판매점 당첨 이력 고유번호',
  lotto_store_id BIGINT      NOT NULL COMMENT '판매점 고유번호',
  round_no       INT         NOT NULL COMMENT '당첨 회차 번호',
  winning_rank   INT         NOT NULL COMMENT '당첨 등수',
  win_type       VARCHAR(20) NULL     COMMENT '자동/수동/반자동',
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  PRIMARY KEY (id),
  KEY idx_store_winning_history_store (lotto_store_id),
  KEY idx_store_winning_history_round (round_no),
  CONSTRAINT fk_store_winning_history_store FOREIGN KEY (lotto_store_id) REFERENCES lotto_store (id),
  CONSTRAINT fk_store_winning_history_round FOREIGN KEY (round_no) REFERENCES draw_round (round_no),
  CONSTRAINT ck_store_winning_history_rank CHECK (winning_rank BETWEEN 1 AND 5)
) ENGINE=InnoDB COMMENT='판매점 당첨 이력(회차별)';

-- [추가 2026-10-16] 판매점별 당첨 집계 (1등 자동/수동/반자동, 2등)
CREATE TABLE store_win_summary (
  id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '판매점 당첨 집계 고유번호',
  lotto_store_id     BIGINT      NOT NULL COMMENT '판매점 고유번호. 판매점당 1행',
  first_auto_count   INT         NOT NULL DEFAULT 0 COMMENT '1등 자동 횟수',
  first_manual_count INT         NOT NULL DEFAULT 0 COMMENT '1등 수동 횟수',
  first_semi_count   INT         NOT NULL DEFAULT 0 COMMENT '1등 반자동 횟수',
  second_count       INT         NULL     COMMENT '2등 횟수. 자료가 없으면 NULL',
  match_status       VARCHAR(20) NOT NULL COMMENT 'MATCHED / UNMATCHED / AMBIGUOUS. 파일 간 판매점 연결 결과',
  source_base_date   DATE        NULL     COMMENT '집계 기준일',
  upload_id          BIGINT      NULL     COMMENT '반영한 업로드',
  updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_win_summary_store (lotto_store_id),
  KEY idx_store_win_summary_match (match_status),
  CONSTRAINT fk_store_win_summary_store FOREIGN KEY (lotto_store_id) REFERENCES lotto_store (id),
  CONSTRAINT fk_store_win_summary_upload FOREIGN KEY (upload_id) REFERENCES store_data_upload (id),
  CONSTRAINT ck_store_win_summary_match CHECK (match_status IN ('MATCHED','UNMATCHED','AMBIGUOUS')),
  CONSTRAINT ck_store_win_summary_counts CHECK (
    first_auto_count >= 0 AND first_manual_count >= 0 AND first_semi_count >= 0 AND
    (second_count IS NULL OR second_count >= 0))
) ENGINE=InnoDB COMMENT='판매점 당첨 집계';

-- =====================================================================
-- F. 익명 당첨 인증
-- =====================================================================

CREATE TABLE anonymous_verification (
  id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '익명 인증 고유번호',
  number_issue_id BIGINT      NOT NULL COMMENT '발급 기록 고유번호',
  file_upload_id  BIGINT      NOT NULL COMMENT '인증 사진 파일. 인증 1건당 사진 1개',
  issue_code      VARCHAR(30) NOT NULL COMMENT '발급코드',
  `comment`       TEXT        NULL     COMMENT '사용자 댓글',
  review_status   VARCHAR(20) NOT NULL COMMENT '검수 상태',
  is_public       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '승인된 인증의 공개 여부',
  admin_comment   TEXT        NULL     COMMENT '관리자 코멘트',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  reviewed_at     DATETIME    NULL     COMMENT '검수일자',
  PRIMARY KEY (id),
  UNIQUE KEY uk_anonymous_verification_file (file_upload_id),
  KEY idx_anonymous_verification_issue (number_issue_id),
  KEY idx_anonymous_verification_status (review_status, is_public),
  CONSTRAINT fk_anonymous_verification_issue FOREIGN KEY (number_issue_id) REFERENCES number_issue (id),
  CONSTRAINT fk_anonymous_verification_file FOREIGN KEY (file_upload_id) REFERENCES file_upload (id)
) ENGINE=InnoDB COMMENT='익명 당첨 인증';

-- =====================================================================
-- G. 꿈 데이터 (지식베이스)
-- =====================================================================

CREATE TABLE dream_symbol (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '꿈 상징 고유번호',
  symbol_keyword VARCHAR(50)  NOT NULL COMMENT '꿈 상징 키워드(대표 상징)',
  category       VARCHAR(30)  NOT NULL COMMENT '상징 분류',
  description    TEXT         NULL     COMMENT '상징 설명',
  is_active      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '사용 여부',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dream_symbol_keyword (symbol_keyword)
) ENGINE=InnoDB COMMENT='꿈 상징 사전';

CREATE TABLE dream_symbol_alias (
  id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '꿈 상징 별칭 고유번호',
  dream_symbol_id  BIGINT      NOT NULL COMMENT '꿈 상징 고유번호',
  alias_keyword    VARCHAR(50) NOT NULL COMMENT '자연어 별칭 또는 유사 표현',
  alias_type       VARCHAR(30) NOT NULL COMMENT '별칭 유형',
  created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dream_symbol_alias (dream_symbol_id, alias_keyword),
  KEY idx_dream_symbol_alias_keyword (alias_keyword),
  CONSTRAINT fk_dream_symbol_alias_symbol FOREIGN KEY (dream_symbol_id) REFERENCES dream_symbol (id)
) ENGINE=InnoDB COMMENT='꿈 상징 별칭';

CREATE TABLE dream_interpretation (
  id                     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '꿈 해석 고유번호',
  dream_symbol_id        BIGINT      NOT NULL COMMENT '꿈 상징 고유번호',
  situation_keyword      VARCHAR(100) NOT NULL COMMENT '상황 키워드',
  interpretation_summary TEXT        NOT NULL COMMENT '해석 요약',
  polarity               VARCHAR(20) NOT NULL COMMENT 'POSITIVE/CAUTION/MIXED',
  confidence             VARCHAR(20) NOT NULL COMMENT 'HIGH/MEDIUM/LOW',
  created_at             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  updated_at             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종수정일',
  PRIMARY KEY (id),
  KEY idx_dream_interpretation_symbol (dream_symbol_id),
  CONSTRAINT fk_dream_interpretation_symbol FOREIGN KEY (dream_symbol_id) REFERENCES dream_symbol (id),
  CONSTRAINT ck_dream_interpretation_polarity CHECK (polarity IN ('POSITIVE','CAUTION','MIXED')),
  CONSTRAINT ck_dream_interpretation_confidence CHECK (confidence IN ('HIGH','MEDIUM','LOW'))
) ENGINE=InnoDB COMMENT='꿈 상황별 해석';

-- 추천 번호는 문자열로 묶지 않고 번호 1개당 1행으로 저장한다
CREATE TABLE dream_symbol_number (
  id              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '꿈 상징 번호 매핑 고유번호',
  dream_symbol_id BIGINT   NOT NULL COMMENT '꿈 상징 고유번호',
  number_value    INT      NOT NULL COMMENT '추천 번호',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dream_symbol_number (dream_symbol_id, number_value),
  CONSTRAINT fk_dream_symbol_number_symbol FOREIGN KEY (dream_symbol_id) REFERENCES dream_symbol (id),
  CONSTRAINT ck_dream_symbol_number_value CHECK (number_value BETWEEN 1 AND 45)
) ENGINE=InnoDB COMMENT='꿈 상징 번호 매핑';

CREATE TABLE dream_source (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '꿈 데이터 출처 고유번호',
  source_name VARCHAR(200) NOT NULL COMMENT '출처명',
  source_type VARCHAR(30)  NOT NULL COMMENT '민속자료/학술자료/책/웹/심리학',
  source_url  VARCHAR(500) NULL     COMMENT '출처 URL 또는 서지 정보',
  trust_level VARCHAR(20)  NOT NULL COMMENT 'HIGH/MEDIUM/LOW',
  memo        TEXT         NULL     COMMENT '출처 참고 메모',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  PRIMARY KEY (id),
  CONSTRAINT ck_dream_source_trust CHECK (trust_level IN ('HIGH','MEDIUM','LOW'))
) ENGINE=InnoDB COMMENT='꿈 데이터 출처';

CREATE TABLE dream_symbol_source (
  id              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '꿈 상징 출처 연결 고유번호',
  dream_symbol_id BIGINT   NOT NULL COMMENT '꿈 상징 고유번호',
  dream_source_id BIGINT   NOT NULL COMMENT '꿈 데이터 출처 고유번호',
  memo            TEXT     NULL     COMMENT '참고 내용 요약(복붙 금지, 서비스 문장으로 재작성)',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dream_symbol_source (dream_symbol_id, dream_source_id),
  CONSTRAINT fk_dream_symbol_source_symbol FOREIGN KEY (dream_symbol_id) REFERENCES dream_symbol (id),
  CONSTRAINT fk_dream_symbol_source_source FOREIGN KEY (dream_source_id) REFERENCES dream_source (id)
) ENGINE=InnoDB COMMENT='꿈 상징 출처 연결';

-- =====================================================================
-- H. 콘텐츠 / 운영 로그
-- =====================================================================

CREATE TABLE notice (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '공지사항 고유번호',
  admin_user_id BIGINT       NOT NULL COMMENT '작성 관리자 고유번호',
  title         VARCHAR(200) NOT NULL COMMENT '제목',
  content       TEXT         NOT NULL COMMENT '내용',
  notice_type   VARCHAR(20)  NOT NULL COMMENT '공지 유형',
  is_visible    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '공개 여부',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종수정일',
  PRIMARY KEY (id),
  KEY idx_notice_visible_created (is_visible, created_at),
  CONSTRAINT fk_notice_admin FOREIGN KEY (admin_user_id) REFERENCES admin_user (id)
) ENGINE=InnoDB COMMENT='공지사항';

CREATE TABLE content_page (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '콘텐츠 고유번호',
  page_key      VARCHAR(50)  NOT NULL COMMENT '안내 페이지 식별자',
  title         VARCHAR(200) NOT NULL COMMENT '페이지 제목',
  content       TEXT         NOT NULL COMMENT '페이지 내용',
  version       INT          NOT NULL DEFAULT 1 COMMENT '콘텐츠 버전',
  is_visible    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '공개 여부',
  admin_user_id BIGINT       NOT NULL COMMENT '최종 수정 관리자',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일자',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종수정일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_page_key (page_key),
  CONSTRAINT fk_content_page_admin FOREIGN KEY (admin_user_id) REFERENCES admin_user (id)
) ENGINE=InnoDB COMMENT='콘텐츠 페이지';

CREATE TABLE admin_action_log (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '관리자 작업 로그 고유번호',
  admin_user_id BIGINT      NOT NULL COMMENT '작업 관리자 고유번호',
  action_type   VARCHAR(30) NOT NULL COMMENT '작업 유형',
  target_type   VARCHAR(30) NOT NULL COMMENT '대상 유형',
  target_id     BIGINT      NULL     COMMENT '대상 데이터 ID',
  action_detail TEXT        NULL     COMMENT '작업 상세',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작업일자',
  PRIMARY KEY (id),
  KEY idx_admin_action_log_admin (admin_user_id, created_at),
  CONSTRAINT fk_admin_action_log_admin FOREIGN KEY (admin_user_id) REFERENCES admin_user (id)
) ENGINE=InnoDB COMMENT='관리자 작업 로그';

-- AI 사용 로그. 사용자 질문·꿈 원문·응답 원문은 저장하지 않는다.
CREATE TABLE ai_usage_log (
  id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'AI 사용 로그 고유번호',
  feature_type       VARCHAR(30) NOT NULL COMMENT 'AI 기능 구분',
  request_status     VARCHAR(20) NOT NULL COMMENT '성공/실패 상태',
  input_token_count  INT         NULL     COMMENT '입력 토큰 수',
  output_token_count INT         NULL     COMMENT '출력 토큰 수',
  error_message      TEXT        NULL     COMMENT '실패 사유',
  created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일자',
  PRIMARY KEY (id),
  KEY idx_ai_usage_log_feature (feature_type, created_at)
) ENGINE=InnoDB COMMENT='AI 사용 로그';

CREATE TABLE api_error_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'API 오류 로그 고유번호',
  api_name      VARCHAR(50)  NOT NULL COMMENT 'API 이름',
  request_url   VARCHAR(500) NULL     COMMENT '요청 URL (개인정보·위치 값 제외)',
  http_status   INT          NULL     COMMENT 'HTTP 상태 코드',
  error_message TEXT         NULL     COMMENT '오류 메시지',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발생일자',
  PRIMARY KEY (id),
  KEY idx_api_error_log_created (created_at)
) ENGINE=InnoDB COMMENT='API 오류 로그';

CREATE TABLE scheduler_log (
  id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '스케줄러 로그 고유번호',
  job_name        VARCHAR(50) NOT NULL COMMENT '작업명',
  job_status      VARCHAR(20) NOT NULL COMMENT '성공/실패 상태',
  target_round_no INT         NULL     COMMENT '[추가 2026-10-16] 작업 대상 회차. 회차와 무관한 작업은 NULL',
  started_at      DATETIME    NOT NULL COMMENT '시작 시간',
  ended_at        DATETIME    NULL     COMMENT '종료 시간',
  error_message   TEXT        NULL     COMMENT '실패 사유',
  PRIMARY KEY (id),
  KEY idx_scheduler_log_job (job_name, started_at)
) ENGINE=InnoDB COMMENT='스케줄러 실행 로그';
