# FitMate P0 Backend 2인 역할 분담

- 작성일: 2026-09-03
- 최종 수정일: 2026-09-03
- 버전: v0.1
- 상태: 검토 중
- 기준 문서:
  - `docs/01-spec/db-설계-초기화-전-기획.md`
  - `docs/01-spec/p0-api-명세.md`
  - `db/migration/V1__create_p0_schema.sql`
  - `db/migration/V2__seed_p0_reference_data.sql`

## 1. 분담 원칙

P0 Backend를 다음 두 도메인으로 분리한다.

| 담당 | 책임 영역 | 최종 결과 |
|---|---|---|
| Backend A | 공통 기반, 인증, 회원, 프로필, 동의 | 사용자가 가입·로그인하고 운동 프로필을 완성할 수 있음 |
| Backend B | 운동 사전, 루틴, 운동 세션, 세트, 리포트 | 로그인 사용자가 혼자 헬스를 완료하고 기본 리포트를 볼 수 있음 |

Backend B는 Backend A가 제공하는 인증 사용자 ID만 사용한다. 운동 도메인에서 회원의 비밀번호, token과 인증 세션을 직접 처리하지 않는다.

## 2. 최초 공동 작업

두 사람이 각자 기능 개발을 시작하기 전에 아래 작업을 먼저 완료한다.

1. Kotlin Spring Boot 프로젝트를 `backend/`에 초기화한다.
2. Spring Web, Validation, Security, Data JPA, MySQL, Flyway와 Test 의존성을 구성한다.
3. `db/migration/V1__create_p0_schema.sql`과 `V2__seed_p0_reference_data.sql`을 Flyway 경로에 연결한다.
4. 빈 MySQL에서 V1과 V2가 순서대로 적용되는지 확인한다.
5. 공통 package 구조와 API 응답 형식을 합의한다.
6. 초기화 작업을 먼저 PR로 병합한 뒤 A와 B가 각자 브랜치에서 개발한다.

최초 공동 작업의 구현 담당은 Backend A로 하고, Backend B가 리뷰와 실행 검증을 맡는다.

## 3. Backend A 담당

### 3.1 담당 범위

- Kotlin Spring Boot 초기 프로젝트
- Flyway와 MySQL 연결
- 공통 응답과 오류 처리
- 입력값 검증
- UUID 변환과 UTC 시간 처리
- Spring Security
- JWT access token
- refresh token rotation과 서버 세션
- 회원가입과 로그인
- 현재 기기 및 전체 기기 로그아웃
- 회원과 계정 상태
- 프로필과 운동 정보
- 약관과 동의
- 활동 지역과 이용 시설
- 앱 내 알림 설정값
- 회원 탈퇴

### 3.2 담당 테이블

| 순서 | 테이블 | 역할 |
|---|---|---|
| 1 | `core_users` | 계정과 상태 |
| 2 | `core_auth_sessions` | refresh token 세션 |
| 3 | `core_revoked_access_tokens` | access JWT 폐기 |
| 4 | `core_auth_events` | 인증 보안 이력 |
| 5 | `core_terms_documents` | 약관 원본 |
| 6 | `core_regions` | 활동 지역 사전 |
| 7 | `core_facilities` | 이용 시설 |
| 8 | `core_user_profiles` | 사용자 프로필 |
| 9 | `core_user_consents` | 동의 이력 |
| 10 | `core_user_activity_regions` | 사용자 활동 지역 |
| 11 | `core_user_facilities` | 사용자 이용 시설 |
| 12 | `core_user_available_times` | 운동 가능 시간 |
| 13 | `core_notification_settings` | 앱 내 알림 설정 |
| 14 | `core_user_sports` | 관심 종목과 숙련도 |
| 15 | `core_user_goals` | 운동 목표와 주 목표 |

`core_user_sports`는 Backend B가 관리하는 `fitness_sports`를 참조한다. A는 사용자 선택 관계를 담당하고 B는 종목 사전 자체를 담당한다.

### 3.3 담당 API

