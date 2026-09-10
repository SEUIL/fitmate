# 검토 중 — UI 7차 상태·공통 규칙 구현·검증

- 작성일: 2026-09-11
- 최종 수정일: 2026-09-11
- 버전: v1.0
- 상태: 검토 중
- 기준: [7차 상태·공통 규칙 시안](../01-spec/검토중-ui-시안-7차-상태-공통규칙.md)

## 적용 범위

승인된 8개 상태에 기존 민트·라이트/다크 스타일을 적용했다. API·검증 순서·동의·저장 데이터·삭제 범위는 유지한다. `StateCard`, `StateWarning`, `StateConfirm`은 개편 대상 화면에서만 사용한다. 전역 `ErrorText`·`ConfirmAction` 및 Samsung Health 내부 UI·연동 로직은 변경하지 않았다.

| 상태 | 변경 | 보존한 동작 |
|---|---|---|
| 계정 조회 실패 | 브랜드·안내 카드·아이콘·재시도 버튼 | 원래 오류 문구, users/me 재조회, 다시 로그인 분기 |
| 알림 빈 목록 | 빈 상태 카드·알림 아이콘 | 설정/관리 메뉴, 모두 읽음·전체 삭제 비활성 조건, Debug 도구 |
| 인바디 빈 목록 | 첫 기록 안내 카드 | 조회 성공·로딩 종료·오류 없음 조건, 사진/촬영/직접 입력·새로고침 |
| 인바디 판독 대기 | 판독 안내 카드·불확정 진행 표시 | 같은 analysisId 조회, 30회·2초 간격, 목록 이동 후 서버 분석 유지 |
| 검수 누락 | 확인 항목 근처 아이콘·오류 표면 | 버튼을 누른 뒤 검증, 확인 전 저장 API 호출 차단 |
| 저장 응답 불명확 | 검수 화면 상단 경고 | 성공/실패로 단정하지 않음, 재저장 차단, 목록 전환·재조회 |
| 측정 기록 삭제 확인 | 삭제 색·확인창 표면·처리 중 표시 | 취소/확인, 서버 성공 후 제거, 처리 중 닫기·중복 실행 차단 |
| 로그아웃 확인 | 확인창 표면·48dp 이상 버튼 | 기존 설명·취소/확인, 현재 기기의 로그아웃 callback |

로딩·조회 실패 중 기록이 없을 때 비어 있는 변화 차트가 나타나던 표현을 제거했다. 이미 불러온 기록이 있으면 기존대로 유지한다. 개편 화면의 `SocialScreenTitle`에 36sp 줄 간격을 지정해 긴 제목이 겹치지 않도록 했다. 오류는 색과 아이콘·문구로 구분하고 접근성 live region을 제공한다. 생성 시안의 장식·색상 차이는 기존 개편 테마를 따른다.

## 검증 결과

- `testDebugUnitTest`: 58개 통과, 실패·오류·건너뜀 0. 일반 debug 및 isolated 테스트 APK 빌드 성공.
- `StateRedesignDeviceTest`: 17개 통과(46.893초). 8개 상태의 두 테마를 검사하고, 320dp·글자 200%에서 재로그인 이동과 검수/저장 응답 불명확 처리를 확인했다.
- 빈 기록은 로딩·503 오류와 구분되며 재조회 성공 후에만 나타난다. 미확인 검수는 POST 0회, 저장 응답 불명확 후 POST 1회 유지, 삭제 확인 중 중복 DELETE 차단을 fixture 요청 수로 확인했다.
- 판독 중 목록 이동 시 분석 ID를 유지하고 새 분석 생성·취소 요청이 발생하지 않는지 확인했다. 다시 판독 확인으로 이동하면 같은 ID의 결과로 검수 화면이 열린다. MOCK 고지도 유지된다.
- 삭제·로그아웃 취소와 확인을 검사했다. 삭제 확인 중 취소/확인 비활성과 뒤로가기 차단을 확인했다. 삭제는 기기 내부 fixture 기록에만 실행했고 로그아웃 확인은 테스트 callback으로 검사했다.
- 초기 테스트 실행은 JUnit 반환형 선언 때문에 시작되지 않았다. 반환형을 `Unit`으로 명시한 뒤 17개가 통과했다. 이후 계정 오류 메시지는 재구성 시 nullable 상태가 바뀌어도 안전하도록 화면에 전달할 값을 고정했다.
- 기존 주요 흐름 회귀는 `WorkoutRedesignDeviceTest` 8개, `MateRedesignDeviceTest` 7개, `SocialRedesignDeviceTest` 8개, `ProfileInbodyRedesignDeviceTest` 9개, `AuthSettingsDeviceTest` 15개가 모두 통과했다. 계정 오류 보완 후 관련 3개 재검사를 더한 실행은 총 50개, 180.355초에 통과했다.
- 주요 회귀 범위는 세트 수정·완료 실패 후 재시도, 메이트 필터·마감·읽기 전용·메시지 재시도, 커뮤니티 초안·저장 실패·DM 요청 제한, 프로필 충돌, 인바디 저장, 가입·설정이다. 개편 테마를 끈 콘텐츠가 기존 테마를 상속하는 검사도 통과했다.
- 실제 개발 서버 연동 검사 6개도 모두 통과했다(43.803초). 두 테마에서 로그인·DM 알림 저장/재조회, 프로필 편집·직접 입력 인바디 저장, 루틴 복사/수정→세트 기록/수정→잠시 나가기/이어하기→운동 완료→리포트/이력 재조회를 확인했다. `AuthSettingsLiveDeviceTest` 2개, `ProfileInbodyLiveDeviceTest` 2개, `WorkoutLiveUiDeviceTest`의 헬스 흐름 2개를 실행했다.
- 실제 서버 검사는 각 흐름의 새 합성 계정에만 데이터를 추가·변경했다. 기존 사용자 데이터·DB 스키마·백엔드 코드는 이번 단계에서 변경하지 않았다. 서버 health `UP`과 USB 8080 reverse를 확인했다.
- USB Samsung SM-S947N의 별도 `.dev.records` 앱에서 검사한 뒤 일반 `com.fitmate.app.dev`에 최종 APK를 업데이트 설치했다. 기존 로그인 상태에서 홈→프로필→설정→로그아웃 확인→취소로 돌아오는 동작을 확인했다. 실제 사용자 로그아웃·기록 삭제·동의 변경은 수행하지 않았다.

