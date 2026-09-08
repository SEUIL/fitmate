# FitMate P0 API 명세

- 작성일: 2026-09-03
- 최종 수정일: 2026-09-07
- 버전: v0.6
- 상태: 검토 중
- 대상: Kotlin Spring Boot Backend, Android Client
- 기준 문서:
  - `docs/01-spec/기능-개발-우선순위.md`
  - `docs/01-spec/기능-구현-백로그.md`
  - `docs/01-spec/db-설계-초기화-전-기획.md`

## 1. 목적

초기 DDL 생성 후 바로 P0 백엔드 개발에 들어갈 수 있도록 화면과 서버 사이의 API 계약을 정의한다.

P0의 사용자 흐름은 다음과 같다.

`회원가입·로그인 -> 운동 프로필 작성 -> 혼자 운동에서 헬스 또는 러닝 선택 -> 헬스 루틴 선택 -> SOLO 운동 세션 생성 -> 세트 기록 -> 운동 완료 -> 기본 개인 리포트`

이 문서는 기존 기능과 DB 기획을 API 형태로 구체화한 초안이다. 새로운 사용자 기능은 추가하지 않는다.

## 2. P0 범위

### 2.1 포함

- 이메일과 비밀번호 회원가입 및 로그인
- JWT access token과 refresh token 세션
- 현재 기기 및 전체 기기 로그아웃
- 운동 프로필과 동의
- 혼자 운동 진입용 활성 종목 `HEALTH`, `RUNNING`
- 지역, 시설, 종목 사전
- 헬스 운동 사전과 대체 운동
- 정적 추천 루틴, 사용자 저장 루틴과 직접 구성 루틴
- `SOLO` 운동 세션
- 세션 운동과 세트 기록
- 시작, 일시정지, 재시작, 완료와 취소
- 이전 운동 기록 조회
- 기본 개인 리포트

### 2.2 P0 제외

- 이메일 인증 메일과 인증 처리
- 비밀번호 재설정 메일과 재설정 token
- 프로필 이미지 업로드
- Push 알림 발송
- Samsung Health Data 서버 저장과 동기화
- 러닝 기록의 서버 저장, 동기화와 리포트
- 같이 운동하기
- 일반 커뮤니티와 DM
- 인바디와 AI 판독
- 관리자
- 광고와 제휴

기능명세서에는 이메일 인증과 비밀번호 재설정 지원이 있으나, DB 초기화 기획에서 첫 구현 제외로 확정했으므로 P0 API에는 포함하지 않는다.

## 3. DDL 선행 작업

API 구현 전에 Flyway로 아래 순서의 테이블과 시드를 생성한다.

### 3.1 첫 마이그레이션 테이블 순서

| 순서 | 물리 테이블 | API 사용 목적 |
|---|---|---|
| 1 | `core_users` | 계정, 상태, 인증 버전 |
| 2 | `core_auth_sessions` | refresh token rotation과 기기 세션 |
| 3 | `core_revoked_access_tokens` | access JWT 즉시 무효화 |
| 4 | `core_auth_events` | 로그인, 로그아웃과 token 재사용 이력 |
| 5 | `core_terms_documents` | 약관과 개인정보 동의 문서 |
| 6 | `core_regions` | 활동 지역 사전 |
| 7 | `core_facilities` | 이용 헬스장과 운동시설 |
| 8 | `core_user_profiles` | 닉네임, 이름과 공개 프로필 |
| 9 | `core_user_consents` | 사용자 동의 이력 |
| 10 | `core_user_activity_regions` | 사용자 활동 지역 |
| 11 | `core_user_facilities` | 사용자 이용 시설 |
| 12 | `core_user_available_times` | 운동 가능 시간 |
| 13 | `core_notification_settings` | 앱 내 알림 범주 설정. Backend A가 V3 migration으로 추가 |
| 14 | `fitness_sports` | 운동 종목 사전 |
| 15 | `fitness_exercise_catalog` | 헬스 운동 사전 |
| 16 | `fitness_exercise_substitutions` | 대체 운동 관계 |
| 17 | `core_user_sports` | 관심 종목과 숙련도 |
| 18 | `core_user_goals` | 운동 목표와 주 목표 |
| 19 | `fitness_routines` | 시스템 및 사용자 루틴 |
| 20 | `fitness_routine_items` | 루틴의 운동 순서와 목표값 |
| 21 | `fitness_workout_sessions` | 개인 운동 세션 |
| 22 | `fitness_workout_session_exercises` | 운동 당시 종목 스냅샷 |
| 23 | `fitness_workout_sets` | 세트별 기록 |
| 24 | `ops_idempotency_keys` | 생성 요청 중복 처리 방지 |
| 25 | `core_user_custom_sports` | 사용자 프로필 전용 커스텀 관심 운동. V4 migration으로 추가 |

`core_user_custom_sports`는 `user_id`, `name`, `normalized_name`, `created_at`을 저장한다. 기본 키는 `(user_id, normalized_name)`이며 같은 이름을 다른 사용자가 등록하는 것은 허용한다. 운영 종목 사전인 `fitness_sports`와 숙련도를 저장하는 `core_user_sports`는 기존 루틴 추천 계약을 유지한다. 커스텀 관심 운동에는 `sportCode`, `sportId`, `skillLevel`을 부여하지 않는다.

### 3.2 초기 시드

- `regions`: 시·도와 시 단위 지역
- `sports`: V2에서 전체 운동 종목 코드를 생성하고 V5에서 혼자 운동 기본 진입 종목인 `HEALTH`, `RUNNING`을 활성화
- `exercise_catalog`: 헬스 운동 20~30개와 YouTube `reference_url`
- `exercise_substitutions`: 같은 종목 안의 대체 운동 관계
- `routines`: 시스템 템플릿 5개
- `terms_documents`: 이용약관 v1, 개인정보 수집·이용 동의 v1

시스템 루틴 템플릿은 다음 다섯 개다.

- 초급 전신
- 초급 2분할
- 근육 증가 3분할
- 다이어트 전신
- 운동 습관용 전신

## 4. 공통 API 규칙

### 4.1 기본 형식