| 구현 순서 | Method | Path |
|---|---|---|
| 1 | `GET` | `/api/v1/auth/email-availability` |
| 2 | `GET` | `/api/v1/auth/nickname-availability` |
| 3 | `GET` | `/api/v1/terms` |
| 4 | `GET` | `/api/v1/regions` |
| 5 | `GET` | `/api/v1/facilities` |
| 6 | `POST` | `/api/v1/auth/signup` |
| 7 | `POST` | `/api/v1/auth/login` |
| 8 | `POST` | `/api/v1/auth/refresh` |
| 9 | `POST` | `/api/v1/auth/logout` |
| 10 | `POST` | `/api/v1/auth/logout-all` |
| 11 | `GET` | `/api/v1/users/me` |
| 12 | `PATCH` | `/api/v1/users/me/profile` |
| 13 | `PATCH` | `/api/v1/users/me/password` |
| 14 | `GET` | `/api/v1/users/me/consents` |
| 15 | `PUT` | `/api/v1/users/me/consents/{termsDocumentId}` |
| 16 | `DELETE` | `/api/v1/users/me/consents/{termsDocumentId}` |
| 17 | `GET` | `/api/v1/users/me/notification-settings` |
| 18 | `PATCH` | `/api/v1/users/me/notification-settings` |
| 19 | `DELETE` | `/api/v1/users/me` |

### 3.4 담당 테스트

- 빈 MySQL Flyway migration
- 약관과 지역 seed 확인
- 중복 이메일과 닉네임
- BCrypt 비밀번호 비교
- 계정 상태별 로그인 제한
- JWT 발급과 검증
- refresh token rotation
- 폐기 token 재사용 감지
- 현재 기기와 전체 기기 로그아웃
- 다른 사용자의 프로필 수정 차단
- 프로필 optimistic lock 충돌
- 운동 가능 시간 검증
- 주 활동 지역과 주 목표 1개 제약
- 필수 약관 동의
- 회원 탈퇴와 인증 세션 무효화

### 3.5 완료 기준

다음 흐름이 통합 테스트에서 성공하면 Backend A의 P0 담당 범위가 완료된다.

`사전 조회 -> 회원가입 -> 로그인 -> token 갱신 -> 내 프로필 조회 -> 프로필 수정 -> 로그아웃`

## 4. Backend B 담당

### 4.1 담당 범위

- 운동 종목 사전
- 헬스 운동 사전
- 대체 운동 관계
- 정적 추천 루틴
- 시스템 루틴과 사용자 루틴
- `SOLO` 운동 세션
- 운동 시작, 일시정지, 재시작, 완료와 취소
- 세션 운동 추가, 삭제, 순서 변경과 대체
- 세트별 중량, 반복 횟수, 시간, 휴식, RPE와 메모
- 운동 도중 임시 저장을 위한 upsert
- 이전 동일 운동 기록
- 기본 개인 리포트 계산
- 생성 요청 idempotency
- 운동 도메인 optimistic lock

### 4.2 담당 테이블

| 순서 | 테이블 | 역할 |
|---|---|---|
| 1 | `fitness_sports` | 운동 종목 사전 |
| 2 | `fitness_exercise_catalog` | 헬스 운동 사전 |
| 3 | `fitness_exercise_substitutions` | 대체 운동 관계 |
| 4 | `fitness_routines` | 시스템 및 사용자 루틴 |
| 5 | `fitness_routine_items` | 루틴 운동 순서와 목표값 |
| 6 | `fitness_workout_sessions` | 개인 운동 세션 |
| 7 | `fitness_workout_session_exercises` | 세션 운동 스냅샷 |
| 8 | `fitness_workout_sets` | 세트별 기록 |
| 9 | `ops_idempotency_keys` | 생성 요청 중복 방지 |

### 4.3 담당 API

