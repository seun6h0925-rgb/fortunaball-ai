# 데이터 구성 방식

## 1. 실제 데이터

| 데이터 구분 | 테이블명 | 데이터 출처 | 적재 방식 | 비고 |
| --- | --- | --- | --- | --- |
| 회차별 당첨번호 | `draw_round` | 동행복권 공식 회차 API | 1회차부터 최신 회차까지 자동 수집 | 실제 데이터 |
| 등수별 당첨금 | `draw_round` | 동행복권 공식 회차 API | 회차 데이터 수집 시 함께 저장 | 실제 데이터 |
| 등수별 당첨자 수 | `draw_round` | 동행복권 공식 회차 API | 회차 데이터 수집 시 함께 저장 | 실제 데이터 |
| 판매점 목록 | `lotto_store` | 공공데이터포털 CSV | CSV 다운로드 후 관리자 업로드로 DB 적재. 연 1회 갱신 | 실제 데이터. 좌표는 주소 기반 지오코딩으로 채운다 |
| 판매점 당첨 이력 | `store_winning_history` | 공공데이터포털 CSV | CSV 다운로드 후 DB 적재 | 실제 데이터. 회차별 자료가 없으면 비어 있을 수 있다 |
| 판매점 당첨 집계 | `store_win_summary` | 공공데이터포털 1등 자동 CSV, 동행복권 1·2등 배출점 엑셀 | 관리자 업로드(연 1회) 후 판매점과 이름+지역으로 연결 | 실제 데이터. 판매점별 합계만 있고 회차별 이력은 없다 |

## 2. 계산 데이터

| 데이터 구분 | 테이블명 | 생성 기준 | 생성 방식 | 비고 |
| --- | --- | --- | --- | --- |
| 번호별 출현 빈도 | `lotto_statistics` | `draw_round` | 회차 데이터 기반 계산 | 통계 생성용 |
| 장기 미출현 번호 | `lotto_statistics` | `draw_round` | 마지막 출현 회차 기준 계산 | 번호 생성용 |
| 홀짝 비율 | `lotto_statistics` | `draw_round` | 회차별 홀수/짝수 개수 계산 | 분석용 |
| 고저 비율 | `lotto_statistics` | `draw_round` | 1~22 / 23~45 기준 계산 | 분석용 |
| 번호 합계 | `lotto_statistics` | `draw_round` | 당첨번호 6개 합계 계산 | 분석용 |
| 구간 분포 | `lotto_statistics` | `draw_round` | 번호 구간별 출현 계산 | 분석용 |
| 당첨금 추이 | `lotto_statistics` | `draw_round` | 회차별 당첨금 변화 계산 | 분석용 |
| 회차별 통계 상태 | `stat_round_snapshot` | `draw_round` | 결과 수집 후 확정 통계 계산, 다음 회차 준비 통계 생성 | 준비 중 / 결과 대기 / 확정 |
| 통계 패턴 카드 | `stat_pattern_card` | `lotto_statistics` | 회차별로 6개 패턴 카드 생성 | 패턴 모아보기·통계 탭 표시, 패턴 번호 생성용 |

## 3. 개발·시연용 데이터

| 데이터 구분 | 테이블명 | 데이터 성격 | 작성 방식 | 비고 |
| --- | --- | --- | --- | --- |
| 관리자 계정 | `admin_user` | 시연용 기본 계정 | 초기 데이터로 1건 등록 | 로그인 테스트 |
| 공지사항 | `notice` | 시연용 콘텐츠 | 기본 공지 2~3건 등록 | 화면 표시용 |
| 꿈 상징 데이터 | `dream_symbol` | 서비스 운영 데이터 | 대표 상징 키워드 등록 | 꿈풀이 기능용 |
| 익명 당첨 인증 | `anonymous_verification` | 시연용 예시 데이터 | 승인/대기/반려 상태별 등록 | 관리자 검수 테스트 |
| AI 사용 로그 | `ai_usage_log` | 시연용 로그 | 성공/실패 예시 등록 | 관리자 화면 테스트 |
| API 오류 로그 | `api_error_log` | 시연용 로그 | 오류 예시 등록 | 예외처리 화면 테스트 |
| 스케줄러 실행 로그 | `scheduler_log` | 시연용 로그 | 성공/실패 예시 등록 | 자동 작업 상태 확인 |
| 판매점 데이터 업로드 이력 | `store_data_upload` | 관리자 업로드 기록 | 업로드·반영 시 자동 기록 | 운영 기록 |

## 4. 정리 기준

| 구분 | 작성 기준 |
| --- | --- |
| 실제 데이터 | 외부 공식 데이터 또는 공공데이터를 기반으로 DB에 적재한다. |
| 계산 데이터 | 실제 회차 데이터를 기준으로 서버에서 계산해 저장하거나 캐시에 보관한다. |
| 시연용 데이터 | 관리자 화면과 예외 상황을 보여주기 위해 최소한으로 작성한다. |
| 금지 기준 | 실제 회차 데이터와 당첨번호는 임의 더미 데이터로 만들지 않는다. |

# 포르투나볼 AI DB 테이블 설계서

- 기준: 1차 완성본 수준의 서비스화 설계
- 목적: 로또 프로젝트의 DB 테이블, 컬럼, PK/FK 관계, 데이터 무결성 기준을 명확히 정리한다.

## 0. 설계 기준

이 문서는 단순 시연용 더미 DB가 아니라, 배포 전까지 기능 몇 개만 보완하면 되는 1차 완성본 서비스 기준으로 작성한다.

MVP라는 표현을 쓰더라도 이 프로젝트에서는 기능 일부만 보여주는 임시 버전이 아니라, 핵심 기능이 실제 서비스 구조로 동작하는 1차 완성본을 의미한다.

따라서 아래 항목은 처음부터 설계에 반영한다.

현재 최종 설계 테이블 수는 27개다(기존 23개 + 2026-10-16 추가 4개). ERDCloud에서는 핵심 서비스 13개와 운영·AI·콘텐츠 14개로 나누어 작성한다.

