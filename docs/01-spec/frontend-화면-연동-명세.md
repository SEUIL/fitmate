# Backend A 화면·연동 명세

- 작성일: 2026-09-05
- 최종 수정일: 2026-09-05
- 버전: v0.1
- 상태: 검토 중
- 대상: Backend A P0 API (PR #18~#21)

## 1. 목적과 범위

프론트팀이 P0 시연에 필요한 회원·프로필 화면을 구현할 수 있도록 화면 단위의 입력, 상태, API 연동 규칙을 정리한다. API 기준 URL은 `{BASE_URL}/api/v1`이며, 보호 API에는 `Authorization: Bearer {accessToken}` 헤더를 사용한다.

P0에서 구현하지 않는 항목은 화면을 만들지 않거나 비활성 안내로 처리한다.

- 이메일 인증, 소셜 로그인, 비밀번호 찾기
- Push device token 등록과 실제 Push 발송
- 선택 약관의 초기 데이터와 약관 본문 뷰어
- 회원탈퇴 후 실제 데이터 파기

## 2. 공통 연동 규칙

| 구분 | 프론트 처리 |
|---|---|
| 성공 응답 | 본문은 모두 `{ "data": ... }` 형식이다. `204 No Content`는 본문을 읽지 않는다. |
| 인증 만료 | access token 만료/유효하지 않음(`401`)이면 저장된 refresh token으로 `/auth/refresh`를 한 번 호출한다. 성공하면 원래 요청을 한 번 재시도하고, 실패하면 저장 토큰을 지운 뒤 로그인 화면으로 보낸다. |
| refresh token 재사용 | `REFRESH_TOKEN_REUSE_DETECTED`는 모든 세션이 무효화된 상태이므로 즉시 로그인 화면으로 보낸다. |
| 중복 제출 | 회원가입, 로그인, 비밀번호 변경, 탈퇴, 설정 저장 버튼은 요청 중 비활성화한다. 회원가입에는 `Idempotency-Key`를 보내지 않는다. |
| 충돌 | `VERSION_CONFLICT`는 최신 내 정보/설정을 다시 조회한 뒤 “다른 변경이 반영되었습니다. 다시 확인 후 저장해 주세요.”를 표시한다. |
| 필드 오류 | `VALIDATION_ERROR`는 응답 `fields`가 있으면 해당 입력 아래에, 없으면 화면 상단 안내로 표시한다. |
| 보안 | access/refresh token, 비밀번호, 인증 헤더를 로그·분석 이벤트·화면 캡처용 텍스트에 남기지 않는다. |

### 2.1 오류 코드별 기본 안내

| 코드 | 권장 UI 처리 |
|---|---|
| `EMAIL_ALREADY_EXISTS` | 이메일 입력 아래 “이미 사용 중인 이메일입니다.” |
| `NICKNAME_ALREADY_EXISTS` | 닉네임 입력 아래 “이미 사용 중인 닉네임입니다.” |
| `INVALID_CREDENTIALS` | 로그인 폼 상단 “이메일 또는 비밀번호를 확인해 주세요.” |
| `INVALID_REFRESH_TOKEN`, `INVALID_ACCESS_TOKEN` | 로그인 화면으로 이동 |
| `VERSION_CONFLICT` | 최신 데이터 재조회 후 재입력 안내 |
| `INVALID_STATE_TRANSITION` | 필수 약관은 철회할 수 없다는 안내 |
| `ACCOUNT_WITHDRAWN` | 로그인 불가 안내 후 로그인 화면 유지 |

## 3. 화면 목록과 사용자 흐름

```text
앱 시작
 ├─ 토큰 유효 → 내 프로필
 └─ 토큰 없음/갱신 실패 → 로그인
                         └─ 회원가입
                            ├─ 계정 정보
                            ├─ 필수 약관
                            ├─ 기본 프로필 (선택)
                            ├─ 활동 지역·시설·가능 시간 (선택)
                            └─ 운동 종목·목표 → 가입 완료 → 내 프로필

내 프로필
 ├─ 프로필 편집
 ├─ 알림 설정
 ├─ 약관 동의 관리
 ├─ 비밀번호 변경
 └─ 회원 탈퇴
```

| ID | 화면 | 우선순위 | 관련 API |
|---|---|---:|---|
| A01 | 로그인 | P0 | `POST /auth/login`, `POST /auth/refresh` |
| A02 | 회원가입: 계정 정보 | P0 | `GET /auth/email-availability`, `GET /auth/nickname-availability` |
| A03 | 회원가입: 필수 약관 | P0 | `GET /terms` |
| A04 | 회원가입: 프로필·활동 정보 | P0 | `GET /regions`, `GET /facilities`, `GET /sports` |
| A05 | 내 프로필 | P0 | `GET /users/me` |
| A06 | 프로필 편집 | P0 | `PATCH /users/me/profile`, 기준 데이터 조회 API |
| A07 | 알림 설정 | P0 | `GET/PATCH /users/me/notification-settings` |
| A08 | 약관 동의 관리 | P0 | `GET /users/me/consents`, `PUT/DELETE /users/me/consents/{id}` |
| A09 | 비밀번호 변경 | P0 | `PATCH /users/me/password` |
| A10 | 회원 탈퇴 | P0 | `DELETE /users/me` |

## 4. 화면별 상세

### A01. 로그인

| 항목 | 내용 |
|---|---|
| 목적 | 기존 계정으로 로그인하고 토큰을 확보한다. |
| 입력 | 이메일(필수), 비밀번호(필수), 기기 식별자 `deviceId`(선택) |
| 호출 | `POST /auth/login` |
| 성공 | `data.tokens.accessToken`, `data.tokens.refreshToken`을 안전 저장소에 저장하고 내 프로필(A05)로 이동한다. |
| 실패 | `INVALID_CREDENTIALS`는 폼 상단 오류, 그 외 네트워크 오류는 재시도 버튼을 제공한다. |
| 화면 요소 | 이메일/비밀번호 입력, 로그인 버튼, 회원가입 이동 버튼 |

앱 시작 시 refresh token이 있으면 로그인 화면을 잠깐 표시하지 않고 `POST /auth/refresh`를 먼저 시도한다. 갱신 성공 시 새 refresh token도 반드시 교체 저장한다.

### A02. 회원가입 - 계정 정보

| 항목 | 내용 |
|---|---|
| 필수 입력 | 이메일, 비밀번호, 닉네임 |
| 선택 입력 | 표시 이름(`displayName`) — 생략하면 닉네임으로 저장 |
| 이메일 확인 | 입력 완료 후 `GET /auth/email-availability?email={email}` 호출. 실제 응답 필드는 `data.value`, `data.available`이다. |
| 닉네임 확인 | 입력 완료 후 `GET /auth/nickname-availability?nickname={nickname}` 호출. 실제 응답 필드는 `data.value`, `data.available`이다. |
| 비밀번호 규칙 | 8~64자, 문자와 숫자를 각각 1개 이상 포함, UTF-8 기준 72바이트 이하 |
| 진행 조건 | 이메일/닉네임 사용 가능 확인 및 비밀번호 규칙 충족 후 다음 단계 활성화 |

중복 확인은 보조 UX일 뿐이다. 마지막 가입 요청에서도 중복될 수 있으므로 `409 EMAIL_ALREADY_EXISTS`, `409 NICKNAME_ALREADY_EXISTS`를 반드시 처리한다.

### A03. 회원가입 - 필수 약관

| 항목 | 내용 |
|---|---|
| 데이터 | `GET /terms?current=true` |
| 표시 | `type`, `version`, 필수 여부(`required`), 약관 링크(`contentUri`) |
| 가입 조건 | `required=true`인 모든 문서를 체크해야 한다. 현재 시드에는 `SERVICE_TERMS`, `PRIVACY` 2건이 필수다. |
| 전송값 | 선택한 문서 ID 전체를 최종 가입 요청의 `consentDocumentIds` 배열에 넣는다. |

`contentUri`는 P0에서 약관 본문 API가 아니므로, 앱 내 WebView 또는 임시 안내 화면으로 연결 여부를 프론트팀이 결정한다. 시연 우선이면 유형·버전·동의 체크만 우선 구현한다.

### A04. 회원가입 - 프로필·활동 정보·운동 선호

하나의 스크롤 화면 또는 아래 세 단계의 스텝 화면으로 구현한다. 최종 단계에서 A02~A04 값을 합쳐 `POST /auth/signup`으로 한 번만 전송한다.

#### A04-1. 기본 프로필

| 입력 | 값/규칙 |
|---|---|
| 생년월일 | 선택, `YYYY-MM-DD` |
| 성별 | 선택: `FEMALE`, `MALE`, `NONE` |
| 프로필 공개 범위 | `PUBLIC`(기본), `PRIVATE` |
| 자기소개 | 선택, 최대 500자 |
| 몸무게 단위 | `KG`(기본), `LB` |
| 시간대 | `Asia/Seoul`(기본) |

#### A04-2. 활동 지역·시설·운동 가능 시간

| UI 요소 | 데이터/API | 규칙 |
|---|---|---|
| 시/도 선택 | `GET /regions` | 지역 ID와 이름을 보관한다. |
| 하위 지역 선택 | `GET /regions?parentId={시도ID}` | API가 반환하는 논리 UUID를 그대로 다음 요청에 사용한다. |
| 시설 검색·입력 | `GET /facilities?regionId={지역ID}&name={검색어}` | 목록 선택 또는 새 시설명을 텍스트로 입력한다. 최종 전송은 `facilityNames` 문자열 배열이다. |
| 활동 지역 | 선택, 복수 가능. 첫 항목이 주 활동 지역으로 저장된다. |
| 시설 | 선택. 시설을 하나라도 입력하면 활동 지역은 정확히 1개여야 한다. |
| 가능 시간 | 선택, 요일 `0=월요일` ~ `6=일요일`, 시작 < 종료 |

시설은 P0에서 주소·상세정보 입력이 없다. 검색 결과가 없으면 현재 선택된 단일 지역에 같은 이름의 시설이 생성될 수 있다는 문구를 표시한다.

#### A04-3. 운동 종목·목표 및 가입 완료

| UI 요소 | 데이터/API | 규칙 |
|---|---|---|
| 종목/숙련도 | `GET /sports` | `HEALTH`를 정확히 한 번 선택하고 숙련도를 고른다. 다른 활성 종목은 복수 선택 가능하다. 현재 시드에서 활성 종목은 `HEALTH`만이다. |
| 숙련도 | `BEGINNER`, `UNDER_6_MONTHS`, `SIX_MONTHS_TO_ONE_YEAR`, `ONE_TO_THREE_YEARS`, `OVER_THREE_YEARS` | 선택한 종목마다 하나 |
| 운동 목표 | `DIET`, `MUSCLE_GAIN`, `FITNESS`, `RECORD_IMPROVEMENT`, `HABIT` | 하나 이상 선택하고, 정확히 하나를 주 목표(`primary=true`)로 지정 |
| 가입 요청 | `POST /auth/signup` | `201 Created`이면 토큰을 저장하고 A05로 이동 |

가입 직전 체크리스트는 “필수 약관 2건, HEALTH 숙련도, 주 목표 1개”가 모두 충족됐는지 보여 준다. 서버의 `VALIDATION_ERROR`도 가입 화면을 유지하며 표시한다.

### A05. 내 프로필

| 항목 | 내용 |
|---|---|
| 진입/새로고침 | `GET /users/me` |
| 표시 | 표시 이름, 닉네임, 공개 범위, 자기소개, 단위, 활동 지역, 시설, 가능 시간, 종목/숙련도, 목표/주 목표, 약관 동의 상태 |
| 주요 동작 | 프로필 편집(A06), 알림 설정(A07), 약관 관리(A08), 비밀번호 변경(A09), 회원 탈퇴(A10) 이동 |
| 저장값 | 응답 `profile.version`은 프로필 수정 요청에, 알림 응답의 `version`은 설정 수정 요청에 사용한다. |

목록이 비어 있는 선택 항목은 “설정하지 않음”으로 표시한다. 계정 이메일은 P0에서 수정 API가 없으므로 읽기 전용이다.

### A06. 프로필 편집

| 항목 | 내용 |
|---|---|
| 초기 데이터 | A05의 `GET /users/me` 응답 |
| 기준 데이터 | 지역/시설/종목 선택 UI는 A04와 동일한 API를 사용한다. |
| 저장 | `PATCH /users/me/profile` |
| 필수 전송 | 현재 `profile.version` |
| 선택 전송 | 실제로 변경할 필드만 전송 가능하다. 선택 목록을 비우려면 빈 배열을 전송한다. |
| 성공 | 응답 프로필로 화면을 갱신하고, 필요하면 A05를 재조회한다. |

닉네임을 바꾸는 경우 A02의 중복 확인 UI를 재사용한다. 시설명이 있는 경우 활동 지역 1개 규칙을 저장 전에도 검사한다.

### A07. 알림 설정

| 항목 | 내용 |
|---|---|
| 조회 | `GET /users/me/notification-settings` |
| 토글 | 앱 내 알림(`inAppEnabled`), DM(`dmEnabled`), 메이트(`mateEnabled`), 커뮤니티(`communityEnabled`) |
| 저장 | `PATCH /users/me/notification-settings`에 4개 boolean과 현재 `version`을 모두 전송 |
| 성공 | 응답의 새 `version`을 저장 |
| 범위 | P0는 설정 저장만 한다. OS Push 권한 요청, device token 등록, 실제 알림 수신 UI는 만들지 않는다. |

추천 UX는 토글 변경 즉시 저장이 아니라 “저장” 버튼을 두고 네트워크 실패 시 기존 상태를 유지하는 방식이다.

### A08. 약관 동의 관리

| 항목 | 내용 |
|---|---|
| 조회 | `GET /users/me/consents` 또는 A05 응답의 `consents` |
| 선택 약관 동의 | `PUT /users/me/consents/{termsDocumentId}` |
| 선택 약관 철회 | `DELETE /users/me/consents/{termsDocumentId}` |
| 필수 약관 | `required=true` 문서는 철회 토글을 비활성화하고 “필수 약관” 배지를 표시한다. 서버도 `409 INVALID_STATE_TRANSITION`으로 차단한다. |

현재 시드에는 필수 약관 2건만 있으므로, 선택 약관이 추가되기 전에는 이 화면을 A05의 읽기 전용 동의 정보로 축소해도 된다.

### A09. 비밀번호 변경

| 항목 | 내용 |
|---|---|
| 입력 | 현재 비밀번호, 새 비밀번호, 새 비밀번호 확인 |
| 클라이언트 검증 | A02의 비밀번호 규칙과 동일, 새 비밀번호/확인 일치 |
| 호출 | `PATCH /users/me/password` |
| 성공 | `204 No Content`; 모든 세션이 무효화되므로 저장 토큰을 지우고 로그인 화면으로 이동 |
| 실패 | 현재 비밀번호 불일치는 `INVALID_CREDENTIALS`, 형식 오류는 `VALIDATION_ERROR` |

### A10. 회원 탈퇴

| 항목 | 내용 |
|---|---|
| 진입 | 내 프로필의 위험 영역. 별도 확인 화면 또는 바텀시트를 사용한다. |
| 안내 | 계정/프로필은 탈퇴 처리되고, 운동 기록과 동의 이력의 실제 삭제·익명화는 P0 범위 밖임을 고지한다. |
| 입력 | 현재 비밀번호 |
| 호출 | `DELETE /users/me` |
| 성공 | `204 No Content`; 토큰을 지우고 로그인 화면으로 이동 |
| 실패 | `INVALID_CREDENTIALS`는 비밀번호 입력 오류로 표시 |

## 5. 프론트 구현 체크리스트

- [ ] API base URL과 Bearer 인증 interceptor 설정
- [ ] 안전한 토큰 저장 및 refresh token 교체 저장
- [ ] 401 한 번 재시도 후 로그인 전환 처리
- [ ] 로그인·회원가입 중복 제출 방지와 로딩 상태
- [ ] 회원가입 필수 약관/HEALTH/주 목표 클라이언트 검증
- [ ] 시설 입력 시 활동 지역 1개 규칙 검증
- [ ] `profile.version`, `notificationSettings.version` 보관 및 충돌 처리
- [ ] 비밀번호 변경·탈퇴 성공 시 로컬 인증 상태 전체 제거
- [ ] Push 실제 발송 기능을 P0 화면 범위에 포함하지 않음

## 6. 확인이 필요한 프론트 결정

아래 항목은 API 구현을 막지 않지만 화면 완성도에 영향을 준다.

1. 회원가입을 4단계 스텝 화면으로 할지, 단일 스크롤 폼으로 할지
2. 약관 `contentUri`를 WebView로 열지, 시연용 텍스트 화면으로 대체할지
3. 시설 검색 결과가 없을 때 새 시설 생성 가능 안내와 확인 UI를 둘지
4. 알림 설정을 즉시 저장할지, 저장 버튼 방식으로 할지 (권장: 저장 버튼)
5. 탈퇴 데이터 보관 안내 문구의 최종 표현

