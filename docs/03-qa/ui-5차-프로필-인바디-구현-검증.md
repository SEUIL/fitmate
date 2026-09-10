# 검토 중 — UI 5차 프로필·인바디 구현·검증

- 작성일: 2026-09-11
- 최종 수정일: 2026-09-11
- 버전: v1.1
- 상태: 검토 중
- 기준: [5차 프로필·인바디 시안](../01-spec/검토중-ui-시안-5차-프로필-인바디.md)

## 적용 범위

프로필·프로필 편집과 인바디 목록·상세·직접 입력·사진 확인·판독 검수에 라이트/다크 UI를 적용했다. 기존 기능·API·권한·데이터 변환을 유지한다. PROFILE·INBODY 경로에만 기존 개편 테마를 추가했으며 전역 테마와 프로필 → 설정 → Samsung Health 내부 UI는 변경하지 않았다.

| 화면 | 변경 | 유지한 동작 |
|---|---|---|
| 프로필 | 제목·민트 아바타·선택 탭·편집/인바디 버튼 | 서버 운동 통계·팔로워/팔로잉, 게시물/운동 기록, 설정 진입 |
| 편집 | 정보별 카드, 관심 운동·경력·목표·시간 선택 타일, 저장 버튼 | 지역/시설 ID, 사진 즉시 저장, customSports·목표 순서, version PATCH·오류 후 최신 정보 불러오기 |
| 인바디 목록 | 등록·최근 기록·차트·측정 기록 카드, 실제 날짜별 수치 표시 | 사진/촬영/직접 입력, 새로고침, 날짜순 기록·이전 기록 펼침·삭제 확인 |
| 상세 | 핵심 수치·부위별 수치·목표 카드, 지방량 인체 그림 | 측정 대상자 기준 좌우, 실제 측정값·미측정·결과표 목표값, 기타 항목 펼침 |
| 직접 입력·검수 | 행 전체를 눌러 수치 수정, 주요 수치 강조·저장 버튼 | 날짜/선택 항목 수정, 수치 검증, 직접 입력과 판독 검수 구분 |
| 사진 확인 | 사진과 전송 안내 카드, 선택/촬영/판독 버튼 | 외부 전송 안내, analysisId 조회·재시도, 판독/저장 불명확 시 중복 방지 |

부위별 지방량은 배·팔·허벅지에 살집이 있는 고정 그림으로 변경했다. 사용자 수치로 체형을 계산하지 않으며 근육량 그림의 윤곽은 유지한다. 글자 배율 1.3 초과 시 부위별 수치는 세로 목록으로, 핵심 수치·측정 정보·프로필 선택지는 세로 배치로 전환한다. 편집·목록·상세는 기존 스크롤 화면이며 별도 단계를 추가하지 않았다.

## 검증 결과

- `testDebugUnitTest`: 58개, 실패/오류/건너뜀 0.
- `ProfileInbodyRedesignDeviceTest`: 9개 통과. 라이트·다크 프로필 편집/저장, 목록/상세, 직접 입력 수정/저장, 사진 확인/판독 검수, 320dp·글자 200%에서 필수 수치 수정/저장을 확인했다.
- 프로필 저장 실패 후 입력 유지와 최신 정보 다시 불러오기, 검수 미확인 시 저장 차단, MOCK 고지, 저장 응답 불명확 시 중복 저장 차단, 목록 조회 실패 후 재조회, 삭제 확인 취소를 확인했다. 오류·판독 검사는 기기 내부 loopback HTTP fixture를 사용했다.
- `ProfileInbodyLiveDeviceTest`: 라이트·다크 2개 통과. 합성 계정으로 실제 화면 → 개발 HTTP API → DB 경로의 프로필 닉네임/관심 운동 PATCH·version 증가, 직접 입력 기록 저장·서버 재조회·상세 진입을 확인했다.
- 최종 실기기 실행은 64.319초, 총 11개 모두 통과했다. 초기 실행의 실패는 테스트에서 생략된 nullable JSON 필드를 참조하거나 LazyColumn의 미구성 항목을 바로 찾던 문제였다. 응답 계약과 스크롤 선택자를 수정한 뒤 통과했다.
- `assembleDebug`·`assembleDebugAndroidTest` 성공. USB Samsung SM-S947N의 별도 `.dev.records` 앱에서 검사 후 일반 `com.fitmate.app.dev`를 업데이트 설치했다.
- 일반 앱의 기존 로그인 상태에서 프로필 → 편집 → 아래쪽 공개 범위/저장 영역 → 취소 → 인바디 진입을 확인했다. 기존 건강정보 미동의 계정에는 동의 안내가 표시된다. 동의값이나 실제 사용자 프로필·건강 기록을 변경하지 않았다.
- 개발 서버 health `UP`, USB 8080 연결을 확인했다. 이번 단계에서 백엔드 코드·DB 스키마는 변경하지 않았다. 서버 검증 재실행 과정에서 합성 계정과 해당 계정의 직접 입력 기록을 추가했다.

## 실기기 화면

