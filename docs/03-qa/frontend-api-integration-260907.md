# Android API 연동 및 검증 기록

- 작성일: 2026-09-07
- 최종 수정일: 2026-09-07
- 버전: v1.0
- 상태: 핵심 흐름 검증 완료

## 반영 범위

| 영역 | 연결한 API / 동작 |
|---|---|
| 인증 | 가입, 로그인, access token 만료 시 refresh 1회, 로그아웃/전체 로그아웃 |
| 가입 계약 | HEALTH 필수 해제, 운영 또는 커스텀 운동 최소 1개, 실제 필수 약관 ID 조회, 이메일·닉네임 중복 확인 |
| 지역·시설 | V6 법정동 계층 조회, 읍/면/동 UUID 선택, 기존 시설명 계약 유지 |
| 프로필 | 내 정보 조회, 변경 필드만 version을 포함해 PATCH, 저장 응답 반영, 오류 시 편집 유지·재조회 |
| 계정 설정 | 알림 설정 조회/수정, 약관·동의 조회 및 선택 동의 변경, 비밀번호 변경, 탈퇴 |
| 루틴 | 추천·템플릿·내 루틴 조회, 생성·복사·수정·삭제, 운동 상세·대체 운동 조회 |
| 헬스 세션 | 서버 루틴에서 세션 생성, 시작/일시정지/재개, 세트 저장·수정·기록 해제, 완료 후 서버 재조회 |
| 이전 기록 | 동일 운동의 서버 이전 세트 조회 및 입력 재사용 |
| 기기 저장 | Android Keystore AES-GCM 토큰 저장, 토큰 백업 제외, 사용자 UUID별 로컬 기록 격리 |

HTTP 응답의 `data.items`를 기존 `content` 또는 최상위 목록으로 잘못 읽던 부분을 수정했다. 지역 parentId의 UUID 중복 변환 문제와 이전 운동 기록 경로의 인증 누락도 수정했다.

## 아직 서버 연동으로 표시하면 안 되는 부분

- 인바디 저장·OCR, 러닝 기록 업로드, 메이트·커뮤니티·DM: 해당 API 구현/계약이 준비되지 않아 기존 로컬 또는 Mock 흐름을 유지한다.
- 운동 완료 시 세트와 세션은 서버에 저장되지만, 리포트 화면과 전체 기록 목록은 사용자별 기기 기록으로 구성한다. 기기 변경 후 전체 히스토리 복원은 아직 지원하지 않는다.
- 서버 세션 시작 후 운동 추가·삭제·대체, 세션 취소/전체 목록 API는 현재 없으므로 시작 전 루틴 편집 또는 기기 전용 운동을 사용한다.
- Samsung Health 데이터 읽기와 서버 업로드는 별개다. 이번 작업에서 서버 건강 로그 저장을 연결하지 않았다.
- 선택 약관 변경 UI는 서버의 선택 약관이 있을 때만 활성화한다. 현재 seed의 필수 약관은 철회할 수 없다. 건강·위치 토글은 기기 내부 상태다.
- 이메일 인증 발송, 비밀번호 분실 재설정, 운영용 약관 본문 확정은 이번 연동 범위가 아니다.
- 원격 동시 수정의 version 충돌은 자동 덮어쓰지 않는다. 운동 저장 충돌/모호한 네트워크 실패의 사용자 복구 UX는 추가 보강이 필요하다.

## 검증 환경과 결과

- Backend: Java 17, MySQL 8.4, 전용 `fitmate_integration_20260907` DB. 기존 개발 DB 대신 별도 DB에 V1~V6 migration 적용.
- Backend 전체 테스트: 39개 성공, 실패·skip 0. 러닝만/커스텀만 가입, 관심 운동 전체 해제 거절, 동 단위 지역 가입·시설 재조회, 루틴 및 운동 기록 왕복, 이전 기록 무인증 접근 401 포함.
- Android: `testDebugUnitTest` 38개 성공, 실패·skip 0. 응답 목록 파싱, refresh 후 1회 재요청, 가입 매핑, 불필요한 프로필 필드 덮어쓰기 방지, 변경된 세트만 업로드 검증.
- 실기기: Samsung SM-S947N / Android 16. `ServerIntegrationDeviceTest` 1개 성공. 임시 테스트 계정 API 가입 후 UI 로그인 → 서버 추천 루틴 → 세트 기록 → 완료 리포트 → API 재조회로 `COMPLETED` 상태와 이전 운동 기록 저장 확인. 각 입력·탭 사이 2초 대기.
- 실기기 자동화의 초기 실패는 입력 반영 대기, 필드 선택, 마스킹된 비밀번호 semantics 및 절전 상태 확인으로 정리했다. 최종 테스트는 실제 저장 성공을 검증하며 실패 검증을 제거하지 않았다.
- 신규 회원가입 전체 UI, 설정의 모든 토글·탈퇴, 루틴 편집 UI 각각의 실기기 회귀와 네트워크 단절·다중 기기 충돌 시나리오는 추가 확인이 필요하다. 핵심 운동 흐름 성공을 전체 화면 검증으로 확대 해석하지 않는다.

## 재현

1. Backend에 로컬 DB 연결 설정과 충분한 길이의 임시 JWT secret을 환경변수로 제공하고 실행한다. secret을 문서·코드에 저장하지 않는다.
2. 현재 테스트 서버는 localhost:8082이며, debug 앱은 `http://127.0.0.1:8080/api/v1`이다. USB `adb reverse tcp:8080 tcp:8082`로 연결한다.
3. Backend `gradlew test` 실행 시 `FITMATE_TEST_DB_URL`, `FITMATE_TEST_DB_USERNAME`, `FITMATE_TEST_DB_PASSWORD`를 테스트 계정으로 제공한다. 환경변수가 없으면 DB 통합 테스트는 skip된다.
4. Android: `gradlew testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.fitmate.app.ServerIntegrationDeviceTest`.
5. 실기기 테스트는 임시 계정을 새로 만들며 테스트 후 debug 앱을 제거할 수 있다. 사용자 확인을 위해서는 debug APK를 별도로 다시 설치한다. 기존 release 앱은 변경하지 않는다.

## 공유 주의사항

- V6의 공식 법정동 export 출처·checksum은 migration 헤더와 `db/generate-region-migration.ps1`로 추적한다. 배포 후 V6를 수정하지 말고 새 migration을 사용한다.
- 기존 Gradle wrapper 변경 4개와 개인 평가 HWP는 사용자 변경으로 보존했다.
- 이 작업에서는 commit, push, PR, merge를 수행하지 않았다.
