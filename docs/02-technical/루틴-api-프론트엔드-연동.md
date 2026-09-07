# 루틴 API 프론트엔드 연동 가이드

- 작성일: 2026-09-07
- 최종 수정일: 2026-09-07
- 버전: v0.1
- 상태: 검토 중
- 대상: Android Client, Kotlin Spring Boot Backend

## 1. 공통 규칙

모든 루틴 API는 `Authorization: Bearer {accessToken}`이 필요하다. 성공 응답의 실제 데이터는 `data`에 들어간다. UUID는 API에서 일반 UUID 문자열을 사용한다.

## 2. 조회 흐름

| 용도 | Method | Path | 반환 |
|---|---|---|---|
| 사용자 맞춤 추천 하나 | `GET` | `/api/v1/routines/recommended?sportCode=HEALTH` | 상세 1개 |
| 전체 기본 템플릿 | `GET` | `/api/v1/routines/templates` | 템플릿 5개 목록 |
| 사용자가 저장한 루틴 | `GET` | `/api/v1/routines` | 내 루틴 목록 |
| 템플릿 또는 내 루틴 상세 | `GET` | `/api/v1/routines/{routineId}` | 운동 항목 포함 상세 |

추천 API는 한 개만 반환한다. 다른 기본 루틴을 고르는 화면은 `/routines/templates`를 호출한다. 현재 루틴은 헬스만 지원하므로 추천 API에 `RUNNING`을 보내면 `400 VALIDATION_ERROR`가 반환된다.

## 3. 직접 구성 저장

`POST /api/v1/routines`

```json
{
  "name": "나의 전신 루틴",
  "sourceType": "MANUAL",
  "sportCode": "HEALTH",
  "items": [
    {
      "exerciseId": "30000000-0000-0000-0000-000000000001",
      "sequenceNo": 1,
      "targetSets": 3,
      "targetRepsMin": 8,
      "targetRepsMax": 12,
      "targetWeightKg": 20,
      "targetRpe": 7,
      "restSeconds": 60
    }
  ]
}
```

성공하면 `201 Created`와 저장된 루틴 상세를 반환한다. `items`는 한 개 이상이어야 하고 `sequenceNo`는 루틴 안에서 중복될 수 없다.

## 4. 템플릿 복사

`POST /api/v1/routines`

```json
{
  "name": "내 초급 전신 루틴",
  "sourceType": "USER_COPY",
  "sourceRoutineId": "40000000-0000-0000-0000-000000000001"
}
```

서버가 템플릿 운동 항목을 새 사용자 루틴으로 복제하고 `201 Created`를 반환한다. 이 요청에는 `sportCode`와 `items`를 보내지 않는다.

## 5. 수정과 삭제

`PATCH /api/v1/routines/{routineId}`

```json
{
  "version": 0,
  "name": "수정한 루틴",
  "items": [
    {
      "exerciseId": "30000000-0000-0000-0000-000000000003",
      "sequenceNo": 1,
      "targetSets": 4
    }
  ]
}
```

`name`과 `items` 중 하나 이상을 보낸다. `items`를 보내면 기존 운동 항목 전체가 요청 목록으로 교체된다. 성공 응답의 새 `version`을 로컬 상태에 저장한다. 오래된 버전을 보내면 `409 VERSION_CONFLICT`가 반환되므로 최신 상세를 다시 조회한다.

`DELETE /api/v1/routines/{routineId}?version={currentVersion}`

삭제 성공은 `204 No Content`다. 시스템 템플릿과 다른 사용자의 루틴은 수정하거나 삭제할 수 없다.

## 6. 화면 연결 권장 순서

1. 헬스 진입 시 추천 1개, 템플릿 목록, 내 루틴 목록을 조회한다.
2. 템플릿 선택은 상세를 조회한 뒤 그대로 시작하거나 사용자 루틴으로 복사한다.
3. 직접 구성은 운동 사전에서 항목을 고른 뒤 `MANUAL`로 저장한다.
4. 편집 화면은 상세 응답의 `version`을 보관하고 수정·삭제 요청에 사용한다.
