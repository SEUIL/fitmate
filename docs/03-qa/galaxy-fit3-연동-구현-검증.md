# Galaxy Fit3 연동 구현 검증

- 작성일: 2026-09-10
- 최종 수정일: 2026-09-12
- 버전: v0.7
- 상태: 확정
- 최신 원격 기준: `origin/main`의 `5172f84` (2026-09-12)
- 구현 기준 커밋: `3a2df1b` (기존 `5f5e8b7` rebase 결과)
- 보완 기준 커밋: `fd8be7e` (기존 `159f343` rebase 결과)

## 구현 범위

- Samsung Health 운동 읽기 전용 권한과 세션 시간 범위·페이지 조회
- 원본 수정 시각, 실제 활동 시간, 구간 수와 nullable 지표 보존
- `HEALTH / BENCH_PRESS`, `RUNNING / RUNNING` 명시적 종류 매핑과 시작·종료 각각 10분 후보 매칭
- 후보 확인 후 최소 요약 저장, 저장 직전 SDK 재조회, 응답 유실 시 GET 비교 복구
- 완료 직후와 기록 목록 상세의 동일 외부 요약 조회
- 외부 요약 GET/PUT, 세션 소유권·완료 상태·시간·종류·원본 중복·버전 검증
- `health_external_workout_summaries` 단일 V20 migration
- 러닝 실제 시작 시각의 상태·snapshot·저장 payload 보존

## 실행 결과

| 검증 | 결과 | 비고 |
|---|---|---|
| Backend `gradlew.bat test --no-daemon` | 통과 | DB 환경변수 없이 실행한 단위 테스트 |
| Backend Kotlin·test Kotlin 컴파일 | 통과 | 신규 API, entity, repository, service와 통합 테스트 소스 컴파일 |
| 외부 요약·운동·러닝·V19/V20 MySQL 통합 테스트 | 통과 | MySQL 8.4에서 `--rerun-tasks`와 대상 테스트를 실행. 테스트 뒤 컨테이너 중지 |
| 외부 원본 동시 연결 | 통과 | 교착 상황을 500이 아닌 409로 처리하고 한 세션만 저장되는지 검증 |
| Backend 전체 MySQL 통합 테스트 | 기존 실패 1건 | 이번 변경 대상 테스트는 수정 후 통과. 기존 `InbodyApiIntegrationTest`의 생성자 parameter resolver 설정 실패가 별도로 남음 |
| `git diff --check` | 통과 | 공백 오류 없음. Windows 줄바꿈 경고만 존재 |
| Android 순수 Kotlin 계약 테스트 | 통과 | SDK 구성 전 모델·후보 매칭·응답 정규화 6개 테스트를 Kotlin 2.1/JUnit 4로 실행 |
| Android Gradle unit/build | 통과 | 로컬 SDK Platform 37.0·Build Tools 36.0.0과 JDK 17에서 `:app:testDebugUnitTest :app:assembleDebug --rerun-tasks` 실행. 63개 테스트, 실패 0개, debug APK 생성 |
| MySQL V19→V20·빈 DB→V20 | 통과 | 각각 1개·20개 migration 실행과 재실행 0개, generated column·4열 unique index 확인 |
| 실기기 실행 준비 | 통과 | Samsung Galaxy S24+ 연결, Samsung Health·Fit3 플러그인 확인, 최신 debug APK 설치, `adb reverse tcp:8080` 설정, MySQL 8.4와 Backend V20 기동 |
| 실기기 SDK 권한·조회 | 통과 | Samsung Health Data SDK 상태 사용 가능, 운동 읽기 권한 허용, 최근 7일 조회 성공. 2026-09-10 조회에서 가장 최근 반환 기록은 2026-09-05 `PULL_UPS`, 출처 앱 `com.sec.android.app.shealth` |
| 실제 Fit3 단일 운동 시연 | 통과 | Fit3 `BENCH_PRESS` 16:42~16:43과 FitMate 헬스 세션 16:41~16:49를 연결. 단일 구간·실제 활동 94초·10.00 kcal·평균 심박 103.00 bpm 저장·표시 |
| 앱 재실행·동일 PUT 재시도 | 통과 | 앱 강제 종료·재실행 후 기록 목록에서 같은 요약 복원. 동일 실제 payload 재시도 전후 버전 0·`savedAt` 동일 |
| Samsung Health 미사용 실기기 회귀 | 통과 | 별도 헬스 세션에서 스위치 미선택, 1세트 저장·완료 후 `Samsung Health 미사용` 기본 리포트 표시 |
| 홈 운동 일지 재진입 | 통과 | 실기기에서 운동 일지 카드가 커뮤니티로 잘못 이동하는 라우팅을 수정하고, 재빌드·재설치 후 서버 운동 기록 목록과 상세 진입 확인 |