[추가 2026-10-16] 추가 테이블: `stat_round_snapshot`, `stat_pattern_card`, `store_win_summary`(핵심 서비스), `store_data_upload`(운영). 변경 내역은 문서 끝 "28. 변경 이력"에 정리했다.

- DB 데이터 무결성
- PK/FK 관계
- 중복 데이터 방지
- 발급 기록 추적
- 당첨 검증 중복 방지
- 트랜잭션 저장
- 관리자 운영 추적
- API 오류 로그
- 스케줄러 실행 로그
- AI 사용량 및 실패 로그

## 1. 핵심 설계 결정

| 항목 | 결정 |
| --- | --- |
| 회차 데이터 저장 기준 | `draw_round`에는 추첨이 완료된 공식 회차만 저장한다. |
| 당첨금 저장 기준 | 회차 기본 정보와 분리된 `draw_prize`에 1~5등 당첨금과 당첨자 수를 저장한다. |
| 미래 회차 번호 발급 | `number_issue.target_round_no`에 숫자로 저장하고 FK로 묶지 않는다. |
| 미래 회차 FK 제거 이유 | 아직 추첨되지 않은 회차는 `draw_round`에 없으므로 FK로 묶으면 발급 저장이 막힐 수 있다. |
| 당첨 검증 기준 | 추첨 후 해당 회차가 `draw_round`에 저장되면 발급 번호를 검증한다. |
| 중복 검증 방지 | `winning_verification.issue_number_id`에 UNIQUE를 걸어 번호 조합 1개당 검증 결과 1개만 허용한다. |
| 발급 저장 기준 | 생성 결과는 캐시에 짧게 보관하고, 발급 확정 시 `number_issue`와 `issue_number`를 하나의 트랜잭션으로 저장한다. |
| 폐기 결과 기준 | 다시 생성으로 폐기된 결과는 DB에 저장하지 않는다. 캐시 TTL 만료 시 자동 폐기한다. |
| 동일 조합 제한 | `issue_number.combo_key`를 정렬된 번호 문자열로 저장하고, 같은 게스트·회차의 확정 조합이 3회를 넘지 않도록 서비스에서 검사한다. |
| 게임 수 검증 | `number_issue.game_count`와 실제 `issue_number` 저장 개수가 일치해야 한다. |
| 실제 데이터 기준 | 회차 데이터와 당첨번호는 더미가 아니라 동행복권 공식 데이터 기반으로 적재한다. |
| 시연 데이터 기준 | 관리자 계정, 공지사항, 꿈 상징, 익명 인증 예시는 시연용 데이터로 등록할 수 있다. |
| 꿈 데이터 기준 | 꿈 데이터는 더미가 아니라 민속자료, 학술자료, 책, 웹 공통 상징을 비교해 구축한 지식베이스로 관리한다. |
| 꿈 자연어 처리 기준 | 대표 상징, 별칭, 상황별 해석, 출처, 추천 번호를 분리해 저장한다. |
| 회차별 통계 상태 | [추가 2026-10-16] 회차별 통계는 `stat_round_snapshot`에 준비 중 → 결과 대기 → 확정 상태로 저장한다. 확정 후에도 준비 중이던 통계는 삭제하지 않고 보관한다. |
| 통계 패턴 생성 | [추가 2026-10-16] 통계 탭마다 패턴 카드(`stat_pattern_card`)를 만들고, 패턴 번호 생성은 서버 통계 알고리즘이 한다. AI는 설명만 한다. |
| 판매점 당첨 집계 | [추가 2026-10-16] 회차별 판매점 자료가 없으므로 판매점별 합계(1등 자동·수동·반자동, 2등)를 `store_win_summary`에 저장한다. `store_winning_history`는 회차별 자료가 생길 때를 위해 유지한다. |
| 판매점 데이터 갱신 | [추가 2026-10-16] 공공데이터 파일과 동행복권 엑셀을 관리자가 연 1회 업로드한다. 반영 전에 기존 데이터와 비교하고, 새로 생기거나 바뀐 판매점만 지오코딩한다. 이력은 `store_data_upload`에 남긴다. |

## 2. 회차 데이터

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `draw_round` | `id` | BIGINT | PK | 회차 데이터 고유번호 |
| `draw_round` | `round_no` | INT | UNIQUE | 로또 회차 번호 |
| `draw_round` | `draw_date` | DATE |  | 추첨일 |
| `draw_round` | `number_1` | INT |  | 당첨번호 1 |
| `draw_round` | `number_2` | INT |  | 당첨번호 2 |
| `draw_round` | `number_3` | INT |  | 당첨번호 3 |
| `draw_round` | `number_4` | INT |  | 당첨번호 4 |
| `draw_round` | `number_5` | INT |  | 당첨번호 5 |
| `draw_round` | `number_6` | INT |  | 당첨번호 6 |
| `draw_round` | `bonus_number` | INT |  | 보너스 번호 |
| `draw_round` | `data_source` | VARCHAR |  | 데이터 출처 |
| `draw_round` | `created_at` | DATETIME |  | 등록일자 |
| `draw_round` | `updated_at` | DATETIME |  | 최종수정일 |

## 3. 회차별 등수별 당첨금

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `draw_prize` | `id` | BIGINT | PK | 당첨금 데이터 고유번호 |
| `draw_prize` | `round_no` | INT | FK -> `draw_round.round_no` | 회차 번호 |
| `draw_prize` | `winning_rank` | INT |  | 당첨 등수. 1~5등 |
| `draw_prize` | `prize_amount` | BIGINT |  | 해당 등수 당첨금 |
| `draw_prize` | `winner_count` | INT |  | 해당 등수 당첨자 수 |
| `draw_prize` | `created_at` | DATETIME |  | 등록일자 |
| `draw_prize` | `updated_at` | DATETIME |  | 최종수정일 |

무결성 기준:

- `round_no + winning_rank`는 UNIQUE로 설정한다.
- 공식 API 재수집 시 회차와 등수별 당첨금 데이터를 함께 갱신한다.
- 수동 숫자 수정 화면은 제공하지 않고, 공식 데이터 재수집만 허용한다.

