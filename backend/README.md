# FitMate Backend Bootstrap

`backend/`는 `docs/01-spec/p0-backend-2인-역할분담.md` 기준으로 `Backend B`가 바로 이어서 개발할 수 있도록 만든 초기 백엔드 토대다.

## 현재 포함 범위

- Kotlin + Spring Boot + JPA + Flyway + Validation + Security 기본 설정
- 로컬 MySQL 8.4용 `compose.yaml`
- 기존 `db/migration/`을 그대로 읽는 Flyway 설정
- `Backend B`용 패키지 구조
  - `common`
  - `fitness`
  - `routine`
  - `workout`
  - `report`
  - `ops`
- 조회 시작점 API
  - `GET /api/v1/health`
  - `GET /api/v1/sports`
  - `GET /api/v1/exercises`
  - `GET /api/v1/exercises/{exerciseId}`
  - `GET /api/v1/exercises/{exerciseId}/substitutions`
  - `GET /api/v1/routines/recommended`
  - `GET /api/v1/routines/templates`
  - `GET /api/v1/routines`
  - `GET /api/v1/routines/{routineId}`
- 회원가입·로그인·JWT 세션 인증
- 내 프로필 조회 `GET /api/v1/users/me`와 수정 `PATCH /api/v1/users/me/profile`
- 회원가입·프로필 API의 `customSports: [{ "name": "필라테스" }]` 저장·조회. V4 migration의 사용자 전용 테이블을 사용하며 운영 종목·숙련도는 `sports`로 유지한다.
- V5 migration부터 `GET /api/v1/sports`는 혼자 운동 기본 진입 종목인 `HEALTH`, `RUNNING`을 표시한다. 현재 추천 루틴과 세트 기반 서버 기록은 `HEALTH`만 지원하며 러닝 기록 서버 저장은 후속 범위다.

루틴 추천은 로그인 사용자의 주 목표와 해당 운영 종목 숙련도를 사용한다. 정확히 일치하는 템플릿, 같은 목표의 숙련도 공통 템플릿, 헬스 초급 전신 fallback 순서로 선택한다. 시스템 템플릿은 모든 로그인 사용자가 조회할 수 있고 사용자 루틴은 소유자만 조회할 수 있다.

`GET /api/v1/exercises`의 `cursor`는 현재 토대 단계에서는 페이지 인덱스 문자열로 동작한다. 실제 운영용 keyset cursor는 운동 세션 구현 단계에서 함께 고도화하면 된다. Flyway reference seed ID는 MySQL ordered UUID이므로 운동 사전과 루틴 API 경계에서 논리 UUID로 변환한다.

## 아직 안 넣은 것

- 비밀번호 변경·회원 탈퇴와 약관/지역/시설 전용 API
- 루틴 생성/수정/삭제
- 운동 세션/세트 생성과 상태 전이
- 리포트 계산
- idempotency 처리 로직

위 항목은 역할분담 문서 기준으로 Backend A 계약 또는 Backend B 상세 구현 단계에서 이어서 붙이면 된다.

## 실행 전 준비

1. JDK 17과 Docker Desktop이 설치되어 있어야 한다.
2. `backend/compose.yaml`로 MySQL을 띄운다.

```cmd
docker compose up -d
```

3. Windows에서는 아래 명령으로 백엔드를 실행한다.

```cmd
gradlew.bat bootRun
```

4. 아래 환경변수를 맞춘다. 환경변수를 설정하지 않으면 기본값으로 로컬 MySQL `3307` 포트를 사용한다.

```text
FITMATE_DB_URL=jdbc:mysql://localhost:3307/fitmate?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
FITMATE_DB_USERNAME=fitmate
FITMATE_DB_PASSWORD=fitmate
```

## Flyway 경로

Flyway는 아래 두 경로를 모두 본다.

- `../db/migration`
- `db/migration`

즉, `backend/`에서 실행하든 저장소 루트에서 실행하든 기존 SQL을 재사용할 수 있게 맞춰 두었다.

## 커스텀 관심 운동 검증

`gradlew.bat test`는 단위 테스트를 실행한다. 실제 MySQL 마이그레이션과 회원가입·프로필 HTTP 통합 테스트는 별도 임시 DB를 준비한 뒤 `FITMATE_TEST_DB_URL`을 해당 JDBC URL로 설정하고 `gradlew.bat test --rerun-tasks`로 실행한다. 통합 테스트는 임시 DB의 로컬 `root` 계정과 빈 비밀번호를 사용하며 테스트 사용자를 생성하므로 개발 공용·운영 DB를 지정하지 않는다. 해당 환경변수가 없으면 MySQL 통합 테스트는 건너뛴다.

검증 범위는 가입 후 재조회, 기존 클라이언트의 필드 생략, 수정 시 유지·교체·삭제, 이름 정규화와 중복 거절, 사용자별 분리, 실패 시 트랜잭션 롤백, 동시 수정의 version 충돌이다. Android의 Mock 관심 운동을 이 API에 연결하는 작업은 별도다.

## 다음 추천 작업 순서

1. 사용자 루틴 생성/수정/삭제 API 구현
2. `workout` 세션 생성 API 구현
3. 세트 upsert와 version 충돌 처리
4. report 계산 서비스 구현
5. Backend A 인증 계약 연결
