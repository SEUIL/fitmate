# 검토 중 — UI 4차 커뮤니티·DM 구현·검증

- 작성일: 2026-09-11
- 최종 수정일: 2026-09-11
- 버전: v1.0
- 상태: 검토 중
- 기준: [4차 커뮤니티·DM 시안](../01-spec/검토중-ui-시안-4차-커뮤니티-dm.md)

## 적용 범위

피드·댓글·게시물 작성/수정·DM 목록·대화·받은 요청에 라이트/다크 UI를 적용했다. `WorkoutDesignTheme` 적용 경로에 COMMUNITY·DM·DM_DETAIL을 추가하고 새 소셜 컴포넌트는 해당 화면에서 사용한다. 전역 테마와 프로필 → 설정 → Samsung Health 내부는 변경하지 않았다.

| 화면 | 변경 | 유지한 동작 |
|---|---|---|
| 피드 | 민트 선택 탭, 테두리 카드·작성 버튼·작성자 아바타, 목록 로딩/실패/재조회 | 전체/팔로잉/내 게시물, 차단·맞팔 필터, 좋아요·댓글·작성자 이동·메뉴·더 보기 |
| 댓글 | 닫기 헤더, 스크롤 본문, 고정 입력·게시 영역, 오류·임시 신고 안내 | 500자, 내 댓글 삭제/타인 신고, 기존 서버/로컬 분기 |
| 작성·수정 | 공개 범위 선택 카드, 사진 추가 버튼, 글자 수, 고정 저장·초안 버튼 | 2,000자·사진 10장·제거·운동 요약 선택, 초안 보존/삭제 확인, ID·version 수정 |
| DM 목록 | 요청·대화 카드, 미리보기·알림 끔·미확인 수, 실패/재시도 | 받은/보낸 요청, 나간 방·차단 제외, 진입 시 로컬 unread 초기화 |
| DM 대화 | 고정 헤더·입력창, 좌우 말풍선·민트 본인 메시지, 시간·전송/읽음 | 텍스트·사진·첨부 취소, 요청/차단/거절 제한, 대화/메시지 옵션 |
| DM 요청 | 요청 설명 카드·수락/거절 버튼, 발신 요청 대기 설명 | 수락 전 수신 입력 숨김, 발신 최초 텍스트 1개, 기존 서버 응답 처리 |

상단 뒤로가기는 기존 프로필/DM 진입 경로로 돌아가고 대화방 나가기는 기존 DM 목록 목적지를 유지한다. 저장 중에는 기존 저장 잠금과 함께 운동 요약 선택/제거도 잠근다. 게시물 메뉴 터치 높이는 해당 경로에서 최소 48dp로 보정했다.

## 검증 결과