## 4. 번호 발급 기록

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `number_issue` | `id` | BIGINT | PK | 발급 기록 고유번호 |
| `number_issue` | `issue_code` | VARCHAR | UNIQUE | 발급코드 |
| `number_issue` | `guest_token` | VARCHAR |  | 게스트 식별값 |
| `number_issue` | `target_round_no` | INT |  | 대상 회차 번호. 미래 회차 발급을 위해 FK로 묶지 않는다. |
| `number_issue` | `game_count` | INT |  | 생성 게임 수 |
| `number_issue` | `issue_type` | VARCHAR |  | 번호 생성 방식 |
| `number_issue` | `generation_basis_type` | VARCHAR |  | [추가 2026-10-16] 생성 근거 유형. 예: STAT_PATTERN. 패턴을 쓰지 않으면 NULL |
| `number_issue` | `generation_basis_value` | VARCHAR |  | [추가 2026-10-16] 생성 근거값. 예: `pattern_code`(HOT_NUMBER 등) |
| `number_issue` | `created_at` | DATETIME |  | 발급일자 |

인덱스와 저장 제한:

- `guest_token`과 `target_round_no`를 함께 조회할 수 있도록 복합 인덱스를 둔다.
- `issue_code`는 UNIQUE이며 10자 이상의 추측하기 어려운 값으로 생성한다.
- 같은 `guest_token`, `target_round_no`, `combo_key` 조합은 확정 저장 기준 3회를 초과하지 않는다.
- 이 제한은 UNIQUE 제약조건이 아니라 발급 확정 서비스의 누적 횟수 검사로 처리한다.

## 5. 발급 번호 조합

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `issue_number` | `id` | BIGINT | PK | 발급 번호 고유번호 |
| `issue_number` | `number_issue_id` | BIGINT | FK -> `number_issue.id` | 발급 기록 고유번호 |
| `issue_number` | `game_order` | INT |  | 게임 순서 |
| `issue_number` | `combo_key` | VARCHAR |  | 오름차순 번호를 연결한 중복 검사용 키 |
| `issue_number` | `number_1` | INT |  | 발급번호 1 |
| `issue_number` | `number_2` | INT |  | 발급번호 2 |
| `issue_number` | `number_3` | INT |  | 발급번호 3 |
| `issue_number` | `number_4` | INT |  | 발급번호 4 |
| `issue_number` | `number_5` | INT |  | 발급번호 5 |
| `issue_number` | `number_6` | INT |  | 발급번호 6 |
| `issue_number` | `created_at` | DATETIME |  | 생성일자 |

## 6. 당첨 검증 결과

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `winning_verification` | `id` | BIGINT | PK | 검증 결과 고유번호 |
| `winning_verification` | `issue_number_id` | BIGINT | FK -> `issue_number.id`, UNIQUE | 발급 번호 고유번호. 번호 조합 1개당 검증 결과 1개만 저장한다. |
| `winning_verification` | `round_no` | INT | FK -> `draw_round.round_no` | 검증 회차 번호 |
| `winning_verification` | `match_count` | INT |  | 당첨번호 일치 개수 |
| `winning_verification` | `bonus_matched` | BOOLEAN |  | 보너스 번호 일치 여부 |
| `winning_verification` | `winning_rank` | INT |  | 당첨 등수 |
| `winning_verification` | `result_status` | VARCHAR |  | 검증 상태 |
| `winning_verification` | `verified_at` | DATETIME |  | 검증일자 |

## 7. 통계 결과

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `lotto_statistics` | `id` | BIGINT | PK | 통계 결과 고유번호 |
| `lotto_statistics` | `base_round_no` | INT | FK -> `draw_round.round_no` | 기준 회차 번호 |
| `lotto_statistics` | `stat_type` | VARCHAR |  | 통계 유형 |
| `lotto_statistics` | `stat_key` | VARCHAR |  | 통계 기준값 |
| `lotto_statistics` | `stat_value` | VARCHAR |  | 통계 결과값 |
| `lotto_statistics` | `calculated_at` | DATETIME |  | 계산일자 |

## 7-1. 회차별 통계 상태 [추가 2026-10-16]

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `stat_round_snapshot` | `id` | BIGINT | PK | 회차별 통계 고유번호 |
| `stat_round_snapshot` | `target_round_no` | INT | UNIQUE | 통계가 보여 줄 대상 회차. 미래 회차일 수 있어 FK로 묶지 않는다. |
| `stat_round_snapshot` | `basis_round_no` | INT | FK -> `draw_round.round_no` | 계산에 포함된 마지막 회차 |
| `stat_round_snapshot` | `status` | VARCHAR |  | PREPARING(준비 중) / WAITING(결과 대기) / CONFIRMED(확정) |
| `stat_round_snapshot` | `generated_at` | DATETIME |  | 통계 생성일자 |
| `stat_round_snapshot` | `confirmed_at` | DATETIME |  | 확정일자. 확정 전에는 NULL |
| `stat_round_snapshot` | `created_at` | DATETIME |  | 등록일자 |
| `stat_round_snapshot` | `updated_at` | DATETIME |  | 최종수정일 |

운영 기준:

- 예: 1246회 준비 중 통계는 `target_round_no=1246`, `basis_round_no=1245`이다.
- 토요일 20:00에 대상 회차를 전환하고, 20:40부터 공식 결과를 확인한다. 결과가 저장되면 확정 통계를 계산하고 다음 회차 준비 통계를 만든다.
- 실패하면 이전 통계를 유지하고 관리자에게 알린다. 작업은 같은 회차를 다시 실행해도 결과가 같도록(멱등) 만든다.
- 관리자는 회차 데이터 관리 화면에서 다시 계산할 수 있다.