현재 코드 색상으로 계산한 sRGB 대비는 라이트 본문/표면 16.30:1, 다크 본문/표면 12.77:1, 민트 버튼 글자/배경 9.59:1이다. 경고 표면은 화면 배경에 오류 색을 8% 합성했을 때 라이트 5.40:1, 다크 8.15:1이다. 고정 색상 계산이며 모든 픽셀·눌림·비활성 상태나 시스템 바의 대비 검증을 의미하지 않는다.

## 실제 화면

8개 컷 × 라이트·다크 = 16장, 모두 1080×2340 PNG다. 실제 Compose 화면에 합성 오류·기록을 주입했으며 실제 사용자 건강 기록을 캡처하지 않았다. 일반 Activity의 상단 바·하단 탭·시스템 바 설정은 이 캡처에 포함하지 않는다. 검수 오류는 확인 항목까지 스크롤한 컷, 저장 응답 불명확은 상단 경고 컷이다. 저장 버튼과 목록 확인 버튼은 아래쪽 스크롤 영역에 있다. 판독 진행 막대는 불확정 애니메이션의 정지 컷이다.

| 상태 | 라이트 | 다크 |
|---|---|---|
| 계정 조회 실패 | [화면](../assets/ui-states-implemented/27b-session-error-light.png) | [화면](../assets/ui-states-implemented/27b-session-error-dark.png) |
| 알림 빈 목록 | [화면](../assets/ui-states-implemented/30c-notifications-empty-light.png) | [화면](../assets/ui-states-implemented/30c-notifications-empty-dark.png) |
| 인바디 빈 목록 | [화면](../assets/ui-states-implemented/23c-inbody-empty-light.png) | [화면](../assets/ui-states-implemented/23c-inbody-empty-dark.png) |
| 판독 대기 | [화면](../assets/ui-states-implemented/25c-inbody-processing-light.png) | [화면](../assets/ui-states-implemented/25c-inbody-processing-dark.png) |
| 검수 누락 | [화면](../assets/ui-states-implemented/26b-inbody-review-error-light.png) | [화면](../assets/ui-states-implemented/26b-inbody-review-error-dark.png) |
| 저장 응답 불명확 | [화면](../assets/ui-states-implemented/26c-inbody-save-uncertain-light.png) | [화면](../assets/ui-states-implemented/26c-inbody-save-uncertain-dark.png) |
| 측정 기록 삭제 확인 | [화면](../assets/ui-states-implemented/23d-inbody-delete-confirm-light.png) | [화면](../assets/ui-states-implemented/23d-inbody-delete-confirm-dark.png) |
| 로그아웃 확인 | [화면](../assets/ui-states-implemented/31e-logout-confirm-light.png) | [화면](../assets/ui-states-implemented/31e-logout-confirm-dark.png) |

## 검증 한계와 후속 확인

이번 상태 테스트는 기기 내부 HTTP fixture를 사용했다. 실제 외부 AI 분석·사진 업로드·1분 타임아웃 대기·실제 사용자 기록 삭제·로그아웃을 수행한 결과는 아니다. 기존 알림 초기 조회 실패의 별도 오류 처리는 이번 UI 범위에서 추가하지 않았다. 따라서 알림 빈 화면이 서버 조회 성공을 보장하지 않는 기존 한계도 유지된다.

작은 화면·큰 글자 검사는 위에 명시한 흐름에 한정한다. 전체 화면의 가로 배치·TalkBack 읽기 순서·키보드·팝업 조합과 모든 API 오류 분기를 검증한 것은 아니다. 1~7차 주요 시안의 적용 이후에도 이러한 파생 상태와 실제 외부 서비스·GPS 경로는 후속 점검 대상으로 남는다. Samsung Health 내부 UI는 계속 제외한다.