| 항목 | 계약 |
|---|---|
| Base URL | `/api/v1` |
| 요청과 응답 | `application/json` |
| API 필드명 | `camelCase` |
| DB 컬럼명 | `snake_case` |
| 업무 ID | UUID 문자열 |
| 시각 | ISO 8601 UTC, 예: `2026-09-03T10:30:00.123Z` |
| 인증 | `Authorization: Bearer {accessToken}` |
| 생성 중복 방지 | 인증된 생성 요청은 `Idempotency-Key: {clientGeneratedUuid}` 사용. 회원가입은 활성 이메일 unique와 단일 트랜잭션으로 중복 생성 방지 |
| 수정 충돌 방지 | 요청의 `version`과 서버의 현재 version 비교 |

### 4.2 성공 응답

단건 응답은 `data`에 결과를 반환한다.

```json
{
  "data": {
    "id": "7c6c394e-eabb-4e7b-9aaa-5065df3d80b9"
  }
}
```

목록 응답은 `data.items`와 다음 cursor를 반환한다.

```json
{
  "data": {
    "items": [],
    "nextCursor": null,
    "hasNext": false
  }
}
```

### 4.3 오류 응답

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청값이 올바르지 않습니다.",
    "fields": [
      {
        "field": "email",
        "reason": "INVALID_FORMAT"
      }
    ]
  }
}
```

### 4.4 HTTP 상태 코드

| 상태 | 사용 기준 |
|---|---|
| `200 OK` | 조회와 수정 성공 |
| `201 Created` | 계정, 루틴, 세션과 세트 생성 성공 |
| `204 No Content` | 응답 본문이 없는 삭제와 로그아웃 성공 |
| `400 Bad Request` | 형식 오류, 필수값 누락과 허용되지 않은 상태 전이 |
| `401 Unauthorized` | 인증 없음, token 만료 또는 무효 |
| `403 Forbidden` | 계정 상태 또는 소유권으로 접근 불가 |
| `404 Not Found` | 대상 리소스가 없거나 접근할 수 없음 |
| `409 Conflict` | 이메일·닉네임 중복, version 충돌과 중복 요청 충돌 |
| `422 Unprocessable Entity` | 형식은 맞지만 업무 규칙을 충족하지 못함 |

### 4.5 공통 오류 코드

| 오류 코드 | HTTP | 의미 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | 요청 필드 검증 실패 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |
| `INVALID_ACCESS_TOKEN` | 401 | access token이 유효하지 않음 |
| `EXPIRED_ACCESS_TOKEN` | 401 | access token 만료 |
| `INVALID_REFRESH_TOKEN` | 401 | refresh token이 유효하지 않음 |
| `REFRESH_TOKEN_REUSE_DETECTED` | 401 | 폐기된 refresh token 재사용 감지 |
| `ACCOUNT_SUSPENDED` | 403 | 일시 정지 계정 |
| `ACCOUNT_BANNED` | 403 | 영구 정지 계정 |
| `ACCOUNT_WITHDRAWN` | 403 | 탈퇴 계정 |
| `RESOURCE_NOT_FOUND` | 404 | 리소스 없음 |
| `EMAIL_ALREADY_EXISTS` | 409 | 활성 계정 이메일 중복 |
| `NICKNAME_ALREADY_EXISTS` | 409 | 활성 계정 닉네임 중복 |
| `VERSION_CONFLICT` | 409 | 수정 version 불일치 |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | 같은 key로 다른 요청 본문 전송 |
| `INVALID_STATE_TRANSITION` | 409 | 현재 상태에서 요청 상태로 변경 불가 |
| `FORBIDDEN_RESOURCE` | 403 | 다른 사용자의 리소스 접근 |

## 5. 인증 API

### 5.1 API 목록

| 작업 ID | Method | Path | 인증 | 설명 |
|---|---|---|---|---|
| `MEMBER-02` | `GET` | `/auth/email-availability` | 불필요 | 이메일 중복 확인 |
| `MEMBER-04` | `GET` | `/auth/nickname-availability` | 불필요 | 닉네임 중복 확인 |
| `MEMBER-01~12` | `POST` | `/auth/signup` | 불필요 | 회원가입과 운동 프로필 저장 |
| `MEMBER-13` | `POST` | `/auth/login` | 불필요 | 로그인과 token 발급 |
| `MEMBER-13` | `POST` | `/auth/refresh` | refresh token | token rotation |
| `MEMBER-19` | `POST` | `/auth/logout` | 필요 | 현재 기기 로그아웃 |
| `MEMBER-19` | `POST` | `/auth/logout-all` | 필요 | 전체 기기 로그아웃 |

### 5.2 이메일 중복 확인

`GET /api/v1/auth/email-availability?email=user@example.com`

```json
{
  "data": {
    "email": "user@example.com",
    "available": true
  }
}
```

이메일 비교는 공백과 대소문자를 정규화한 `email_normalized`를 사용한다.

### 5.3 닉네임 중복 확인

`GET /api/v1/auth/nickname-availability?nickname=fitmate`

```json
{
  "data": {
    "nickname": "fitmate",
    "available": true
  }
}
```

### 5.4 회원가입

`POST /api/v1/auth/signup`

회원가입에는 `Idempotency-Key`를 사용하지 않는다. 가입 전에는 `ops_idempotency_keys.user_id`를 결정할 수 없고 token 응답 원문도 저장하지 않기 때문이다. 재요청에 따른 중복 계정은 정규화 이메일 unique 제약과 가입 트랜잭션으로 차단한다.

Request:

```json
{
  "email": "user@example.com",
  "password": "plain-password-from-client",
  "displayName": "홍길동",
  "nickname": "fitmate",
  "birthDate": "2000-01-01",
  "gender": "MALE",
  "visibility": "PUBLIC",
  "bio": "운동을 시작합니다.",
  "weightUnit": "KG",
  "timezoneId": "Asia/Seoul",
  "activityRegionIds": [
    "10000000-0000-0000-0000-000000000001"
  ],
  "facilityNames": [
    "FitMate 헬스장"
  ],
  "availableTimes": [
    {
      "weekdayLocal": 1,
      "startLocalTime": "19:00:00",
      "endLocalTime": "21:00:00",
      "timezoneId": "Asia/Seoul"
    }
  ],
  "sports": [
    {
      "sportCode": "HEALTH",
      "skillLevel": "BEGINNER"
    }
  ],
  "customSports": [
    { "name": "필라테스" },
    { "name": "크로스핏" }
  ],
  "goals": [
    {
      "goalCode": "HABIT",
      "primary": true
    }
  ],
  "consentDocumentIds": [
    "20000000-0000-0000-0000-000000000001",
    "20000000-0000-0000-0000-000000000002"
  ],
  "deviceId": "android-installation-id"
}
```

루틴 없이 운동을 직접 선택해서 시작할 수도 있다.

```json
{
  "exercises": [
    {
      "exerciseId": "30000000-0000-0000-0000-000000000001",
      "sequenceNo": 1,
      "targetSets": 3,
      "targetWeightKg": 20,
      "targetRepetitions": 10
    }
  ]
}
```

`routineId`와 `exercises` 중 하나만 보낸다. 서버는 목표 세트 수만큼 미완료 세트를 생성하고, 프론트는 각 세트의 무게와 횟수를 수정해 완료 처리한다.

Response `201 Created`:

```json
{
  "data": {
    "user": {
      "id": "ea1155ab-4a65-46fb-953d-974d33e3ced9",
      "email": "user@example.com",
      "accountStatus": "ACTIVE",
      "profile": {
        "nickname": "fitmate",
        "displayName": "홍길동",
        "visibility": "PUBLIC",
        "weightUnit": "KG",
        "timezoneId": "Asia/Seoul",
        "version": 0
      },
      "customSports": [
        { "name": "크로스핏" },
        { "name": "필라테스" }
      ]
    },
    "tokens": {
      "accessToken": "access-token",
      "accessTokenExpiresInSeconds": 900,
      "refreshToken": "refresh-token"
    }
  }
}
```

검증 규칙:

- 활성 계정의 이메일과 닉네임은 중복될 수 없다.
- P0 필수 입력은 이메일, 비밀번호, 닉네임, 필수 약관 2건, 주 목표 1개와 `HEALTH` 숙련도다. `displayName`을 생략하면 닉네임과 같은 값으로 저장하고 나머지 프로필 입력은 선택 사항으로 처리한다.
- 비밀번호는 8~64자이며 문자와 숫자를 각각 하나 이상 포함하고, BCrypt 입력은 UTF-8 기준 72바이트를 넘을 수 없다.
- 비밀번호는 BCrypt hash만 DB에 저장한다.
- 활동 지역은 등록된 `regionId`만 허용한다.
- `facilityNames`가 있으면 `activityRegionIds`는 정확히 1개여야 하며, 해당 지역을 주 활동 지역으로 사용해 시설을 조회하거나 생성한다.
- 관심 종목은 복수 선택할 수 있다.
- `customSports`는 사용자 프로필 전용 관심 운동 이름 목록이다. 회원가입에서 생략하면 `[]`로 저장하고 응답의 `data.user.customSports`에 반환한다.
- 커스텀 관심 운동은 최대 20개다. 각 이름은 입력 및 정규화 후 1~50자이며, NFKC 유니코드 정규화·앞뒤 공백 제거·연속 공백 축약 후 표시 이름을 저장한다. 비교 키인 `normalized_name`에는 소문자 변환도 적용한다. 제어 문자, 공백뿐인 이름, 정규화 후 중복은 `400 VALIDATION_ERROR`로 거절한다. 응답 순서는 비교 키의 오름차순이며 입력 순서를 보장하지 않는다.
- `customSports`는 운영 종목이나 루틴 추천 대상을 추가하지 않으며, 필수 `HEALTH` 숙련도를 대체하지 않는다.
- 운동 목표는 복수 선택할 수 있고 주 목표는 하나다.
- 운동 가능 시간은 `weekdayLocal 0..6`이고 시작 시간이 종료 시간보다 앞서야 한다.
- 필수 약관에 모두 동의해야 한다.
- P0는 이메일 인증 없이 즉시 계정을 생성한다.

### 5.5 로그인

`POST /api/v1/auth/login`

```json
{
  "email": "user@example.com",
  "password": "plain-password-from-client",
  "deviceId": "android-installation-id"
}
```

Response `200 OK`:

```json
{
  "data": {
    "userId": "ea1155ab-4a65-46fb-953d-974d33e3ced9",
    "accountStatus": "ACTIVE",
    "tokens": {
      "accessToken": "access-token",
      "accessTokenExpiresInSeconds": 900,
      "refreshToken": "refresh-token"
    }
  }
}
```

- `ACTIVE` 계정만 정상 로그인할 수 있다.
- 성공 시 `last_login_at`과 인증 이벤트를 기록한다.
- refresh token 원문은 응답으로 한 번 전달하고 DB에는 hash만 저장한다.
- access token 만료는 15분, refresh token 만료는 30일로 한다.

### 5.6 Token 갱신

`POST /api/v1/auth/refresh`

```json
{
  "refreshToken": "refresh-token",
  "deviceId": "android-installation-id"
}
```

Response `200 OK`:

```json
{
  "data": {
    "accessToken": "new-access-token",
    "accessTokenExpiresInSeconds": 900,
    "refreshToken": "new-refresh-token"
  }
}
```

- 기존 refresh session을 `ROTATED`로 폐기하고 새 session을 생성한다.
- 이미 폐기된 refresh token이 다시 제출되면 `REUSE_DETECTED`를 기록한다.
- 재사용 감지 시 사용자의 모든 refresh session을 폐기하고 `auth_version`을 증가시킨다.

### 5.7 로그아웃

현재 기기:

`POST /api/v1/auth/logout`

```json
{
  "refreshToken": "refresh-token"
}
```

전체 기기:

`POST /api/v1/auth/logout-all`

- 현재 기기 로그아웃은 현재 refresh session과 access JWT의 `jti`를 폐기한다.
- 전체 기기 로그아웃은 모든 refresh session을 폐기하고 `auth_version`을 증가시킨다.
- 성공 응답은 `204 No Content`다.

## 6. 내 계정과 프로필 API

### 6.1 API 목록

| 작업 ID | Method | Path | 설명 |
|---|---|---|---|
| `MEMBER-14` | `GET` | `/users/me` | 내 계정과 운동 프로필 조회 |
| `MEMBER-14` | `PATCH` | `/users/me/profile` | 프로필과 운동 정보 수정 |
| `MEMBER-15` | `PATCH` | `/users/me/password` | 로그인 상태에서 비밀번호 변경 |
| `MEMBER-18` | `GET` | `/users/me/notification-settings` | 앱 내 알림 설정 조회 |
| `MEMBER-18` | `PATCH` | `/users/me/notification-settings` | 앱 내 알림 설정 수정 |
| `MEMBER-20` | `DELETE` | `/users/me` | 회원 탈퇴 |

### 6.2 내 계정 조회

`GET /api/v1/users/me`

Response `200 OK`:

```json
{
  "data": {
    "id": "ea1155ab-4a65-46fb-953d-974d33e3ced9",
    "email": "user@example.com",
    "accountStatus": "ACTIVE",
    "profile": {
      "nickname": "fitmate",
      "displayName": "홍길동",
      "birthDate": "2000-01-01",
      "gender": "MALE",
      "visibility": "PUBLIC",
      "bio": "운동을 시작합니다.",
      "weightUnit": "KG",
      "timezoneId": "Asia/Seoul",
      "version": 0
    },
    "activityRegions": [],
    "facilities": [],
    "availableTimes": [],
    "sports": [],
    "customSports": [
      { "name": "필라테스" }
    ],
    "goals": [],
    "consents": []
  }
}
```

### 6.3 프로필 수정

`PATCH /api/v1/users/me/profile`

변경할 값과 현재 `version`을 전송한다.

```json
{
  "nickname": "new-fitmate",
  "displayName": "홍길동",
  "birthDate": "2000-01-01",
  "gender": "MALE",
  "visibility": "PRIVATE",
  "bio": "저녁 운동을 선호합니다.",
  "weightUnit": "KG",
  "timezoneId": "Asia/Seoul",
  "activityRegionIds": [
    "10000000-0000-0000-0000-000000000001"
  ],
  "facilityNames": [
    "FitMate 헬스장"
  ],
  "availableTimes": [],
  "sports": [
    {
      "sportCode": "HEALTH",
      "skillLevel": "UNDER_6_MONTHS"
    }
  ],
  "customSports": [
    { "name": "필라테스" },
    { "name": "크로스핏" }
  ],
  "goals": [
    {
      "goalCode": "MUSCLE_GAIN",
      "primary": true
    }
  ],
  "version": 0
}
```

Response는 내 프로필 조회와 같은 `data` 구조이며, 갱신된 `data.profile.version`, `data.sports`, `data.customSports`를 포함한다. `version`은 필수이고 0 이상의 정수다. 현재 version과 다르면 `409 VERSION_CONFLICT`를 반환한다. 커스텀 관심 운동만 변경해도 프로필 version이 증가한다.
프로필 수정에서도 `facilityNames`가 있으면 `activityRegionIds`는 정확히 1개여야 하며 모든 시설을 해당 지역에 연결한다.

- 수정에서 생략하거나 `null`로 보낸 선택 필드는 기존 값을 유지한다. `bio: ""`는 소개를 지운다.
- 목록 필드는 전달한 목록으로 교체한다. `customSports: []`는 커스텀 관심 운동만 모두 삭제한다. `sports`를 전달하면 기존과 동일하게 활성 종목과 필수 `HEALTH` 숙련도를 검증한다.
- 커스텀 관심 운동의 입력 검증은 회원가입과 동일하다. 프로필과 운동 정보는 하나의 트랜잭션에서 저장되며, 일부 필드 검증이 실패하면 전체 변경을 취소한다.
- 사용자 ID는 인증 토큰에서 얻는다. 다른 사용자의 커스텀 관심 운동은 조회하거나 수정할 수 없다.
- 활동 지역 변경 시 기존 시설이 새 지역에 속하지 않으면 시설 목록도 함께 수정해야 한다.

### 6.4 비밀번호 변경

`PATCH /api/v1/users/me/password`

```json
{
  "currentPassword": "current-password",
  "newPassword": "new-password"
}
```

- 현재 비밀번호가 일치해야 한다.
- 새 비밀번호에 최소 길이와 조합 규칙을 적용한다.
- 변경 후 모든 인증 세션을 무효화한다.

### 6.5 앱 내 알림 설정

`GET /api/v1/users/me/notification-settings`

```json
{
  "data": {
    "inAppEnabled": true,
    "dmEnabled": true,
    "mateEnabled": true,
    "communityEnabled": true,
    "version": 0
  }
}
```

`PATCH /api/v1/users/me/notification-settings`

```json
{
  "inAppEnabled": true,
  "dmEnabled": false,
  "mateEnabled": true,
  "communityEnabled": true,
  "version": 0
}
```

P0에서는 설정값만 저장하며 Push device token과 외부 Push 발송은 구현하지 않는다.
`core_notification_settings`는 기존 V1/V2를 수정하지 않고 Backend A의 V3 migration에서 추가한다.

### 6.6 회원 탈퇴

`DELETE /api/v1/users/me`

```json
{
  "password": "current-password"
}
```

- 탈퇴 전 삭제 또는 보관되는 데이터 범위를 화면에서 안내한다.
- P0에서는 계정 상태를 `WITHDRAWN`으로 변경하고 `withdrawn_at`, 프로필 `deleted_at`을 기록한다.
- 모든 인증 세션을 폐기하고 `auth_version`을 증가시킨다.
- 운동 기록과 동의 이력의 실제 삭제·익명화는 후속 개인정보 파기 정책과 migration에서 처리한다.

## 7. 약관 API

### 7.1 API 목록

| 작업 ID | Method | Path | 설명 |
|---|---|---|---|
| `CONSENT-01~05` | `GET` | `/terms` | 현재 유효한 약관 목록 조회 |
| `CONSENT-01~05` | `GET` | `/users/me/consents` | 내 동의 상태 조회 |
| `CONSENT-03~05` | `PUT` | `/users/me/consents/{termsDocumentId}` | 선택 동의 |
| `CONSENT-03~05` | `DELETE` | `/users/me/consents/{termsDocumentId}` | 선택 동의 철회 |

### 7.2 약관 목록

`GET /api/v1/terms`

Query:

- `type`: 약관 유형 선택
- `current=true`: 현재 유효한 버전만 조회

Response:

```json
{
  "data": {
    "items": [
      {
        "id": "20000000-0000-0000-0000-000000000001",
        "type": "SERVICE_TERMS",
        "version": "v1",
        "contentUri": "/legal/service-terms/v1",
        "effectiveAt": "2026-09-03T00:00:00.000Z",
        "required": true
      }
    ]
  }
}
```

- 철회는 동의 행을 삭제하지 않고 `withdrawn_at`을 기록한다.
- P0에서는 활성 계정의 필수 약관 철회를 허용하지 않는다. 선택 약관만 `withdrawn_at`을 기록해 철회한다.

## 8. 공통 사전 API

### 8.1 API 목록

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/regions` | 활성 지역 목록 |
| `GET` | `/facilities` | 지역별 시설 목록 |
| `GET` | `/sports` | 운동 종목 목록 |
| `GET` | `/exercises` | 헬스 운동 목록 |
| `GET` | `/exercises/{exerciseId}` | 운동 설명과 참고 링크 |
| `GET` | `/exercises/{exerciseId}/substitutions` | 대체 운동 목록 |

