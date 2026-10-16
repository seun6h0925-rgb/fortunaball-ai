# 새 컴퓨터에서 이어서 작업하기 (집 PC / 학원 PC 공용)

코드와 문서는 GitHub에 있다. 컴퓨터에 따로 만들어야 하는 건 **프로그램 설치, `.env`, 로컬 DB** 세 가지뿐이다.

## 1. 프로그램 설치 (한 번만)
| 프로그램 | 비고 |
|---|---|
| Git for Windows | Git Bash 포함 |
| JDK 21 | IntelliJ에서 받아도 됨 |
| IntelliJ IDEA | Community 무료로 충분 |
| MySQL 8.0 Community | 설치 중 **root 비밀번호를 직접 정하고 메모**. 포트 3306 그대로 |
| Node.js LTS | 프론트 작업 시작할 때 설치 |

## 2. 코드 받기
```bash
cd /c/myws/semi_project          # 폴더가 없으면 mkdir -p 로 먼저 만든다
git clone https://github.com/seun6h0925-rgb/fortunaball-ai.git
cd fortunaball-ai
```

## 3. `.env` 만들기 (프로젝트 루트, GitHub에는 올라가지 않음)
`.env.example`을 복사해 `.env`로 이름을 바꾸고 채운다.
```
DB_HOST=localhost
DB_PORT=3306
DB_NAME=fortuna_ball
DB_USER=fortuna
DB_PASSWORD=<4단계에서 정한 fortuna 비밀번호>
```
나머지 키(AI_API_KEY 등)는 해당 기능을 만들 때 채운다.
**주의:** 메모장으로 만들 때 `.env.txt`가 되지 않게 "파일 형식: 모든 파일"로 저장한다.

## 4. DB 만들기 (IntelliJ 기준, 순서 중요)
1. IntelliJ Database 창에서 `+ > Data Source > MySQL` → User `root`, root 비밀번호(3단계의 설치 때 정한 값), Database는 `mysql` → Test Connection 성공 → OK. (관리용 접속기)
2. 이 접속기를 오른쪽 클릭 → `New > Query Console`에서 아래를 실행한다. `<새비밀번호>`는 직접 정하고, 편집 창을 Ctrl+A로 전체 선택한 뒤 Ctrl+Enter.
   ```sql
   CREATE USER 'fortuna'@'localhost' IDENTIFIED BY '<새비밀번호>';
   CREATE DATABASE IF NOT EXISTS fortuna_ball DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   GRANT ALL PRIVILEGES ON fortuna_ball.* TO 'fortuna'@'localhost';
   SHOW GRANTS FOR 'fortuna'@'localhost';
   ```
   결과에 `fortuna_ball`이 들어간 줄이 나오면 성공. (같은 내용이 `docs/db/schema.sql` 맨 위 주석에도 있다.)
3. `+ > Data Source > MySQL`로 두 번째 접속기를 만든다: Name `fortuna_ball@localhost`, User `fortuna`, Password `<새비밀번호>`, Database `fortuna_ball` → Test Connection 성공 → OK.
4. 3번 접속기(`fortuna_ball@localhost`)를 오른쪽 클릭 → `SQL Scripts > Run SQL Script...` → `docs/db/schema.sql` 선택. 끝나면 새로고침(⟳) 후 `tables`가 **30**인지 확인.
5. 같은 방법으로 `docs/db/seed_dream_symbols.sql` 실행.
- `schema.sql`·시드는 **처음 한 번만** 실행한다. 이미 테이블이 있으면 `already exists`, 시드는 중복 키 오류로 멈추며 기존 데이터는 지워지지 않는다.

## 5. 서버 실행 확인
IntelliJ에서 `backend` 폴더를 Gradle 프로젝트로 연다. 터미널(`backend` 폴더)에서:
```
.\gradlew test
.\gradlew bootRun
```
브라우저에서 `http://localhost:8080/api/health` 가 `success: true`면 완료.

## 6. 컴퓨터를 옮길 때 같이 옮길 것 (GitHub에 없는 것)
- 로컬 DB에 **직접 넣은 데이터**(판매점·당첨점 등)는 `mysqldump`로 파일을 만들어 USB나 개인 클라우드로 옮긴다.
  `"/c/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe" -u root -p --default-character-set=utf8mb4 fortuna_ball > fortuna_backup.sql`
- 원본 CSV(판매점 목록, 1등 자동 당첨 현황)와 `.env`의 키 값은 공개 저장소에 올리지 말고 따로 보관한다.
- 작업을 끝낼 때마다 `git push`, 시작할 때마다 `git pull` 하면 두 컴퓨터가 같은 상태로 유지된다.

## 7. 다른 컴퓨터에서 Claude에게 이어서 시키기
저장소 루트의 `CLAUDE.md`에 프로젝트 규칙과 진행 상황을 적어 두었다. 새 대화에서 "CLAUDE.md 읽고 이어서 작업해줘"라고 하면 같은 방식으로 이어진다.