| 구현 순서 | Method | Path |
|---|---|---|
| 1 | `GET` | `/api/v1/sports` |
| 2 | `GET` | `/api/v1/exercises` |
| 3 | `GET` | `/api/v1/exercises/{exerciseId}` |
| 4 | `GET` | `/api/v1/exercises/{exerciseId}/substitutions` |
| 5 | `GET` | `/api/v1/routines/recommended` |
| 6 | `GET` | `/api/v1/routines/templates` |
| 7 | `GET` | `/api/v1/routines` |
| 8 | `GET` | `/api/v1/routines/{routineId}` |
| 9 | `POST` | `/api/v1/routines` |
| 10 | `PATCH` | `/api/v1/routines/{routineId}` |
| 11 | `DELETE` | `/api/v1/routines/{routineId}` |
| 12 | `POST` | `/api/v1/workout-sessions` |
| 13 | `GET` | `/api/v1/workout-sessions` |
| 14 | `GET` | `/api/v1/workout-sessions/{sessionId}` |
| 15 | `POST` | `/api/v1/workout-sessions/{sessionId}/start` |
| 16 | `POST` | `/api/v1/workout-sessions/{sessionId}/pause` |
| 17 | `POST` | `/api/v1/workout-sessions/{sessionId}/resume` |
| 18 | `POST` | `/api/v1/workout-sessions/{sessionId}/complete` |
| 19 | `POST` | `/api/v1/workout-sessions/{sessionId}/cancel` |
| 20 | `POST` | `/api/v1/workout-sessions/{sessionId}/exercises` |
| 21 | `PATCH` | `/api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}` |
| 22 | `DELETE` | `/api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}` |
| 23 | `PUT` | `/api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}/sets/{setNo}` |
| 24 | `GET` | `/api/v1/exercises/{exerciseId}/previous-record` |
| 25 | `GET` | `/api/v1/workout-sessions/{sessionId}/report` |

### 4.4 담당 테스트

- 종목, 운동, 대체 운동과 루틴 seed 확인
- 주 목표와 헬스 숙련도 기반 정적 추천
- 추천 템플릿이 없을 때 초급 전신 fallback
- 시스템 템플릿 수정과 삭제 차단
- 사용자 루틴 소유권 검사
- 루틴과 루틴 항목 optimistic lock 충돌
- `SOLO`, `READY` 세션 생성
- 선택 루틴의 세션 운동 스냅샷 생성
- 운동 세션 상태 전이
- 잘못된 상태 전이 차단
- 운동 추가, 삭제, 순서 변경과 대체
- 세트 수치와 RPE 범위 검증
- 세트 upsert와 version 충돌
- 같은 Idempotency-Key 재전송 시 중복 생성 방지
- 다른 사용자의 세션과 리포트 접근 차단
- Samsung Health Data 없이 운동 완료
- 완료 세트, 반복 수와 총 볼륨 계산
- 직전 동일 운동 기록 비교

### 4.5 완료 기준

Backend A의 인증을 사용해 다음 흐름이 통합 테스트에서 성공하면 Backend B의 P0 담당 범위가 완료된다.

`로그인 사용자 -> 추천 루틴 조회 -> SOLO 세션 생성 -> 운동 시작 -> 세트 기록 -> 일시정지·재시작 -> 운동 완료 -> 기본 리포트 조회`

## 5. 공유 계약

두 담당자가 같은 기능을 중복 구현하지 않도록 아래 계약을 먼저 확정한다.

### 5.1 Backend A가 Backend B에 제공

| 계약 | 내용 |
|---|---|
| 현재 사용자 | 검증된 JWT에서 `userId`를 제공 |
| 계정 상태 | `ACTIVE` 사용자만 보호 API 접근 허용 |
| 공통 오류 | API 오류 응답 형식과 오류 코드 변환 |
| 공통 시간 | UTC `Instant`와 DB `DATETIME(3)` 변환 |
| 공통 ID | UUID와 MySQL `BINARY(16)` 변환 |
| 공통 감사 컬럼 | `createdAt`, `updatedAt` 처리 |

### 5.2 Backend B가 Backend A에 제공

| 계약 | 내용 |
|---|---|
| 운동 종목 조회 | `sportCode`로 활성 종목 확인 |
| 사용자 종목 검증 | 프로필에 저장할 `sportId`와 숙련도 허용 여부 확인 |
| 추천 루틴 조회 | 사용자 주 목표와 `HEALTH` 숙련도를 입력으로 사용 |