### 8.2 지역 목록

`GET /api/v1/regions?parentId={regionId}`

```json
{
  "data": {
    "items": [
      {
        "id": "10000000-0000-0000-0000-000000000001",
        "parentId": null,
        "name": "서울특별시",
        "level": "PROVINCE",
        "code": "KR-SEOUL"
      }
    ]
  }
}
```

### 8.3 시설 목록

`GET /api/v1/facilities?regionId={regionId}&name={name}`

- 초기 시설 시드는 없다.
- 사용자가 입력한 시설은 지역별 정규화 이름으로 기존 시설을 찾고 없으면 생성한다.
- 정확 좌표는 저장하지 않는다.

### 8.4 종목 목록

`GET /api/v1/sports`

P0 응답에는 혼자 운동 기본 진입 종목인 `HEALTH`, `RUNNING`을 제공한다.

```json
{
  "data": {
    "items": [
      {
        "id": "01000000-0000-0000-0000-000000000001",
        "code": "HEALTH",
        "name": "헬스"
      },
      {
        "id": "01000000-0000-0000-0000-000000000002",
        "code": "RUNNING",
        "name": "러닝"
      }
    ]
  }
}
```

Android의 혼자 러닝 화면 진입은 이 활성 종목을 사용한다. 러닝 기록의 서버 저장, 동기화와 리포트 API는 P1 범위다.

