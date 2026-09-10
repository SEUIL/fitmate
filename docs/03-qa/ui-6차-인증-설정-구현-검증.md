# 검토 중 — UI 6차 인증·설정 구현·검증

- 작성일: 2026-09-11
- 최종 수정일: 2026-09-11
- 버전: v1.0
- 상태: 검토 중
- 기준: [6차 인증·설정 시안](../01-spec/검토중-ui-시안-6차-인증-설정.md)

## 적용 범위

로그인·회원가입 4단계·건강정보 동의·알림·팔로우 요청·일반 설정·화면 모드·계정 관리에 기존 개편 스타일과 두 테마를 적용했다. 기능·API 계약·저장 필드·동의 및 권한 정책을 유지한다. Samsung Health 내부 화면과 연동 로직은 제외하며 기존 진입 경로를 보존한다. 새 테마는 해당 경로에 한정하고 전역 테마는 변경하지 않았다.

| 화면 | 변경 | 유지한 동작 |
|---|---|---|
| 로그인·세션 조회 오류 | 제목·입력 카드·주 버튼·재시도 강조 | 이메일·비밀번호 검증, 로그인·세션 재조회·가입 진입 |
| 가입 1·2단계 | 진행 표시, 기본 정보 카드, 중복 확인 배치·입력란 높이 | 이메일 확인 상태, 비밀번호·성인 검증, 생년월일 포커스, 닉네임·지역/시설 ID |
| 가입 3·4단계 | 관심 운동·경험·목표·시간 카드, 선택 타일, 동의 행 | customSports, 목표 primary 순서, 서버 약관 ID·버전, 선택 건강정보 동의 |
| 건강정보 동의 | 안내 카드·제목 줄 간격·동의/나중에 버튼 | 기존 민감정보·외부 AI 전송 안내, 로컬 동의·이전 화면 복귀 |
| 알림·팔로우 요청 | 읽음 상태별 카드, 요청자·수락/거절 버튼 배치 | 알림 열기/읽음/메뉴, 서버 요청 수락·거절, 개발 도구 표시 |
| 일반 설정·화면 모드 | 그룹 카드·아이콘·단일 선택 타일 | 기존 목적지·로그아웃 확인, 시스템/라이트/다크 즉시 저장 |
| 계정·개인정보 | 서버 알림·약관·기기 동의·계정 관리 카드 | 서버 PATCH·재조회, 필수 약관 비활성, 로컬 동의, 비밀번호·탈퇴 검증과 삭제 확인 |

동의·설정 스위치는 행 전체를 누를 수 있고 접근성 노드에서 항목명과 스위치 상태를 함께 제공한다. 설정 조회 재시도 시 이전 오류 메시지를 지워 복구된 화면에 오류가 남지 않게 했다. 긴 화면은 기존처럼 스크롤한다. 가입 경험 선택지는 작은 라벨, 목표는 2열로 배치하고 글자 배율 1.3 초과 시 선택 타일은 1열로 전환한다.

## 검증 결과

- `testDebugUnitTest`: 58개, 실패·오류·건너뜀 0. 최종 debug 빌드 성공.
- `AuthSettingsDeviceTest`: 15개 통과. 라이트·다크 로그인 실패/재시도, 가입 4단계·필수 입력·동의·전송 payload, 일반 설정·화면 모드, 계정 설정, 알림, 팔로우 요청, 건강정보 동의 분기를 확인했다.
- 계정 설정은 320dp·글자 200%에서 조회 실패→재조회, 저장 실패→기존 값 유지→재시도까지 검사했다. 필수 약관 변경 차단, 로컬 기록 삭제 취소, 비밀번호 변경/탈퇴 시트의 빈 입력 제출 차단도 확인했다.
- `AuthSettingsLiveDeviceTest`: 라이트·다크 2개 통과. 새 합성 계정으로 실제 로그인 UI→개발 API→계정 설정 UI→DM 알림 PATCH→서버 재조회를 확인했다. 다른 알림 3개 값이 보존되는지와 세션 유효성도 확인했다.
- 17개 합산 실행은 80.663초에 통과했다. 이후 건강정보 제목 줄 간격과 설정 하단 캡처 위치만 보완하고 해당 두 화면의 라이트·다크 검사 4개를 재실행해 모두 통과했다(17.144초).
- 테스트 중 스위치 선택자의 모호함을 수정하고, 캡처의 테마 환경을 명시적으로 설정한 뒤 두 테마를 재검증했다. 앱의 OS 테마가 테스트의 라이트 캡처에 섞이던 문제를 제거했다.
- USB Samsung SM-S947N에서 별도 `.dev.records` 앱으로 검사했다. 일반 `com.fitmate.app.dev`는 최종 APK로 업데이트 설치했다.
- 일반 앱의 기존 로그인 상태에서 프로필→설정→계정·개인정보 조회와 설정 하단 탐색, 라이트/시스템/다크 선택을 확인했다. 테마는 검사 전 선택값인 다크로 복원했다. 실제 사용자 동의·알림 설정·프로필은 변경하지 않았다.
- 개발 서버 health `UP`, USB 8080 reverse를 확인했다. 이번 UI 단계에서 백엔드 코드·DB 스키마는 변경하지 않았다. 실제 서버 검사는 합성 계정 생성과 해당 계정의 DM 알림 설정만 변경했다.