## 첫 완료 기준 대조

| 완료 기준 | 현재 증거 | 판정 |
|---|---|---|
| Fit3와 FitMate에서 같은 운동 시작·종료 | Fit3 벤치프레스 16:42~16:43, FitMate 헬스 세션 16:41~16:49로 시작·종료 오차와 겹침 확인 | 완료 |
| Fit3 기록의 Samsung Health 동기화 | 실기기 SDK에서 `BENCH_PRESS`, 단일 구간, 수정 시각과 측정 지표 조회 | 완료 |
| 새로고침 후 종류·시각 일치 후보 확인 | 완료 리포트에서 `BENCH_PRESS · 09-10 16:42 · 1분` 후보 노출 | 완료 |
| 확인한 요약 저장과 리포트 표시 | 확인 후 94초·10.00 kcal·평균 심박 103.00 bpm, 저장 버전 0 표시 | 완료 |
| 기록 재진입과 동일 PUT 중복 방지 | 앱 강제 종료·재실행 후 같은 요약 복원, 동일 PUT 재시도 전후 버전 0·`savedAt` 동일 | 완료 |
| Samsung Health 미사용 기본 기록 | 별도 58초·1세트 세션에서 `Samsung Health 미사용`과 직접 기록 기본 리포트 표시 | 완료 |

## 실기기 완료 절차

1. 발표용 휴대전화에서 Samsung Health 개발 모드를 켜고 FitMate 운동 읽기 권한을 허용한다.
2. Fit3에서 벤치프레스를 한 번 시작·종료하고 진단 화면에서 `BENCH_PRESS`, 단일 세션 여부, 출처 앱, 수정 시각과 제공 지표를 확인한다.
3. 같은 시간대에 서버 루틴 헬스 세션을 시작하고 완료 세트를 하나 이상 저장한 뒤 완료한다.
4. 리포트에서 Samsung Health 운동을 불러와 후보를 명시적으로 선택·저장한다.
5. 기록 목록에서 같은 세션을 다시 열어 저장 요약이 유지되는지 확인하고 같은 내용 PUT 재시도에서 행·버전이 늘지 않는지 확인한다.
6. 권한 거부·기록 없음·API 실패에서도 기본 리포트가 계속 표시되는지 확인한다.

## 미확인 코드 경고 원인