사용자가 직접 입력한 `customSports`는 이 목록에 포함하지 않는다. 사용자용 `POST /sports`는 추가하지 않는다.

### 8.5 운동 목록

`GET /api/v1/exercises?sportCode=HEALTH&bodyPart=CHEST&equipmentType=MACHINE&cursor={cursor}&size=20`

```json
{
  "data": {
    "items": [
      {
        "id": "bf7a5460-854d-481c-9786-ed224a1a3db0",
        "sportCode": "HEALTH",
        "name": "체스트 프레스",
        "bodyPart": "CHEST",
        "equipmentType": "MACHINE",
        "referenceUrl": "https://www.youtube.com/watch?v=example"
      }
    ],
    "nextCursor": null,
    "hasNext": false
  }
}
```

`referenceUrl`은 허용된 YouTube HTTPS URL만 반환한다.

## 9. 루틴 API

### 9.1 API 목록

| 작업 ID | Method | Path | 설명 |
|---|---|---|---|
| `SOLO-BEGINNER-02` | `GET` | `/routines/recommended` | 주 목표와 숙련도 기반 정적 추천 |
| `WORKOUT-COMMON-01` | `GET` | `/routines/templates` | 시스템 템플릿 목록 |
| `WORKOUT-COMMON-01` | `GET` | `/routines` | 내 저장 루틴 목록 |
| `WORKOUT-COMMON-01` | `GET` | `/routines/{routineId}` | 루틴 상세 |
| `SOLO-HEALTH-04` | `POST` | `/routines` | 직접 구성 또는 템플릿 복사 |
| `SOLO-HEALTH-04` | `PATCH` | `/routines/{routineId}` | 내 루틴 수정 |
| `SOLO-HEALTH-04` | `DELETE` | `/routines/{routineId}` | 내 루틴 삭제 |