### 5.3 공통 오류 처리

- 인증 없음과 token 오류: `401`
- 계정 상태와 소유권 오류: `403`
- 없는 리소스: `404`
- 중복과 version 충돌: `409`
- 잘못된 요청값: `400`

## 6. 파일 충돌 방지

| 영역 | 소유자 |
|---|---|
| `backend/.../common` | Backend A 작성, Backend B 사용 |
| `backend/.../auth` | Backend A |
| `backend/.../member` | Backend A |
| `backend/.../terms` | Backend A |
| `backend/.../fitness` | Backend B |
| `backend/.../routine` | Backend B |
| `backend/.../workout` | Backend B |
| `backend/.../report` | Backend B |
| `V1__create_p0_schema.sql` | 이미 공유된 migration이므로 직접 수정하지 않음 |
| `V2__seed_p0_reference_data.sql` | 이미 공유된 migration이므로 직접 수정하지 않음 |

V1 또는 V2를 수정해야 할 문제가 발견되면 기존 파일을 고치지 않고 새 Flyway migration을 추가한다. `core_*` 변경 migration은 Backend A, `fitness_*` 변경 migration은 Backend B가 작성한다. 두 영역을 함께 변경하면 상대 담당자의 리뷰를 받는다.

## 7. 브랜치와 PR 순서

| 순서 | 브랜치 예시 | 담당 | 내용 |
|---|---|---|---|
| 1 | `feature/backend-bootstrap` | A | Kotlin Spring Boot, 공통 설정, Flyway 연결 |
| 2 | `feature/auth-member` | A | 인증, 회원, 프로필과 동의 |
| 3 | `feature/fitness-reference-routine` | B | 종목, 운동 사전과 루틴 |
| 4 | `feature/workout-session` | B | 세션, 운동, 세트와 상태 전이 |
| 5 | `feature/workout-report` | B | 이전 기록과 기본 리포트 |
| 6 | `test/p0-integration` | 공동 | 가입부터 운동 완료까지 통합 테스트 |

각 브랜치는 앞 단계가 병합된 최신 `main`을 기준으로 만든다. 한 PR에 인증과 운동 전체를 동시에 넣지 않는다.

## 8. 실제 진행 순서

### 1단계

- A가 Kotlin Spring Boot와 Flyway를 초기화한다.
- B가 V1, V2를 검토하고 빈 MySQL 적용 결과를 확인한다.

### 2단계

- A가 공통 응답, 오류, UUID, UTC와 인증을 구현한다.
- B가 운동 사전과 루틴 엔티티, Repository와 조회 기능을 구현한다.

### 3단계

- A가 회원가입, 프로필, 동의와 로그아웃을 구현한다.
- B가 운동 세션, 세션 운동, 세트 기록과 상태 전이를 구현한다.

### 4단계

- A가 계정 상태, token 재사용, 탈퇴와 권한 테스트를 완료한다.
- B가 이전 기록, 기본 리포트와 idempotency 테스트를 완료한다.

### 5단계

- 두 사람이 회원가입부터 혼자 헬스 완료까지 통합 테스트한다.
- Android 요청·응답과 API 명세가 일치하는지 확인한다.

## 9. 공동 P0 완료 시나리오

1. 사용자가 이메일, 비밀번호와 운동 프로필로 가입한다.
2. 사용자가 로그인해 access token과 refresh token을 받는다.
3. 사용자의 주 목표와 헬스 숙련도에 맞는 정적 루틴을 조회한다.
4. 루틴을 선택해 `SOLO`, `READY` 운동 세션을 만든다.
5. 운동을 시작하고 세트별 중량, 반복 수와 RPE를 기록한다.
6. 운동을 일시정지하고 재시작한다.
7. Samsung Health Data 없이 운동을 완료한다.
8. 완료 세트 수, 총 반복 수, 총 볼륨과 이전 기록 비교를 조회한다.
9. 다른 사용자는 해당 운동 세션과 리포트를 조회할 수 없다.