- `testDebugUnitTest`: 58개, 실패/오류/건너뜀 0.
- `SocialRedesignDeviceTest`: 최종 8개 통과(26.882초). 라이트·다크 피드→좋아요→댓글 작성→초안 보존→맞팔 게시→수정, DM 목록→대화→텍스트 전송→받은 요청 수락→차단을 검사했다.
- 발신 요청 첫 메시지 이후 입력 숨김, 거절 시 입력 숨김, 320dp·글자 200%에서 고정 저장과 초안 삭제/취소 접근, 서버 저장 실패 후 본문·공개 범위 보존/재시도, DM 조회 실패→재조회→빈 상태를 확인했다.
- `SocialLiveUiDeviceTest`: 라이트·다크 2개 통과. 실제 화면→개발 HTTP API→DB 경로에서 합성 계정의 **맞팔 공개 게시물 작성→수정→version 증가 확인→본인 댓글 저장→서버 재조회**를 확인했다. 전체/팔로잉 피드 API에서 본인 게시물 조회와 새 계정의 DM/요청 빈 응답도 확인했다.
- UI 8개와 실제 서버 검사 2개의 동시 실행은 39.38초, 10개 모두 통과했다. 이후 카드 높이·터치 영역 보정 후 UI 8개와 단위 테스트를 다시 통과했다.
- DM 전송·수락·차단 검사는 합성 로컬 상태다. 저장 실패/재조회 검사는 기기 내부 loopback HTTP fixture를 사용했다. 다른 실제 계정에 DM·팔로우·신고를 보내지 않았다.
- `assembleDebug`·`assembleDebugAndroidTest` 성공. USB SM-S947N의 별도 `.dev.records` 앱에서 검사하고 일반 `com.fitmate.app.dev`도 업데이트 설치했다.
- 일반 앱의 기존 로그인 상태에서 홈→커뮤니티, 전체/팔로잉/내 게시물 탭 표시, 작성 시트 열기/닫기와 고정 저장·초안 버튼, DM 목록 이동을 직접 확인했다. 실제 사용자 계정으로 게시나 전송을 실행하지 않았다.
- 최종 캡처 14장을 읽기·육안 검토했고 관련 문서 로컬 링크 66개 및 `git diff --check`가 통과했다.
- 개발 서버 health `UP`, USB 8080 reverse 확인. 이번 단계에서 백엔드 코드·DB 스키마·기존 계정 데이터는 변경하지 않았다. 합성 검증 계정 2개에 각각 맞팔 공개 게시물 1개와 본인 댓글 1개를 추가했다.

첫 실행의 커뮤니티 2개 실패는 ModalBottomSheet와 배경을 단일 Compose root로 캡처하려던 테스트 선택자 문제였다. 화면 전체 캡처로 수정한 후 통과했다. 실제 캡처 검토 후 공개 범위 카드 높이를 맞췄다.

## 실기기 화면

아래는 합성 데이터로 실행한 실제 Compose 화면 7개 × 두 테마, 14장이다. 생성 시안과 구분한다. 테스트 화면에는 앱의 하단 5탭과 실제 Activity의 시스템 바 설정을 포함하지 않는다.

| 화면 | 라이트 | 다크 |
|---|---|---|
| 피드 | [화면](../assets/ui-social-implemented/16-feed-light.png) | [화면](../assets/ui-social-implemented/16-feed-dark.png) |
| 댓글 | [화면](../assets/ui-social-implemented/17-comments-light.png) | [화면](../assets/ui-social-implemented/17-comments-dark.png) |
| 작성 | [화면](../assets/ui-social-implemented/18-compose-light.png) | [화면](../assets/ui-social-implemented/18-compose-dark.png) |
| 수정 | [화면](../assets/ui-social-implemented/18b-edit-light.png) | [화면](../assets/ui-social-implemented/18b-edit-dark.png) |
| DM 목록 | [화면](../assets/ui-social-implemented/19-dm-list-light.png) | [화면](../assets/ui-social-implemented/19-dm-list-dark.png) |
| DM 대화 | [화면](../assets/ui-social-implemented/20-dm-chat-light.png) | [화면](../assets/ui-social-implemented/20-dm-chat-dark.png) |
| 받은 요청 | [화면](../assets/ui-social-implemented/20b-dm-request-light.png) | [화면](../assets/ui-social-implemented/20b-dm-request-dark.png) |

## 범위와 남은 검증

운동 요약은 기존 서버 게시물 요청에 포함되지 않는다. DM 알림·차단·나가기·메시지 삭제의 로컬 처리와 일부 신고의 임시 접수 의미를 유지한다. UI 개편으로 서버 기능이 추가되었다고 표시하지 않는다.

사진 picker의 실제 선택/10장 업로드, 사진 DM의 실제 서버 전송, 두 실제 계정 간 요청/읽음 동기화, 모든 큰 글자·가로 화면 조합과 전체 Android 회귀 모음은 이번 검사에 포함하지 않았다. 기존 API·사진 처리 코드를 유지했으며 이 범위를 통과했다고 간주하지 않는다. 다음 구현 묶음은 5차 프로필·인바디다.
