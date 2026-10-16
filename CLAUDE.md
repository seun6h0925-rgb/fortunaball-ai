# 포르투나볼 AI — 작업 인계 (새 대화·새 컴퓨터에서 가장 먼저 읽을 것)

## 서비스
통계 기반 로또 정보·번호 생성 서비스. AI 분석 챗봇, 꿈 해석, 통계, 당첨 회차 조회, 서비스 당첨 기록, 판매점 지도, 세금 계산기, 관리자 사이트. 축소 MVP가 아니라 최종 완성본 기준으로 진행한다.
- 스택: Java 21, Spring Boot 3.5.0, Gradle 8.14.3, MySQL 8.0(DB `fortuna_ball`, 30개 테이블), Spring Data JPA(`ddl-auto: none`), 프론트는 React(fetch). 패키지 `com.fortunaball`.
- 설계 문서는 `docs/`(api, db, design). DB 스키마는 `docs/db/schema.sql`.

## 사용자 작업 규칙 (반드시 지킬 것)
- 백엔드 먼저, 프론트는 그 다음. 한 번에 기능 하나씩 하되 전체를 생각하고 만든다.
- 기능 1개 = 브랜치 1개(`feature/NN-이름`), 기능 끝날 때 커밋(`feat(범위): 내용`), `--no-ff` 병합. 명령은 사용자가 Git Bash에서 실행하므로 붙여넣기 가능한 형태로 준다.
- 패키지는 `domain/<기능>/{controller,service,repository,entity,dto}`로 기능별 정리, IntelliJ 탐색기에서 찾기 쉽게. 자료구조도 고려.
- 중요하거나 헷갈리는 코드는 위에 주석. 학원에서 배운 내용을 쓴 부분은 "📚 배운 내용"으로 표시(주석 포함). 학원 자료는 참고용이고 안정성·완성도가 우선.
- 오류는 추적이 쉬워야 한다(사용자가 오류일지에 기록). 응답/예외/traceId 규칙은 아래 참고.
- 방식은 안정성·속도·관리 편의·비용 기준으로 하나를 확정해서 제시. 되는지/범위/우선순위는 사용자가 정한다. 자료를 주면 먼저 파악만 알려주고, 요청 전에는 만들지 않는다.
- 긴 질문 금지, 구체적이고 단순한 지시를 선호한다.

## 이미 정해진 정책
- 번호는 서버 통계 알고리즘만 생성, AI는 설명만. 같은 회차·같은 조합은 확정 발급 최대 3회(`combo_issue_count`). 패턴 생성은 `issue_type=STAT_CUSTOM`, `generation_basis_type=STAT_PATTERN`, `generation_basis_value=<패턴코드>`.
- 공통 응답: 성공 `{success,data,message}`, 실패 `{success,errorCode,message,traceId}`. 오류코드 `COMMON-001~008, 999`. 요청마다 traceId(MDC, `X-Trace-Id` 헤더), 로그 `logs/app.log`, `logs/error.log`.
- 위치정보: 지도 스위치 → 동의/거절 대화상자 → 브라우저 권한. 위치는 저장·로그·AI 전송 금지(AI에는 결과 판매점 목록만). 위치정보법 검토는 미확인.
- 공유는 "공유하기" 단일 버튼(Web Share API, 실패 시 링크 복사).
- 지도 표시는 네이버 지도, 주소→좌표는 `GeocodingClient` 인터페이스 뒤에 둔다. 행안부 도로명주소 좌표제공 API는 신청 후 승인 대기.

## 진행 상황
- 완료: 요구사항·기능·화면·API·DB 설계 문서, 백엔드 뼈대(공통 응답, 예외 처리, traceId 필터, 로그 설정, `/api/health`, 예외 핸들러 테스트) — main에 병합됨.
- 진행 중: 내 컴퓨터에 MySQL을 설치하고 `schema.sql` 실행 → `gradlew test` / `bootRun` 확인(샌드박스에서는 빌드 검증이 불가했음). 절차는 `docs/setup/NEW_PC_SETUP.md`.
- 다음: 꿈 상징 시드 적재(`docs/db/seed_dream_symbols.sql`) → 첫 기능 브랜치. 기능 우선순위는 사용자가 정한다.
- 나중에: 2등·회차별 당첨점 데이터 점진 수집(우선 있는 데이터로 시작), 판매점 CSV 적재(좌표 없음), 행안부 API 승인 결과 반영, 위치정보법 확인, 꿈 데이터 확장(감정·행동 층 필드 스키마 확장 여부), Security/관리자, 오류를 `api_error_log`에 저장, 프론트.