### 9.2 추천 루틴

`GET /api/v1/routines/recommended?sportCode=HEALTH`

- 사용자의 주 목표와 `HEALTH` 숙련도에 맞는 `SYSTEM_TEMPLATE` 하나를 반환한다.
- 선택 순서는 주 목표와 숙련도 정확 일치, 같은 목표의 `targetLevel=null` 공통 템플릿, `FITNESS+BEGINNER` 초급 전신 fallback이다.
- 일치하는 템플릿이 없으면 헬스 초급 전신 루틴을 반환한다.
- `sportCode=RUNNING`은 러닝용 루틴 템플릿이 없으므로 `400 VALIDATION_ERROR`를 반환한다.
- P0에서는 AI, 인바디와 건강 데이터를 추천에 사용하지 않는다.

`GET /api/v1/routines/templates`와 `GET /api/v1/routines`는 다음 목록 구조를 사용한다. 템플릿 목록은 활성 시스템 템플릿만 반환하고, 내 루틴 목록은 인증 사용자가 소유한 활성 사용자 루틴만 반환한다.

```json
{
  "data": {
    "items": [
      {
        "id": "40000000-0000-0000-0000-000000000001",
        "ownerUserId": null,
        "sportCode": "HEALTH",
        "name": "초급 전신 루틴",
        "sourceType": "SYSTEM_TEMPLATE",
        "targetGoalCode": "FITNESS",
        "targetLevel": "BEGINNER",
        "difficulty": "BEGINNER",
        "splitCount": 1,
        "template": true,
        "version": 0
      }
    ]
  }
}
```

시스템 템플릿 상세는 모든 로그인 사용자가 조회할 수 있다. 사용자 루틴은 소유자만 목록과 상세를 조회할 수 있으며, 다른 사용자의 사용자 루틴 상세 요청은 `404 RESOURCE_NOT_FOUND`를 반환한다.

### 9.3 루틴 상세 응답

```json
{
  "data": {
    "id": "6fb84380-a9f9-4641-95a2-76525a723610",
    "ownerUserId": null,
    "sportCode": "HEALTH",
    "name": "초급 전신",
    "sourceType": "SYSTEM_TEMPLATE",
    "targetGoalCode": "HABIT",
    "targetLevel": "BEGINNER",
    "difficulty": "BEGINNER",
    "splitCount": 1,
    "template": true,
    "version": 0,
    "items": [
      {
        "id": "0200c66e-8d74-48df-8b9c-716d00af9451",
        "exerciseId": "bf7a5460-854d-481c-9786-ed224a1a3db0",
        "exerciseName": "체스트 프레스",
        "bodyPart": "CHEST",
        "sequenceNo": 1,
        "splitDayNo": 1,
        "targetSets": 3,
        "targetRepsMin": 8,
        "targetRepsMax": 12,
        "targetWeightKg": null,
        "targetRpe": null,
        "restSeconds": 60,
        "version": 0
      }
    ]
  }
}
```

