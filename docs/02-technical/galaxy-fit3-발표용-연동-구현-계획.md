# Galaxy Fit3 · FitMate 발표용 연동 구현 계획

- 작성일: 2026-09-09
- 최종 수정일: 2026-09-12
- 버전: v1.3
- 상태: 확정
- 목적: 대학 프로젝트 발표 시연
- 최신 원격 기준: `origin/main`, `5172f84` (2026-09-12 pull 후)
- 구현 착수 기준: `10f9c11` (2026-09-10 당시 원격 기준)
- 구현 완료 기준: `3a2df1b` (기존 `5f5e8b7`을 최신 `origin/main`에 rebase)
- 보완 착수 기준: `fd8be7e` (기존 `159f343`을 최신 `origin/main`에 rebase)
- 발표 보완 구현 기준: `58fe7c0` (`codex/galaxy-fit3-match-guidance`, 2026-09-12)
- 이번 보완 계획의 최초 판단 근거: v1.0 계획서와 [구현 검증 기록 v0.5](../03-qa/galaxy-fit3-연동-구현-검증.md). v1.1 계획 수립 때는 문서 결과만 사용했고, v1.2 구현과 v1.3 rebase 후 실행 결과는 같은 검증 문서에 별도로 기록
- 범위 결정: [결정·회의 로그](../00-project/결정-회의-로그.md#2026-09-09-galaxy-fit3-단일-테이블-승인과-발표-후-수동-정리)

v0.4는 사용자가 승인한 단일 테이블 구조와 발표 후 개발 DB 수동 정리 방식을 반영한다. 72시간 조회 제한, 1분 주기 삭제, 서버 재시작 시 만료 정리는 구현 범위에서 제외한다. 이번 요청은 계획서와 결정·회의 로그 수정이며, 코드 구현은 후속 요청에서 시작한다.

v0.5는 현재 코드의 V19 메이트 확장을 반영해 외부 요약 migration을 V20으로 제안하고, 단일 헬스 세션 시연과 저장·재시도 계약을 구체화한다. 사용자 요청에 따라 10절의 WBS·단계별 구현 순서와 관련 작업 ID·일정은 수정하지 않는다. 해당 표에 남아 있는 V19 표기는 기술 구현 번호로 사용하지 않으며, migration과 이행 검증은 최신화한 8절·11절을 기준으로 한다. 이번 검토에서는 테스트·빌드·앱·서버·DB를 실행하지 않는다.

v0.8은 후속 구현과 실기기 검증 완료 상태를 기록한다. Android 후보 확인·저장·재조회 흐름, 외부 요약 API 2개, 단일 V20 테이블과 계약 테스트를 구현했고 Android 전체 빌드·63개 단위 테스트, 백엔드 단위 검증, MySQL 8.4 기반 API·V19/V20 이행 검증을 통과했다. 발표용 Galaxy S24+와 Fit3에서 벤치프레스가 `BENCH_PRESS`로 반환되는 것을 확인해 첫 헬스 허용 값으로 확정했으며, 후보 확인·저장·앱 재실행 후 재조회·동일 PUT 재시도·Samsung Health 미사용 기본 리포트까지 실제 시연했다.

v0.9는 구현 완료 커밋을 기준으로 문서 상태를 확정하고, v0.6의 구현 전 설명과 v0.8 이후 확인 결과를 구분했다. 또한 실기기에서 나타난 미확인 코드 경고의 확인 사실과 추정을 분리해 기록하고, 앱 안내가 제외된 운동 코드와 복합 운동 여부를 구분해 보여주도록 보완했다.

v1.0은 미확인 운동 코드가 반복될 수 있는 구조적 이유와 `exerciseType`·`customTitle`·FitMate 운동 이름을 함께 사용하는 추천 매핑 방식을 추가했다. 이름 비교는 자동 연결 조건이 아니라 후보 추천 근거로만 사용하고 최종 연결 전 사용자 확인을 유지한다.

v1.1은 문서 기준으로 남은 발표용 보완 계획을 14절에 정리한다. 미확인 운동의 이름 일치는 참고 정보로만 사용하고 저장 가능한 후보와 구분한다. 이미 구현한 API·V20 테이블을 다시 추가하도록 되어 있던 후속 요청을 수정한다. 다른 담당자의 `InbodyApiIntegrationTest` 설정 실패는 이번 작업 및 완료 기준에서 제외한다. 이번 요청에서는 계획서와 결정·회의 로그만 수정하며 테스트·빌드·앱·서버·DB는 실행하지 않는다.

v1.2는 14절의 헬스 필수 보완을 구현한 결과다. 현재 완료 세션의 운동 이름을 리포트에 표시하고 `customTitle`과 이름이 같은 미지원 운동을 참고 기록으로만 분리했다. 권한·기록 없음·시간 불일치·미지원 코드·복합 운동·활동 시간 누락 안내에 원인과 다음 행동을 추가했다. 당시 Android 전체 단위 테스트 66개와 debug 빌드는 통과했으며, 이 전체 명령에 포함된 기존 회귀 테스트 외에 러닝 전용 변경·추가 테스트·실기기 시연은 수행하지 않았다. 당시에는 Backend·DB·인바디와 실기기 재시연도 실행하지 않았다.

v1.3은 최신 `origin/main`을 pull한 뒤 기존 구현 커밋을 rebase한 상태를 기록한다. 원격 UI 개편과 충돌한 화면 3개는 최신 UI 구성요소를 유지하면서 Samsung Health 상태·조회·권한 요청 전달을 보존하도록 병합했다. 병합 후 Android 단위 테스트 68개와 debug 빌드가 통과했고, PR 전 회귀 확인으로 Backend 테스트도 실행해 99개 중 52개 통과·47개 건너뜀·실패 0개를 확인했다. DB 환경변수를 사용한 MySQL 통합 검증, 인바디 전용 검증과 실기기 재시연은 수행하지 않았다.

## 1. 목표와 첫 완료 기준

Galaxy Fit3에서 기록한 운동을 Samsung Health를 통해 FitMate로 가져와, 사용자가 선택한 운동의 리포트에 실제 측정 요약을 표시한다. 일반 서비스 출시 수준의 운영 기능보다 발표에서 설명하고 재현할 수 있는 흐름을 우선한다.

첫 완료 목표는 **운동 한 종류로 실제 데이터 연동 시연을 끝까지 성공하는 것**이다. 우선 기존 헬스 화면에 연결하고, 러닝까지 확장하는 것은 후순위로 둔다.

구현·시연을 쉽게 하기 위한 추천은 **서버 헬스 세션 하나 ↔ Fit3 단일 운동 기록 하나**다. 서버 루틴에 시연할 근력 운동 한 종류를 구성하고 실제 완료 세트를 하나 이상 기록한다. Fit3에서도 같은 운동을 한 번 시작하고 한 번 종료해 전체 시간이 대응되게 한다. 외부 요약은 ‘이 운동 세션의 Samsung Health 요약’으로 표시하며, 개별 세트에 심박수·칼로리를 배분하거나 기본 리포트 수치에 합산하지 않는다. 러닝의 실제 시작 시각·재전송 상태 확장은 헬스 시연이 안정된 뒤 진행한다.

1. 발표자가 Fit3와 FitMate에서 같은 운동을 시작하고 종료한다.
2. Fit3 기록이 휴대전화의 Samsung Health에 나타난다.
3. FitMate에서 최근 운동을 새로고침하고 해당 후보를 확인한다.
4. 해당 요약을 기존 완료 운동 세션에 저장하고 시간·칼로리·제공된 심박수 등을 리포트에 표시한다.
5. 기록 목록에서 다시 열어도 같은 세션의 요약이 조회되고, 같은 요청을 재시도해도 중복 저장되지 않는다.
6. Samsung Health를 사용하지 않아도 기존 직접 기록과 기본 리포트는 동작한다.

이것이 앞선 계획에서 말한 ‘실기기 개발 검증’의 의미다. 별도 인증 절차가 아니라 **발표에 쓸 휴대전화와 Fit3로 위 흐름을 직접 해보는 것**이다. 현재는 기본 운동 서버 연동이 구현되어 있으므로 이를 재사용해 저장·재조회까지 하나의 시연으로 완성한다. 기기에서 운동 한 건을 읽는 확인은 시작 단계이며 최종 완료 기준은 아니다.

## 2. 포함 범위와 제외 범위

| 구분 | 이번 계획 |
|---|---|
| 연동 방식 | 현재 Samsung Health Data SDK 1.1.0 유지 |
| 실행 환경 | 발표자 휴대전화, Fit3, Samsung Health 개발 모드, 개발용 앱 |
| 운동 선택 | 운동마다 Samsung Health 사용 여부 선택 |
| 조회 | 운동 종료 후 최근 운동 후보를 수동 새로고침. 기본 조회 창은 기존 7일 재사용 |
| 연결 | 운동 종류와 시작·종료 시각으로 후보를 추리고 사용자 확인 |
| 리포트 | 기본 기록에 외부 운동 요약 영역 추가 |
| 서버 저장 | 사용자가 확인한 최소 운동 요약만 저장. 발표 후 개발 DB에서 테스트 데이터 수동 정리 |
| 오류 대응 | 권한 거부, 기록 없음, 동기화 지연, 통신 실패, 중복 저장 |

다음 기능은 이번 발표용 구현에서 제외한다.

- 앱스토어 배포, Samsung 파트너 심사, 배포용 서명 등록, 운영 서버 배포 및 CI/CD 구축
- 보유기간 정책, 자동 삭제, 백업·복원, 운영용 모니터링과 장기 이력 관리
- 72시간 조회 제한, 1분 주기 삭제 작업, 서버 재시작 시 만료 정리
- 외부 요약 삭제 화면/API, 별도 동의 철회·탈퇴 기능 및 삭제 연쇄 처리
- 자동 백그라운드 동기화, Push 알림, 원본 변경·삭제 감시
- 직접 Bluetooth 통신, Fit3 원격 운동 제어, 실시간 심박수 스트리밍
- 심박수 원시 샘플, GPS 경로, 수면, 심박 구간, 지도와 고급 분석
- 복합 운동의 여러 구간을 나눠 연결하는 기능, 이미 연결한 기록을 다른 기록으로 교체하는 기능

기존에 구현된 계정·동의 기능을 제거하는 작업은 포함하지 않는다. Samsung Health의 필수 읽기 권한 요청은 데이터 조회에 필요하므로 유지한다.

## 3. 기획 문서와 현재 코드

참조 문서는 [기능명세서 v1.2](../프로젝트제출용/3주차/기능명세서_v1.2.pdf), [기능 구현 백로그](../01-spec/기능-구현-백로그.md), [개발 우선순위](../01-spec/기능-개발-우선순위.md), [P0 API 명세](../01-spec/p0-api-명세.md), [DB 기획](../01-spec/db-설계-초기화-전-기획.md), [프론트·백엔드 연동 요구사항](../01-spec/프론트-백엔드-연동-요구사항.md), [운동 기록 연동](./운동-기록-api-프론트엔드-연동.md), [러닝 기록 연동](./러닝-기록-api-프론트엔드-연동.md)이다. 문서의 미완료 목록과 코드가 다르면 아래 코드 확인 결과를 기준으로 작업량을 산정한다.

백로그의 `SAMSUNG-HEALTH-01~11`은 선택적 사용, 운동 종료 후 조회, 종류·시간 매칭, 사용자 확인, 상세 리포트 반영을 요구한다. 이 핵심 흐름을 유지하되, 삭제·철회·백업 등은 이번에 승인된 발표 범위에 맞춰 제외한다. 원본 기능명세서와 `1차기획` 파일은 수정하지 않는다.

아래 표와 이어지는 차이 목록은 **v0.6 계획 수립 당시의 기준선**이다. 현재 구현 상태는 이 표의 미구현 표현이 아니라 뒤의 v0.8 결과 문단과 검증 문서를 기준으로 판단한다.

| 영역 | v0.6 당시 확인한 구현 | 당시 필요한 작업 |
|---|---|---|
| SDK | `android/app/libs/samsung-health-data-api-1.1.0.aar` 존재, Gradle에서 로컬 AAR 사용 | 버전과 방식 유지 |
| 조회 | `SamsungHealthManager`가 오늘 걸음 수와 최근 7일 운동을 조회 | 기존 조회 창 재사용, 선택 세션에 맞는 시간 범위·페이지 처리 추가 |
| 모델 | UID, 출처 앱·기기, 시각, 거리·칼로리·심박수 등 존재 | 원본 수정 시각, 실제 활동 시간, 구간 수 보존 |
| UI | `SamsungHealthTestScreen`은 별도 조회 화면 | 운동 완료 리포트에서 후보 조회·확인 연결 |
| 헬스 진행 | `ServerWorkout`이 시작·세트 저장·일시정지·완료·완료 세션 재조회를 수행 | 응답의 세션 ID·시각을 외부 연결에 사용 |
| 로컬 리포트 | `ReportScreen`이 조회 목록의 최신 `endTime` 운동을 표시 | 종류·시각 확인 없이 최신 기록을 붙이는 방식을 후보 확인으로 교체 |
| 서버 리포트 | `ServerRecordDetail`이 헬스 리포트·세션 또는 러닝 상세를 조회. Samsung 상태는 전달받지 않음 | 완료 직후와 기록 목록 상세에 동일한 외부 요약 영역 추가 |
| Android 서버 연결 | `FitMateApiClient`, `SessionTokenStore`, `ServerRecordApi` 구현 | 기존 인증·갱신·요청 방식을 재사용, 새 HTTP 클라이언트 불필요 |
| 로컬 보존 | `FrontendLocalStore`의 계정별 snapshot과 `noBackupFilesDir` 사용 | 사용 여부·시각 등 연결 메타데이터만 보완, 원시 건강 데이터 보관 확대 없음 |
| 운동 백엔드 | `WorkoutController`·`WorkoutService`·`WorkoutReportService` 구현. 완료 리포트는 `BASIC_READY` | 기본 상태·API 계약 유지, 외부 요약 GET/PUT만 추가 |
| 러닝 | `RunningFlow`·`ServerRecordApi`·`RunningRecordService`가 저장·재조회 지원. 저장 시 RUNNING 완료 공통 세션 생성 | 반환된 기록 ID를 공통 세션 ID로 사용, 실제 `startedAt` 보완 |
| DB | migration V1~V19, 러닝 V8·인바디 V14·메이트 V15~V19 존재. V19는 모집글 상세 확장 | V1~V19 보존, 현재 다음 번호 V20으로 외부 요약 테이블 1개 추가 |
| 테스트 | Android 계약·상태·실기기 API 테스트, Backend 운동·러닝·migration 통합 테스트 존재 | 기존 검증 확장, Samsung 요약 전용 검증 추가 |

v0.1의 ‘기본 운동 API 미구현’, ‘리포트는 빈 인터페이스’, ‘DB V3까지’ 전제는 이전 체크아웃 `628ddca`에 해당한다. v0.2에서 이를 정정하고 `BASE-01` 신규 구현 단계를 제거했다. 소셜·인바디·메이트 코드도 추가되어 있으나 이번 연동을 위해 확장할 필요는 없다. v0.2 코드 검토 시작 시 git 작업 트리는 깨끗했으며, 이후 문서 변경은 계속 보존한다. 실제 기기·DB의 현재 동작을 재검증한 것은 아니다.

v0.6 당시 남아 있던 핵심 차이는 다음과 같다.

- 서버 루틴을 선택한 헬스는 완료 후 `ServerRecordDetail`로 이동한다. 로컬 `ReportScreen`만 고치면 주 시연 경로에는 반영되지 않는다.
- `WorkoutSession.useSamsung`은 로컬 선택값이며 생성 API에는 전송하지 않는다. 완료 뒤와 기록 재진입 시 이 선택을 전달·복원해야 한다.
- 로컬 전용 운동은 서버 세션 ID가 없을 수 있다. 첫 시연에서는 서버 루틴으로 생성한 헬스 세션을 사용한다. 로컬 운동의 신규 서버 변환 기능은 추가하지 않는다.
- 러닝의 `runningPayload`는 현재 실제 `startedAt`을 보내지 않는다. 서버의 종료 시각에서 활동 시간을 빼는 보정은 휴식 시간이 있는 운동 매칭에 부정확할 수 있다.
- `MainActivity.onResume()`의 새 상태 생성은 조회 결과를 비울 수 있다. 권한 확인과 선택·저장 상태를 분리하고 계정 전환 시 이전 계정의 후보를 비운다.
- 백엔드에는 아직 Samsung 외부 요약 전용 API·테이블·원본 UID 연결이 없다.

위 목록은 v0.6 작성 시점의 차이다. v0.8 구현에서는 서버 리포트 연결, 세션별 명시적 후보 확인, 상태 보존, 실제 러닝 시작 시각, 외부 요약 API·V20 테이블과 실기기 `BENCH_PRESS` 계약을 반영했다. 로컬 전용 운동은 계획대로 서버 외부 요약 저장 대상에서 제외했다.

[기존 Fit2 가이드](../00-project/1차기획/Fit2_SamsungHealth_Android_연동_가이드.md)는 Health Connect를 제안하지만 실제 코드는 Samsung Health Data SDK다. [기존 SDK 테스트 안내](../../android/SAMSUNG_HEALTH_DATA_SDK_TEST.md)의 Fit2 기기명도 Fit3 검증 완료의 근거로 사용하지 않는다.

## 4. 공식 지원 범위와 선택 이유

공식 자료 확인일은 2026-09-09다.

| 구분 | 확인 내용과 계획에 미치는 영향 |
|---|---|
| 확인된 사실 | Fit3는 운동을 기록하고 Samsung Health에서 상세 정보를 확인할 수 있다. [Samsung Fit3 운동 안내](https://www.samsung.com/us/support/answer/ANS10004899/) |
| 확인된 사실 | SDK는 Samsung Health에 저장된 운동·걸음·수면 등을 읽으며 Galaxy Fit 계열의 출처 정보를 다룬다. [Samsung SDK FAQ](https://developer.samsung.com/health/data/faq.html) |
| 확인된 사실 | 개발 중에는 개발 모드로 등록 없이 SDK를 사용할 수 있다. 일반 사용자용 배포에는 별도 등록 절차가 있다. 이번 범위는 개발용 시연이다. [앱 개발 절차](https://developer.samsung.com/health/data/process.html) |
| 확인된 사실 | 데이터 출처의 device ID로 기기 상세를 조회할 수 있다. [출처 기기 안내](https://developer.samsung.com/health/data/guide/features/device-manager.html) |
| 추론 | 기존 조회 코드와 위 공식 지원을 바탕으로 Fit3 운동 요약을 읽는 방식이 가장 변경량이 작다. Health Connect로 교체할 이유가 없다. |
| 기기에서 확인 필요 | 실제 Fit3 기록에 어떤 심박수·거리 필드와 기기 모델명이 들어오는지, 동기화까지 얼마나 걸리는지 |

출처 모델명이 비어 있으면 ‘Samsung Health 운동 기록’으로 표시한다. 모든 레코드를 확실한 Fit3 생성 데이터라고 단정하지 않는다. 발표에서는 직접 기록한 운동과 Samsung Health 화면을 함께 대조한다.

운동별 필드가 없으면 ‘기록 없음’으로 표시한다. 오늘 전체 걸음 수를 특정 운동의 걸음 수로 붙이지 않는다. 실시간 측정값이 아닌 **운동 종료 후 동기화된 요약**으로 설명한다.

## 5. 전체 흐름

```text
Galaxy Fit3 운동 기록
  → 휴대전화 Samsung Health에 동기화
  → SamsungHealthManager에서 운동 읽기
  → 시간·종류로 후보 추리기
  → 사용자가 해당 운동 확인
  → 완료된 서버 운동 세션에 요약 저장
  → ServerRecordDetail에서 기본 리포트와 외부 요약 표시
  → 기록 목록에서 재조회해 같은 리포트 복원 [첫 시연 완료]
```

1. 설정에서 연결 가능 여부를 확인하고 필요한 운동 읽기 권한을 요청한다.
2. 이번 운동에서 Samsung Health 사용을 선택한다. 미사용이면 기존 흐름으로 진행한다.
3. Fit3에서 운동을 직접 시작한 후 FitMate 운동을 시작한다. 앱은 실제 시작 시각을 기록한다.
4. 두 기기의 운동을 종료한다. FitMate 기본 리포트는 즉시 볼 수 있다.
5. ‘Samsung Health 운동 불러오기’를 눌러 후보를 조회한다.
6. 후보가 없으면 Samsung Health 동기화를 확인하고 새로고침한다. 고정 시간 안에 동기화된다고 약속하지 않는다.
7. 종류·시간·제공된 요약을 보고 사용자가 후보를 확인한다. 한 개여도 확인을 거친다.
8. 해당 완료 세션에 요약을 저장하고 재조회 결과를 리포트에 표시한다. 저장 전 후보 미리보기와 저장 완료를 구분한다.
9. 저장 실패 시 기본 리포트를 유지하고 재시도를 제공한다.

연결 해제·데이터 삭제 메뉴는 새로 만들지 않는다. 사용자가 Samsung Health 설정에서 권한을 해제했을 때 읽기에 실패해도 FitMate 자체 운동은 계속 사용할 수 있게 한다.

## 6. Android 구현 계획

아래 경로의 기준은 `android/app/src/main/java/com/fitmate/app/`다. 신규 파일명과 클래스 분리는 구현 가정이며, 착수 시 같은 역할의 파일이 생겼다면 기존 구조를 사용한다.

| 대상 | 변경 내용 | 완료 기준 |
|---|---|---|
| `samsunghealth/SamsungHealthManager.kt` | 운동만 요청하는 권한 경로, 기간 지정 조회, 페이지 처리, `point.updateTime`·활동 시간·구간 수 추출 | 기존 진단용 걸음 수 조회와 독립적으로 운동 조회 가능 |
| `samsunghealth/SamsungWorkoutUiModel.kt` | nullable 값과 원본 정보를 보존하는 매핑 | 미제공 값을 0으로 꾸미지 않음 |
| 신규 `samsunghealth/ExternalWorkoutSummary.kt` | 화면·저장용 최소 요약 모델 | SDK 객체나 원시 로그를 서버에 보내지 않음 |
| 신규 `samsunghealth/WorkoutMatchEngine.kt` | 종류와 시간 기반 후보 필터·정렬, UID 중복 제거 | 엉뚱한 최신 운동을 자동 연결하지 않음 |
| `samsunghealth/SamsungHealthUiState.kt` | 권한 상태와 조회·후보·선택 상태 관리 | 로딩·빈 상태·오류·재시도 구분 |
| `MainActivity.kt` | SDK 호출을 운동 리포트에 전달, 화면 복귀 시 선택 상태 보존 | 권한 화면에서 돌아와도 기본 운동 기록 유지 |
| `data/model/FitMateModels.kt` | 기존 `WorkoutSummary.serverId`, 완료 시각·사용 여부 재사용. 필요한 연결 시각만 보완 | 운동별로 연결이 분리됨 |
| `ui/FitMatePrototypeApp.kt` | 서버 리포트 경로에 세션별 사용 여부·SDK 콜백 전달. 로컬 최신 운동 자동 표시 교체 | 서버 완료 직후와 로컬 경로를 혼동하지 않음 |
| `ui/ServerRecords.kt` | `ServerRecordDetail`과 목록 상세에 외부 요약 조회·후보 확인 연결 | 기록 목록에서 재진입해도 동일 요약, 추가 조회 실패에도 기본 리포트 유지 |
| `ui/WorkoutSession.kt`, `ui/FrontendLocalStore.kt` | 기존 서버 상태·사용 여부를 활용하고 계정별 세션 메타데이터 복원 보완 | 기존 snapshot 읽기 호환, 계정 간 후보·선택 혼합 없음 |
| `ui/RunningFlow.kt`, `ui/ServerRecordApi.kt` | 러닝 확장 시 실제 시작 시각을 상태·snapshot·payload에 추가, 저장 결과 ID 전달 | 일시정지 포함 시간 매칭, `pendingUpload`·`clientRecordId` 재시도 유지 |
| 신규 `ui/ExternalWorkoutSection.kt` | 후보 목록·확인·요약·저장 상태 UI | 데이터가 없거나 실패해도 기본 리포트 표시 |
| 신규 `data/remote/ServerHealthApi.kt` | `FitMateApiClient.request`로 GET/PUT, 기존 인증 흐름 사용 | `data: null` 안전 처리, 저장 후 재조회 가능 |

동기화 상태는 운동 완료 상태와 분리한다. UI 상태는 `NOT_USED`, `NEEDS_PERMISSION`, `IDLE`, `LOADING`, `EMPTY`, `CANDIDATES`, `SAVING`, `LINKED`, `ERROR` 정도로 제한한다. 서버에 별도 동기화 상태 테이블을 만들지 않는다.

후보·선택·미리보기는 메모리에서 관리하고 서버 저장된 요약은 GET으로 복원한다. 기존 snapshot에는 필요 시 세션 ID별 사용 여부와 실제 시작 시각만 추가한다. 구버전 snapshot의 누락 필드는 기본값으로 처리한다. 새 Repository·ViewModel·DI 프레임워크나 네트워크 의존성은 도입하지 않는다. 작업 취소는 오류로 삼키지 않도록 `CancellationException`을 다시 던진다.

헬스 매칭 시각은 완료 후 GET으로 읽은 서버 `startedAt`·`endedAt`을 기준으로 한다. 새 `startedAt` 서버 필드는 필요 없다. 러닝은 기존 DTO의 선택 필드 `startedAt`을 활용한다. 이전 snapshot처럼 실제 시작 시각이 없으면 정확한 매칭을 보장하지 않고 후보 재확인을 안내한다. 첫 시연에서는 신규 기록을 사용한다.

기록 재진입 시 서버의 연결된 요약이 있으면 먼저 표시한다. 연결이 없고 로컬 사용 선택을 복원하지 못하면 ‘이번 기록에 연결’ 버튼을 제공하고 명시적으로 눌렀을 때만 기기 후보를 읽는다. 건강 데이터 동의 시스템을 새로 만들지 않고, 기존 운동별 선택과 후보 확인을 이어 준다.

### 수정 시각 획득 계약

2026-09-09에 [HealthDataPoint 공식 API](https://developer.samsung.com/health/data/api-reference/-shd/com.samsung.android.sdk.health.data.data/-health-data-point/index.html)의 `updateTime: Instant?`와 로컬 `samsung-health-data-api-1.1.0.aar`의 `getUpdateTime(): java.time.Instant` 메서드를 확인했다. 이는 API 존재 확인이며 실제 Fit3 레코드에서 값이 채워지는지까지 검증한 것은 아니다.

```text
store.readData(EXERCISE 요청).dataList
  → 각 HealthDataPoint의 point.updateTime
  → SamsungWorkoutUiModel.sourceModifiedAt: Instant?
  → ExternalWorkoutSummary.sourceModifiedAt: Instant?
  → PUT sourceModifiedAt (UTC ISO-8601 또는 null)
  → DB source_modified_at (UTC DATETIME(3), nullable)
```

v0.6 당시 `toWorkout()`은 이 속성을 버리고 있었으나, v0.8 구현에서 부모 `HealthDataPoint.updateTime`을 `sourceModifiedAt`으로 매핑했다. 복합 운동 제외 규칙은 유지한다. 공식 설명상 수정 이력이 없으면 `startTime`과 같을 수 있다. 값이 null이면 null을 유지하며 운동 종료 시각·조회 시각·서버 저장 시각으로 대체하지 않는다.

[FAQ의 readChanges 안내](https://developer.samsung.com/health/data/faq.html)는 변경 종류·시각을 조회하는 별도 경로다. 이번 수동 조회에서는 `readData()` 결과의 `updateTime`을 사용하고, 이를 얻기 위해 변경 감시나 `readChanges()`를 새로 도입하지 않는다. 미제공 시에는 8절의 버전 확인·재조회·사용자 재확인을 적용한다.

## 7. 후보 매칭과 중복 처리

- 내부 시간은 `Instant`/UTC로 보관하고 화면에서만 현지 시각으로 변환한다.
- 초기 허용 오차는 **시작과 종료 각각 ±10분**으로 가정한다. 시간 구간이 겹치고 운동 종류가 맞는 후보만 제안한다. 실제 시연 기록으로 오차를 조정한다.
- 운동 종류는 문자열 유사도 대신 명시적 코드 매핑을 사용한다. 종류가 불분명하면 자동 추천하지 않는다.
- 거리 m, 활동 시간 초, 칼로리 kcal, 심박수 bpm으로 정규화한다. 전체 경과 시간과 실제 활동 시간을 구분한다.
- 중복 비교는 계정 범위 안에서 `(provider, sourceAppKey, sourceRecordId)`로 통일한다. `sourceAppKey`의 null 처리 규칙은 8절을 따른다. 같은 UID라도 출처 앱이 다르면 별개 후보이며, 시간만 같다는 이유로 합치지 않는다.
- 첫 구현은 원본에 단일 `ExerciseSession`이 있는 운동만 연결한다. 복합 운동은 지원하지 않는다고 표시한다.
- 저장 직전에 원본을 다시 읽고 변경됐다면 확인 화면을 갱신한다.
- 매칭 실패 시 다른 운동을 억지로 붙이지 않고 기본 리포트만 유지한다.

### 첫 시연의 종류·시간 매칭 추천

FitMate의 `HEALTH`는 세션 종목이고 SDK의 `exerciseType`은 기기 운동 종류이므로 문자열을 직접 비교하지 않는다. 첫 구현은 아래의 작은 허용 목록을 사용한다.

| FitMate 대상 | 허용할 SDK 종류 | 연결 단위 |
|---|---|---|
| 서버 루틴으로 만든 완료 `HEALTH` 세션 | `BENCH_PRESS` (2026-09-10 Fit3 실기기 확인) | 세션 전체와 단일 `ExerciseSession` 한 개 |
| `RUNNING` 세션 | `RUNNING` (코드·자동 테스트 반영, Fit3 실기기 시연은 미확인) | 러닝 기록 전체 |
| 미확인 코드·종류 없음·여러 `ExerciseSession` | 후보로 연결하지 않고 지원하지 않는 기록으로 안내 | 연결 없음 |

2026-09-10 실기기 조회에서 Fit3 벤치프레스 원본이 `BENCH_PRESS`, `sessionCount = 1`로 반환된 것은 확인된 사실이다. 작업 당시 커밋 전 코드에서는 `HEALTH` 허용값이 `WEIGHT_MACHINE`으로 설정된 상태를 확인했으며, 이 중간 상태는 별도 커밋으로 보존되지 않아 현재 Git 이력만으로는 재검증할 수 없다. 당시 앱은 허용 목록에 없는 코드와 복합 운동을 하나의 경고로 표시했으므로, 단일 세션이었던 해당 기록의 경고는 `BENCH_PRESS`가 허용 목록에 없어서 발생한 것으로 추정한다. 운동 읽기 권한 미허용은 그보다 앞서 조회 자체를 막은 별도 상태이며, 조회 후 표시된 미확인 코드 경고의 직접 원인으로 보지 않는다.

확인된 `BENCH_PRESS`는 Android 후보 필터와 Backend 종목 검증에 동일하게 반영했다. 그 밖의 코드는 실기기에서 확인하기 전까지 임의로 허용하지 않는다. 앱 안내에서는 지원하지 않는 실제 운동 코드와 복합 운동 건수를 나눠 표시해 원인을 간단히 확인할 수 있게 한다.

### 미확인 코드가 반복될 수 있는 이유

Samsung Health Data SDK의 `ExerciseSession.exerciseType`은 Samsung Health가 정의한 운동 enum이고, FitMate의 운동 이름은 서버 운동 카탈로그 또는 사용자가 만든 이름이다. 두 체계에는 공통 ID가 없으므로 새 Samsung 운동 코드가 들어오면 FitMate가 그 의미를 자동으로 확정할 수 없다. 현재 허용 목록이 `HEALTH / BENCH_PRESS`, `RUNNING / RUNNING`으로 작게 제한된 이유도 확인하지 않은 코드를 다른 운동으로 잘못 연결하지 않기 위해서다.

`ExerciseSession.customTitle`은 운동 제목이지만 nullable이다. Samsung 공식 계약에서는 사전 정의 운동에 맞는 값이 없을 때 `exerciseType = OTHER`와 함께 사용자 정의 운동 이름을 제공하는 용도로 설명한다. 따라서 `BENCH_PRESS` 같은 사전 정의 코드에서도 항상 FitMate 운동 이름과 같은 값이 들어온다고 가정하지 않는다. 참고: [ExerciseSession.customTitle](https://developer.samsung.com/health/data/api-reference/-shd/com.samsung.android.sdk.health.data.data.entries/-exercise-session/custom-title.html), [ExerciseType.CUSTOM_TITLE](https://developer.samsung.com/health/data/api-reference/-shd/com.samsung.android.sdk.health.data.request/-data-type/-exercise-type/-companion/-c-u-s-t-o-m_-t-i-t-l-e.html).

운동 이름은 언어·표기·사용자 입력에 따라 달라질 수도 있다. 예를 들어 `벤치프레스`, `벤치 프레스`, `Bench Press`가 같은 운동을 뜻할 수 있지만, 문자열이 비슷하다는 이유만으로 같은 운동이라고 확정할 수는 없다. `customTitle`이 비어 있거나 일반적인 제목인 경우도 있으므로 이름 하나만으로 서버 허용 목록을 자동 확장하지 않는다.

### 추천 매핑 방식

매핑은 아래 우선순위로 후보를 좁히는 데 사용한다.

1. **확인된 `exerciseType` 매핑을 우선한다.** 헬스 시연은 실기기에서 확인한 `BENCH_PRESS`의 기존 허용 매핑을 사용한다. 외부 요약은 완료 세션 전체에 연결하며 개별 운동 ID에 귀속시키지 않는다.
2. **현재 완료 세션의 운동 이름을 설명에 활용한다.** Samsung 기록의 `customTitle`이 있으면 현재 세션의 `exerciseName` 목록과 비교해 사용자가 기록을 식별할 참고 정보로 표시한다.
3. **비교 범위는 현재 완료 세션으로 제한한다.** 최근 운동 전체를 조회하는 추천과 사용자별 별칭 저장은 이번 보완에서 구현하지 않는다.
4. **이름은 보수적으로 정규화한다.** 앞뒤 공백, 연속 공백, 영문 대소문자처럼 의미를 바꾸지 않는 차이만 정규화한다. 번역, 부분 일치, 편집 거리 같은 유사도 비교만으로 자동 확정하지 않는다.
5. **이름 일치로 허용 종류를 확대하지 않는다.** `OTHER` 또는 미확인 코드는 이름이 하나와 정확히 일치해도 참고 정보로만 표시한다. 사용자 확인만으로 서버 허용 종류 검사를 우회하지 않는다.
6. **저장 가능한 후보도 사용자 확인을 거친다.** Android와 Backend가 함께 허용하는 종류, 단일 구간, 시간 조건을 모두 만족해야 한다. 후보가 한 개여도 자동 저장하지 않는다. 새 코드 허용은 실제 사례를 확인하고 양쪽 매핑을 함께 수정하는 별도 확장으로 남긴다.

최소 구현에서는 새 서버 테이블을 바로 추가하지 않고, 현재 세션의 `exerciseName` 목록을 `ExternalWorkoutSection`과 매칭 엔진에 전달해 후보 설명과 사용자 확인에만 활용한다. 최근 운동 기반 추천과 사용자별 별칭 저장은 여러 미확인 코드의 실제 사례를 모은 뒤 별도 계약으로 확장한다. 외부 요약은 지금처럼 완료 세션 전체에 연결하며, FitMate 개별 운동 ID에 직접 귀속시키는 기능은 별도 범위로 둔다.

추천 판정은 다음처럼 구분한다.

| 입력 상황 | 처리 |
|---|---|
| 확인된 `exerciseType`이고 시간 조건 일치 | 기존 명시적 매핑으로 후보 제안 |
| `OTHER` 또는 미확인 코드, `customTitle`이 현재 세션 운동 하나와 정확히 일치 | 이름 일치 참고 정보와 미지원 사유만 표시. 연결·저장 후보에는 포함하지 않음 |
| 현재 세션에는 없고 최근 운동에만 같은 이름이 있음 | 이번 범위에서는 최근 운동을 조회하거나 비교하지 않음 |
| 확인된 `exerciseType`이며 `customTitle` 없음 또는 이름 일치 없음 | 이름 설명 없이 기존 종류·단일 구간·시간 조건으로 후보 판정 |
| 미확인 코드이며 `customTitle` 없음, 이름 후보 없음 또는 여러 후보 | 자동 추정하지 않고 미지원 기록으로 표시 |
| 시간 조건 불일치 또는 여러 `ExerciseSession` | 이름이 일치해도 연결 후보에서 제외 |

시작·종료 오차는 각각 10분 이하이고 두 시간 구간이 겹쳐야 한다. 후보 정렬은 시작 오차와 종료 오차의 합이 작은 순서로 하며, 동률이면 원본 중복 키 순서로 고정한다. 후보 한 개도 자동 저장하지 않는다. 서버도 완료 상태·허용 종류·시간 조건을 확인한다. 조건을 벗어나면 다른 후보나 새 기록을 준비하며 첫 시연을 위해 오차 범위를 자동으로 넓히지 않는다.

## 8. 백엔드 API와 DB 계획

기존 `WorkoutController`의 완료·조회·리포트 API와 `RunningRecordController`의 저장·조회 API를 사용한다. 헬스는 `ServerWorkout.complete()` 후 보유한 세션 ID, 러닝은 저장 응답의 기록 ID가 연결 대상이다. 러닝도 같은 `fitness_workout_sessions`에 연결되므로 종목별 외부 저장 API를 따로 만들지 않는다. 기본 API 신규 구현이나 호환성 변경은 필요 없다.

외부 요약을 위한 신규 API는 2개로 제한한다. 기본 리포트 응답을 변경하지 않고 추가 조회 결과를 화면에서 함께 표시한다.

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | `/api/v1/workout-sessions/{sessionId}/external-workout` | 본인 세션에 저장된 외부 요약 조회. 연결된 요약이 없으면 `data: null`. 시간 경과에 따른 차단 없음 |
| PUT | `/api/v1/workout-sessions/{sessionId}/external-workout` | 사용자가 확인한 요약 저장 또는 동일 원본 갱신 |

요청 필드는 `provider`, `sourceRecordId`, `sourceAppId?`, `sourceModifiedAt?`, `exerciseType`, `startedAt`, `endedAt`, `activeDurationSeconds`, `distanceMeters?`, `caloriesKcal?`, `meanHeartRate?`, `minHeartRate?`, `maxHeartRate?`, `expectedVersion`으로 제안한다. `provider`는 `SAMSUNG_HEALTH_DATA_SDK`로 제한한다.

사용자 ID는 인증 정보에서 얻는다. 시각 순서, 양수 활동 시간, 음수가 아닌 거리·칼로리, 제공된 심박수 범위의 일관성을 검증한다. 활성 시간은 경과 시간을 초과하지 않도록 단위 반올림 오차를 고려한다. 원본 UID는 임의 UUID 형식으로 강제하지 않는다.

백엔드 신규 경로 기준은 `backend/src/main/kotlin/com/fitmate/backend/health/`다.

| 신규 파일 | 역할 |
|---|---|
| `api/ExternalWorkoutController.kt`, `api/ExternalWorkoutDtos.kt` | 인증, 요청 검증, 기존 `ApiResponse` 형식 |
| `application/ExternalWorkoutService.kt` | 세션 소유권·완료 상태 확인, 최소 요약 저장·갱신 |
| `domain/ExternalWorkoutSummary.kt` | 외부 요약 Entity |
| `repository/ExternalWorkoutSummaryRepository.kt` | 조회와 중복 방지 |
| `db/migration/V20__create_external_workout_summaries.sql` (저장소 루트 기준) | 외부 요약 테이블 추가. V1~V19 보존, 착수 시 다음 번호 재확인 |

기존 `workout/repository/WorkoutRepositories.kt`에는 본인 세션을 쓰기 잠금으로 조회하는 메서드를 추가한다. 기존 서비스의 API ID ↔ MySQL ordered UUID 변환 경계를 그대로 따라 FK·소유권 조회가 같은 ID를 사용하도록 한다. 외부 요약의 `version`은 기본 운동 세션의 `version`과 별개다. 기존 메이트용 `ops_idempotency_keys`나 알림 처리까지 확장하지 않는다.

테이블은 사용자 승인에 따라 `health_external_workout_summaries` 하나로 합친다. 기존 [DB 기획의 운동·외부 요약 모델](../01-spec/db-설계-초기화-전-기획.md)은 `health.external_workout_records`와 `health.external_workout_links`를 분리하지만, 이번 발표용 구현에는 승인된 단일 테이블 단순화를 적용한다. v0.8 구현에서 migration을 작성하고 MySQL 8.4에서 V19→V20·빈 DB→V20·API·동시 연결 테스트를 실행했다.

기존 두 테이블의 ‘외부 요약’과 ‘연결’을 한 행으로 합치되, 사용자 확인 전 후보는 메모리에만 둔다. `workout_session_id` PK로 세션당 한 요약, 아래 원본 unique로 원본당 한 세션 연결을 유지한다. 사용자 확인은 인증된 본인의 PUT 성공 시 확정되며 `user_id`와 최초 `saved_at`으로 확인자·확정 시각을 표현한다. 미확정 후보·매칭 상태 이력을 서버에 보관하지 않는 것이 기존 안과의 차이다.

| 컬럼 그룹 | 설계 |
|---|---|
| 연결 | `workout_session_id` PK/FK → `fitness_workout_sessions.id`, `user_id` FK |
| 원본 | `provider`, `source_record_id`, `source_app_id` nullable, `source_app_key` generated, `source_modified_at` nullable |
| 운동 | `exercise_type`, `started_at`, `ended_at`, `active_duration_seconds` |
| 선택 지표 | `distance_meters`, `calories_kcal`, `mean_heart_rate`, `min_heart_rate`, `max_heart_rate` nullable |
| 관리 | 최소 요약의 `payload_hash`, `version`, `saved_at`, `updated_at` |

중복 키의 논리 계약은 기존 DB 기획의 **`(user_id, provider, source_app_id, source_record_id)`**를 유지한다. v0.2에서 `source_app_id`를 뺀 정의는 사용하지 않는다. nullable 출처 앱을 그대로 MySQL UNIQUE에 넣으면 중복 null 행이 허용되므로 다음과 같이 구현한다.

- SDK가 출처 앱을 주지 않으면 API·DB의 `source_app_id`는 null로 유지한다. 실제 앱 ID는 빈 문자열을 허용하지 않는다.
- DB에서 `source_app_key = COALESCE(source_app_id, '')`인 저장형 generated column을 만든다. 이 파생값은 클라이언트 요청 필드가 아니다.
- 물리 제약은 `UNIQUE(user_id, provider, source_app_key, source_record_id)`로 둔다. Android 후보 중복 비교도 null을 같은 빈 키로 대응시킨다.
- 앱 ID와 원본 UID는 대소문자와 후행 공백이 구분되는 비교 방식을 사용한다. 앱 ID 최대 255자, UID 최대 128자로 제한하고 같은 값을 임의로 소문자화하지 않는다. 이는 저장 길이 제안이며 DEMO-01에서 SDK 기록과 확인한다.
- 출처 앱이 뒤늦게 채워져 키가 달라지면 기존 연결을 자동 이관하지 않는다. 같은 세션의 다른 원본 키 요청으로 409를 반환하고 재확인을 안내한다.

UUID 저장은 기존 `UuidBinaryConverter` 방식을 따른다. 만료 시각 컬럼과 정리용 인덱스는 추가하지 않는다. `payload_hash`는 허용된 최소 요약 필드의 중복 비교용 해시이며 전체 SDK payload나 그 원문을 저장하는 필드가 아니다.

같은 세션·원본 키·내용의 PUT 재시도는 성공으로 반환하고 행을 추가하지 않는다. 다른 내용의 갱신은 `expectedVersion`을 확인하며 동시 요청 충돌은 409로 처리한다. 비교 가능한 두 원본 수정 시각 중 요청 값이 오래됐으면 덮어쓰지 않는다. 어느 한쪽 수정 시각이 없거나 같으면서 내용이 다르면 다시 읽은 후보를 사용자에게 확인시킨 후 현재 버전으로 저장한다. 이미 연결된 세션에 다른 원본 키를 넣는 교체는 제외한다.

소유권 확인·행 잠금·쓰기·버전 증가를 한 트랜잭션에서 처리한다. UNIQUE 충돌 시 실패한 트랜잭션 밖에서 재조회해 동일 세션·동일 내용의 재시도만 성공으로 반환하고 다른 세션과의 연결 충돌은 409로 처리한다. 인증 실패는 401, 본인 세션이 아니거나 없으면 404, 잘못된 입력은 400, 연결·버전 충돌은 409로 구분한다. 서버는 외부 데이터의 진위를 증명하지 않으며, 발표자가 확인한 기기 기록을 저장한다.

### 저장·재시도·갱신 계약 추천

첫 시연의 화면은 ‘후보 확인 → 저장 → 저장된 요약’으로 구성한다. 동일 원본의 갱신은 연결된 요약의 ‘원본 다시 확인’에서만 시작하고, 자동 갱신이나 다른 원본으로의 교체는 하지 않는다.

| 상황 | 요청·서버 처리 |
|---|---|
| 최초 저장 | `expectedVersion: null`로 PUT. 연결이 없을 때만 생성하며 최초 `version`은 0 |
| 같은 내용 재시도 | 같은 세션·원본 키·정규화 내용이면 `expectedVersion`이 null 또는 이전 값이어도 200과 현재 요약 반환. 버전·최초 저장 시각 변경 없음 |
| 동일 원본 갱신 | GET에서 받은 외부 요약 `version`을 `expectedVersion`으로 전송. 현재 버전과 일치하고 원본 수정 시각 규칙을 만족하면 갱신하고 버전은 정확히 1 증가 |
| 내용이 다른데 버전 누락·불일치 | 409. GET 재조회와 SDK 원본 재조회 후 변경 내용을 다시 확인받음. 새 버전만 끼워 넣어 자동 재전송하지 않음 |
| 다른 세션에 연결된 원본·현재 세션의 다른 원본 | 409. 기존 연결 유지, 다른 후보 또는 기본 리포트로 돌아가기 안내 |

GET과 PUT 성공은 기존 `ApiResponse`의 `data`를 사용한다. 연결이 없을 때 GET만 `data: null`이고, 연결이 있거나 PUT이 성공하면 `sessionId`, 요청의 요약·원본 필드 전체(`expectedVersion` 제외), `version`, `savedAt`, `updatedAt`을 반환한다. 선택 필드는 null을 허용하고 Android는 JSON 필드 누락도 null로 처리한다. 서버 내부 `user_id`, generated key, `payload_hash`는 응답하지 않는다. `version`은 기본 세션 버전과 혼용하지 않는다.

PUT 응답 유실이나 저장 후 GET 실패 시 ‘저장 여부 확인’을 제공한다. 먼저 같은 계정·대상 세션의 GET을 다시 읽어, 아래 Android 정규화 비교로 확인한 요청과 같으면 저장 완료로 복원한다. 연결이 없으면 같은 확인 payload로 PUT을 재시도하고, 다른 내용이면 재확인 절차로 이동한다. 저장 직전 SDK 재조회에서 선택한 원본이 사라졌거나 조회에 실패하면 새 PUT을 하지 않는다. 이미 서버에 저장된 요약의 GET 복원에는 SDK 권한이나 재조회 성공을 요구하지 않는다.

`payload_hash`는 서버에서 SHA-256으로 계산한다. 대상은 8절 요청 필드 중 `expectedVersion`을 제외한 모든 필드이며 `sourceModifiedAt`도 포함한다. 고정된 필드 순서의 JSON을 사용하고, 선택 필드 누락과 명시적 null은 같은 null로 정규화한다. 시각은 UTC 밀리초 정밀도로 통일하고 그보다 세밀한 입력은 400으로 거절한다. 활동 시간은 정수 초이며 Android에서 SDK duration을 초 단위로 내림한다. 선택 수치는 유한한 0 이상 값만 받아 소수 둘째 자리에서 HALF_UP 반올림하여 저장·비교하고, 숫자 1과 1.0은 같은 값으로 처리한다. 원본 ID와 앱 ID는 대소문자·공백을 보존한다. 정규화된 저장값으로 해시를 만들고 응답에도 같은 값을 사용하여 DB 재조회 전후 비교가 달라지지 않게 한다.

Android도 확인한 요청과 GET 응답의 비교에 위 서버 정규화 규칙을 동일하게 적용한다. 비교 대상은 `provider`, `sourceRecordId`, `sourceAppId`, `sourceModifiedAt`, `exerciseType`, `startedAt`, `endedAt`, `activeDurationSeconds`, `distanceMeters`, `caloriesKcal`, `meanHeartRate`, `minHeartRate`, `maxHeartRate`만이다. `sessionId`는 대상 세션 일치 확인에 사용하고, `expectedVersion`, `version`, `savedAt`, `updatedAt`은 내용 비교에서 제외한다. GET 전체 JSON이나 SDK 원본 객체를 그대로 비교하지 않는다.

Android의 비교 기준은 실제 PUT에 직렬화한 요청 값이다. 요청과 GET의 숫자를 십진 문자열 기반 `BigDecimal`로 해석해 소수 둘째 자리 HALF_UP으로 정규화하고 수치로 비교한다. SDK Float를 Double로 확장한 이진 근삿값으로 다시 반올림하지 않는다. 선택 필드 누락과 null, 숫자 1과 1.0은 각각 같은 값이며 시각은 같은 UTC 밀리초 값으로 비교한다. ID의 대소문자·공백과 `sourceModifiedAt`의 null·값 차이는 보존한다. 따라서 요청 `123.456`과 GET `123.46`은 동일 내용으로 복원하지만 GET `123.47`은 재확인이 필요하다. 클라이언트 해시나 새 응답 필드는 추가하지 않는다.

## 9. 최소 요약 저장과 발표 후 수동 정리

이번 기능은 대학 프로젝트 발표용이다. 사용자가 확인한 최소 운동 요약만 서버에 저장하며, 시간 경과를 이유로 업로드를 거절하거나 저장된 요약 조회·표시를 차단하지 않는다. 자동 삭제와 보유기간 정책은 이번에 구현하지 않는다.

기기 후보의 기본 7일 조회 창은 SDK 조회량을 줄이는 검색 조건이며 서버 보유기간이 아니다. 선택한 FitMate 세션이 창 밖에 있으면 해당 세션의 시작·종료 시각과 매칭 오차 범위로 조회한다. 이미 서버에 연결된 요약은 기기 후보 검색 기간과 관계없이 GET으로 다시 읽는다.

| 데이터 | 처리 |
|---|---|
| 확인한 최소 운동 요약·출처·수정 시각 | 8절의 필드만 서버에 저장. 발표 후 개발 DB에서 테스트 데이터 수동 정리 |
| 후보 목록, 전체 기기 ID, 원본 SDK 객체 | 단말 메모리에서만 사용, 서버 업로드하지 않음 |
| 원시 심박수 샘플, GPS 경로, 수면, 전체 SDK payload | 이번 연동에서는 서버와 단말 파일에 저장하지 않음 |
| 건강 수치·토큰이 담긴 로그, 실제 개인정보 테스트 fixture | 남기지 않음 |
| 백업·복원 | 신규 구현 없음. 기존 프로젝트 설정을 임의로 변경하지 않음 |

확인 화면에는 ‘선택한 운동의 최소 요약을 발표용 리포트에 저장합니다. 발표용 테스트 데이터는 발표 후 개발 DB에서 수동으로 정리합니다’라고 안내한다. 별도 동의 이력 테이블이나 철회 절차는 만들지 않는다. 발표자 본인 데이터와 본인 계정을 사용하며 건강 요약은 다른 사용자에게 공개하지 않는다.

발표 후 개발 담당자는 연결된 DB가 발표용 개발 DB인지 확인하고, 발표에 사용한 테스트 계정·세션 ID로 외부 요약 행을 조회한 뒤 해당 행만 수동 삭제한다. 정리 후 대상 행이 남아 있지 않은지 확인한다. 다른 개발 데이터나 기본 운동 기록을 일괄 초기화하지 않으며, 정리 수행 여부만 결정·회의 로그에 기록한다. 실제 계정 ID나 건강 수치를 문서에 남기지 않는다. 이번 요청에서 데이터 정리를 실행하지 않는다.

운영 배포 전 후속 과제는 보유기간의 정의, 자동 삭제·파기 검증, 사용자용 삭제·동의 철회 기능이다. 이 기능들은 현재 WBS·API·migration·테스트의 구현 대상에 포함하지 않는다.

## 10. 단계별 구현 순서

기간은 담당자가 관련 코드를 이해한 상태의 대략적인 작업일이며 확정 일정이 아니다.

| ID | 작업과 담당 | 주요 대상 | 선행조건 | 완료 기준 | 예상 |
|---|---|---|---|---|---|
| DEMO-01 | 공통: 기존 연동 동작 확인 | Manager, 테스트 화면, ServerWorkout, ServerRecordDetail | 발표 기기·개발 모드·개발 서버 | 실제 Fit3 운동 1건 조회, 서버 루틴 운동 완료·재조회 확인 | 반나절~1일 |
| DEMO-02 | Android: 요약·매칭 모델과 조회 보완 | Manager, 모델, MatchEngine, UiState | DEMO-01의 기록 필드 확인 | 같은 운동 후보 제안, UID·null·복합 운동 처리 | 1일 |
| DEMO-03 | Backend: 최소 외부 요약 저장·조회 | health 패키지, WorkoutRepositories, V19, migration 테스트 | 기존 완료 세션 API와 8절 계약 | 소유권·중복·수정·최소 필드 저장 검증 통과 | 1~2일 |
| DEMO-04 | Android: 서버 헬스 리포트에 후보 확인·저장 연결 | MainActivity, PrototypeApp, ServerRecords, ServerHealthApi, ExternalWorkoutSection, snapshot | DEMO-02~03 | **첫 시연 성공**: 확인 → 저장 → 기록 재조회, 미사용 흐름 정상 | 1~2일 |
| DEMO-05 | Android: 러닝에도 같은 요약 영역 연결 | RunningFlow, ServerRecordApi, RunningSnapshot, ServerRecords | DEMO-04 | 실제 시작 시각 전송, 러닝 저장 ID 연결, 재시도 후 중복 없음 | 반나절~1일 |
| DEMO-06 | 공통: 예외 처리와 발표 리허설 | 기존 계약·통합 테스트 + 신규 요약 테스트 | DEMO-04, 러닝 포함 시 DEMO-05 | 권한 거부·동기화 지연·외부 저장 실패에도 기본 기록 유지 | 1일 |

이 표의 ID는 v0.2 기준으로 재정렬했다. 이전 `BASE-01`은 삭제하고 기존 코드의 동작 확인만 DEMO-01에 포함한다. DEMO-03은 DEMO-02 완료를 기다리지 않고 명시된 DTO 계약을 기준으로 진행할 수 있다. 러닝 저장 기능을 다시 만드는 작업은 없다. 발표 일정이 촉박하면 DEMO-05를 미루고 헬스 한 종류의 저장·재조회 흐름으로 시연한다.

## 11. 필요한 검증

| 검증 | 확인 내용 |
|---|---|
| Android 단위 테스트 | null 지표, UTC 변환, 시작·종료 오차 경계, 종류 불일치, 중복 UID, 복합 운동 제외 |
| Backend 단위 테스트 | 원본 수정·버전 충돌, 허용 필드·단위·시간 순서 검증 |
| Backend DB/API 테스트 | 본인만 접근, 같은 PUT 재시도, 동시 중복 연결, 같은 UID·다른 앱 분리, null 앱 중복 차단, 대소문자 키 구분, null 지표 |
| 최소 저장 범위 | 원시 심박수 샘플·GPS 경로·수면·전체 SDK payload가 요청 DTO와 저장 필드에 포함되지 않음 |
| 기존 기능 회귀 | Samsung Health 미사용·부분 권한·권한 해제·외부 API 실패에도 기본 운동 완료와 서버 리포트 가능 |
| 화면·계정 상태 | 완료 직후 및 기록 목록 모두 같은 세션 연결, 권한 화면 복귀 시 상태 보존, 계정 전환 시 후보 초기화, 구버전 snapshot 복원 |
| 러닝 확장 | 일시정지를 포함한 실제 시작 시각, 기존 `pendingUpload` 재전송과 `clientRecordId` 보존 |
| 실제 Fit3 | 동일 운동의 시각·종류·요약을 Samsung Health와 비교, 새로고침 후 확인·반영 |
| 서버 연결 후 | 저장 성공 후 화면 재진입·앱 재실행에서 해당 세션 요약 재조회 |

Android 폴더에서 실행할 검증 명령:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Backend 폴더에서 실행할 검증 명령:

```powershell
.\gradlew.bat test --no-daemon
```

기존 테스트를 아래처럼 확장한다. 경로 기준은 Android의 `app/src/test/java/com/fitmate/app/`, Backend의 `src/test/kotlin/com/fitmate/backend/`다.

| 기존 대상 또는 신규 대상 | 검증 변경 |
|---|---|
| Android `samsunghealth/SamsungHealthModelsTest.kt`, 신규 `WorkoutMatchEngineTest.kt` | `point.updateTime`의 값·null·시작 시각과 동일한 값 보존, 조회 시각으로 대체하지 않음, 출처 앱 포함 중복 키와 후보 경계 |
| Android `ui/ServerRecordContractTest.kt`, `ui/FrontendStateTest.kt`, `ui/FrontendCompletionTest.kt` | 기존 저장 payload·상태 보존 회귀, 러닝 시작 시각과 snapshot 호환 |
| 신규 Android `ui/ServerHealthContractTest.kt` | 외부 GET의 null·선택 필드, PUT 단위·시각·원본 UID 계약. 반올림된 저장값의 응답 유실 복구와 요청 필드만의 정규화 비교 |
| Backend `workout/api/WorkoutApiIntegrationTest.kt`, `running/api/RunningRecordApiIntegrationTest.kt` | 기존 운동 생성·완료·리포트·러닝 재조회 회귀 |
| 신규 Backend `health/api/ExternalWorkoutApiIntegrationTest.kt`, `health/application/ExternalWorkoutServiceTest.kt` | 본인 접근, 중복·동시성·버전 충돌·최소 요약 저장 |
| Backend `migration/MateMigrationUpgradeIntegrationTest.kt` | 현재 V13→최신 6개 단정은 V20 추가 시 7개가 되어 실패. 메이트 이행의 target을 V19로 고정하고 6개 단정 및 재실행 0개 검증 유지. V19 모집글 상세 컬럼·기존 행 기본값 검증 추가 |
| 신규 Backend `migration/ExternalWorkoutMigrationIntegrationTest.kt` | V19→V20은 1개, 빈 테스트 DB→V20은 20개, 재실행은 0개 이행. 기존 운동·러닝·메이트 데이터 보존, V19 상세 컬럼 유지 및 외부 요약 FK·unique 확인 |

V19는 `V19__extend_mate_post_details.sql`이며 `meeting_point`, `visibility`, `sport_fields`, `beginner_welcome`을 추가한다. 기존 메이트 이행 검증을 V18로 되돌리지 않는다. 외부 요약 구현 착수 전에 migration이 더 추가되면 기존 파일을 보존하고 신규 번호와 이행 개수를 다시 맞춘다. v0.7에서 메이트 이행 target을 V19로 고정하고 MySQL 8.4에서 V19→V20 및 빈 DB→V20 이행 테스트를 실행했다.

외부 API 계약 검증에는 최초 버전 0, 동일 PUT의 버전 불변, 내용 갱신 시 1 증가, 응답 유실 후 GET 복원, 같은 숫자 표현·누락/null의 동일 해시, 오래된 원본 거절, 종류·시간 불일치 거절을 포함한다.

Android 응답 유실 복구 검증은 합성 수치 `caloriesKcal: 123.456`의 PUT 저장 후 응답이 유실되고 GET이 `123.46`과 서버 관리 필드를 반환하는 상황을 포함한다. 저장 완료로 복원하고 불필요한 PUT·재확인 화면이 발생하지 않아야 한다. HALF_UP 경계 `123.455 → 123.46`, 선택 필드 누락/null, 1/1.0도 같은 내용으로 처리하고, GET `123.47` 또는 다른 원본 수정 시각은 다른 내용으로 처리한다. v0.7에서 이 계약의 순수 Kotlin 모델·비교 테스트 6개와 서버 정규화·API 테스트를 실행했다.

Android SDK·JDK·로컬 AAR이 필요하다. Backend 통합 테스트는 `FITMATE_TEST_DB_URL` 설정 시 실행되며 없으면 건너뛴다. 따라서 `test` 명령 성공만으로 DB 검증 완료라고 기록하지 않는다. 테스트 DB를 따로 준비하고 기존 개인 DB를 초기화하지 않는다. migration 테스트의 임시 DB 생성 권한도 사전에 확인한다.

기존 기기 API 테스트는 `android/app/src/androidTest/java/com/fitmate/app/LiveRecordIntegrationTest.kt`에 있고 Kotlin 패키지는 `com.fitmate.app.ui`다. 개발 서버와 연결된 기기에서 다음 명령을 사용한다. 테스트는 별도 계정·기록을 생성하며 Samsung SDK 읽기 검증을 대신하지 않는다.

```powershell
adb reverse tcp:8080 tcp:8080
.\gradlew.bat -PfitmateIsolatedTest=true :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.fitmate.app.ui.LiveRecordIntegrationTest -Pandroid.testInstrumentationRunnerArguments.liveRecords=true
```

위 Gradle 명령은 Android 폴더 기준이다. 현재 debug 패키지는 `com.fitmate.app.dev`, 격리 테스트는 `com.fitmate.app.dev.records`다. 발표 앱에서 개발 모드·권한을 확인하며, 격리 테스트 앱의 권한 상태와 같다고 가정하지 않는다. 기존 debug API 주소 `http://127.0.0.1:8080/api/v1`와 USB reverse 연결을 활용해 배포 작업을 추가하지 않는다.

v0.2~v0.3에서 코드·DB 기획 대조, 공식 API와 로컬 AAR 메서드 서명을 확인했다. v0.4에서는 사용자 결정을 문서에 반영하고 Markdown 검사를 수행했으며 당시에는 빌드·통합 테스트·실제 Fit3 시연을 실행하지 않았다. 후속 v0.7~v0.8 구현에서 Android·Backend·MySQL 자동 검증과 실제 Fit3 시연을 실행했으며 결과는 [Galaxy Fit3 연동 구현 검증](../03-qa/galaxy-fit3-연동-구현-검증.md)에 기록한다.

v0.5는 `fedb4c0`의 migration 파일과 이행 테스트 소스를 읽고 계획을 최신화했다. 문서 변경 내용만 대조했으며 테스트·빌드·앱·서버·DB 및 Markdown 검사 도구는 실행하지 않았다.

v0.6은 Android의 응답 유실 복구 비교에 서버와 동일한 정규화·요청 필드 제한을 명시하고 반올림 복구 검증 계획을 추가했다. WBS와 실행 범위는 변경하지 않았다.

## 12. 발표 순서와 실패 시 대체안

발표 전날 또는 당일에 같은 운동을 Fit3와 FitMate에서 함께 기록해 두면 후보를 찾기 쉽다. 준비 시점은 권장 시연 절차이며 저장된 기록의 조회 기한은 아니다. 자동 운동 인식보다 기기에서 직접 시작·종료하는 방식으로 준비한다.

가장 재현하기 쉬운 발표 방식은 **사전 기록·동기화 완료 → 현장에서 후보 확인·저장·재조회**다. 리허설에 이미 연결한 세션과 별도로, 발표용으로 같은 운동의 FitMate 완료 세션과 Fit3 원본 한 쌍을 준비해 외부 요약은 아직 연결하지 않은 상태로 둔다. 원본당 한 세션 제약이 있으므로 리허설 원본을 다른 세션에 재사용하지 않는다. 발표 중 운동 자체를 새로 수행하거나 동기화를 기다릴 필요 없이 실제 저장 흐름을 보여줄 수 있다. 사전 준비된 기록이라는 점을 먼저 설명한다. 기기의 착용·운동 시작·종료·권한 허용은 발표자가 직접 수행하고, 코드 구현만으로 실기기 확인이 끝났다고 판단하지 않는다.

이번 발표의 필수 경로는 `BENCH_PRESS` 한 종류로 고정한다. 리포트에서 현재 FitMate 운동 이름을 먼저 확인하고, 같은 시간대의 `BENCH_PRESS` 후보를 선택해 기존 PUT으로 저장한 뒤 기록 목록에서 같은 세션을 다시 열어 기존 GET으로 복원되는 흐름을 보여준다. 미지원 코드가 현재 세션 운동과 이름이 같더라도 참고 기록일 뿐 연결 버튼을 제공하지 않는다.

3~5분 시연 순서:

1. ‘Fit3 → Samsung Health → FitMate’ 구조와 운동 종료 후 연동 방식을 설명한다.
2. Samsung Health에서 준비한 실제 운동 기록을 보여준다.
3. FitMate의 해당 기본 리포트에서 ‘운동 불러오기’를 누른다.
4. 후보의 종류·시간을 확인하고 연결한다.
5. 칼로리·제공된 심박수 등의 요약을 보여준다.
6. 저장 후 기록 목록에서 다시 열어 요약이 유지되는 것과 중복 저장 방지를 보여준다.

| 위험 | 대체안 |
|---|---|
| 현장 동기화 지연 | 미리 동기화한 동일 운동을 사용하고 사전 기록임을 설명 |
| 일부 지표 없음 | 제공되는 필드만 보여주고 ‘기록 없음’ 표시 |
| 서버 연결 불안정 | 사전 점검한 개발 PC 연결 사용. 필요하면 실제 성공한 리허설 영상으로 해당 구간 설명 |
| 권한·개발 모드 해제 | 발표 전 확인. 실패 시 기본 리포트를 유지하고 설정에서 복구 |
| 외부 요약 연동 미완료 | 후보 미리보기와 실제 저장 완료를 구분한다. 기존 기본 리포트는 유지하며 미완료 항목을 설명 |
| 로컬 운동으로 잘못 진입 | 발표에서는 서버 루틴을 선택하고 완료 세션 ID가 있는지 확인 |

사전 기록이나 영상을 현재 실시간 수집 중인 것처럼 설명하지 않는다. 발표가 성공하려면 핵심 흐름 하나를 안정적으로 보여주는 것으로 충분하다.

## 13. 승인된 범위와 후속 요청

사용자는 단일 테이블 구조를 승인했다. 이번 발표 범위는 기존 SDK·운동 API를 활용한 최소 요약 저장·재조회이며, 자동 삭제·보유기간 정책·사용자용 삭제·동의 철회는 운영 배포 전 후속 과제로 분리한다. 발표용 테스트 데이터는 발표 후 개발 DB에서 수동 정리한다. 이전 버전의 3일 제한·주기 삭제·재시작 정리 제안은 이 결정으로 대체한다.

원본 중복 키는 출처 앱을 포함하고, `sourceModifiedAt`은 `readData()` 결과의 `HealthDataPoint.updateTime`에서 가져온다. 신규 API 2개와 단일 테이블, 기존 기본 리포트 유지, 심박수 원시 샘플·GPS 경로·수면·전체 SDK payload 저장 제외를 적용한다.

이 범위에 대한 추가 승인은 필요하지 않다. v0.6 당시에는 문서 작업으로 한정했으나, v0.7~v0.8 후속 요청으로 코드·migration 구현과 실제 Fit3 시연을 진행했다. 발표 후 개발 DB 수동 정리는 계속 별도 운영 작업으로 남긴다.

이 범위 안의 세부 구현 때문에 같은 승인을 반복해서 요청하지 않는다. 연동 방식 교체, 직접 Bluetooth, 원시 데이터 저장, 배포 또는 기존 API의 호환성 변경이 필요해질 때만 별도 판단한다.

후속 구현 요청 예시(이미 완료된 기능 위에 추가하는 보완 작업):

```text
docs/02-technical/galaxy-fit3-발표용-연동-구현-계획.md v1.1의 14절 필수 보완을 구현해줘.
착수 시 관련 파일을 읽고 이미 반영된 부분은 유지하며 남은 변경만 적용해줘.
기존 BENCH_PRESS 후보 확인·저장·재조회와 기본 리포트 흐름을 유지해줘.
현재 완료 세션의 운동 이름을 후보 설명에 활용하되,
OTHER·미확인 코드의 이름 일치는 참고 정보로만 표시하고 연결·저장을 허용하지 마.
종류·단일 구간·시간 조건을 통과한 후보만 사용자의 명시적 확인 후 저장해줘.
지원하지 않는 코드, 복합 운동, 시간 불일치, 권한 문제, 기록 없음을 구분해 안내해줘.
이미 구현된 안내는 재사용하고 실제로 남은 누락만 보완해줘.
기존 외부 요약 GET/PUT, FitMateApiClient와 V20 단일 테이블을 재사용해줘.
API·테이블·migration을 다시 추가하지 마. 기존 원본 키·버전·재시도 계약을 유지해줘.
최근 운동 추천, 사용자별 별칭 저장, 새 운동 코드 확대는 이번 범위에 넣지 마.
사용자가 확인한 최소 운동 요약만 서버에 저장해줘.
72시간 조회 제한, 1분 주기 삭제, 서버 재시작 시 만료 정리는 구현하지 마.
원시 심박수 샘플, GPS 경로, 수면, 전체 SDK payload는 저장하지 마.
발표용 테스트 데이터는 발표 후 개발 DB에서 수동 정리하며 이번 구현에 자동 정리 기능을 추가하지 마.
보유기간 정책, 자동 삭제, 사용자용 삭제·동의 철회는 운영 배포 전 후속 과제로 남겨줘.
WBS와 10절 단계별 구현 순서는 수정하지 마.
러닝 전용 코드 수정·추가 테스트·실기기 시연을 이번 작업과 완료 조건에서 제외해줘. Android 전체 단위 테스트에 기존 러닝 회귀 테스트가 포함되는 것은 새 러닝 작업으로 간주하지 마.
InbodyApiIntegrationTest의 생성자 parameter resolver 설정 실패는 다른 담당자 작업이므로 제외해줘.
Backend 전체 통합 테스트 성공을 이번 보완의 완료 조건으로 삼지 마.
배포, 백업, 삭제·동의 철회·탈퇴 확장, 실시간 스트리밍은 구현하지 마.
가장 가까운 Android 단위 테스트와 debug 빌드만 실행해줘.
Backend·DB·인바디 테스트와 러닝 전용 테스트·실기기 재시연은 실행하지 말고, 기존 문서의 확인 결과와 이번 실행 결과를 구분해줘.
```

## 14. 현재 구현 이후의 발표용 보완 계획

### 판단 기준과 범위

이 절은 문서에 기록된 완료 사실과 아직 제안인 항목을 나눠 작성한 후속 계획이다. 실제 코드의 추가 누락이나 새 실패를 이번에 확인했다고 주장하지 않는다. 6절·10절·11절의 최초 구현 항목을 전부 다시 수행하는 지시가 아니며, WBS·작업 ID·일정은 변경하지 않는다.

| 구분 | 문서 기준 상태 | 이번 처리 |
|---|---|---|
| 헬스 연동 | `BENCH_PRESS` 실기기 후보 확인 → 저장 → 재조회와 재시도 확인 기록 있음 | 발표의 기본 경로로 유지 |
| 서버 저장 | 외부 요약 API 2개와 V20 단일 테이블 구현·검증 기록 있음 | 기존 계약 재사용. 새 API·테이블·migration 추가 없음 |
| 미지원 안내 | 제외된 운동 코드와 복합 운동 여부를 구분하는 보완 기록 있음 | 원인별 다음 행동까지 Android에 보완 완료 |
| 운동 이름 활용 | v1.0의 후속 제안이며 완료 근거 없음 | 현재 세션 이름 전달·참고 정보 분리와 자동 테스트 완료 |
| 러닝 | 기존 코드가 있으나 이번 요청에서 전체 제외 | 전용 코드 수정·추가 테스트·실기기 시연 및 완료 기준에서 제외. Android 전체 회귀 명령에 포함된 기존 테스트만 실행 |
| 인바디 통합 테스트 | 다른 담당자의 `InbodyApiIntegrationTest` 생성자 parameter resolver 설정 실패 | 수정·재실행·원인 분석 및 이번 완료 기준에서 제외 |

### 필수 보완 결과

| 순서 | 작업 | 구현 결과 | 완료 근거 |
|---|---|---|---|
| 1 | 저장 가능한 후보와 이름 참고 정보 구분 | `ServerRecordDetail`의 현재 헬스 운동 이름을 `ExternalWorkoutSection`에 전달한다. `WorkoutMatchEngine.analyze()`가 저장 후보, 미지원 코드, 복합 운동, 시간 불일치와 불완전 기록을 분리한다. 미지원 코드의 `customTitle` 이름 일치는 참고 정보로만 반환한다. | `OTHER / 벤치 프레스`와 현재 세션 `벤치프레스`가 일치해도 후보가 비어 있는 단위 테스트 통과. 기존 허용 종류·단일 구간·±10분·시간 겹침 필터 유지 |
| 2 | 후보가 없는 이유와 다음 행동 안내 | 권한 허용, Samsung Health 기록 확인·새로고침, 같은 시간대 기록 준비, 지원 `BENCH_PRESS` 사용, 단일 운동 재기록을 상태별로 표시한다. 기록이 없다는 사실만으로 동기화 지연이라고 단정하지 않는다. | Android 컴파일·단위 테스트·debug 빌드 통과. 기본 리포트 렌더링과 기존 저장 블록은 유지 |
| 3 | 발표 안내와 완료 상태 정리 | 12절에 현재 운동 이름 확인 → `BENCH_PRESS` 후보 선택 → 기존 PUT 저장 → 기록 재진입 후 기존 GET 복원 순서를 명시했다. 새 API·테이블·migration은 추가하지 않았다. | 변경 파일 대조 완료. 헬스만 필수 범위로 두고 인바디와 러닝 전용 변경·검증은 제외 |

1번은 Android의 이름 전달·설명 보완을 중심으로 하고, Backend의 허용 종류를 넓히지 않는다. 새 운동 코드 지원, 최근 운동 조회, 개인별 별칭 저장 및 개별 운동 ID에 외부 요약을 귀속시키는 기능은 이번 범위 밖이다. 기존 `customTitle`이 없더라도 확인된 코드와 시간 조건으로 후보를 제안하는 흐름을 유지한다. 저장 직전 원본이 변경되면 확인 화면을 갱신한다는 기존 계약도 유지한다.

### 보완 구현 확인 결과

2026-09-12에 최신 `origin/main` rebase 후 JDK 17과 기존 Android SDK 환경에서 `:app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=2`를 다시 실행했다. 68개 테스트가 실패·오류·건너뜀 없이 통과했고 debug APK 조립도 통과했다. 이어 Backend에서 `test --no-daemon --max-workers=2`를 실행해 99개 중 52개 통과·47개 건너뜀·실패·오류 0개를 확인했다. DB 환경변수를 사용한 MySQL 통합 검증으로 해석하지 않는다.

| 확인 항목 | 결과 | 근거와 제한 |
|---|---|---|
| 지원 코드·단일 구간·시간 조건과 사용자 확인 유지 | 완료 | 기존 `candidates()`가 새 `analyze()`의 저장 가능 후보만 반환. 기존 테스트와 신규 경계 테스트 통과 |
| 이름만 일치하는 미지원 코드의 저장 차단 | 완료 | 미지원 `OTHER`와 현재 세션 이름 일치 테스트에서 후보 0건·참고 정보 1건 확인 |
| `customTitle` 부재 시 기존 `BENCH_PRESS` 후보 유지 | 완료 | 이름이 없고 현재 세션 이름이 달라도 허용 코드·시간 조건으로 후보가 되는 테스트 통과 |
| 원인별 안내와 기본 리포트 유지 | 코드·빌드 확인 완료 | 외부 요약 영역만 보완했으며 권한·기록 없음·시간 불일치·미지원·복합·불완전 기록별 안내와 다음 행동을 표시. 실기기 화면 재확인은 미실행 |
| 기존 저장·재조회·재시도 계약 | 변경 없음 | `ServerHealthApi`, Backend API, V20 테이블과 저장 로직을 수정하지 않음. 2026-09-10 실기기 성공 기록을 재사용하며 이번에는 서버·DB·실기기 미실행 |
| 인바디·러닝 제외 | 완료 | 인바디 및 러닝 전용 소스·테스트를 수정하지 않음. 인바디·DB·실기기는 미실행이며 Android 전체 명령의 기존 러닝 회귀 테스트만 함께 실행. PR 전 Backend 회귀 테스트는 별도로 실행 |

서버 저장 필드는 승인된 최소 요약만 유지하며 원시 심박수 샘플·GPS 경로·수면·전체 SDK payload 및 이름 비교용 새 저장소를 추가하지 않았다. 발표 후 개발 DB 수동 정리 방침과 보유기간·자동 삭제·사용자용 삭제·동의 철회 후속 과제도 유지한다.

Backend 전체 MySQL 통합 테스트의 기존 인바디 실패는 QA 문서에 과거 결과로 남기되, 이 보완 작업의 실패·미완료 사유로 계산하지 않는다. 이번 작업에서 해당 실패를 해결했다고 표시하거나 QA의 과거 결과를 삭제하지 않는다.
