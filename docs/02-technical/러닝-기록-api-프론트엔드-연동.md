# 러닝 기록 API 프론트엔드 연동 가이드

- 작성일: 2026-09-07
- 최종 수정일: 2026-09-07
- 버전: v0.1
- 상태: 검토 중
- 대상: Android Client, Kotlin Spring Boot Backend

## 1. 구현 범위

기능 명세서의 러닝 종료 기록과 Android `RunningFlow.kt`의 `RunResult`를 기준으로 다음 흐름을 지원한다.

`러닝 설정 -> GPS 기반 거리·시간 측정 -> 종료 후 RPE·컨디션·메모 입력 -> 서버 저장 -> 러닝 기록 목록/상세 조회`

모든 API는 `Authorization: Bearer {accessToken}`이 필요하다.

| 화면 기능 | Method | Path |
|---|---|---|
| 종료한 러닝 저장 | `POST` | `/api/v1/running-records` |
| 내 러닝 기록 목록 | `GET` | `/api/v1/running-records?cursor=0&size=20` |
| 내 러닝 기록 상세 | `GET` | `/api/v1/running-records/{recordId}` |
| 전체 완료 운동 목록 | `GET` | `/api/v1/workout-sessions?status=COMPLETED&cursor=0&size=20` |

러닝 저장 시 공통 운동 세션도 `sportCode: RUNNING`, `status: COMPLETED`로 생성된다. 따라서 전체 운동 목록에서는 헬스와 러닝을 `sportCode`로 구분할 수 있다.

## 2. Android 데이터 매핑

| Android 값 | API 필드 | 변환 |
|---|---|---|
| `goal` | `runType` | 자유=`FREE`, 거리=`DISTANCE_TARGET`, 시간=`TIME_TARGET`, 페이스=`PACE_TARGET`, 인터벌=`INTERVAL` |
| `meters` | `distanceMeters` | 그대로 전송 |
| `seconds` | `activeDurationSeconds` | 그대로 전송 |
| `completedAt` | `completedAt` | epoch millisecond를 UTC ISO-8601로 변환 |
| `targetDistance` | `targetDistanceMeters` | km × 1000 |
| `targetMinutes` | `targetDurationSeconds` | 분 × 60 |
| `targetPace` | `targetPaceSecondsPerKm` | 초/km 그대로 전송 |
| `intervalMinutes` | `intervalSeconds` | 분 × 60 |
| `autoPause` | `autoPauseEnabled` | 그대로 전송 |
| `rpe` | `rpe` | 1~10 |
| `condition` | `conditionScore` | 1~5 |
| `memo` | `memo` | 최대 500자 |
| `RunLap.number` | `laps[].lapNo` | 그대로 전송 |
| `RunLap.meters` | `laps[].distanceMeters` | 그대로 전송 |
| `RunLap.seconds` | `laps[].durationSeconds` | 그대로 전송 |

`startedAt`을 따로 보관하지 않았다면 생략할 수 있다. 서버가 `completedAt - activeDurationSeconds`로 계산한다. 일시정지 시간까지 정확히 표현하려면 프론트에서 실제 `startedAt`을 함께 전송한다.

현재 화면의 `나만 보기`와 `요약만 공유`는 모두 경로 자체를 공개하지 않으므로 `routeVisibility`는 `PRIVATE`으로 보낸다. 이후 경로 공개 UI를 추가할 때만 `PUBLIC`을 사용한다.

## 3. 저장 요청

`POST /api/v1/running-records`

```json
{
  "runType": "DISTANCE_TARGET",
  "targetDistanceMeters": 5000,
  "routeVisibility": "PRIVATE",
  "autoPauseEnabled": true,
  "startedAt": "2026-09-07T11:28:00Z",
  "completedAt": "2026-09-07T12:00:00Z",
  "activeDurationSeconds": 1800,
  "distanceMeters": 5000,
  "rpe": 6,
  "conditionScore": 4,
  "memo": "한강 러닝",
  "laps": [
    { "lapNo": 1, "distanceMeters": 1000, "durationSeconds": 370 },
    { "lapNo": 2, "distanceMeters": 1000, "durationSeconds": 365 }
  ],
  "routePoints": [
    {
      "latitude": 37.525,
      "longitude": 127.028,
      "recordedAt": "2026-09-07T11:28:00Z",
      "altitudeMeters": 18.2
    }
  ]
}
```