### 9.4 사용자 루틴 생성

`POST /api/v1/routines`

직접 구성:

```json
{
  "sportCode": "HEALTH",
  "name": "나의 전신 루틴",
  "sourceType": "MANUAL",
  "splitCount": 1,
  "items": [
    {
      "exerciseId": "bf7a5460-854d-481c-9786-ed224a1a3db0",
      "sequenceNo": 1,
      "splitDayNo": 1,
      "targetSets": 3,
      "targetRepsMin": 8,
      "targetRepsMax": 12,
      "targetWeightKg": 20,
      "targetRpe": 7,
      "restSeconds": 60
    }
  ]
}
```

시스템 템플릿 복사:

```json
{
  "sourceRoutineId": "6fb84380-a9f9-4641-95a2-76525a723610",
  "name": "내 초급 전신 루틴",
  "sourceType": "USER_COPY"
}
```

- 시스템 템플릿은 수정하거나 삭제할 수 없다.
- 사용자 루틴은 소유자만 수정하고 삭제할 수 있다.
- 운동 순서는 루틴 안에서 중복될 수 없다.
- `splitCount`는 `1..10`, 각 항목의 `splitDayNo`는 `1..splitCount`이며 모든 분할에 운동이 하나 이상 필요하다.
- 목표 중량, 반복 수, RPE와 휴식 시간의 허용 범위를 검증한다.
- 직접 구성은 `sourceType=MANUAL`, `sportCode=HEALTH`, 하나 이상의 `items`를 사용한다.
- 템플릿 복사는 `sourceType=USER_COPY`, `sourceRoutineId`를 사용하며 원본 항목을 사용자 루틴으로 복제한다.

수정은 루틴 전체의 `version`으로 동시 변경을 검사한다. `items`를 보내면 기존 항목 목록 전체를 새 목록으로 교체한다.

`PATCH /api/v1/routines/{routineId}`

```json
{
  "version": 0,
  "name": "수정한 전신 루틴",
  "splitCount": 1,
  "items": [
    {
      "exerciseId": "bf7a5460-854d-481c-9786-ed224a1a3db0",
      "sequenceNo": 1,
      "splitDayNo": 1,
      "targetSets": 4,
      "targetRepsMin": 8,
      "targetRepsMax": 12,
      "targetWeightKg": 25,
      "targetRpe": 8,
      "restSeconds": 90
    }
  ]
}
```

삭제도 현재 루틴 `version`을 사용한다.

`DELETE /api/v1/routines/{routineId}?version=1`

## 10. 운동 세션 API

### 10.1 API 목록

| 작업 ID | Method | Path | 설명 |
|---|---|---|---|
| `SOLO-HEALTH-02` | `POST` | `/workout-sessions` | `SOLO` 세션 생성 |
| `WORKOUT-COMMON-01` | `GET` | `/workout-sessions/{sessionId}` | 세션과 운동·세트 조회 |
| `WORKOUT-COMMON-08` | `GET` | `/workout-sessions` | 내 운동 기록 목록 |
| `WORKOUT-COMMON-03` | `POST` | `/workout-sessions/{sessionId}/start` | 운동 시작 |
| `WORKOUT-COMMON-03` | `POST` | `/workout-sessions/{sessionId}/pause` | 운동 일시정지 |
| `WORKOUT-COMMON-03` | `POST` | `/workout-sessions/{sessionId}/resume` | 운동 재시작 |
| `WORKOUT-COMMON-03` | `POST` | `/workout-sessions/{sessionId}/complete` | 운동 완료 |
| `WORKOUT-COMMON-03` | `POST` | `/workout-sessions/{sessionId}/cancel` | 운동 취소 |
| `WORKOUT-COMMON-04` | `POST` | `/workout-sessions/{sessionId}/exercises` | 세션에 운동 추가 |
| `WORKOUT-COMMON-04` | `PATCH` | `/workout-sessions/{sessionId}/exercises/{sessionExerciseId}` | 운동 순서 또는 대체 운동 변경 |
| `WORKOUT-COMMON-04` | `DELETE` | `/workout-sessions/{sessionId}/exercises/{sessionExerciseId}` | 세션 운동 삭제 |
| `WORKOUT-COMMON-05` | `PUT` | `/workout-sessions/{sessionId}/exercises/{sessionExerciseId}/sets/{setNo}` | 세트 기록 생성 또는 수정 |
| `WORKOUT-COMMON-08` | `GET` | `/exercises/{exerciseId}/previous-record` | 동일 운동의 이전 기록 |
| `REPORT` | `GET` | `/workout-sessions/{sessionId}/report` | 기본 개인 리포트 |

### 10.2 SOLO 세션 생성

`POST /api/v1/workout-sessions`

Header:

```text
Authorization: Bearer {accessToken}
Idempotency-Key: 6b5d9427-c319-47ce-90f1-37d2876043cb
```

Request:

```json
{
  "entryType": "SOLO",
  "routineId": "6fb84380-a9f9-4641-95a2-76525a723610",
  "clientUpdatedAt": "2026-09-03T10:30:00.123Z"
}
```

Response `201 Created`:

```json
{
  "data": {
    "id": "6c070701-88ca-434f-99b1-3c330d0a6f12",
    "entryType": "SOLO",
    "routineId": "6fb84380-a9f9-4641-95a2-76525a723610",
    "routineWorkoutDayNumber": 3,
    "routineSplitDayNo": 1,
    "routineSplitCount": 2,
    "focusBodyParts": ["CHEST", "BACK", "SHOULDERS"],
    "status": "READY",
    "startedAt": null,
    "endedAt": null,
    "reportStatus": "PENDING",
    "version": 0,
    "exercises": []
  }
}
```