## 7-2. 통계 패턴 카드 [추가 2026-10-16]

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `stat_pattern_card` | `id` | BIGINT | PK | 패턴 카드 고유번호 |
| `stat_pattern_card` | `snapshot_id` | BIGINT | FK -> `stat_round_snapshot.id` | 회차별 통계 고유번호 |
| `stat_pattern_card` | `pattern_code` | VARCHAR |  | HOT_NUMBER / WINTER_SLEEP / BALANCE_MIX / HIGH_LOW_MIX / CENTER_ZONE / ALL_SECTION_SPREAD |
| `stat_pattern_card` | `pattern_name` | VARCHAR |  | 핫넘버, 겨울잠 번호, 밸런스 믹스, 하이로우 믹스, 센터 존, 올 구간 스프레드 |
| `stat_pattern_card` | `description` | VARCHAR |  | 사실 위주의 설명 문구 |
| `stat_pattern_card` | `period_rounds` | INT |  | 해당 횟수 계산 기간. 기본 100 |
| `stat_pattern_card` | `hit_count` | INT |  | 기간 안에서 패턴에 해당한 회차 수 |
| `stat_pattern_card` | `condition_params` | JSON |  | 번호 생성에 쓰는 조건값 |
| `stat_pattern_card` | `created_at` | DATETIME |  | 등록일자 |

무결성 기준:

- `snapshot_id + pattern_code`는 UNIQUE로 설정한다.
- 기간, 패턴 개수, 패턴 이름은 사용자가 최종 확정한다. 확정 전 값은 위 표를 기준으로 한다.
- "예측", "확률", "우세" 같은 표현은 `description`에 쓰지 않는다.

## 8. 판매점 데이터

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `lotto_store` | `id` | BIGINT | PK | 판매점 고유번호 |
| `lotto_store` | `store_name` | VARCHAR |  | 판매점명 |
| `lotto_store` | `road_address` | VARCHAR |  | 도로명 주소 |
| `lotto_store` | `jibun_address` | VARCHAR |  | 지번 주소 |
| `lotto_store` | `sido` | VARCHAR |  | 시도 |
| `lotto_store` | `sigungu` | VARCHAR |  | 시군구 |
| `lotto_store` | `latitude` | DECIMAL |  | 위도 |
| `lotto_store` | `longitude` | DECIMAL |  | 경도 |
| `lotto_store` | `store_type` | VARCHAR |  | 판매점 유형. 공공데이터의 유형 코드값을 저장한다(코드 의미는 확인 필요). |
| `lotto_store` | `geocode_status` | VARCHAR |  | [추가 2026-10-16] PENDING / SUCCESS / FAILED. 좌표 변환 상태 |
| `lotto_store` | `geocoded_at` | DATETIME |  | [추가 2026-10-16] 좌표 변환일자 |
| `lotto_store` | `source_base_date` | DATE |  | [추가 2026-10-16] 공공데이터 기준일 |
| `lotto_store` | `first_seen_upload_id` | BIGINT | FK -> `store_data_upload.id` | [추가 2026-10-16] 처음 확인된 업로드. 루키 명당 판정에 쓴다. |
| `lotto_store` | `first_seen_at` | DATETIME |  | [추가 2026-10-16] 처음 확인된 일자 |
| `lotto_store` | `created_at` | DATETIME |  | 등록일자 |
| `lotto_store` | `updated_at` | DATETIME |  | 최종수정일 |

중복 방지 기준:

- 동일 판매점이 CSV에서 반복될 수 있으므로 `store_name + road_address` 조합을 중복 방지 기준으로 사용한다.
- [추가 2026-10-16] 원본 파일에는 좌표가 없으므로 `latitude`, `longitude`는 지오코딩 전까지 NULL이다. 지오코딩 서비스와 이용 조건은 확정 전이다.
- [추가 2026-10-16] 루키 명당(새로 등록됐는데 당첨 기록이 있는 판매점)은 `first_seen_upload_id`가 최근 업로드이고 당첨 집계가 있는 판매점으로 판정하는 안이다. "신규" 기준은 사용자가 확정한다.

## 9. 판매점 당첨 이력

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `store_winning_history` | `id` | BIGINT | PK | 판매점 당첨 이력 고유번호 |
| `store_winning_history` | `lotto_store_id` | BIGINT | FK -> `lotto_store.id` | 판매점 고유번호 |
| `store_winning_history` | `round_no` | INT | FK -> `draw_round.round_no` | 당첨 회차 번호 |
| `store_winning_history` | `winning_rank` | INT |  | 당첨 등수 |
| `store_winning_history` | `win_type` | VARCHAR |  | 자동/수동/반자동 |
| `store_winning_history` | `created_at` | DATETIME |  | 등록일자 |

## 9-1. 판매점 당첨 집계 [추가 2026-10-16]

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `store_win_summary` | `id` | BIGINT | PK | 판매점 당첨 집계 고유번호 |
| `store_win_summary` | `lotto_store_id` | BIGINT | FK -> `lotto_store.id`, UNIQUE | 판매점 고유번호. 판매점당 1행 |
| `store_win_summary` | `first_auto_count` | INT |  | 1등 자동 횟수 |
| `store_win_summary` | `first_manual_count` | INT |  | 1등 수동 횟수 |
| `store_win_summary` | `first_semi_count` | INT |  | 1등 반자동 횟수 |
| `store_win_summary` | `second_count` | INT |  | 2등 횟수. 자료가 없으면 NULL |
| `store_win_summary` | `match_status` | VARCHAR |  | MATCHED / UNMATCHED / AMBIGUOUS. 파일 간 판매점 연결 결과 |
| `store_win_summary` | `source_base_date` | DATE |  | 집계 기준일 |
| `store_win_summary` | `upload_id` | BIGINT | FK -> `store_data_upload.id` | 반영한 업로드 |
| `store_win_summary` | `updated_at` | DATETIME |  | 최종수정일 |

저장 기준:

- 1등 자동 파일과 1·2등 배출점 엑셀은 판매점 이름과 지역으로 `lotto_store`와 연결한다. 연결되지 않거나 후보가 여러 개이면 `UNMATCHED` 또는 `AMBIGUOUS`로 저장하고 관리자가 확인한다.
- 지도와 판매점 상세의 1등·2등·자동·수동 표시는 이 테이블을 사용한다.
- 업로드 파일의 범위(예: 온라인 시스템 자동 1등만 포함)가 달라 총 횟수가 다른 사이트와 다를 수 있다.