목표별 필수값은 다음과 같다.

| `runType` | 필수 목표값 |
|---|---|
| `FREE` | 없음 |
| `DISTANCE_TARGET` | `targetDistanceMeters` |
| `TIME_TARGET` | `targetDurationSeconds` |
| `PACE_TARGET` | `targetDistanceMeters`, `targetPaceSecondsPerKm` |
| `INTERVAL` | `targetDurationSeconds`, `intervalSeconds` |

`caloriesKcal`, `cadenceSpm`, `strideLengthCm`, `elevationGainMeters`도 수신 데이터가 있을 때 선택 전송할 수 있다.

현재 `RunTrackingService`는 GPS 좌표 배열을 보관하지 않으므로 `routePoints`를 생략하거나 빈 배열로 보낸다. 백엔드 계약은 좌표 저장을 지원하지만 지도 경로를 표시하려면 프론트에서 위치 콜백의 위도·경도·측정 시각을 누적하도록 추가 구현해야 한다.

## 4. 저장 응답

성공 상태는 `201 Created`다. 평균 페이스, 최고 랩 페이스, 평균 속도와 목표 달성 여부는 서버가 계산한다.

```json
{
  "data": {
    "id": "01991f3a-2c80-7000-8000-000000000001",
    "runType": "DISTANCE_TARGET",
    "startedAt": "2026-09-07T11:28:00Z",
    "completedAt": "2026-09-07T12:00:00Z",
    "elapsedDurationSeconds": 1920,
    "activeDurationSeconds": 1800,
    "distanceMeters": 5000,
    "averagePaceSecondsPerKm": 360,
    "bestPaceSecondsPerKm": 365,
    "averageSpeedKmh": 10.00,
    "goalAchieved": true,
    "routePoints": [
      { "latitude": 37.525, "longitude": 127.028, "recordedAt": "2026-09-07T11:28:00Z", "altitudeMeters": 18.2 }
    ],
    "laps": [
      { "lapNo": 1, "distanceMeters": 1000, "durationSeconds": 370, "averagePaceSecondsPerKm": 370 }
    ],
    "version": 0
  }
}
```

GPS 신호 문제로 거리가 0이어도 시간과 메모를 저장할 수 있다. 이 경우 `averagePaceSecondsPerKm`, `bestPaceSecondsPerKm`, `averageSpeedKmh`는 `null`이고 랩은 빈 배열이어야 한다.

## 5. 목록과 상세 조회

```http
GET /api/v1/running-records?cursor=0&size=20
GET /api/v1/running-records/{recordId}
```

목록은 최신 완료순이며 `cursor`는 0부터 시작하는 페이지 번호, `size`는 1~100이다. 상세 응답은 저장 응답과 같은 형태로 전체 랩과 경로 좌표를 포함한다. 다른 사용자의 기록 ID로 조회하면 존재 여부를 숨기기 위해 `404 RESOURCE_NOT_FOUND`를 반환한다.

## 6. 검증 규칙

- 시작 시각은 완료 시각보다 앞서야 한다.
- 활동 시간은 시작부터 완료까지의 전체 시간보다 길 수 없다.
- 랩 번호는 1부터 빠짐없이 순서대로 보내며 랩 시간·거리 합계는 전체 기록을 넘을 수 없다.
- 경로 좌표 시각은 시작과 완료 사이이며 측정 시각 순서대로 보내야 한다.
- 잘못된 목표값과 합계는 `400 VALIDATION_ERROR`, 인증 누락은 `401`이다.
