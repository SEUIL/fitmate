# [검토 중] 운영자 API 프론트엔드 연동 v0.5 — 권한·이의신청·운영 규칙

- 작성일: 2026-09-14 / 최종 수정일: 2026-09-16
- 버전: v0.5
- 상태: 검토 중. 1차 API와 Android HTTP 저장소 연결 구현. 운영 계정 역할 부여와 배포는 별도다.
- 대상: Android 프론트 / Spring Boot 백엔드 담당자
- 기준: main `7f59e66`(PR #65), PR #66의 공지·카테고리·OCR, 이후 로컬 핵심 기능 구현
- 제품 범위: [운영자 기능 설계](../01-spec/검토중-운영자-기능-설계-v0.1.md). 광고 제외.

## 1. 프론트 담당자에게 전달할 결론

Android 기본 경로는 `ServerAdminRepository`다. 앱의 로그인 세션을 공유하며 서버 실패를 Fake 데이터로 대체하지 않는다. Fake는 테스트에서 명시적으로 주입할 때만 사용한다.

설정의 개발 메뉴에서 **운영자 센터**로 진입한다. `/access`의 `eligible=false` 또는 401/403 응답이면 데이터와 조치를 차단한다. 최초 운영자 역할은 `ops_admin_roles`에 사전에 부여해야 한다. 자동 통합 테스트는 분리된 임시 DB와 임시 계정을 사용한다. 현재 사용자 수동 확인 환경도 기존 `fitmate` DB와 분리된 테스트 DB와 합성 계정을 사용한다. 계정 정보와 로컬 실행 설정은 저장소에 포함하지 않는다.

실제 MFA·운영 전용 일반 세션·Release 서버 주소·Push 결과 알림은 이번 범위에 포함되지 않는다. 사용자 이의신청 결과는 로그인 화면 및 설정의 전용 화면에서 조회한다.

현재 로컬 후속 구현은 MODERATOR의 기간 정지를 금지하고, 다른 운영자 제재는 SUPER_ADMIN만 허용한다. 공지·카테고리·OCR·운영 규칙은 ADMIN/SUPER_ADMIN, 운영자 권한 관리는 SUPER_ADMIN 전용이다. 이의신청은 MODERATOR도 조회할 수 있지만 검토 시작·결정은 ADMIN/SUPER_ADMIN만 가능하다.

### v0.5 운영자 권한·테스트 인증

| 메서드 / 경로 | 계약 |
|---|---|
| GET `/admin/operators` | 20건 cursor 목록, 전역 catalog version, actorId, 활성/회수 역할 |
| GET `/admin/operators/candidates/{userId}` | UUID로 회원 닉네임·계정 상태·현재 역할 확인. 이메일 검색/노출 없음 |
| POST `/admin/operators/reauth` | 6자리 code. 불투명 token, expiresAt, mode=DEMO_CODE |
| PUT `/admin/operators/{userId}` | expectedVersion, role, enabled, reason(1~300). Idempotency-Key 및 X-Admin-Reauth 필수 |

- `fitmate.admin.demo-auth-enabled` 기본 false, `fitmate.admin.demo-code` 기본 빈 값. local/test 프로필에서만 명시적으로 켤 수 있다. 운영 프로필이 섞이면 거부한다. 코드 값은 환경 설정으로만 공급하며 저장소에 기록하지 않는다.
- 인증 증명은 해시로 저장하고 현재 계정·일반 로그인 세션·authVersion에 묶으며 5분 후 만료한다. 잘못된 코드 5회는 해당 운영자 인증을 5분간 제한한다.
- 본인 역할 변경과 마지막 SUPER_ADMIN 회수를 차단한다. 전역 카탈로그 잠금으로 상호 동시 회수를 직렬화한다. 역할 변경은 대상 authVersion을 올리고 기존 일반 로그인 세션을 회수한다.
- 응답 유실 시 동일 키·본문으로 재확인한다. 재인증 만료 시 코드를 다시 확인한 뒤 같은 요청을 재시도한다.

### v0.5 사용자 이의신청·운영자 재검토

| 메서드 / 경로 | 계약 |
|---|---|
| POST `/auth/appeal-sessions` | email/password 검증 → 30분 전용 token. 정지 중에도 사용 가능 |
| GET `/appeal-access/sanctions` | 본인 제재, 사유·기간·해제 시각·신청 가능 여부·최근 신청 결과. cursor 20건 |
| GET `/appeal-access/appeals` | 본인 전체 신청·공개 처리 사유·결과. cursor 20건 |
| POST `/appeal-access/appeals` | sanctionId, statement(1~2000), Idempotency-Key |
| GET `/admin/appeals`, GET `/{id}` | 상태별 cursor 목록·상세. 원래 제재·신청 내용·내부 메모·canReview |
| POST `/admin/appeals/{id}/claim` | expectedVersion. RECEIVED→REVIEWING |
| POST `/admin/appeals/{id}/decision` | expectedVersion, status=ACCEPTED/REJECTED, publicReason(1~500), internalNote(선택 500), Idempotency-Key |

- `/appeal-access/**`는 `Authorization: Appeal <token>`만 허용한다. 일반 Bearer 세션과 저장·갱신을 공유하지 않으며 일반/운영자 API에 사용할 수 없다. 비밀번호 오류 5회 시 5분 제한, authVersion 변경·탈퇴·기간 만료 시 증명을 거부한다.
- 제재 한 건에 미처리 신청은 하나만 허용한다. 기각 뒤 재신청은 가능하며 모든 신청 결과는 신청 내역에 남는다. 본인 제재만 신청할 수 있고, 이미 해제된 제재는 접수할 수 없다.
- 본인 신청은 검토할 수 없다. ADMIN은 다른 활성 운영자의 신청을 처리할 수 없고 SUPER_ADMIN은 가능하다.
- 수용은 해당 제재만 revokedAt/by/reason으로 해제한다. 다른 유효한 정지가 있으면 SUSPENDED를 유지하고 마지막 유효 정지가 없어지면 ACTIVE로 복구한다. 제재·신고 이력은 삭제하지 않는다. 신고 상세에도 해제 시각·사유를 표시한다.
- 사용자 응답은 내부 메모를 제외한다. 상세 열람·검토 시작·결정은 감사 로그에 기록한다. 결과 Push는 미구현이다.

### v0.5 운영 규칙

| 메서드 / 경로 | 계약 |
|---|---|
| GET `/admin/rules` | 20건 cursor 규칙 목록 |
| PUT `/admin/rules/{id}` | 클라이언트 UUID, expectedVersion, name(1~80), kind, scope, term(최대 100), thresholdCount(2~100), windowSeconds(10~3600), enabled, reason(1~300), Idempotency-Key |
| POST `/admin/rules/preview` | rule 초안, sampleText(최대 5000), priorMatchingCount(0~100). 샘플 저장 없음 |
| GET `/admin/rules/{id}/history` | 버전별 설정 전체·변경 사유·시각, 20건 cursor |
| GET `/admin/rules/detections`, GET `/detections/{id}` | reviewed 필터·20건 cursor. 상세는 현재 공개 원문만 별도 조회·감사 |
| POST `/admin/rules/detections/{id}/review` | reason(1~300), Idempotency-Key. 검토 완료 표시만 수행 |

- WORD는 NFKC, 소문자, 연속 공백 정규화 뒤 포함 일치한다. 임의 정규식·코드를 실행하지 않는다.
- REPEAT는 정규화한 전체 내용의 같은 해시, RATE는 내용과 무관한 작성 횟수다. 같은 회원·영역(POST/COMMENT/MATE), `(현재-구간, 현재]`의 신규 공개 작성만 세고 현재 글을 포함한다. 수정은 횟수에 더하지 않으며 WORD 검사는 다시 한다.
- POST는 본문, COMMENT는 댓글 본문, MATE는 제목+본문을 검사한다. 비공개 커뮤니티 프로필·비공개 글/댓글·비공개 메이트와 DM은 탐지 대상에서 제외한다.
- 적용된 규칙이 있는 시점부터 이벤트를 기록하며 기존 글을 소급 수집하지 않는다. 이벤트는 내용 해시·대상 ID·시각만 보관한다. 한 시간 이전 이벤트는 해당 회원의 다음 쓰기에서 정리하므로 휴면 회원의 해시는 별도 보관 정책 수립 전까지 남을 수 있다.
- 탐지는 규칙 ID·버전·콘텐츠 ID당 한 건이다. 후보에 원문을 복제하지 않는다. 삭제/비공개 전환 시 현재 원문을 제공하지 않는다. 검토 완료는 글 숨김·경고·계정 정지를 실행하지 않는다.
- 규칙별 설정·버전·사유는 이력으로 저장한다. 앱의 새 규칙은 기본 중지 상태다. 후보 자동 제재와 대량 조치는 포함하지 않는다.

### v0.5 적용 경계

- V30: 권한 관리 카탈로그·인증 시도·재인증 증명. V31: 제재 해제 필드·이의신청 및 제한 인증. V32: 규칙·설정 이력·탐지 해시·후보. V33: 작성자별 탐지 직렬화 잠금.
- V28은 계속 예약 번호다. 이미 적용된 migration은 수정하지 않는다. V32 적용 후 동시 작성 시험에서 발견한 잠금 보완도 V33으로 분리했다.
- 신규 migration은 별도 자동 테스트 DB에서 검증한 뒤 사용자 승인으로 수동 테스트 DB를 백업하고 적용했다. 수동 환경은 V33, Flyway 32개 검증 완료 상태이며 백엔드·앱 업데이트와 실서버 실기기 4건을 통과했다. 원래 `fitmate` DB는 V29를 유지한다. 백업과 검증 구분은 [핵심 기능 검증](../03-qa/검토중-운영자-핵심기능-검증-260916.md)에 기록했다.
- [핵심 기능 검증 기록](../03-qa/검토중-운영자-핵심기능-검증-260916.md)을 참고한다. 아래 v0.4/v0.3 결과는 이전 범위의 기록이다.

### v0.4 공지·카테고리·시스템 API

모든 경로는 `/api/v1` 기준이며 로그인 인증이 필요하다. `/admin` 도구는 ADMIN/SUPER_ADMIN만 사용한다. 공지·순서 변경은 UUID `Idempotency-Key`를 필수로 받는다. 같은 계정·동작·본문·키는 저장된 응답을 반환하며 다른 요청에 키를 재사용하면 409다. 응답 유실 시 Android는 입력을 잠그고 같은 키로 결과를 확인한다. 역할은 재시도에서도 다시 검사한다.

| 메서드 / 경로 | 계약 |
|---|---|
| GET `/admin/notices`, GET `/notices` | `cursor`, `size`(기본 20, 최대 50). `items,nextCursor,hasNext`. 관리자 전체 / 사용자 PUBLISHED만 |
| GET `/admin/notices/{id}`, GET `/notices/{id}` | 제목·본문·상태·게시/종료 시각·version·targetType=ALL. 사용자에게 초안·종료는 404 |
| POST `/admin/notices` | title(1~100), body(1~5000), expectedVersion=0. 초안 생성 |
| PUT `/admin/notices/{id}` | title, body, expectedVersion. 초안 또는 게시 중 수정, 종료 후 수정 불가 |
| POST `/admin/notices/{id}/publish`, `/end` | expectedVersion. DRAFT→PUBLISHED→ENDED만 허용, 서버 시각 저장 |
| GET `/admin/categories` | version, 전체 items(id,code,name,active,displayOrder), otherSports(name,memberCount), otherSportsLimit=50 |
| PUT `/admin/categories/order` | expectedVersion, 전체 ids. 중복·누락 거부, 전체 순서 원자적 저장 |
| GET `/admin/system/summary` | UTC from/to 선택, 기본 최근 24시간, 최대 31일 길이, 끝 시각 미포함. statuses, failures, failureRate, asOf |

- 공지 목록은 본문을 제외한다. Android 설정의 공지사항에서 사용자 목록·상세를 연다. 공지 알림 진입도 이 목록으로 연결한다. 예약·고정·지역/종목 대상·Push는 미구현이다.
- 게시 중 공지는 수정 확인 후 즉시 반영한다. 게시/종료/편집은 잠금과 version으로 동시 수정 충돌을 거부한다. 사용자 화면은 상세 진입 때 재조회하며 이미 열린 본문을 실시간 회수하는 기능은 없다.
- 카테고리는 기존 UUID/code/name/isActive를 변경하지 않는다. 전체 카탈로그의 단일 version으로 정렬 충돌을 검출한다. 기타 운동은 기존 normalized_name별 등록 회원 수 상위 50개만 반환하며 개인 식별자는 포함하지 않는다.
- OCR은 `[from,to)`에 **접수된 요청의 현재 상태** 집계다. 종료 시각 기반 처리량이나 재시도 시도별 통계가 아니다. FAILED에 completedAt이 없는 기존 구조 때문에 종료 시각 기반 지표로 표시하지 않는다.
- 실패율은 FAILED/(COMPLETED+REVIEW_REQUIRED+FAILED), 분모 0이면 null. API 오류율·응답 시간·활성 사용자는 UNAVAILABLE/null이다. 판독 본문·건강 수치·이미지·회원 ID·오류 원문은 조회하지 않는다.
- 도구 감사 로그에는 조작 코드와 대상 ID를 남기며 공지 본문·기타 운동 입력 내용은 복제하지 않는다.

### v0.4 migration·검증 상태

- 집 환경 DB는 확인 당시 V24였다. 백업 후 사용자 승인으로 기존 V25~V27을 적용했으며 Flyway 27개 검증이 통과했다.
- 노트북 Handoff의 V28 파일은 확인 가능한 Git 이력에서 발견되지 않았다. 충돌 예방을 위해 번호를 예약하고 신규 파일은 `V29__create_admin_notices_and_category_settings.sql`로 준비했다. V28의 출처가 밝혀지면 별도로 대조한다.
- V29는 사용자 승인과 별도 DB 통합 검증 후 기존 DB를 다시 백업하고 적용했다. 적용 후 Flyway 28개 검증이 통과했다(V28 번호는 미사용). 기존 DB의 공지·제재·운영자 역할은 0건이며 카테고리 version은 0이다. 테스트 데이터는 기존 DB에 쓰지 않았다.
- 백엔드 22건 통과: 권한·OCR 단위 13건 + 신규 도구/신고 페이지 HTTP/MySQL 8건 + 기존 신고·중첩 정지 HTTP/MySQL 1건. Android 115건 전부 통과(실서버 저장소 왕복 포함, skipped 0). Debug 앱·기기 테스트 APK 빌드 통과.
- Samsung SM-S947N / Android 16에서 누적 고유 테스트 17건 통과. 실제 로그인→설정→센터, 일반 계정 차단, 공지 작성·게시·수정·사용자 조회·종료, 카테고리 저장, OCR 라이트/다크, 경고·반려·내부 메모, 기간 정지·기존 세션 차단·중첩 만료·로그인 복구를 확인했다. 추가로 20건 초과 조회·원문 없는 신고·저장 후 응답 TCP 유실 복구·로그인 테마 비교·회전/프로세스 재시작 진입을 확인했다. 실행 구분과 제한은 검증 기록을 따른다.
- 만료는 임시 DB의 가상 회원 제재 시각을 조정해 모의했다. 실제 30일을 기다린 검증은 아니다. 임시 서버·DB·USB 테스트 포트는 검증 후 정리했다. 기존 설치 앱은 보존하고 별도 테스트 패키지를 사용했다.
- [검증 상세 및 남은 항목](../03-qa/운영자-후속-검증-260915.md). 아래 v0.3 통과 기록은 노트북에서 수행한 과거 결과다.

### 구현된 API

모든 경로는 `Authorization: Bearer <일반 로그인 access token>`이 필요하다. 서버는 토큰 사용자가 활성 운영자 역할(`MODERATOR`, `ADMIN`, `SUPER_ADMIN`)인지 다시 검사한다. 역할이 없으면 `403 FORBIDDEN_RESOURCE`다.

| 메서드 / 경로 | 화면 연결 지점 | 구현 상태 |
|---|---|---|
| GET `/api/v1/admin/access` | 운영자 진입 전 자격 확인 | 구현 |
| GET `/api/v1/admin/dashboard` | 현황 탭 | 구현 |
| GET `/api/v1/admin/reports` | 신고 목록·검색·상태 필터 | 구현 |
| GET `/api/v1/admin/reports/{reportId}` | 신고 상세·근거·처리 이력 | 구현 |
| GET `/api/v1/admin/members?query=` | 회원 탭의 신고 연결 회원 목록 | 구현 |
| GET `/api/v1/admin/contents` | 콘텐츠 탭의 신고된 게시물·댓글·메이트글 목록 | 구현 |
| POST `/api/v1/admin/reports/{reportId}/claim` | 검토 시작 | 구현 |
| POST `/api/v1/admin/reports/{reportId}/decisions` | 반려·경고·기간 정지 저장 | 구현 |

`GET /reports`의 `status`, `query`, `cursor`, `size`는 선택값이며 `size`는 1~100이다. `query`는 신고 번호, 신고 사유, 신고 대상 제목, 대상 회원 닉네임을 검색한다. 응답은 `data.items`, `data.nextCursor`, `data.hasNext`다. 다음 페이지에는 응답의 `nextCursor`를 그대로 전송한다.

### 화면별 호출 순서

1. 관리자 화면 진입 전 `/access`를 호출한다. `eligible=false`면 운영자 화면을 열지 않는다.
2. 현황 탭은 `/dashboard`, 신고 탭은 `/reports`를 각각 호출한다. 서로의 결과로 수치를 계산하지 않는다.
3. 신고 행 선택 시 `/reports/{reportId}`를 다시 호출한다. 상세 응답의 `version`과 `allowedActions`를 기준으로 버튼을 활성화한다.
4. 검토 시작과 최종 처리는 상세의 최신 `version`을 요청 본문 `expectedVersion`으로 보낸다. `409 VERSION_CONFLICT` 또는 `INVALID_STATE_TRANSITION`이면 자동 재시도하지 말고 상세를 다시 조회한다.
5. 최종 처리 요청에는 확인한 입력·신고·버전에 대응하는 UUID `Idempotency-Key`를 넣는다. 결과가 불명확하면 조치를 차단하고 최신 상세를 재조회한다. 같은 키·본문의 재전송은 서버가 중복 실행하지 않는다.

### 최종 처리 요청 예시

```json
{
  "expectedVersion": 1,
  "resolution": "RESOLVED",
  "action": "SUSPEND",
  "reasonCode": "HARASSMENT",
  "publicReason": "운영 정책 위반으로 7일간 이용이 제한됩니다.",
  "internalNote": "신고 근거 확인 완료",
  "durationDays": 7
}
```

- 반려: `resolution=DISMISSED`, `action=NONE`, `reasonCode=NO_VIOLATION` 또는 `INSUFFICIENT_EVIDENCE`, `durationDays=null`
- 경고: `resolution=RESOLVED`, `action=WARNING`, `durationDays=null`
- 기간 정지: `resolution=RESOLVED`, `action=SUSPEND`, `durationDays`는 `1`, `3`, `7`, `30` 중 하나
- `publicReason`은 1~500자 필수이며 `internalNote`는 500자 이하 선택값이다.

기간 정지는 대상 회원이 있는 경우에만 가능하다. 서버는 USER 및 게시물·댓글·DM·메이트글의 작성자를 조회해 대상을 연결한다. 삭제된 대상은 연결되지 않을 수 있다. 본인 또는 대상 회원이 없는 신고에는 `NONE`만 허용한다. 콘텐츠 숨김·복구는 미구현이다.

### v0.3 실제 응답과 표시

- 현황 수치는 `/dashboard`의 서버 집계를 사용한다. 신고 목록의 현재 페이지 크기로 대기 신고 수를 계산하지 않는다.
- 신고는 20건씩 서버 검색·필터링하고 `nextCursor`로 더 불러온다. 신고 선택 시 상세를 재조회한다.
- 상세의 `evidence.text`는 게시물·댓글의 현재 원문이며 `reporterStatement`는 신고자 설명이다. 삭제/미제공 시 `availability=UNAVAILABLE`이며 원문처럼 대체 표시하지 않는다. DM·회원·메이트 원문/이미지 근거는 이번 구현에서 미제공이다.
- 상세 열람은 `REPORT_VIEWED` 감사 이력으로 기록한다.
- `decision`에는 `action`, `reasonCode`, `publicReason`, `internalNote`, `decidedAt`, `sanctionEndsAt`가 포함된다. 미처리이면 null이다. 내부 메모는 운영자 응답에서만 사용한다.
- `allowedActions`는 상세의 최상위 필드이며 화면은 이 목록으로 조치 선택을 제한한다. 서버의 본인 제재 차단은 별도로 유지한다.
- 정지 복구는 로그인/인증 시 모든 정지 기록의 만료 여부를 확인한다. 이전의 긴 정지가 남아 있으면 최근의 짧은 정지가 끝나도 복구하지 않는다. 기록이 없는 수동 정지는 자동 복구하지 않는다.

### 현재 검증

- 백엔드 운영자 테스트 7건 통과: Mock 회귀 6건 + 별도 MySQL DB/실제 HTTP 통합 1건.
- Android HTTP 매핑 테스트 3건과 실제 백엔드 왕복 테스트 1건 통과. 실제 왕복은 앱의 `FitMateApiClient`와 `ServerAdminRepository`를 JVM에서 실행했다.
- 실제 왕복 범위: 자격 없는 회원 차단, 신고 생성·검색·상세, 검토 시작, 경고 저장, 동일 키 재전송, 처리 사유/내부 메모 재조회.
- Android 단위 테스트 108건 통과(실제 서버 왕복 1건 포함, skipped 0). Debug 앱·기기 테스트 APK 빌드 통과.
- Samsung SM-S947N / Android 16에서 UI 테스트 5건 통과: Fake 주입 처리·로딩·오류·충돌 4건, 실제 서버 경고 저장·DB 재조회 1건. 실서버 기기 테스트는 `ServerAdminDeviceTest`이며 격리된 테스트 패키지와 임시 운영자를 사용했다.
- 실기기 검증 과정에서 검색 후 상세 진입 시 남은 검색 요청을 취소하고 키보드를 숨기도록 보완했다. 서명이 다른 기존 테스트 앱은 삭제하지 않고 임시 패키지로 분리했다.
- `git diff --check` 통과. 테스트용 서버·DB·패키지는 검증 후 정리한다.
- 운영 서버 배포, MFA, 휴대폰에서 기간 정지·만료까지의 전체 흐름은 미검증이다. 기간 정지·중첩 만료는 백엔드 실제 HTTP/DB 통합 테스트에서 검증했다. 기존 개발 DB의 지정 계정 역할 부여는 수동 설정이며 권한 관리 기능 구현을 의미하지 않는다.

## 이전 인계 제안과 후속 범위

아래 내용은 v0.1~v0.2의 설계·검증 이력이다. 현재 구현의 wire 계약은 위 v0.3 설명과 `AdminDtos.kt`를 기준으로 한다. 아래 MFA/독립 evidence API/감사 목록 API/targetVersion/통지 제안은 현재 구현 완료를 의미하지 않는다.

| 영역 | 현재 프론트 | 백엔드 확인/할 일 |
|---|---|---|
| 현황 / 신고 / 상세 / 제재 확인 | PR #54에 기본 화면 병합 | 운영자 전용 API 신규 구현 |
| 비동기 상태 / 조치 입력 구조 | 후속 브랜치: 로딩·재조회·충돌·중복 요청 방지, enum·사유 분리 | 아래 오류·version·멱등 계약 검토 |
| 일반 신고 접수 | 기존 사용자 API 존재 | `POST /api/v1/reports`와 관리 API 구분 |
| 회원 / 콘텐츠 | 신고와 연결된 가상 목록만 있음 | 상세·제재 해제·숨김/복구 필요 |
| 운영 인증 / MFA | 개발 메뉴와 DEBUG 차단만 있음 | 서버 역할 검사·추가 인증·세션 회수 필요 |
| 사용자 처리 결과 / 이의신청 | 운영 결과와 종단 간 연결 안 됨 | 본인 신고 결과·제재 안내·제한 인증 계약 필요 |
| 기타 도구 | 준비 중 표시 | 공지·카테고리·규칙·감사·시스템은 별도 단계 |

## 2. 기존 서버와 유지할 부분

- 현재 `ReportController`는 일반 사용자 신고 **접수**만 제공한다. 관리 조회/결정에 이 API를 다른 회원 ID로 우회 재사용하지 않는다.
- 성공: `{"data": ...}`. 페이지: `data.items`, `data.nextCursor`, `data.hasNext`.
- 오류: `error.code`, `error.message`, `error.fields[]`의 `field`/`reason`.
- 기존 신고 상태: `RECEIVED`, `REVIEWING`, `RESOLVED`, `DISMISSED`. 반려를 `REJECTED`로 전송하지 않는다.
- 기존 대상: `USER`, `POST`, `COMMENT`, `DM_MESSAGE`. `MATE_POST`는 서버 enum/DB CHECK/사용자 신고 계약을 함께 확장해야 한다.
- 기존 신고자 사유: `SPAM`, `HARASSMENT`, `HATE`, `SEXUAL`, `VIOLENCE`, `SELF_HARM`, `OTHER`.
- **신고자가 선택한 사유와 운영자가 결정한 사유는 다른 필드다.** 기존 `ReportReason`을 관리자 결정용으로 덮어쓰지 않는다.
- 기존 Flyway 파일은 변경하지 않는다. 개발 시점의 최신 버전 뒤에 새 migration을 만든다.

## 3. 공통 전달 규칙 (제안)

- 기본 경로: `/api/v1/admin`. 일반 사용자 토큰으로 모든 관리 API가 열리는 구조는 금지한다.
- ID: UUID 문자열. `R-1001` 등 현재 데모 번호는 UI fixture 식별자이며 서버 ID가 아니다. 사용자에게 보여줄 번호는 별도 `displayId`로 제공한다.
- 시각: UTC ISO-8601 문자열. Android에서 기기 시간대로 변환한다. “오늘 14:20”은 서버 응답이 아니다.
- `version`: 0 이상 Long. 조치 시 클라이언트의 `expectedVersion`과 비교한다.
- 상태/조치/사유는 영문 enum으로 전달하고 한국어는 화면 label로만 사용한다.
- optional은 아래 표의 nullable 기준을 따른다. 빈 문자열·0으로 미집계/미제공을 대신하지 않는다.
- 읽기: cursor 페이지(기본 20, 최대 100 제안), 신규순 정렬은 `createdAt DESC, id DESC`로 안정화한다.
- 변경: `Idempotency-Key` UUID header + version. 같은 키/같은 요청은 동일 조치를 재실행하지 않는다. 같은 키/다른 요청은 409.
- 멱등 키 저장기간·운영 세션 TTL은 백엔드와 합의 필요. 프론트가 성공 응답 유실 후 새 요청을 자동 생성하지 않는다.
- 실제 응답과 Fake를 자동 전환하지 않는다. Remote 실패 시 오류를 표시하고 Fake 통계나 계정을 끼워 넣지 않는다.

## 4. 1차 필요한 API

아래 항목은 전부 **제안**이다. API 존재 여부·스키마 합의·화면 시연·실연동을 별도로 관리한다.

| 메서드 / 경로 | 입력 | 반환 / 용도 |
|---|---|---|
| GET `/access` | 일반 인증 | `eligible:Boolean`, `role:String?`, `mfaEnrolled:Boolean`. 자기 자격만 조회 |
| POST `/mfa/challenges` | 일반 인증 + 신원 재확인 | challenge와 만료 시각. 상세 보안 계약 별도 합의 |
| POST `/mfa/verifications` | challenge + TOTP | 제한 수명의 운영 세션. 비밀값 로깅 금지 |
| GET `/dashboard?from=&to=` | 운영 인증, UTC 범위 | 아래 Dashboard DTO |
| GET `/reports?query=&status=&cursor=&size=` | 번호/닉네임/제목 검색, 상태 enum optional | ReportSummary 페이지 |
| GET `/reports/{reportId}` | 운영 인증 + 업무 권한 | ReportDetail, 허용 조치와 최신 version |
| GET `/reports/{reportId}/evidence` | 해당 신고 근거 열람 자격 | 제한된 근거 및 availability. 열람 감사 필수 |
| POST `/reports/{reportId}/claim` | `expectedVersion:Long` + 멱등 header | 갱신된 ReportDetail. 접수→검토 중 |
| POST `/reports/{reportId}/decisions` | DecisionRequest + 멱등 header | report, actionId, affectedResources |
| GET `/audit-logs?targetId=&cursor=&size=` | 운영 권한 범위 필터 | 비민감 처리 이력 페이지. 수정/삭제 기능 없음 |

MFA 발급 경로는 아직 운영 세션이 없으므로 일반 인증+운영자 자격으로 제한하고, 다른 관리 경로는 운영 세션을 검사한다. DEBUG 메뉴는 권한 검사로 취급하지 않는다.

## 5. 응답 DTO

### Dashboard

| 필드 | 타입 / nullable | 의미 |
|---|---|---|
| generatedAt | ISO instant / N | 집계 생성 시각 |
| from, to | ISO instant / N | 집계 범위 |
| pendingReports | Metric / N | RECEIVED + REVIEWING |
| newUsers | Metric / N | 기간 내 신규 가입 |
| completedWorkouts | Metric / N | 기간 내 완료된 개인 운동 세션 수. 모임 인원과 구분 |
| failedAnalyses | Metric / N | 기간 내 실패한 판독 작업 수 |

Metric: `value:Long?`, `availability:AVAILABLE|UNAVAILABLE`. AVAILABLE은 0 이상 값, UNAVAILABLE은 null. Android 표시는 `—` 또는 집계 불가이며 0으로 바꾸지 않는다. 현재 후속 코드의 snapshot은 5개 fixture 전체 집합이라 `pendingCount`를 로컬 계산한다. **실서버에서는 페이지 항목 수로 전체 대기 건수를 계산하지 않는다.**

### ReportSummary / ReportDetail

| 필드 | 타입 / nullable | 의미 |
|---|---|---|
| id | UUID / N | 실제 신고 식별자 |
| displayId | String / N | 사용자에게 보여줄 접수 번호 |
| targetType | USER/POST/COMMENT/DM_MESSAGE/MATE_POST / N | MATE_POST는 확장 제안 |
| targetId | UUID / N | 콘텐츠 또는 회원 ID |
| subjectUser | `{id:UUID,nickname:String}` / Y | 제재 대상 회원. 없으면 회원 조치 금지 |
| title | String / N | 비민감 요약. DM 원문을 제목에 복제하지 않음 |
| reportReasonCode | 기존 ReportReason / N | 신고자 사유 |
| status | 신고 상태 enum / N | 위 4개 상태 |
| createdAt | ISO instant / N | 접수 시각 |
| version | Long / N | 신고 낙관적 잠금 버전 |
| assignedOperatorId | UUID / Y | 미배정 시 null |
| targetVersion | Long / Y | 조치 대상 버전. 제재/콘텐츠 조치 시 필요 |
| allowedActions | String[] / N | 해당 운영자·상태에 허용된 조치. 빈 목록은 조치 불가 |
| decision | DecisionSummary / Y | 미처리이면 null |

Summary에는 담당자/targetVersion/allowedActions/decision을 생략할 수 있고 Detail에는 제공한다. 행동은 상세 재조회 후 결정한다.

DecisionSummary: `actionId:UUID`, `resolution`, `action`, `reasonCode`, `publicReason`, `decidedAt`, `operatorDisplayName`. 내부 메모는 허용된 운영 상세/감사 응답에만 제공하며 사용자 응답에는 넣지 않는다.

근거 응답: `availability:AVAILABLE|UNAVAILABLE|EXPIRED`, `items[]`는 type(TEXT/IMAGE), 최소 본문 또는 접근 검사 미디어 URL, 시각. DM은 신고 관련 범위만 제공한다. 이미지 만료/근거 없음 상태를 별도로 표시해야 하며 이 뷰어는 아직 미구현이다.

## 6. 결정 요청 — 프론트가 분리한 필드

| 필드 | 타입 / nullable | 제약 |
|---|---|---|
| expectedVersion | Long / N | 최신 신고 version |
| targetVersion | Long / 조건부 | NONE은 null 가능. WARNING/SUSPEND는 상세의 대상 버전으로 검증 권장; 현재 Fake에는 미구현 |
| resolution | RESOLVED / DISMISSED / N | 화면 조치에서 결정 |
| action | NONE / WARNING / SUSPEND / N | 지금 화면에서 구현한 조치. 향후 HIDE 등 확장 |
| reasonCode | 아래 결정 사유 enum / N | 신고자 사유와 별도 |
| publicReason | String / N | trim 후 1~500자, 사용자에게 보여줄 안내 |
| internalNote | String / Y | 최대 500자, 미입력은 null |
| durationDays | Int / 조건부 | SUSPEND만 1/3/7/30; 나머지는 null |

- 반려: `resolution=DISMISSED`, `action=NONE`, 사유 `NO_VIOLATION` 또는 `INSUFFICIENT_EVIDENCE`.
- 경고/기간 정지: `resolution=RESOLVED`, `action=WARNING|SUSPEND`, 사유 `POLICY_VIOLATION|SPAM|HARASSMENT`.
- 위 결정 사유 코드는 **제안**이며 아직 백엔드 enum이 아니다. 최종 사유 카탈로그는 합의 후 확장한다.
- 1/3/7/30일은 시안의 프리셋이다. 실제 정책 확정·서버 검증·만료 기준은 백엔드와 확인한다.
- 기간 정지의 실제 시작/종료는 서버가 산정하고 반환한다. 기기 시계로 계정 상태를 복구하지 않는다.

예시: 경고 처리 요청(JSON의 ID는 예시 값).

```json
{
  "expectedVersion": 1,
  "targetVersion": 3,
  "resolution": "RESOLVED",
  "action": "WARNING",
  "reasonCode": "SPAM",
  "publicReason": "반복 홍보 게시로 경고가 부여되었습니다.",
  "durationDays": null,
  "internalNote": "신고 근거와 운영 기준 대조 완료"
}
```

응답 골격:

```json
{
  "data": {
    "report": {
      "id": "00000000-0000-4000-8000-000000000001",
      "status": "RESOLVED",
      "version": 2,
      "allowedActions": []
    },
    "actionId": "00000000-0000-4000-8000-000000000002",
    "affectedResources": [
      {
        "type": "USER",
        "id": "00000000-0000-4000-8000-000000000003",
        "accountStatus": "ACTIVE",
        "sanctionKind": "WARNING",
        "sanctionEndsAt": null,
        "version": 4
      }
    ]
  }
}
```

위 report는 결과 핵심 필드만 보여준 골격이다. 실제 응답은 ReportDetail 전체를 반환해 화면을 갱신한다. 신고 상태·제재·감사·통지 이벤트는 일관되게 기록한다. 경고인데 계정 정지가 되었다는 안내를 만들지 않는다.

## 7. 오류·재시도

| 응답 / 코드 제안 | 화면 동작 | 현재 상태 |
|---|---|---|
| 조회 진행 | 로딩, 조회 전 실제 수치 숨김 | 구현 |
| 빈 결과 | 정상 0건 + 검색 초기화 | 구현 |
| 조회 네트워크 실패 | 오류 + 다시 조회, Fake 대체 금지 | 구현, 오류 주입 테스트 |
| 400 VALIDATION_ERROR | fields별 입력 안내 | 공통 오류만 구현; field 매핑 필요 |
| 401 ADMIN_SESSION_EXPIRED | 민감 snapshot 제거 후 재인증 | 제거·종료 안내 구현; 인증 화면 미구현 |
| 403 ADMIN_PERMISSION_REQUIRED | snapshot 제거, 작업 차단 | 상태 처리 구현; 실제 권한/MFA는 미구현 |
| 404 RESOURCE_NOT_FOUND | 대상 없음·목록 재조회 | 오류 모델 구현, Remote 매핑 필요 |
| 409 VERSION_CONFLICT / ALREADY_PROCESSED | 추가 조치 차단, 최신 상태 확인 | 구현, 오류 주입 테스트 |
| 409 IDEMPOTENCY_CONFLICT | 재제출 금지, 결과 확인 | Fake 저장소·상태 처리 구현 |
| 429 RATE_LIMITED | 재시도 시각 안내 | 미구현 |
| timeout / 500 | 성공으로 표시하지 않음, 결과 조회부터 수행 | 구현, 실제 HTTP 매핑 필요 |

현재 `AdminError`는 HTTP를 읽는 adapter가 아니라 UI 도메인 오류 모델이다. 서버 코드→도메인 오류 매핑은 Remote repository 구현 시 추가한다.

같은 확인 건의 재구성에는 동일한 멱등 키를 사용하고 입력/대상이 바뀌면 새 키를 생성한다. 요청 중 입력·확인·뒤로가기·탭 전환을 막는다. 오류 후 자동 변경 재시도는 하지 않는다. 앱 프로세스 종료를 넘는 pending request 보관/재조회는 아직 없으므로 실제 제재 연동 전 보강한다.

## 8. Android 구현과 서버 계약의 경계

- `AdminContracts.kt`: 액션/결정 사유 enum, 요청 모델, 검증, UI 오류 모델.
- `AdminUiState.kt`: suspend 저장소 호출, busy/error/snapshot 상태, 재조회 요구, 인증 실패 시 데이터 제거.
- `AdminDemoRepository.kt`: 명시적으로 선택하는 Fake, 가상 지연, 신고 version/중복 키 검증. 운영 서버의 보안 구현이 아니다.
- `AdminCenter.kt`: 화면/입력/확인. 사유 코드·사용자 안내·내부 메모 분리.
- `AdminSnapshot`은 **로컬 화면 모델**이다. `fetch()` 한 번에 실제 관리 데이터 전체를 내려받으라는 API 설계가 아니다. Remote 도입 시 dashboard / paged list / detail / evidence 상태를 분리한다.
- 현재 상태는 Composable 수명에 묶여 있다. 회전·프로세스 복원용 ViewModel/SavedState, 역할/세션, 페이지 조회, targetVersion/allowedActions 반영은 실제 제재 활성화 전 필요하다.
- 데모 ID/한국어 표시형 type/time은 wire DTO로 쓰지 않는다. Remote adapter에서 UUID/enum/Instant를 표시 모델로 변환한다.
- 이번 작업은 의존성·Gradle·서버·DB 설정을 바꾸지 않는다.

## 9. 백엔드 전달 체크리스트

- [x] 기존 API envelope와 반려 상태 DISMISSED 확인
- [x] 프론트가 입력하는 조치·사유·기간·안내·메모 분리
- [x] 운영자 API 제안과 현재 서버 구현을 구분
- [ ] 역할·MFA/세션 계약 합의
- [ ] 결정 사유 코드·정지 프리셋·targetVersion 정책 합의
- [ ] MATE_POST 확장 범위와 근거 접근/보유 정책 합의
- [ ] 페이지/집계/상세/allowedActions DTO 최종 합의
- [ ] 실제 HTTP adapter·권한·동시 조치·응답 유실 테스트
- [ ] 사용자 앱에서 결과/제재/이의신청까지 실제 DB 왕복 확인

## 10. 검증 기록

- PR #54: 신규 단위 3건, 실기기 UI 1건 통과. 서버 미연동.
- 후속 브랜치: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` 성공. `AdminIntegrationStateTest` 6건 + `AdminDemoRepositoryTest` 3건, 총 9건 통과.
- Samsung SM-S947N의 `AdminCenterDeviceTest` 4건 통과: 제재 흐름, 로딩 중 통계 미노출, 조회 실패→재시도, 충돌→최신 조회. 오류/충돌은 Fake를 감싼 테스트 저장소로 주입했으며 실제 HTTP 오류 검증이 아니다.
- 추가 실기기 테스트 최초 실행은 휴대폰 잠금 상태로 4건 중 3건 실패. 사용자 잠금 해제 후 동일 테스트 4건 모두 성공.
- 실서버 왕복, 설정부터 전체 진입, 회전·프로세스 복원, 다크모드, API 접근 권한 검증은 미실행. 실제 운영 적용은 불가하다.
- 기존 개인평가지, Gradle wrapper, 프로필 카운트 변경은 이 작업 범위가 아니다.