## 9-2. 판매점 데이터 업로드 이력 [추가 2026-10-16]

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `store_data_upload` | `id` | BIGINT | PK | 업로드 이력 고유번호 |
| `store_data_upload` | `upload_type` | VARCHAR |  | STORE_LIST(판매점 목록) / FIRST_AUTO(1등 자동) / FIRST_SECOND_EXCEL(1·2등 배출점 엑셀) |
| `store_data_upload` | `file_name` | VARCHAR |  | 원본 파일명 |
| `store_data_upload` | `base_date` | DATE |  | 자료 기준일 |
| `store_data_upload` | `total_rows` | INT |  | 전체 행 수 |
| `store_data_upload` | `new_rows` | INT |  | 새로 생긴 행 수 |
| `store_data_upload` | `changed_rows` | INT |  | 바뀐 행 수 |
| `store_data_upload` | `status` | VARCHAR |  | STAGED(비교 대기) / APPLIED(반영) / FAILED(실패) / CANCELED(취소) |
| `store_data_upload` | `admin_user_id` | BIGINT | FK -> `admin_user.id` | 업로드 관리자 |
| `store_data_upload` | `error_message` | TEXT |  | 실패 사유 |
| `store_data_upload` | `uploaded_at` | DATETIME |  | 업로드일자 |
| `store_data_upload` | `applied_at` | DATETIME |  | 반영일자. 반영 전에는 NULL |

업로드 기준:

- 파일은 먼저 임시 영역에 올려 기존 데이터와 비교한 뒤, 관리자가 확인하면 반영한다(비교용 임시 테이블 구조는 구현 시 정한다).
- 파일 인코딩은 CP949(MS949)를 기준으로 하고, 엑셀(xls)은 Apache POI로 읽는다.
- 동행복권 사이트를 자동으로 수집(크롤링)하지 않는다. 파일은 관리자가 직접 받아 올린다.

## 10. 익명 당첨 인증

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `anonymous_verification` | `id` | BIGINT | PK | 익명 인증 고유번호 |
| `anonymous_verification` | `number_issue_id` | BIGINT | FK -> `number_issue.id` | 발급 기록 고유번호 |
| `anonymous_verification` | `file_upload_id` | BIGINT | FK -> `file_upload.id`, UNIQUE | 인증 사진 파일. 인증 1건당 사진 1개 |
| `anonymous_verification` | `issue_code` | VARCHAR |  | 발급코드 |
| `anonymous_verification` | `comment` | TEXT |  | 사용자 댓글 |
| `anonymous_verification` | `review_status` | VARCHAR |  | 검수 상태 |
| `anonymous_verification` | `is_public` | BOOLEAN |  | 승인된 인증의 공개 여부 |
| `anonymous_verification` | `admin_comment` | TEXT |  | 관리자 코멘트 |
| `anonymous_verification` | `created_at` | DATETIME |  | 등록일자 |
| `anonymous_verification` | `reviewed_at` | DATETIME |  | 검수일자 |

파일 관리 기준:

- 인증 사진은 허용 확장자, 파일 크기, 저장 위치를 제한한다.
- 사진 원본 대화나 개인정보를 별도로 저장하지 않는다.
- OCR/QR 자동 검증은 추후 기능이지만, 파일 검수와 관리자 승인 흐름은 1차 완성본에 포함한다.

## 11. 업로드 파일

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `file_upload` | `id` | BIGINT | PK | 업로드 파일 고유번호 |
| `file_upload` | `original_file_name` | VARCHAR |  | 원본 파일명 |
| `file_upload` | `stored_file_name` | VARCHAR |  | 서버 저장 파일명 |
| `file_upload` | `file_path` | VARCHAR |  | 비공개 저장 경로 |
| `file_upload` | `file_size` | BIGINT |  | 파일 크기 |
| `file_upload` | `file_extension` | VARCHAR |  | 확장자 |
| `file_upload` | `content_type` | VARCHAR |  | 콘텐츠 유형 |
| `file_upload` | `upload_status` | VARCHAR |  | 업로드 상태 |
| `file_upload` | `created_at` | DATETIME |  | 업로드일자 |

파일 보안 기준:

- 허용 확장자와 MIME Type을 모두 검증한다.
- 저장 경로는 외부 공개 URL이 아니라 비공개 저장소 기준으로 관리한다.
- 얼굴, 바코드, 개인정보가 포함될 수 있으므로 업로드 안내와 관리자 검수 절차를 제공한다.

## 12. 꿈 상징 데이터

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `dream_symbol` | `id` | BIGINT | PK | 꿈 상징 고유번호 |
| `dream_symbol` | `symbol_keyword` | VARCHAR |  | 꿈 상징 키워드 |
| `dream_symbol` | `category` | VARCHAR |  | 상징 분류 |
| `dream_symbol` | `description` | TEXT |  | 상징 설명 |
| `dream_symbol` | `is_active` | BOOLEAN |  | 사용 여부 |
| `dream_symbol` | `created_at` | DATETIME |  | 등록일자 |
| `dream_symbol` | `updated_at` | DATETIME |  | 최종수정일 |

저장 기준:

- `dream_symbol`은 꿈 상징 사전이다.
- 추천 번호는 문자열로 저장하지 않고 `dream_symbol_number`에 번호 1개당 1행으로 저장한다.
- 사용자 꿈 원문과 AI 대화 원문은 1차 완성본에서 저장하지 않는다.
- 꿈풀이 사용량과 실패 여부는 `ai_usage_log`로 추적한다.

## 13. 꿈 상징 별칭

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `dream_symbol_alias` | `id` | BIGINT | PK | 꿈 상징 별칭 고유번호 |
| `dream_symbol_alias` | `dream_symbol_id` | BIGINT | FK -> `dream_symbol.id` | 꿈 상징 고유번호 |
| `dream_symbol_alias` | `alias_keyword` | VARCHAR |  | 자연어 별칭 또는 유사 표현 |
| `dream_symbol_alias` | `alias_type` | VARCHAR |  | 별칭 유형 |
| `dream_symbol_alias` | `created_at` | DATETIME |  | 등록일자 |

저장 기준:

