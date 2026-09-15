# 다종목 운동 DB 구현 계획

- 작성일: 2026-09-14
- 최종 수정일: 2026-09-14
- 버전: v1.1
- 상태: 확정
- 대상: MySQL, Flyway
- 구현 상태: 계획만 작성. migration 생성 및 DB 적용 전

## 1. 목적과 연결 문서

기존 공통 운동 세션에 신규 네 종목의 완료 기록을 연결하고, 네 종목이 함께 사용하는 상세 테이블 하나를 추가한다. 승인 근거는 [결정·회의 로그](../00-project/결정-회의-로그.md)의 `2026-09-14 다종목 개인 운동 시연 범위 확정`이다.

- [백엔드 계획](./다종목-운동-백엔드-구현-계획.md): 필드 의미·허용 범위·오류·중복 저장 계약의 기준.
- [프론트 계획](./다종목-운동-프론트-구현-계획.md): 임시 기록과 서버 저장 시점.
- [시연 계획](../03-qa/다종목-운동-시연-계획.md): 개발 환경 예시 데이터와 검증 범위.

v1.1은 결정 로그의 `2026-09-14 백엔드·DB 검토 보완 확정`을 반영한다. DB 담당의 완료는 이행·저장 정합성 검증과 백엔드 연결까지이며, 프론트·시연은 별도 담당자의 범위다.

## 2. 현재 스키마에서 필요한 변경

| 대상 | 현재 상태 | 계획 |
|---|---|---|
| `fitness_sports` | V2에 네 종목 코드와 ID 존재, 네 종목은 비활성 시드 | 기존 행을 활성화하며 ID 보존 |
| `fitness_workout_sessions.sport_code` | V8 CHECK가 `HEALTH`, `RUNNING`만 허용 | 기존 두 종목 + 신규 네 종목 허용으로 변경 |
| `fitness_workout_sessions` | 사용자·종목·시작·종료·상태 보관 | 신규 완료 세션도 같은 테이블 사용 |
| `fitness_running_records` | 러닝 전용 상세 | 변경 없이 유지 |
| `health_external_workout_summaries` | V20 외부 운동 요약 | 신규 종목 연동 범위에 포함하지 않음 |

현재 병합 기준 저장소는 V1~V26까지 있다. 다종목 migration은 기존 V21과 번호가 충돌하므로 `V27__add_multi_sport_records.sql`로 활성화·CHECK 변경·상세 테이블 생성을 묶는다. 기존 migration을 수정하거나 DB를 초기화하지 않는다.

종목 활성화는 공통 `/sports` 조회에 영향을 준다. 가입·프로필·메이트 모집의 기존 종목 선택에 네 종목이 노출될 수 있다. 기존 관심 운동 데이터의 자동 변환이나 신규 메이트 운동 세션 연결은 수행하지 않는다.

## 3. 신규 테이블

테이블 이름은 `fitness_sport_records`로 계획한다. 공통 세션과 1:1 관계이며, nullable 컬럼으로 네 종목의 작은 결과 집합을 표현한다. 종목별 테이블 네 개나 임의 JSON 저장은 추가하지 않는다.

| 컬럼 | 타입·null | 의미 |
|---|---|---|
| `workout_session_id` | BINARY(16), NOT NULL, PK/FK | 공통 세션 ID. API의 `clientRecordId`/`id`와 매핑 |
| `activity_type` | VARCHAR(32), NOT NULL | 종목별 유형 |
| `active_duration_seconds` | INT UNSIGNED, NOT NULL | 일시정지 제외 운동 시간 |
| `place_name` | VARCHAR(100), NULL | 장소 |
| `memo` | VARCHAR(500), NULL | 공통 메모 |
| `game_count`, `win_count` | SMALLINT UNSIGNED, NULL | 배드민턴 게임·승리 수 |
| `attempt_count`, `completion_count` | SMALLINT UNSIGNED, NULL | 클라이밍 시도·완등 수 |
| `difficulty_memo` | VARCHAR(100), NULL | 클라이밍 난이도 자유 메모 |
| `course_name` | VARCHAR(100), NULL | 등산 코스명 |
| `distance_meters` | INT UNSIGNED, NULL | 수동 입력 거리 |
| `shot_count` | SMALLINT UNSIGNED, NULL | 골프 연습 타격 수 |
| `hole_count` | TINYINT UNSIGNED, NULL | 골프 진행 홀 수 |
| `stroke_count` | SMALLINT UNSIGNED, NULL | 골프 총 타수 |
| `created_at`, `updated_at` | DATETIME(3), NOT NULL | 기존 엔티티 시간 규칙과 일치 |

사용자 ID·종목 코드·시작/종료 시각은 부모 세션에서 읽고 중복 저장하지 않는다. 상세 테이블의 업무 데이터는 생성 후 불변이므로 별도 편집 version은 추가하지 않는다. 부모 세션의 기존 version 컬럼은 유지한다.

## 4. 제약과 정합성