## 실기기 화면

합성 데이터의 실제 Compose 화면 12개 컷 × 두 테마, PNG 24장이다. 모두 1080×2340이며 생성 시안과 구분한다. 테스트 캡처에는 일반 Activity의 상단 바·하단 탭·시스템 바 테마 처리를 포함하지 않는다. 가입 4단계는 필수 동의 전 상태이며, 설정 상단/하단은 같은 스크롤 화면이다. 화면 모드 컷은 시스템 선택 상태이고 각 테마 환경을 별도로 제공했다.

| 화면 | 라이트 | 다크 |
|---|---|---|
| 로그인 | [화면](../assets/ui-auth-settings-implemented/27-login-light.png) | [화면](../assets/ui-auth-settings-implemented/27-login-dark.png) |
| 가입 계정 정보 | [화면](../assets/ui-auth-settings-implemented/28a-signup-account-light.png) | [화면](../assets/ui-auth-settings-implemented/28a-signup-account-dark.png) |
| 가입 프로필 | [화면](../assets/ui-auth-settings-implemented/28b-signup-profile-light.png) | [화면](../assets/ui-auth-settings-implemented/28b-signup-profile-dark.png) |
| 가입 운동 정보 | [화면](../assets/ui-auth-settings-implemented/28c-signup-sports-light.png) | [화면](../assets/ui-auth-settings-implemented/28c-signup-sports-dark.png) |
| 가입 동의 | [화면](../assets/ui-auth-settings-implemented/29a-signup-consent-light.png) | [화면](../assets/ui-auth-settings-implemented/29a-signup-consent-dark.png) |
| 건강정보 동의 | [화면](../assets/ui-auth-settings-implemented/29b-health-consent-light.png) | [화면](../assets/ui-auth-settings-implemented/29b-health-consent-dark.png) |
| 알림 | [화면](../assets/ui-auth-settings-implemented/30a-notifications-light.png) | [화면](../assets/ui-auth-settings-implemented/30a-notifications-dark.png) |
| 팔로우 요청 | [화면](../assets/ui-auth-settings-implemented/30b-follow-requests-light.png) | [화면](../assets/ui-auth-settings-implemented/30b-follow-requests-dark.png) |
| 설정 상단 | [화면](../assets/ui-auth-settings-implemented/31a-settings-light.png) | [화면](../assets/ui-auth-settings-implemented/31a-settings-dark.png) |
| 설정 하단 | [화면](../assets/ui-auth-settings-implemented/31b-settings-more-light.png) | [화면](../assets/ui-auth-settings-implemented/31b-settings-more-dark.png) |
| 화면 모드 | [화면](../assets/ui-auth-settings-implemented/31c-appearance-light.png) | [화면](../assets/ui-auth-settings-implemented/31c-appearance-dark.png) |
| 계정·개인정보 | [화면](../assets/ui-auth-settings-implemented/31d-account-light.png) | [화면](../assets/ui-auth-settings-implemented/31d-account-dark.png) |

## 남은 검증

가입 UI 검사는 기기 내부 HTTP fixture를 사용했다. 주소 검색 WebView의 선택 결과 callback을 합성 지역 코드로 전달하여 기존 지역 조회·가입 payload를 확인했으며 외부 주소 검색 서비스 전체 조작을 검증한 것은 아니다. 실제 서버 가입은 로그인 검사 준비용 API 호출이고, 실제 서버를 대상으로 한 전체 가입 UI 검사는 남아 있다.

실제 계정 탈퇴·비밀번호 변경·기기 기록 삭제·실제 상대의 팔로우 승인/거절은 실행하지 않았다. 알림 UI 검사는 합성 로컬 목록이며 실제 Push 수신을 의미하지 않는다. 선택 약관 변경의 모든 서버 분기, 세션 만료, 전체 화면의 큰 글자·가로 화면·TalkBack 읽기 순서와 전체 Android 회귀 검사는 남아 있다.

다음 적용 묶음은 [7차 상태·공통 규칙](../01-spec/검토중-ui-시안-7차-상태-공통규칙.md)이다. 로딩·빈 목록·오류·확인 상태를 정리하고 주요 흐름을 회귀 검증한다. Samsung Health 내부 UI 제외 조건을 유지한다.