- 사용자가 입력하는 자연어 표현을 대표 상징으로 연결하기 위한 테이블이다.
- 예: `황금돼지`, `새끼돼지`, `멧돼지`는 대표 상징 `돼지`로 연결한다.

## 14. 꿈 상황별 해석

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `dream_interpretation` | `id` | BIGINT | PK | 꿈 해석 고유번호 |
| `dream_interpretation` | `dream_symbol_id` | BIGINT | FK -> `dream_symbol.id` | 꿈 상징 고유번호 |
| `dream_interpretation` | `situation_keyword` | VARCHAR |  | 상황 키워드 |
| `dream_interpretation` | `interpretation_summary` | TEXT |  | 해석 요약 |
| `dream_interpretation` | `polarity` | VARCHAR |  | POSITIVE/CAUTION/MIXED |
| `dream_interpretation` | `confidence` | VARCHAR |  | HIGH/MEDIUM/LOW |
| `dream_interpretation` | `created_at` | DATETIME |  | 등록일자 |
| `dream_interpretation` | `updated_at` | DATETIME |  | 최종수정일 |

저장 기준:

- 같은 상징이라도 상황에 따라 해석이 달라지므로 별도 테이블로 분리한다.
- 예: `돼지를 봄`, `돼지를 잡음`, `돼지가 도망감`은 서로 다른 해석을 가질 수 있다.

## 15. 꿈 상징 번호 매핑

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `dream_symbol_number` | `id` | BIGINT | PK | 꿈 상징 번호 매핑 고유번호 |
| `dream_symbol_number` | `dream_symbol_id` | BIGINT | FK -> `dream_symbol.id` | 꿈 상징 고유번호 |
| `dream_symbol_number` | `number_value` | INT |  | 추천 번호 |
| `dream_symbol_number` | `created_at` | DATETIME |  | 등록일자 |

저장 기준:

- 추천 번호는 문자열로 묶지 않는다.
- 번호 1개당 1행으로 저장한다.
- 예: 돼지 상징이 `3, 8, 18, 33`을 가진다면 `dream_symbol_number`에 4행으로 저장한다.

## 16. 꿈 데이터 출처

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `dream_source` | `id` | BIGINT | PK | 꿈 데이터 출처 고유번호 |
| `dream_source` | `source_name` | VARCHAR |  | 출처명 |
| `dream_source` | `source_type` | VARCHAR |  | 민속자료/학술자료/책/웹/심리학 |
| `dream_source` | `source_url` | VARCHAR |  | 출처 URL 또는 서지 정보 |
| `dream_source` | `trust_level` | VARCHAR |  | HIGH/MEDIUM/LOW |
| `dream_source` | `memo` | TEXT |  | 출처 참고 메모 |
| `dream_source` | `created_at` | DATETIME |  | 등록일자 |

저장 기준:

- 꿈 데이터는 출처를 남겨야 한다.
- 문장은 복붙하지 않고 서비스 문장으로 재작성한다.

## 17. 꿈 상징 출처 연결

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `dream_symbol_source` | `id` | BIGINT | PK | 꿈 상징 출처 연결 고유번호 |
| `dream_symbol_source` | `dream_symbol_id` | BIGINT | FK -> `dream_symbol.id` | 꿈 상징 고유번호 |
| `dream_symbol_source` | `dream_source_id` | BIGINT | FK -> `dream_source.id` | 꿈 데이터 출처 고유번호 |
| `dream_symbol_source` | `memo` | TEXT |  | 참고 내용 요약 |
| `dream_symbol_source` | `created_at` | DATETIME |  | 등록일자 |

저장 기준:

- 하나의 상징은 여러 출처와 연결될 수 있다.
- 하나의 출처는 여러 상징과 연결될 수 있다.

## 18. 공지사항

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `notice` | `id` | BIGINT | PK | 공지사항 고유번호 |
| `notice` | `admin_user_id` | BIGINT | FK -> `admin_user.id` | 작성 관리자 고유번호 |
| `notice` | `title` | VARCHAR |  | 제목 |
| `notice` | `content` | TEXT |  | 내용 |
| `notice` | `notice_type` | VARCHAR |  | 공지 유형 |
| `notice` | `is_visible` | BOOLEAN |  | 공개 여부 |
| `notice` | `created_at` | DATETIME |  | 등록일자 |
| `notice` | `updated_at` | DATETIME |  | 최종수정일 |

## 19. 관리자 계정

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `admin_user` | `id` | BIGINT | PK | 관리자 고유번호 |
| `admin_user` | `login_id` | VARCHAR | UNIQUE | 관리자 아이디 |
| `admin_user` | `password` | VARCHAR |  | 암호화된 비밀번호 |
| `admin_user` | `role` | VARCHAR |  | 관리자 권한 |
| `admin_user` | `last_login_at` | DATETIME |  | 마지막 로그인 시간 |
| `admin_user` | `created_at` | DATETIME |  | 등록일자 |

## 20. 관리자 작업 로그

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `admin_action_log` | `id` | BIGINT | PK | 관리자 작업 로그 고유번호 |
| `admin_action_log` | `admin_user_id` | BIGINT | FK -> `admin_user.id` | 작업 관리자 고유번호 |
| `admin_action_log` | `action_type` | VARCHAR |  | 작업 유형 |
| `admin_action_log` | `target_type` | VARCHAR |  | 대상 유형 |
| `admin_action_log` | `target_id` | BIGINT |  | 대상 데이터 ID |
| `admin_action_log` | `action_detail` | TEXT |  | 작업 상세 |
| `admin_action_log` | `created_at` | DATETIME |  | 작업일자 |

## 21. AI 사용 로그

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `ai_usage_log` | `id` | BIGINT | PK | AI 사용 로그 고유번호 |
| `ai_usage_log` | `feature_type` | VARCHAR |  | AI 기능 구분 |
| `ai_usage_log` | `request_status` | VARCHAR |  | 성공/실패 상태 |
| `ai_usage_log` | `input_token_count` | INT |  | 입력 토큰 수 |
| `ai_usage_log` | `output_token_count` | INT |  | 출력 토큰 수 |
| `ai_usage_log` | `error_message` | TEXT |  | 실패 사유 |
| `ai_usage_log` | `created_at` | DATETIME |  | 생성일자 |

