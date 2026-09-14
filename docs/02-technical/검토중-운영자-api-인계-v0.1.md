# [검토 중] 운영자 API 인계 v0.1 — 신고·제재 1차 범위

- 작성일 / 최종 수정일: 2026-09-14
- 버전: v0.1
- 상태: 검토 중. 백엔드와 합의 전인 요청/응답 제안이며 구현 완료 명세가 아니다.
- 대상: Android 프론트 / Spring Boot 백엔드 담당자
- 기준: main `83db7a9` (PR #54), 후속 브랜치 `codex/admin-integration-foundation`
- 제품 범위: [운영자 기능 설계](../01-spec/검토중-운영자-기능-설계-v0.1.md). 광고 제외.

## 1. 먼저 공유할 결론

백엔드 작업은 운영 권한·세션 기반 → 신고 목록/근거/검토 → 결정/제재/감사 → 사용자 처리 결과 순서로 나누어 시작할 수 있다. 아래 계약을 먼저 검토하고 모듈별로 합의한다. 모든 운영 도구 UI 완성까지 백엔드 착수를 기다릴 필요는 없다.

**현재 앱에서 되는 것은 Fake repository 기반 데모다. 운영자 API 실연동, 실제 계정 정지, 통지, MFA가 동작한다는 뜻이 아니다.** 이번 인계는 백엔드가 검토·착수할 수 있는 초안이며 최종 검수 완료본은 아니다.

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
