# 검토 중 — UI 3차 메이트 구현·검증

- 작성일: 2026-09-10
- 최종 수정일: 2026-09-11
- 버전: v1.1
- 상태: 검토 중
- 기준: [3차 메이트 시안](../01-spec/검토중-ui-시안-3차-메이트.md)

## 적용 범위

메이트 목록·필터·상세·모집 작성/수정·참가 신청 관리·문의함·문의 대화에 라이트/다크 UI를 적용했다. 기존 API·저장 필드·권한·상태 전이를 유지했다. 프로필 → 설정 → Samsung Health 내부와 전역 테마는 변경하지 않았다.

| 화면 | 적용 내용 | 보존한 동작 |
|---|---|---|
| 목록 | 민트 상태 배지, 종목/일정/장소/인원 카드, 모임 만들기 | 모집 중 기본값, 종목 필터, 더 보기·중복 제거·새로고침 |
| 필터 | 조건별 카드, 줄바꿈 선택 버튼, 고정 적용 버튼 | 날짜·지역·종목·수준·모집 상태, 초기화의 전체 상태 |
| 상세 | 일정/시간/인원/비용 요약, 장소·운동·종목 조건 카드 | 서버가 제공한 집결 위치만 표시, allowedActions와 신청 상태별 하단 행동 |
| 작성/수정 | 지역·제목·종목·모집 조건·장소/비용 카드, 상세 조건 드롭다운 | 기존 6종목과 모든 옵션, 검증·기존 날짜·공개 범위·중복 방지 키 |
| 신청 관리 | 신청자·상태 카드, 수락/거절, 모집 관리 구분 | 모집 상태별 제한·버전 전달·취소 확인·신청 이후 수정 제한 |
| 문의함 | 상대 이름·최근 메시지·읽지 않은 수 배지 | 기존 방 목록·진입·새로고침, unreadCount=0 배지 생략 |
| 문의 대화 | 좌우 말풍선, 고정 헤더·입력부, 읽기 전용 안내 | 읽음·폴링·2,000자 제한·공백 차단·동일 키 재전송 |

비동기 목록 수신 후 로딩 화면이 남는 현상을 UI 검사에서 재현했다. 상위 구성에서 로딩 상태를 관찰하도록 보정하여 지연 구성되는 목록이 갱신되게 했다. 요청·응답·페이지 처리 정책은 변경하지 않았다. 화면 검토 후 선택 버튼의 세로 간격과 지역 제목 중복도 보완했다.

## 2026-09-10 UI 적용 검증 결과

- `testDebugUnitTest`: 58개, 실패/오류/건너뜀 0.
- `MateRedesignDeviceTest`: 6개 통과. 라이트/다크 화면 이동, 다음 페이지 중복 제거, 미신청 집결 위치 숨김, 신청→대기→모집자 수락 및 version=3 전달, 문의함·대화·작성 조건 접근을 검사했다.
- 같은 검사에서 목록 오류→새로고침→빈 상태, 320dp·글자 200% 필터 초기화/고정 적용, 마감 모집의 신청·수락·거절·수정 제한을 확인했다.
- 문의 검사는 공백/2,001자 전송 비활성, 실패 후 본문 유지·동일 clientMessageId 재전송·성공 후 입력 초기화, 읽음과 writable=false 입력/전송 비활성을 확인했다.
- 기존 `MateDesignDeviceTest`: 2개 통과. 신규 검사와 합쳐 최종 UI 검사 8개 통과.
- `MateLiveUiDeviceTest`: 최종 별도 실행에서 라이트/다크 2개 통과(21.688초). 실제 화면 → 개발 HTTP API → MySQL 경로에서 모집 작성→수정→재조회를 검사했다.
- 실제 저장 값은 러닝, 맞팔 공개, 목표 거리 5km, 페이스 7분/km, 정원 4명이다. 제목·정원을 5명으로 수정한 뒤 종목 조건과 시작 시각 보존을 서버 응답과 대조했다.
- `assembleDebug`, `assembleDebugAndroidTest` 성공. 실기기 SM-S947N의 별도 `.dev.records` 앱에서 자동 검사했다.
- 최종 일반 `com.fitmate.app.dev` APK 업데이트 설치 성공. 기존 로그인 상태의 홈→메이트→모임 만들기→뒤로가기와 목록에서만 하단 탭이 표시되는 것을 직접 확인했다.
- 백엔드 `/api/v1/health`의 `UP`, USB 8080 reverse를 확인했다. 기존 서버·DB를 계속 실행한 상태로 두었다.
- `git diff --check` 통과. 전역 테마·Samsung Health 내부·백엔드·DB 스키마 변경 없음.