합성 데이터로 실행한 실제 Compose 화면 10개 컷 × 두 테마, 20장이다. 모두 1080×2340 PNG이며 생성 시안과 구분한다. 테스트 캡처에는 실제 Activity의 상단 바·하단 5탭·시스템 바 테마 설정을 포함하지 않는다. 긴 화면의 아래쪽은 스크롤해야 한다. 사진 확인 컷은 기존 analysisId가 있는 상태이며 합성 결과표를 표시한다.

| 화면 | 라이트 | 다크 |
|---|---|---|
| 프로필 | [화면](../assets/ui-profile-inbody-implemented/21-profile-light.png) | [화면](../assets/ui-profile-inbody-implemented/21-profile-dark.png) |
| 편집 기본 정보 | [화면](../assets/ui-profile-inbody-implemented/22a-profile-basic-light.png) | [화면](../assets/ui-profile-inbody-implemented/22a-profile-basic-dark.png) |
| 편집 관심 운동 | [화면](../assets/ui-profile-inbody-implemented/22b-profile-sports-light.png) | [화면](../assets/ui-profile-inbody-implemented/22b-profile-sports-dark.png) |
| 목록 상단 | [화면](../assets/ui-profile-inbody-implemented/23a-inbody-list-light.png) | [화면](../assets/ui-profile-inbody-implemented/23a-inbody-list-dark.png) |
| 측정 기록 | [화면](../assets/ui-profile-inbody-implemented/23b-inbody-records-light.png) | [화면](../assets/ui-profile-inbody-implemented/23b-inbody-records-dark.png) |
| 상세 상단 | [화면](../assets/ui-profile-inbody-implemented/24a-inbody-detail-light.png) | [화면](../assets/ui-profile-inbody-implemented/24a-inbody-detail-dark.png) |
| 지방량·목표 | [화면](../assets/ui-profile-inbody-implemented/24b-inbody-fat-light.png) | [화면](../assets/ui-profile-inbody-implemented/24b-inbody-fat-dark.png) |
| 직접 입력 | [화면](../assets/ui-profile-inbody-implemented/25-inbody-manual-light.png) | [화면](../assets/ui-profile-inbody-implemented/25-inbody-manual-dark.png) |
| 사진 확인 | [화면](../assets/ui-profile-inbody-implemented/25b-inbody-photo-light.png) | [화면](../assets/ui-profile-inbody-implemented/25b-inbody-photo-dark.png) |
| 판독 검수 | [화면](../assets/ui-profile-inbody-implemented/26-inbody-review-light.png) | [화면](../assets/ui-profile-inbody-implemented/26-inbody-review-dark.png) |

## 남은 검증

실제 사진 picker·카메라·업로드·외부 AI 분석, 프로필 사진 즉시 업로드, 모든 지역/시설 선택 조합, 모든 글자 크기·가로 화면 조합과 전체 Android 회귀 모음은 이번 검사에 포함하지 않았다. 사진 확인·검수 검사는 합성 사진과 fixture 분석 결과를 사용했으므로 실제 AI 분석 성공으로 간주하지 않는다. 큰 글자 자동 검사는 직접 입력 흐름을 대상으로 했으며 상세 화면 전체의 큰 글자 검증은 남아 있다.

다음 적용 묶음은 [6차 인증·일반 설정](../01-spec/검토중-ui-시안-6차-인증-설정.md)이다. Samsung Health 내부 UI 제외 조건을 유지한다.

## 후속 수정 — 기존 인바디 기록 조회 오류

사용자의 서버 오류 제보 후 백엔드·MySQL·USB 연결이 정상인 상태에서 인바디 조회만 실패하는 것을 확인했다. 기존 DB의 부위별 지방량 JSON에는 `TRUNK_FAT_MASS_KG` 등 5개 항목명이 있었으나 현재 서버 enum은 `TRUNK_FAT_KG` 등을 사용하여 역직렬화 중 목록 전체가 HTTP 500으로 실패했다. 앞선 합성 데이터 검사는 현재 항목명만 사용했으므로 이 경우를 발견하지 못했다.

`InbodyRecordService`에서 기존 왼팔·오른팔·몸통·왼다리·오른다리 지방량 항목명을 읽을 때만 현재 코드로 변환한다. 기존 JSON·측정값·DB 스키마와 현재 저장 요청 계약은 유지한다. 두 코드가 함께 있으면 JSON 순서와 무관하게 현재 코드 값을 우선한다.

회귀 테스트 2개에서 동일 예외를 먼저 재현한 뒤 수정했다. 목록/상세의 기존 수치 보존, 중복 코드 우선순위, 현재 코드와 선택 항목 없는 기록을 포함한 인바디 관련 테스트는 11개 통과, 별도 환경이 필요한 2개는 건너뛰었다. `bootJar`와 `git diff --check`도 통과했다. 개발 백엔드를 동일 local 프로필·Flyway 비활성·DDL 변경 없음으로 재시작했다.

실제 휴대폰에서 기록 새로고침 후 오류 제거, 최근 기록 및 상세 조회, 지방량 5개 부위 라벨과 수치 표시를 확인했다. 백엔드 health `UP`, MySQL healthy, USB 8080 reverse 정상이며 재시작 이후 서버 오류는 0건이었다. Android 재설치는 필요하지 않았고 실제 사용자 데이터는 변경하지 않았다.