## 22. API 오류 로그

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `api_error_log` | `id` | BIGINT | PK | API 오류 로그 고유번호 |
| `api_error_log` | `api_name` | VARCHAR |  | API 이름 |
| `api_error_log` | `request_url` | VARCHAR |  | 요청 URL |
| `api_error_log` | `http_status` | INT |  | HTTP 상태 코드 |
| `api_error_log` | `error_message` | TEXT |  | 오류 메시지 |
| `api_error_log` | `created_at` | DATETIME |  | 발생일자 |

## 23. 콘텐츠 페이지

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `content_page` | `id` | BIGINT | PK | 콘텐츠 고유번호 |
| `content_page` | `page_key` | VARCHAR | UNIQUE | 안내 페이지 식별자 |
| `content_page` | `title` | VARCHAR |  | 페이지 제목 |
| `content_page` | `content` | TEXT |  | 페이지 내용 |
| `content_page` | `version` | INT |  | 콘텐츠 버전 |
| `content_page` | `is_visible` | BOOLEAN |  | 공개 여부 |
| `content_page` | `admin_user_id` | BIGINT | FK -> `admin_user.id` | 최종 수정 관리자 |
| `content_page` | `created_at` | DATETIME |  | 등록일자 |
| `content_page` | `updated_at` | DATETIME |  | 최종수정일 |

## 24. 스케줄러 실행 로그

| 테이블명 | 컬럼명 | 타입 | PK/FK | 설명 |
| --- | --- | --- | --- | --- |
| `scheduler_log` | `id` | BIGINT | PK | 스케줄러 로그 고유번호 |
| `scheduler_log` | `job_name` | VARCHAR |  | 작업명 |
| `scheduler_log` | `job_status` | VARCHAR |  | 성공/실패 상태 |
| `scheduler_log` | `started_at` | DATETIME |  | 시작 시간 |
| `scheduler_log` | `ended_at` | DATETIME |  | 종료 시간 |
| `scheduler_log` | `target_round_no` | INT |  | [추가 2026-10-16] 작업 대상 회차. 회차와 무관한 작업은 NULL |
| `scheduler_log` | `error_message` | TEXT |  | 실패 사유 |

## 25. 주요 관계

| 기준 테이블 | 관계 | 참조 테이블 | 설명 |
| --- | --- | --- | --- |
| `number_issue.id` | 1:N | `issue_number.number_issue_id` | 하나의 발급 기록은 여러 게임 번호를 가진다. |
| `issue_number.id` | 1:1 | `winning_verification.issue_number_id` | 하나의 발급 번호 조합은 하나의 검증 결과만 가진다. |
| `draw_round.round_no` | 1:N | `winning_verification.round_no` | 추첨 완료된 회차 데이터를 기준으로 여러 번호 조합을 검증한다. |
| `draw_round.round_no` | 1:N | `lotto_statistics.base_round_no` | 추첨 완료된 회차 데이터를 기준으로 여러 통계 결과를 계산한다. |
| `draw_round.round_no` | 1:N | `draw_prize.round_no` | 하나의 회차는 1~5등 당첨금 정보를 가진다. |
| `lotto_store.id` | 1:N | `store_winning_history.lotto_store_id` | 하나의 판매점은 여러 당첨 이력을 가질 수 있다. |
| `draw_round.round_no` | 1:N | `store_winning_history.round_no` | 판매점 당첨 이력은 특정 회차와 연결된다. |
| `number_issue.id` | 1:N | `anonymous_verification.number_issue_id` | 하나의 발급 기록으로 인증을 조회할 수 있다. |
| `file_upload.id` | 1:1 | `anonymous_verification.file_upload_id` | 인증 1건은 사진 파일 1개만 연결한다. |
| `dream_symbol.id` | 1:N | `dream_symbol_alias.dream_symbol_id` | 하나의 대표 상징은 여러 자연어 별칭을 가질 수 있다. |
| `dream_symbol.id` | 1:N | `dream_interpretation.dream_symbol_id` | 하나의 대표 상징은 여러 상황별 해석을 가질 수 있다. |
| `dream_symbol.id` | 1:N | `dream_symbol_number.dream_symbol_id` | 하나의 대표 상징은 여러 추천 번호를 가질 수 있다. |
| `dream_symbol.id` | 1:N | `dream_symbol_source.dream_symbol_id` | 하나의 대표 상징은 여러 출처와 연결될 수 있다. |
| `dream_source.id` | 1:N | `dream_symbol_source.dream_source_id` | 하나의 출처는 여러 상징과 연결될 수 있다. |
| `admin_user.id` | 1:N | `notice.admin_user_id` | 하나의 관리자가 여러 공지사항을 작성할 수 있다. |
| `admin_user.id` | 1:N | `admin_action_log.admin_user_id` | 하나의 관리자는 여러 운영 작업 로그를 남긴다. |
| `admin_user.id` | 1:N | `content_page.admin_user_id` | 하나의 관리자는 여러 안내 콘텐츠를 수정할 수 있다. |
| `draw_round.round_no` | 1:N | `stat_round_snapshot.basis_round_no` | [추가 2026-10-16] 추첨 완료된 회차까지의 결과로 회차별 통계를 계산한다. |
| `stat_round_snapshot.id` | 1:N | `stat_pattern_card.snapshot_id` | [추가 2026-10-16] 하나의 회차별 통계는 6개 패턴 카드를 가진다. |
| `lotto_store.id` | 1:1 | `store_win_summary.lotto_store_id` | [추가 2026-10-16] 하나의 판매점은 하나의 당첨 집계를 가진다. |
| `store_data_upload.id` | 1:N | `store_win_summary.upload_id` | [추가 2026-10-16] 하나의 업로드가 여러 판매점 집계를 반영한다. |
| `store_data_upload.id` | 1:N | `lotto_store.first_seen_upload_id` | [추가 2026-10-16] 하나의 업로드가 처음 확인한 여러 판매점을 가진다. |
| `admin_user.id` | 1:N | `store_data_upload.admin_user_id` | [추가 2026-10-16] 하나의 관리자는 여러 판매점 파일을 업로드한다. |