초기 검사에서 기기 절전, 테스트 입력/게시 대기, 비활성 입력 필드 선택자 문제가 있었다. 테스트 화면에서만 keepScreenOn을 설정하고 종료 시 복구하며, 입력값 확인과 키보드 닫기, 비활성 필드에 맞는 선택자를 적용했다. 목록 갱신 문제는 위의 실제 UI 보정으로 해결했다. 최종 통과 결과는 수정 후 실행 기준이며 전체 Android 테스트 모음을 실행한 것은 아니다.

## 실기기 캡처

| 화면 | 라이트 | 다크 |
|---|---|---|
| 목록 | [보기](../assets/ui-mate-implemented/list-light.png) | [보기](../assets/ui-mate-implemented/list-dark.png) |
| 필터 | [보기](../assets/ui-mate-implemented/filter-light.png) | [보기](../assets/ui-mate-implemented/filter-dark.png) |
| 상세 | [보기](../assets/ui-mate-implemented/detail-light.png) | [보기](../assets/ui-mate-implemented/detail-dark.png) |
| 작성 기본 정보 | [보기](../assets/ui-mate-implemented/write-light.png) | [보기](../assets/ui-mate-implemented/write-dark.png) |
| 작성 장소·종목 조건 | [보기](../assets/ui-mate-implemented/write-details-light.png) | [보기](../assets/ui-mate-implemented/write-details-dark.png) |
| 참가 신청·모집 관리 | [보기](../assets/ui-mate-implemented/manage-light.png) | [보기](../assets/ui-mate-implemented/manage-dark.png) |
| 문의함 | [보기](../assets/ui-mate-implemented/inbox-light.png) | [보기](../assets/ui-mate-implemented/inbox-dark.png) |
| 문의 대화 | [보기](../assets/ui-mate-implemented/chat-light.png) | [보기](../assets/ui-mate-implemented/chat-dark.png) |
| 실제 서버 수정 후 상세 | [보기](../assets/ui-mate-implemented/live-detail-light.png) | [보기](../assets/ui-mate-implemented/live-detail-dark.png) |

추가로 [문의 읽기 전용](../assets/ui-mate-implemented/chat-readonly-light.png)을 보관했다. 총 19장은 Android 실기기의 실제 Compose 화면이며 생성 시안과 구분한다. 긴 화면은 일부 스크롤 위치를 캡처했다. 테스트 구성 화면은 MainActivity의 시스템 바·하단 탐색 바를 포함하지 않으며, 일반 설치본의 탐색은 별도로 직접 검사했다.

## 데이터와 검증 한계

목록·신청·문의 검사는 기기 내부 HTTP 테스트 서버와 합성 데이터로 실행했다. 외부 사용자에게 메시지를 보내거나 실제 모집글에 신청하지 않았다. 실제 개발 서버 검사는 새 합성 계정과 맞팔 공개 모집글만 생성했고, 토큰은 테스트 메모리에서 사용했다. 기존 계정·모집글·스키마를 수정하거나 테스트 데이터를 삭제하지 않았다.

