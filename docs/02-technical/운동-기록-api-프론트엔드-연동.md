# 운동 기록 API 프론트엔드 연동 가이드

- 작성일: 2026-09-07
- 최종 수정일: 2026-09-07
- 버전: v0.2
- 상태: 검토 중
- 대상: Android Client, Kotlin Spring Boot Backend

## 1. 사용자 흐름

`운동 선택과 목표 입력 -> 세션 생성 -> 운동 시작 -> 세트별 무게·횟수 저장 -> 프론트 휴식 타이머 -> 운동 완료 -> 다음 운동에서 직전 기록 비교`

모든 API는 `Authorization: Bearer {accessToken}`이 필요하다. 휴식 타이머는 프론트 로컬 상태이며 서버에 저장하지 않는다.

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### 전체 API 목록

| 화면 기능 | Method | Path |
|---|---|---|
| 직접 선택 또는 루틴으로 운동 생성 | `POST` | `/api/v1/workout-sessions` |
| 내 운동 기록 목록 | `GET` | `/api/v1/workout-sessions?status={status}&cursor=0&size=20` |
| 진행 중 또는 완료 운동 상세 | `GET` | `/api/v1/workout-sessions/{sessionId}` |
| 운동 시작 | `POST` | `/api/v1/workout-sessions/{sessionId}/start` |
| 일시정지 | `POST` | `/api/v1/workout-sessions/{sessionId}/pause` |
| 재시작 | `POST` | `/api/v1/workout-sessions/{sessionId}/resume` |
| 운동 완료 | `POST` | `/api/v1/workout-sessions/{sessionId}/complete` |
| 운동 취소 | `POST` | `/api/v1/workout-sessions/{sessionId}/cancel` |
| 세트 입력 또는 수정 | `PUT` | `/api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}/sets/{setNo}` |
| 같은 운동의 직전 기록 | `GET` | `/api/v1/exercises/{exerciseId}/previous-record?beforeSessionId={sessionId}` |
| 완료 운동 리포트 | `GET` | `/api/v1/workout-sessions/{sessionId}/report` |

## 2. 직접 운동 생성

`POST /api/v1/workout-sessions`

```json
{
  "exercises": [
    {
      "exerciseId": "30000000-0000-0000-0000-000000000001",
      "sequenceNo": 1,
      "targetSets": 3,
      "targetWeightKg": 20,
      "targetRepetitions": 10
    }
  ]
}
```

성공 응답은 `201 Created`다. 서버가 `targetSets`만큼 미완료 세트를 만들어 반환하므로 프론트는 각 세트 입력 행을 바로 그릴 수 있다. 저장 루틴으로 생성하려면 `exercises` 대신 `routineId`만 보낸다.

```json
{
  "routineId": "40000000-0000-0000-0000-000000000001"
}
```

`routineId`와 `exercises`를 동시에 보내거나 둘 다 생략하면 `400 VALIDATION_ERROR`가 반환된다.

생성 응답에서 프론트가 보관할 값은 세션 `id`, 세션 `version`, 각 운동의 `id`, 각 세트의 `setNo`와 `version`이다.

루틴으로 생성하면 오늘 수행할 분할의 운동만 들어오며 다음 필드도 반환된다.

```json
{
  "routineWorkoutDayNumber": 3,
  "routineSplitDayNo": 1,
  "routineSplitCount": 2,
  "focusBodyParts": ["CHEST", "BACK", "SHOULDERS"]
}
```

프론트는 이를 `3일차 · 1/2분할 · 가슴/등/어깨`처럼 표시할 수 있다. 누적 운동일은 같은 루틴을 완료할 때만 증가하고, 분할 순번은 전체 분할 수 안에서 순환한다. 취소한 세션은 다음 순번에 반영되지 않는다. 직접 운동을 선택한 세션에는 이 세 분할 필드가 `null`이고 `focusBodyParts`만 선택한 운동 기준으로 반환된다.

## 3. 운동 시작과 세트 기록

`POST /api/v1/workout-sessions/{sessionId}/start`

```json
{
  "version": 0
}
```

세트 하나를 완료할 때 다음 요청을 보낸다.

`PUT /api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}/sets/{setNo}`

```json
{
  "weightKg": 22.5,
  "repetitions": 12,
  "completed": true,
  "version": 0
}
```

완료 세트에는 `weightKg`와 `repetitions`가 필요하다. 성공 응답을 받은 뒤 프론트가 자체 휴식 타이머를 시작하고, 시간이 끝나면 다음 세트 입력을 활성화한다. 응답의 새 세트 `version`을 다음 수정 요청에 사용한다.

세트 요청에는 필요하면 `durationSeconds`, `rpe`, `memo`를 함께 보낼 수 있다. 이미 존재하는 세트를 수정할 때 `version`이 서버 값과 다르면 `409 VERSION_CONFLICT`가 반환된다.

세션 상태 변경은 같은 형식으로 다음 경로를 사용한다.

- `POST /api/v1/workout-sessions/{sessionId}/pause`
- `POST /api/v1/workout-sessions/{sessionId}/resume`
- `POST /api/v1/workout-sessions/{sessionId}/complete`
- `POST /api/v1/workout-sessions/{sessionId}/cancel`

