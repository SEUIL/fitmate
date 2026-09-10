# 검토 중 — UI 2차 개인 운동 구현·검증

- 작성일: 2026-09-10
- 최종 수정일: 2026-09-10
- 버전: v1
- 상태: 검토 중
- 기준: [2차 개인 운동 시안](../01-spec/검토중-ui-시안-2차-개인운동.md)

## 적용 범위

운동 선택·운동 준비·러닝 설정·러닝 진행·헬스 완료 리포트·러닝 종료/저장·서버 운동 이력에 라이트/다크 UI를 적용했다. 기존 API·저장 데이터·측정 로직은 유지했다.

| 화면 | 적용 내용 | 유지한 동작 |
|---|---|---|
| 운동 선택 | 헬스/러닝 아이콘과 큰 카드·시작 버튼 | 루틴 선택→준비, 러닝 설정 진입 |
| 운동 준비 | 루틴 이름·설명·운동 개수·순서 목록 | 실제 선택 값, Samsung Health 선택 스위치, 시작 요청·실패 처리 |
| 러닝 설정 | 목표 3열/2행, 목표별 입력, 기록 설정 카드, 접는 개발 도구 | 자유/거리/시간/페이스/인터벌, 프리셋·거리 검증·동의·권한 요청 |
| 러닝 진행 | 큰 시간·거리, 페이스·목표·GPS·랩 카드, 하단 고정 일시정지/종료 | 실제 서비스 및 예시 GPS 상태 처리·취소 확인 |
| 헬스 리포트 | 요약 수치·운동별 요약·세트별 기록 카드 | 서버 리포트/세트 데이터·비교·RPE·메모, 로컬 경로의 목표/비교/조건부 Samsung 정보 |
| 러닝 종료/저장 | 저장 전 표시, 거리/시간·페이스, RPE 5열/2행 | 마지막 부분 구간·거리 0·목표 결과, 저장 성공 후 홈, 동일 pendingUpload/clientRecordId 재시도 |
| 서버 이력 | 헬스/러닝 선택·운동 카드·빈 상태·더 보기 | 출처 표시, 완료/HEALTH 필터, 새로고침·페이지·중복 제거·상세 시트 |

큰 글자에서는 지표를 세로로, 선택 항목을 최대 2열로 표시한다. 내용이 긴 화면은 스크롤하며 러닝 진행 조작부는 항상 하단에 표시한다. 시안의 예시 숫자와 운동명은 사용하지 않고 실제 응답과 상태를 표시한다. 전역 테마 및 Samsung Health 내부 파일은 변경하지 않았다.

## 검증

- Android `testDebugUnitTest`: 58개, 실패/오류 0.
- 기존 `WorkoutRedesignDeviceTest`: 8개 통과. 홈·루틴·세트 입력/수정·종료 실패/재시도·작은 화면·제외 영역 테마 회귀.
- 새 `PersonalWorkoutDeviceTest`: 4개 통과. 목표별 입력/거리 검증, 경로 공개 옵션 조건, GPS 불량/일시정지/종료, 저장 실패 시 동일 payload 재시도, 320dp·글자 200%에서 RPE 10·저장 버튼 접근, 거리 0 리포트.
- 실제 `MainActivity → HTTP → MySQL` 테스트: 라이트/다크 헬스 2개, 러닝 2개 통과.
- 헬스: 루틴 복사/이름 수정/운동 순서 유지 → 준비 → 세트 기록/수정 → 이어하기 → 완료 → 총 볼륨 225kg·1세트 확인 → 서버 이력/상세 재조회.
- 러닝: 거리 목표 2km → 예시 GPS 2랩 → 일시정지/재개 → 종료 → RPE 8·메모 입력 → 서버 저장 → 이력/상세 재조회. 거리·랩·RPE·메모를 실제 API 응답과 대조했다.
- `assembleDebug`, `assembleDebugAndroidTest` 성공. 일반 앱과 분리된 `.dev.records` 패키지에서 검사했다.
- 검증 후 일반 `com.fitmate.app.dev` APK를 업데이트 설치했다. 기존 사용자 세션으로 홈의 운동 시작·운동 기록 진입이 표시되는 것을 확인했다.
- 실기기 SM-S947N, 기존 백엔드 8080 응답 `UP`, USB reverse 정상. 기존 개발 DB에 새 합성 테스트 계정/기록만 추가했으며 기존 데이터·스키마는 변경하지 않았다.

러닝 하단 조작부 보정 후 실제 서버 연동 4개가 다시 통과했다. 개인 운동 검사에서 오류 표시 애니메이션을 기다리지 않아 1회 실패했고, 입력 값 확인과 오류 문구 표시 대기를 추가한 뒤 4개 모두 통과했다(7.069초). 아래 24장 캡처는 최종 실제 앱 실행 결과다.

## 실기기 캡처

| 화면 | 라이트 | 다크 |
|---|---|---|
| 운동 선택 | [보기](../assets/ui-personal-implemented/selection-light.png) | [보기](../assets/ui-personal-implemented/selection-dark.png) |
| 운동 준비 | [보기](../assets/ui-personal-implemented/preparation-light.png) | [보기](../assets/ui-personal-implemented/preparation-dark.png) |
| 러닝 설정 | [보기](../assets/ui-personal-implemented/running-setup-light.png) | [보기](../assets/ui-personal-implemented/running-setup-dark.png) |
| 러닝 진행 | [보기](../assets/ui-personal-implemented/running-active-light.png) | [보기](../assets/ui-personal-implemented/running-active-dark.png) |
| 헬스 리포트 | [보기](../assets/ui-personal-implemented/report-light.png) | [보기](../assets/ui-personal-implemented/report-dark.png) |
| 러닝 리포트 | [보기](../assets/ui-personal-implemented/running-report-light.png) | [보기](../assets/ui-personal-implemented/running-report-dark.png) |
| 러닝 RPE·메모/키보드 | [보기](../assets/ui-personal-implemented/running-condition-light.png) | [보기](../assets/ui-personal-implemented/running-condition-dark.png) |
| 헬스 이력 | [보기](../assets/ui-personal-implemented/history-list-light.png) | [보기](../assets/ui-personal-implemented/history-list-dark.png) |
| 러닝 이력 | [보기](../assets/ui-personal-implemented/running-history-light.png) | [보기](../assets/ui-personal-implemented/running-history-dark.png) |

캡처는 새 합성 계정의 실제 UI이며 생성 시안과 별도로 관리한다. 긴 화면은 스크롤 위치의 일부만 캡처했다. 기기 키보드가 열린 상태에서 메모 입력과 저장 버튼 스크롤·클릭이 성공했다.

## 검증 한계

실외 이동 GPS 정확도·장시간 백그라운드 추적·실제 위치 권한 거부 UI·Samsung Health 데이터 수신·TalkBack 전체 탐색·시스템 테마 전환은 이번 검사에서 실행하지 않았다. 위치 권한·서비스 코드는 유지했고 실제 러닝 E2E 수치는 개발용 예시 GPS로 만들었다. 서버 이력 다중 페이지와 로컬 헬스 완료 경로는 코드 대조만 했으며 이번 실기기 E2E에 포함하지 않았다. 기존 기록 삭제나 테스트 계정 정리는 수행하지 않았다.