- **확인된 사실:** 2026-09-10 실기기에서 Fit3 벤치프레스는 `BENCH_PRESS`, 단일 `ExerciseSession`으로 조회됐다. 따라서 복합 운동이어서 제외된 경우는 아니다.
- **작업 중 관찰:** 커밋 전 구현의 `HEALTH` 허용값은 `WEIGHT_MACHINE`이었다. 이 중간 상태는 별도 커밋으로 남지 않아 현재 Git 이력만으로 사후 재검증할 수 없다.
- **추정 결론:** 당시 UI가 미허용 코드와 복합 운동을 같은 경고로 묶었고 실제 기록은 단일 세션이었으므로, `BENCH_PRESS`가 당시 허용 목록에 없었던 것이 경고 원인으로 가장 타당하다.
- **별도 원인:** 운동 읽기 권한 미허용은 SDK 조회 자체가 되지 않았던 원인이다. 권한 허용 후 실제 코드를 조회했고, Android와 Backend 허용값을 `BENCH_PRESS`로 맞춘 뒤 연결에 성공했다.
- **현재 상태:** 앱은 제외된 실제 운동 코드와 복합 운동 건수를 구분해 간단히 표시한다.
- **후속 매핑 원칙:** 미확인 코드와 `customTitle`을 FitMate 현재 세션·최근 운동 이름에 대조하는 방식은 [구현 계획의 추천 매핑 방식](../02-technical/galaxy-fit3-발표용-연동-구현-계획.md#추천-매핑-방식)을 따른다. 이름 일치만으로 자동 연결하거나 서버 허용 목록을 자동 확장하지 않는다.

## 확정 사항

- 2026-09-10 발표용 Fit3에서 새로 기록한 벤치프레스가 실기기 SDK에서 `BENCH_PRESS`, 단일 구간, 16:42~16:43, 실제 활동 94초로 반환되는 것을 확인했다. 따라서 Android `WorkoutMatchEngine`과 Backend `ExternalWorkoutService`의 첫 헬스 허용 값을 `BENCH_PRESS`로 함께 확정했다. 출처 앱은 `com.sec.android.app.shealth`로 반환되므로 Fit3 여부를 출처 앱 값만으로 판정하지 않는다.
- 연결된 단말은 Samsung Galaxy S24+이고 Samsung Health와 Fit3 플러그인이 설치되어 있다. 실기기 시연에서는 운동 읽기 권한을 허용한 뒤 Fit3 벤치프레스가 `BENCH_PRESS`로 조회·연결되는 것을 확인했다.

## 2026-09-12 발표용 보완 구현 검증

이번 검증의 기능 범위는 헬스 Android 보완에 한정한다. 최신 `origin/main` rebase 후 Android 전체 단위 테스트와 debug 빌드를 다시 실행했고, PR 전 회귀 확인으로 Backend 테스트도 실행했다. 러닝 전용 변경·추가 테스트, DB 환경변수를 사용한 MySQL 통합 검증, 인바디 전용 검증과 실기기 재시연은 수행하지 않았다. 2026-09-10의 실제 `BENCH_PRESS` 저장·재조회 결과는 기존 증거로 유지하되 이번 실행 결과로 중복 기록하지 않는다.

| 검증 | 결과 | 근거 |
|---|---|---|
| 현재 세션 운동 이름 전달 | 통과 | 헬스 리포트·세션의 `exerciseName`을 중복 제거해 외부 운동 영역에 전달하고 화면에 표시 |
| 미지원 코드와 이름 일치 | 통과 | `OTHER / 벤치 프레스`가 현재 세션 `벤치프레스`와 보수적 정규화 후 일치해도 저장 후보는 0건이고 참고 정보만 1건인 단위 테스트 |
| 확인된 코드의 이름 비의존성 | 통과 | `customTitle`이 없고 현재 세션 이름이 달라도 `BENCH_PRESS`가 기존 종류·단일 구간·시간 조건을 만족하면 후보 유지 |
| 시간 불일치·복합 운동 분리 | 통과 | 지원 코드의 ±10분 초과와 여러 `ExerciseSession`을 각각 다른 제외 사유로 집계하는 단위 테스트 |
| 원인별 다음 행동 | 코드·빌드 통과 | 권한 허용, 기록 확인·새로고침, 같은 시간대 기록 준비, 지원 코드 사용, 단일 운동 재기록 안내를 분리. 실기기 화면 확인은 미실행 |
| Android 단위 테스트 | 통과 | 최신 `origin/main` rebase 후 `:app:testDebugUnitTest`, 68개 실행·실패 0·오류 0·건너뜀 0 |
| Android debug 빌드 | 통과 | `:app:assembleDebug`, JDK 17과 기존 로컬 Android SDK 사용 |
| Backend 회귀 테스트 | 통과 | `test --no-daemon --max-workers=2`, 99개 중 52개 통과·47개 건너뜀·실패·오류 0개. DB 통합 검증 완료 근거로 사용하지 않음 |
| 기존 API·테이블 재사용 | 변경 없음 | Android `ServerHealthApi`, Backend 외부 요약 GET/PUT, V20 migration을 수정하지 않음 |
| 인바디·러닝 제외 | 확인 | 인바디 및 러닝 전용 코드·테스트를 수정하지 않음. Android 전체 명령의 기존 러닝 회귀 테스트 외에 DB·인바디 전용·실기기 검증은 미실행 |

## 후속 정리

- 발표 후 개발 DB에서 전용 시연 계정 `fit3demo1636`과 2026-09-10에 생성한 헬스 세션 2건·외부 요약을 수동 정리한다.