- `workout_session_id`는 `fitness_workout_sessions(id)`를 참조한다. 기존 UUID 변환 규칙과 `UuidBinaryConverter`를 사용한다.
- PK가 세션당 상세 하나와 동일 `clientRecordId` 중복 삽입을 차단한다. 동일 요청인지의 판단은 백엔드가 소유자와 정규화 내용을 비교한다.
- 신규 종목 세션은 `status=COMPLETED`, 루틴 관련 필드는 null이다. 세트·세션 운동 행은 만들지 않는다.
- CHECK로 활동 시간 > 0, 횟수 0~9999, 홀 1~18, 타수 1~9999, 거리 0~1,000,000을 검증한다. 선택 값은 null을 허용한다.
- 승리 수 입력 시 게임 수 필수 및 승리 ≤ 게임, 완등 수 입력 시 시도 수 필수 및 완등 ≤ 시도, 타수 입력 시 홀 수 필수를 명시한다.
- SQL CHECK는 null이 있으면 UNKNOWN으로 통과할 수 있으므로, 의존 필드 검증에 `IS NOT NULL` 조건을 포함한다.
- 유형은 백엔드 계획의 enum 집합만 허용한다. 부모의 종목과 상세 유형 매칭, 타 종목 필드 금지, 활동 시간 ≤ 시작~종료 경과 시간은 트랜잭션 서비스에서 검증한다. 별도 트리거는 만들지 않는다.
- 공통 세션과 상세는 한 트랜잭션으로 저장하고 상세 실패 시 부모도 롤백한다.

## 5. Migration 순서와 인덱스

1. 새 파일 이름·번호 중복과 현행 공통 세션 CHECK 이름을 확인한다.
2. `chk_fitness_workout_sessions_sport`를 여섯 종목 허용 조건으로 교체한다.
3. 신규 상세 테이블과 PK/FK/CHECK를 생성한다.
4. 기존 네 종목의 `fitness_sports.is_active`를 true로 변경하고 `updated_at`을 갱신한다.
5. 기존 `ix_fitness_workout_sessions_user_sport_started`와 목록 SQL을 확인한다. 발표 규모에서는 우선 기존 인덱스를 사용하고 대규모 성능 개선은 범위에 넣지 않는다.

MySQL DDL 전체가 하나의 트랜잭션으로 롤백된다고 가정하지 않는다. 빈 임시 DB와 V20 적용 임시 DB에서 먼저 검증하고, 부분 적용 실패 시 실제 스키마와 Flyway 이력을 확인한 후 복구한다. 자동 drop·reset·기존 migration checksum 변경을 복구 방식으로 사용하지 않는다.

## 6. 기존 데이터와 시연 데이터

- 헬스·러닝·루틴·메이트·외부 요약 데이터와 기존 종목 UUID를 보존한다.
- 이름이 같은 `customSports`는 사용자별 관심 운동이다. 활성 운영 종목으로 자동 병합하거나 삭제하지 않는다.
- 사용자·기록 예시는 migration에 넣지 않는다. 기능 구현 후 발표용 계정으로 앱/API를 통해 생성해 재조회한다.
- 시연 준비는 계정별 필요한 기록만 추가한다. 일괄 초기화나 데이터 삭제 작업은 이번 계획의 실행 범위가 아니다.

## 7. 완료 판단과 한정 검증

| 검증 대상 | 후속 구현 때 확인할 결과 |
|---|---|
| V20 → 신규 migration | 기존 기록 보존, CHECK 확장, 새 테이블 생성 |
| 빈 임시 DB → 최신 | 모든 migration 순차 적용 성공 |
| 종목 사전 | 여섯 대상 종목 활성, 관련 없는 종목의 활성 상태 유지 |
| 신규 저장 | 종목별 부모 1개·상세 1개, 세트 행 없음 |
| 중복·실패 | 동일 ID 중복 불가, 고아 세션/상세 없음 |
| 제약 | 범위 위반과 의존 필드 누락 거절 |

### 7.1 기존 검사 보존과 신규 이행 검사

- `ExternalWorkoutMigrationIntegrationTest`는 현재 최신 target을 지정하지 않고 V19 이후 1개·빈 DB 20개 적용을 단정한다. 해당 두 Flyway 호출에 target `20`을 명시해 기존 V19→V20·빈 DB→V20 검사의 의미를 보존한다. 신규 migration 때문에 단순히 기대 개수만 늘리지 않는다.
- `MateMigrationUpgradeIntegrationTest`는 이미 target `19`를 사용하므로 기존 범위를 유지한다.
- 신규 `MultiSportMigrationIntegrationTest`를 추가해 V20→V27, 빈 DB→V27, 재적용 시 0개를 검증한다. 실제 구현 시 번호가 바뀌면 신규 검사의 target과 문서를 함께 맞춘다.
- 이행 검증에 기존 헬스·러닝 부모/상세 기록의 식별자와 주요 값 보존을 포함한다. 종목 행 수만 같다고 기존 운동 기록 보존을 통과 처리하지 않는다.
- 활성 종목 코드와 기존 UUID가 유지되는지 검사한다. display_order를 바꾸지 않으므로 응답 순서는 `HEALTH`, `RUNNING`, `BADMINTON`, `HIKING`, `CLIMBING`, `GOLF`다.
- `RoutineQueryApiIntegrationTest`의 카탈로그 응답에 있는 코드 목록과 UUID 목록의 기존 두 종목 단정을 여섯 종목으로 갱신한다. 가입·프로필·메이트의 종목 관련 한정 검증은 백엔드 계획 7절을 따른다.

신규 이행 검사·직접 수정한 외부 운동 이행 검사·카탈로그 관련 검사와 신규 기록 DB 통합 검증만 지정한다. 전체 테스트를 실행하지 않으며, 기존 메이트 이행 검사를 전체 실행의 근거로 확대하지 않는다. DB 검증용 임시 환경이 없으면 미실행으로 기록한다.

이번 문서 개정에서는 migration 파일 생성, DB 접속·적용·초기화와 테스트를 실행하지 않았다.
