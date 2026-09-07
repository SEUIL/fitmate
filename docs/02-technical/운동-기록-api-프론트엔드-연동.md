# 운동 기록 API 프론트엔드 연동 가이드

- 작성일: 2026-09-07
- 최종 수정일: 2026-09-07
- 버전: v0.1
- 상태: 검토 중
- 대상: Android Client, Kotlin Spring Boot Backend

## 1. 사용자 흐름

`운동 선택과 목표 입력 -> 세션 생성 -> 운동 시작 -> 세트별 무게·횟수 저장 -> 프론트 휴식 타이머 -> 운동 완료 -> 다음 운동에서 직전 기록 비교`

모든 API는 `Authorization: Bearer {accessToken}`이 필요하다. 휴식 타이머는 프론트 로컬 상태이며 서버에 저장하지 않는다.

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

세션 상태 변경은 같은 형식으로 다음 경로를 사용한다.

- `POST /api/v1/workout-sessions/{sessionId}/pause`
- `POST /api/v1/workout-sessions/{sessionId}/resume`
- `POST /api/v1/workout-sessions/{sessionId}/complete`

운동 완료에는 완료된 세트가 하나 이상 필요하다.

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