## 26. ERD 작성 기준

ERD는 이 문서를 기준으로 작성한다.

ERD에서 반드시 표시할 것:

- 각 테이블의 PK
- 각 테이블의 FK
- `number_issue.target_round_no`는 FK가 아니라 일반 INT라는 점
- `winning_verification.issue_number_id`는 FK이면서 UNIQUE라는 점
- `number_issue`와 `issue_number`는 트랜잭션 저장 대상이라는 점
- `draw_round`는 추첨 완료된 공식 회차만 저장한다는 점
- `dream_symbol`은 대표 상징이고, 자연어 별칭/상황별 해석/추천 번호/출처는 별도 테이블로 분리한다는 점
- [추가 2026-10-16] `stat_round_snapshot.target_round_no`는 FK가 아니라 일반 INT라는 점
- [추가 2026-10-16] `store_win_summary.lotto_store_id`는 FK이면서 UNIQUE라는 점

## 27. 구현 시 주의사항

- `draw_round.round_no`는 UNIQUE이며 1개 회차가 중복 저장되면 안 된다.
- `draw_prize.round_no + winning_rank`는 UNIQUE이며 한 회차·등수 조합은 1개만 저장한다.
- `issue_number.number_1`부터 `number_6`까지는 오름차순으로 저장한다.
- `issue_number.combo_key`는 오름차순 번호를 기준으로 생성한다.
- `number_issue`의 `guest_token + target_round_no` 조회 인덱스를 생성한다.
- `issue_number`는 `number_issue_id + combo_key` 조회 인덱스를 생성한다.
- `number_issue` 저장 실패 시 `issue_number`도 저장되면 안 된다.
- `issue_number` 저장 실패 시 `number_issue`도 롤백되어야 한다.
- 번호 생성 API는 생성 결과와 임시 확정 토큰만 반환하고, 발급 확정 API가 토큰과 TTL을 검증한 뒤 DB에 저장한다.
- `game_count`와 실제 저장된 `issue_number` 개수는 반드시 일치해야 한다.
- 같은 `issue_number_id`로 당첨 검증 결과가 2개 이상 저장되면 안 된다.
- 같은 게스트·대상 회차·조합은 확정 저장 기준 3회를 초과할 수 없다.
- 회차 데이터는 공식 API 기반 실제 데이터로 적재한다.
- 개발 중 필요한 관리자, 공지, 꿈 상징, 로그 예시는 시연용 데이터로만 구분한다.
- API Key, 관리자 비밀번호, 외부 서비스 키는 DB에 평문 저장하지 않는다.
- `guest_token`과 발급코드에는 조회·요청 횟수 제한을 적용한다.
- 인증 사진은 비공개 저장하고 허용 확장자·용량·콘텐츠 유형을 검증한다.
- 꿈 상징 추천 번호는 문자열로 저장하지 않는다.
- 사용자 꿈 원문 전체는 기본 저장하지 않는다.
- 꿈 데이터 출처는 `dream_source`, `dream_symbol_source`로 추적한다.
- [추가 2026-10-16] `stat_round_snapshot.target_round_no`는 UNIQUE이며, 같은 회차의 통계가 2개 만들어지면 안 된다.
- [추가 2026-10-16] `stat_pattern_card`는 `snapshot_id + pattern_code`가 UNIQUE이다.
- [추가 2026-10-16] 회차별 통계 작업은 여러 번 실행해도 결과가 같아야 한다. 실패하면 이전 통계를 유지하고 `scheduler_log`와 관리자 알림을 남긴다.
- [추가 2026-10-16] 패턴 번호는 서버 통계 알고리즘이 만들며, 같은 조합은 서비스 전체에서 최대 3회까지만 허용한다.
- [추가 2026-10-16] 판매점 파일은 반영 전에 기존 데이터와 비교하고, 새로 생기거나 바뀐 판매점만 지오코딩한다.
- [추가 2026-10-16] 내 위치와 검색 위치는 저장하거나 로그에 남기지 않고 AI에도 보내지 않는다.
- 동행복권 API가 일시적으로 실패하면 재시도와 실패 로그를 남기며, 최초 구축 시에는 과거 회차 1회 적재 작업과 공식 대체 데이터 경로를 준비한다.

## 28. 변경 이력

| 날짜 | 구분 | 대상 | 내용 |
| --- | --- | --- | --- |
| 2026-10-16 | 신규 테이블 | `stat_round_snapshot` | 회차별 통계의 준비 중 / 결과 대기 / 확정 상태 관리 |
| 2026-10-16 | 신규 테이블 | `stat_pattern_card` | 회차별 6개 통계 패턴 카드와 패턴 번호 생성 조건 |
| 2026-10-16 | 신규 테이블 | `store_win_summary` | 판매점별 1등 자동·수동·반자동, 2등 집계 |
| 2026-10-16 | 신규 테이블 | `store_data_upload` | 판매점 파일 업로드·반영 이력 |
| 2026-10-16 | 컬럼 추가 | `lotto_store` | `geocode_status`, `geocoded_at`, `source_base_date`, `first_seen_upload_id`, `first_seen_at` |
| 2026-10-16 | 컬럼 추가 | `number_issue` | `generation_basis_type`, `generation_basis_value` |
| 2026-10-16 | 컬럼 추가 | `scheduler_log` | `target_round_no` |
| 2026-10-16 | 문서 수정 | 0~1장, 25~27장 | 테이블 수 23 → 27개, 핵심 결정·관계·ERD 기준·구현 주의사항 추가 |

확정이 필요한 항목:

- 루키 명당의 "신규" 판정 기준
- 패턴 개수, 계산 기간(최근 100회), 패턴 이름·설명 문구
- `store_winning_history`를 유지할지, `store_win_summary`로 합칠지(현재는 둘 다 유지)
- 판매점 유형 코드(1, 2 등)의 의미
- 지오코딩 서비스와 이용 조건
- 동행복권 1·2등 배출점 엑셀의 이용 가능 여부와 조건