- `SOLO` 세션의 `mateParticipationId`는 `null`이다.
- 선택한 루틴의 운동을 세션 운동 스냅샷으로 생성한다.
- 같은 루틴의 완료 세션 수를 기준으로 누적 운동일을 계산하고, 분할 순번을 `1..splitCount` 안에서 순환한다. 취소 세션은 진행 횟수에서 제외한다.
- 세션에는 현재 분할의 운동만 복사하며 `routineWorkoutDayNumber`, `routineSplitDayNo`, `routineSplitCount`, `focusBodyParts`를 반환한다.
- 카탈로그나 루틴이 나중에 변경되어도 진행 중인 세션의 운동명과 부위를 유지한다.
- Samsung Health 사용 여부는 P0에서 Android 화면 내부 선택이며 서버에 외부 데이터를 저장하지 않는다.

### 10.3 세션 상태 변경

공통 Request:

```json
{
  "version": 0,
  "clientUpdatedAt": "2026-09-03T10:31:00.000Z"
}
```

허용 상태 전이:

```text
READY -> IN_PROGRESS
IN_PROGRESS -> PAUSED
PAUSED -> IN_PROGRESS
IN_PROGRESS|PAUSED -> COMPLETED
READY|IN_PROGRESS|PAUSED -> CANCELED
```

P0에서는 Samsung Health 서버 동기화를 하지 않으므로 `SYNC_WAITING`, `REVIEW_REQUIRED` 상태 전이는 사용하지 않는다.

취소 Request는 사유를 함께 보낼 수 있다.

```json
{
  "cancelReason": "사용자 취소",
  "version": 1,
  "clientUpdatedAt": "2026-09-03T10:40:00.000Z"
}
```

### 10.4 세션 운동 추가

`POST /api/v1/workout-sessions/{sessionId}/exercises`

```json
{
  "exerciseId": "bf7a5460-854d-481c-9786-ed224a1a3db0",
  "sequenceNo": 3,
  "targetSets": 3,
  "targetWeightKg": 20,
  "targetRepetitions": 12,
  "clientUpdatedAt": "2026-09-03T10:35:00.000Z"
}
```

Response `201 Created`는 운동 사전에서 복사한 운동명·부위, `sequenceNo`, `version`과 생성된 미완료 세트를 반환한다. 기존 위치에 추가하면 뒤의 운동 순서를 자동으로 조정한다.

### 10.5 세션 운동 순서 또는 대체 운동 변경

`PATCH /api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}`

```json
{
  "exerciseId": "e486078b-65ca-4de0-9f7c-aad63ce6ab00",
  "sequenceNo": 2,
  "version": 0,
  "clientUpdatedAt": "2026-09-03T10:36:00.000Z"
}
```

- 대체 운동은 원래 운동과 같은 종목이어야 한다.
- 순서를 변경하면 사이에 있는 운동 순서를 자동으로 조정한다.
- 대체 운동으로 변경해도 기존 세트 기록은 유지한다.
- 완료된 세션은 수정할 수 없다.

세션 운동 삭제는 현재 운동 `version`을 사용한다. 연결된 세트를 함께 삭제하고 남은 순서를 다시 정렬한다. 마지막 운동은 삭제할 수 없다.

`DELETE /api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}?version=1`

### 10.6 세트 기록

`PUT /api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}/sets/{setNo}`

Header:

```text
Idempotency-Key: 73ceeb40-a891-4a40-b7ba-f9db31f281f0
```

Request:

```json
{
  "weightKg": 20.0,
  "repetitions": 12,
  "durationSeconds": 45,
  "rpe": 7,
  "completed": true,
  "memo": "마지막 두 회가 힘들었음",
  "clientUpdatedAt": "2026-09-03T10:37:00.000Z",
  "version": 0
}
```

Response:

```json
{
  "data": {
    "id": "1651ab6e-c7a5-402b-8434-bd140c1a4304",
    "setNo": 1,
    "weightKg": 20.0,
    "repetitions": 12,
    "durationSeconds": 45,
    "rpe": 7,
    "completed": true,
    "memo": "마지막 두 회가 힘들었음",
    "version": 1
  }
}
```

검증 규칙:

- `setNo`는 같은 세션 운동 안에서 중복될 수 없다.
- 중량, 반복 횟수, 운동 시간과 휴식 시간은 음수일 수 없다.
- RPE는 `1..10` 또는 `null`이다.
- 본인의 진행 중인 세션만 기록할 수 있다.
- `clientUpdatedAt`과 `version`으로 오프라인 재전송과 동시 수정 충돌을 확인한다.
- 세트 사이 휴식 타이머는 프론트의 로컬 UI 상태로 처리하며 서버에 저장하지 않는다.

### 10.7 동일 운동 이전 기록

`GET /api/v1/exercises/{exerciseId}/previous-record?beforeSessionId={sessionId}`

```json
{
  "data": {
    "workoutSessionId": "059208f9-f7fd-41bc-b784-e6193e04dfe5",
    "performedAt": "2026-08-30T09:00:00.000Z",
    "sets": [
      {
        "setNo": 1,
        "weightKg": 17.5,
        "repetitions": 12,
        "rpe": 6
      }
    ]
  }
}
```

### 10.8 운동 기록 목록

`GET /api/v1/workout-sessions?status=COMPLETED&cursor={cursor}&size=20`

정렬은 `startedAt DESC, id DESC`를 사용한다.

```json
{
  "data": {
    "items": [
      {
        "id": "6c070701-88ca-434f-99b1-3c330d0a6f12",
        "entryType": "SOLO",
        "routineName": "초급 전신",
        "status": "COMPLETED",
        "startedAt": "2026-09-03T10:31:00.000Z",
        "endedAt": "2026-09-03T11:20:00.000Z"
      }
    ],
    "nextCursor": null,
    "hasNext": false
  }
}
```

## 11. 기본 개인 리포트 API

### 11.1 조회

`GET /api/v1/workout-sessions/{sessionId}/report`

P0 리포트는 별도 테이블을 만들지 않고 세션, 세션 운동과 세트에서 조회 시 계산한다.

Response:

```json
{
  "data": {
    "workoutSessionId": "6c070701-88ca-434f-99b1-3c330d0a6f12",
    "reportStatus": "BASIC_READY",
    "startedAt": "2026-09-03T10:31:00.000Z",
    "endedAt": "2026-09-03T11:20:00.000Z",
    "durationSeconds": 2940,
    "exerciseCount": 4,
    "completedSetCount": 12,
    "totalRepetitions": 120,
    "totalVolumeKg": 2400.0,
    "exercises": [
      {
        "exerciseId": "bf7a5460-854d-481c-9786-ed224a1a3db0",
        "exerciseName": "체스트 프레스",
        "bodyPart": "CHEST",
        "sets": [],
        "previousComparison": {
          "previousMaxWeightKg": 17.5,
          "currentMaxWeightKg": 20.0,
          "previousVolumeKg": 630.0,
          "currentVolumeKg": 720.0
        }
      }
    ]
  }
}
```

계산 기준:

- 완료 세트 수는 `is_completed=true`인 세트만 집계한다.
- 총 반복 수는 완료 세트의 `repetitions` 합계다.
- 총 볼륨은 완료 세트의 `weight_kg × repetitions` 합계다.
- 이전 비교는 같은 사용자의 직전 동일 운동 완료 기록을 사용한다.
- 외부 건강 데이터 없이 `BASIC_READY` 리포트를 제공한다.

## 12. 화면별 API 연결

프론트 화면명이 확정되면 실제 이름으로 갱신한다.

| 화면 흐름 | 호출 API |
|---|---|
| 로그인 | `POST /auth/login` |
| 회원가입 중복 확인 | `GET /auth/email-availability`, `GET /auth/nickname-availability` |
| 회원가입 입력 | `GET /terms`, `GET /regions`, `GET /sports`, `POST /auth/signup` |
| 내 프로필 | `GET /users/me` |
| 프로필 수정 | `PATCH /users/me/profile` |
| 알림 설정 | `GET`, `PATCH /users/me/notification-settings` |
| 혼자 운동 진입 | `GET /sports`로 헬스·러닝 선택지를 조회. 헬스 선택 시 `GET /routines/recommended`, `GET /routines/templates`, `GET /routines` |
| 루틴 직접 구성 | `GET /exercises`, `GET /exercises/{id}/substitutions`, `POST /routines` |
| 운동 시작 전 | `GET /routines/{id}`, `POST /workout-sessions` |
| 운동 진행 | 세션 조회, 상태 변경, 운동 추가·변경·삭제, 세트 `PUT` |
| 이전 기록 | `GET /exercises/{id}/previous-record` |
| 운동 완료 | `POST /workout-sessions/{id}/complete` |
| 운동 결과 | `GET /workout-sessions/{id}/report` |
| 운동 기록 | `GET /workout-sessions`, `GET /workout-sessions/{id}` |

## 13. API 구현 순서

DDL과 seed가 적용된 뒤 아래 순서로 구현한다.

| 순서 | 구현 묶음 | 선행 조건 |
|---|---|---|
| 1 | 공통 응답, 오류 처리, UUID와 UTC 변환 | Spring Boot 프로젝트 초기화 |
| 2 | 회원가입, 로그인과 JWT 세션 | `core_users`, 인증 테이블 |
| 3 | 지역, 종목과 약관 조회 | 공통 사전 seed |
| 4 | 프로필, 운동 정보와 동의 | 회원 인증과 `core_user_*` 테이블 |
| 5 | 운동 사전과 대체 운동 | 헬스 운동 seed |
| 6 | 추천·시스템·사용자 루틴 | 루틴 템플릿 seed |
| 7 | SOLO 운동 세션과 상태 변경 | 루틴 API |
| 8 | 세션 운동과 세트 기록 | 운동 세션 API |
| 9 | 이전 기록과 기본 리포트 | 완료 운동 기록 |
| 10 | 로그아웃, 탈퇴와 보안 이력 | 인증·회원 기능 |

## 14. 필수 테스트

### 14.1 DDL과 seed

- 빈 MySQL에서 전체 Flyway migration 성공
- 이미 적용된 DB에서 재실행 시 변경 없음
- UUID FK의 타입과 byte order 일치
- 이메일, 닉네임, 루틴 운동 순서와 세트 순서 unique 제약
- 음수 중량·반복·시간과 범위 밖 RPE의 CHECK 제약
- 지역, 종목, 운동 사전, 대체 운동, 루틴과 약관 seed 확인

### 14.2 인증

- 중복 이메일 회원가입 거절
- BCrypt hash가 일치할 때만 로그인 성공
- 정지, 영구 정지와 탈퇴 계정 로그인 거절
- refresh token rotation 성공
- 폐기한 refresh token 재사용 감지와 전체 session 폐기
- 현재 기기 로그아웃과 전체 기기 로그아웃 구분

### 14.3 프로필과 루틴

- 사용자별 주 활동 지역 하나와 주 목표 하나 유지
- 운동 가능 시간의 시작과 종료 검증
- 주 목표와 `HEALTH` 숙련도에 맞는 정적 템플릿 선택
- 일치 템플릿이 없을 때 초급 전신 fallback
- 시스템 템플릿 수정·삭제 거절
- 다른 사용자의 루틴 조회·수정·삭제 거절
- version이 다른 수정 요청에 `409 VERSION_CONFLICT`

### 14.4 운동

- 루틴 선택 후 `SOLO`, `READY` 세션 생성
- 세션 생성 재전송 시 중복 생성 없음
- 허용 상태 전이는 성공하고 잘못된 전이는 거절
- 운동 추가, 삭제, 순서 변경과 대체 운동
- 같은 세트 재전송 시 중복 행 생성 없음
- 동시 세트 수정의 version 충돌 처리
- Samsung Health를 사용하지 않아도 `COMPLETED` 가능
- 완료 세션의 기본 리포트 계산
- 다른 사용자의 세션과 리포트 접근 거절

## 15. 프론트 확정 후 갱신할 항목

다음 항목은 화면 설계가 나오면 기능을 추가하지 않고 계약 표현만 맞춘다.

- 화면별 API 호출 시점
- 회원가입을 한 화면 또는 단계별 화면으로 나누는 방식
- 부분 수정 API에서 한 번에 보내는 필드 범위
- 목록의 기본 page size
- 오류 문구와 필드별 표시 위치
- 임시 저장 주기와 Android 재전송 시점

## 16. 후속 API 문서 분리 기준

P0 완료 후 기능 개발 순서에 맞춰 다음 문서를 별도로 작성한다.

- `p1-samsung-health-api-명세.md`
- `p1-running-api-명세.md`
- `p1-mate-api-명세.md`
- `p2-community-dm-api-명세.md`
- `p3-inbody-ai-api-명세.md`