서버 간 실제 문의 전달·실제 신청 수락 E2E, 신청 이력이 있는 글 수정 거부의 실기기 재현, 모든 종목 작성 저장, 장시간 폴링·재연결, TalkBack 전체 탐색, 작은 화면/키보드 조합 전체, 시스템 테마 전환은 이번 검사에 포함하지 않았다. 관련 기존 로직을 코드로 대조했으며 위 항목까지 완료로 간주하지 않는다. 다음 구현 범위는 [4차 커뮤니티·DM](../01-spec/검토중-ui-시안-4차-커뮤니티-dm.md)이다.

## 2026-09-11 후속 수정 — 종목·날짜 필터 오류

사용자가 메이트에서 헬스를 누르거나 종목 선택 후 오늘 조건을 적용하면 서버 내부 오류가 발생한다고 보고했다. 일반 설치 앱과 실제 개발 서버에서 `sportCode=HEALTH` 단독 요청의 HTTP 500을 재현했다. 종목을 전체로 초기화한 오늘·7일 이내 조회와 지역 검색/선택은 정상 동작했다. 선택된 종목은 날짜 필터 편집 중에도 유지되므로 종목 타입 오류가 날짜 조합에서도 발생했다.

원인은 `Sport.code`가 `SportCode` Enum인 반면 `MatePostRepository.findPage`가 종목 조건을 String으로 받는 불일치였다. 서비스에서 종목 코드를 Enum으로 변환하고 Repository 매개변수 타입을 맞췄다. 알 수 없는 코드는 500 대신 기존 `VALIDATION_ERROR` HTTP 400으로 응답한다. API 필드·DB 스키마·공개 범위·조회 조건은 유지했다.

별도로 목록 요청이 실패한 뒤 다른 종목 조회가 성공해도 이전 오류 문구가 남는 현상을 Compose 테스트로 재현했다. 새 목록 요청 시 해당 목록 오류를 초기화한다. 상세 화면의 신청/관리 실패 안내 처리에는 적용하지 않았다.

- Backend `MateServiceTest` 6개 통과: 기존 3개와 Enum 변환·복합 조건 보존·전체/잘못된 종목 검사 3개.
- 새 `MateFilterLiveApiTest`는 기존 서버에서 HEALTH 요청 500으로 실패했고 수정본 재시작 후 통과했다. 전체+6종목 × 전체 기간/오늘/내일/7일 이내 28개, 수준 3개, 모집 상태 3개, 지역 1개, 복합 조건 1개로 정상 조회 36개 조합을 검사했다. 반환 행의 종목·시간 범위·수준·상태·지역 일치와 잘못된 종목 400도 확인했다.
- 실제 서버 검사는 `FITMATE_LIVE_FILTER_TEST=true`로 명시 실행한다. localhost:8080에서 새 합성 계정만 생성하고 모집글·신청·메시지는 읽기만 한다. 결과는 정상/오류 상태만 기록하며 인증값은 출력하지 않는다.
- Android 단위 테스트 58개 및 `MateRedesignDeviceTest` 7개 통과. 새 오류 잔류 테스트는 수정 전 실패, 수정 후 통과했다.
- Backend `bootJar`, Android `assembleDebug`/`assembleDebugAndroidTest` 성공. 같은 local 설정으로 백엔드를 재시작했고 해당 재시작에서는 Flyway와 Hibernate 스키마 변경을 비활성화했다. 기존 DB 데이터·스키마 변경 없음(합성 검증 계정 추가 제외).
- 일반 `.dev` 앱을 업데이트 설치한 뒤 기존 로그인 상태에서 메이트 → 헬스 → 전체 지역 → 오늘 → 선택한 조건 적용을 직접 실행했다. 오류 없이 해당 날짜의 헬스 모집글이 표시됐다.

앞선 UI 검사의 목록·필터는 합성 HTTP 서버를 사용했고 실제 서버 검사는 작성/수정에 집중하여 종목 필터의 DB 바인딩 오류를 검출하지 못했다. 이번 실제 필터 회귀 검사를 추가했으며, 위 9월 10일 결과를 전체 실제 필터 검증으로 해석하지 않는다. 이번 후속 수정은 Samsung Health 내부와 무관하다.