운동 완료에는 완료된 세트가 하나 이상 필요하다.

취소 요청은 사유를 선택적으로 보낼 수 있다.

```json
{
  "version": 1,
  "cancelReason": "사용자 취소"
}
```

상태 전이는 `READY → IN_PROGRESS → PAUSED → IN_PROGRESS → COMPLETED` 순서다. `READY`, `IN_PROGRESS`, `PAUSED`에서는 취소할 수 있다. 상태 변경 성공 응답의 새 세션 `version`을 다음 요청에 사용한다.

## 4. 과거 기록 비교

새 세션 생성 응답의 각 `exercises[]`에는 같은 운동의 직전 완료 기록인 `previousRecord`가 포함된다.

```json
{
  "previousRecord": {
    "workoutSessionId": "7c6c394e-eabb-4e7b-9aaa-5065df3d80b9",
    "performedAt": "2026-09-06T10:30:00Z",
    "sets": [
      {
        "setNo": 1,
        "weightKg": 22.5,
        "repetitions": 12,
        "completed": true,
        "version": 1
      }
    ]
  }
}
```

별도 조회가 필요하면 다음 API를 사용한다.

`GET /api/v1/exercises/{exerciseId}/previous-record?beforeSessionId={currentSessionId}`

이전 완료 기록이 없으면 `data`가 `null`이다. 비교 화면은 현재 입력값과 `previousRecord.sets`의 같은 `setNo`를 나란히 표시하면 된다.

## 5. 운동 기록 목록과 완료 리포트

운동 기록 화면은 다음 API를 최신순으로 조회한다.

`GET /api/v1/workout-sessions?status=COMPLETED&cursor=0&size=20`

`status`는 생략하거나 `READY`, `IN_PROGRESS`, `PAUSED`, `COMPLETED`, `CANCELED`를 사용할 수 있다. 응답의 `hasNext=true`이면 `nextCursor`를 다음 요청의 `cursor`로 보낸다. 진행 중 운동 이어하기는 `IN_PROGRESS`와 `PAUSED` 목록을 사용한다.

```json
{
  "data": {
    "items": [
      {
        "id": "7c6c394e-eabb-4e7b-9aaa-5065df3d80b9",
        "routineName": "초급 전신 루틴",
        "routineWorkoutDayNumber": 3,
        "routineSplitDayNo": 1,
        "routineSplitCount": 2,
        "status": "COMPLETED",
        "startedAt": "2026-09-07T10:00:00Z",
        "endedAt": "2026-09-07T10:30:00Z",
        "exerciseCount": 4,
        "completedSetCount": 12,
        "version": 2
      }
    ],
    "nextCursor": null,
    "hasNext": false
  }
}
```

완료 화면에서는 다음 API로 집계 리포트를 조회한다.

`GET /api/v1/workout-sessions/{sessionId}/report`

리포트는 완료 세트만 사용해 운동 수, 완료 세트 수, 총 반복 수와 총 볼륨을 계산한다. 총 볼륨은 각 세트의 `weightKg × repetitions` 합계다. 운동별로 최대 중량과 직전 완료 운동 대비 볼륨 증감도 반환한다.

완료되지 않은 세션은 리포트를 조회할 수 없으며 `409 INVALID_STATE_TRANSITION`이 반환된다. 다른 사용자의 세션은 `404 RESOURCE_NOT_FOUND`로 숨긴다.

리포트 응답 예시는 다음과 같다.

```json
{
  "data": {
    "workoutSessionId": "7c6c394e-eabb-4e7b-9aaa-5065df3d80b9",
    "reportStatus": "BASIC_READY",
    "durationSeconds": 1800,
    "exerciseCount": 1,
    "completedSetCount": 3,
    "totalRepetitions": 30,
    "totalVolumeKg": 750.0,
    "exercises": [
      {
        "exerciseName": "벤치 프레스",
        "maxWeightKg": 25.0,
        "previousComparison": {
          "previousVolumeKg": 690.0,
          "currentVolumeKg": 750.0,
          "volumeChangeKg": 60.0
        }
      }
    ]
  }
}
```

## 6. 프론트 구현 순서

1. 루틴 시작 화면에서는 생성 응답의 누적 운동일, 분할 순번과 부위를 표시한다. 직접 운동 화면에서는 `exerciseId`, 목표 세트 수, 초기 무게와 횟수를 받아 세션을 생성한다.
2. 생성 응답의 `exercises[].sets[]`로 세트 입력 행을 구성한다.
3. 시작 API 호출 후 각 세트를 `PUT`으로 저장한다.
4. 세트 저장 성공 시에만 프론트 로컬 휴식 타이머를 시작한다.
5. 마지막 세트 저장 후 완료 API를 호출한다.
6. 완료 응답을 받은 뒤 리포트 API를 호출해 결과 화면을 표시한다.
7. 다음 운동 생성 응답의 `previousRecord`를 현재 세트 옆에 비교값으로 표시한다.
