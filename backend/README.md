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

`GET /api/v1/exercises`의 `cursor`는 현재 토대 단계에서는 페이지 인덱스 문자열로 동작한다. 실제 운영용 keyset cursor는 운동 세션 구현 단계에서 함께 고도화하면 된다.

## 아직 안 넣은 것

- JWT 인증/인가
- 회원/프로필/약관/지역/시설
- 루틴 생성/수정/삭제
- 운동 세션/세트 생성과 상태 전이
- 리포트 계산
- idempotency 처리 로직

위 항목은 역할분담 문서 기준으로 Backend A 계약 또는 Backend B 상세 구현 단계에서 이어서 붙이면 된다.

## 실행 전 준비

1. JDK 22와 Docker Desktop이 설치되어 있어야 한다.
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

## 다음 추천 작업 순서

1. `routine` 조회 API 구현
2. `workout` 세션 생성 API 구현
3. 세트 upsert와 version 충돌 처리
4. report 계산 서비스 구현
5. Backend A 인증 계약 연결
