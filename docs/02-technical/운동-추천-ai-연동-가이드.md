# 운동 추천 AI 연동 가이드

- 작성일: 2026-09-14
- 최종 수정일: 2026-09-14
- 버전: v0.6
- 상태: 검토 중
- 대상: Kotlin Spring Boot Backend, Android Client

## 1. 목적과 현재 상태

이 문서는 PR #56의 기존 설명 전용 API를 다룬다. 새 Android 맞춤 추천은 [운동 추천 확장 API](./운동-추천-확장-api-계약.md)의 초안 생성·교체·저장 흐름을 사용한다. 아래 기존 구성·응답 설명을 새 확장 기능의 완료 범위와 혼동하지 않는다.

기존 API도 `externalAiConsent: true`, `consentVersion: "workout-plan-demo-v1"` 및 서버 `plan-generation-enabled` 활성화가 있어야 설명용 외부 AI를 호출한다. 기본값은 비활성화이며, 동의 없이 호출하면 기본 추천과 `aiSummary: null`을 제공한다. 공개 서비스 정책은 미확정이다.

맞춤 운동 추천은 사용자의 목표, 헬스 숙련도, 주 운동 횟수, 1회 운동 시간, 사용 가능한 기구와 선택한 인바디 기록을 바탕으로 생성한다.

추천 운동·세트·반복·휴식은 백엔드 규칙과 등록된 운동 카탈로그로 확정한다. OpenAI Luna는 확정된 추천의 **설명 작성 용도**로만 호출한다.

AI는 운동 항목, 중량, 세트, 반복 수, 제한사항을 바꾸지 않는다. 서버가 검증한 결과에 대한 짧은 설명만 반환한다.

## 2. 현재 맞춤 추천 API

모든 요청에는 `Authorization: Bearer {accessToken}`이 필요하다.

```http
POST /api/v1/workout-recommendations
Content-Type: application/json
```

```json
{
  "daysPerWeek": 3,
  "sessionMinutes": 45,
  "equipmentTypes": ["MACHINE", "CABLE"],
  "inbodyRecordId": "00000000-0000-0000-0000-000000000000"
}
```

| 필드 | 필수 | 규칙 |
|---|---:|---|
| `daysPerWeek` | 예 | 1~7 |
| `sessionMinutes` | 예 | 20~180분 |
| `equipmentTypes` | 아니오 | 비우면 전체 기구 사용 가능, 지정하면 해당 기구와 맨몸 운동만 사용 |
| `inbodyRecordId` | 아니오 | 로그인한 사용자가 저장한 기록만 선택 가능 |

인바디를 보내지 않아도 추천이 생성된다. 서버는 최신 인바디를 임의로 선택하지 않는다.

기존 응답의 `items`는 `POST /api/v1/routines`의 `items`와 같은 운동 ID·세트·반복·RPE·휴식 구조를 사용한다. 새 Android 맞춤 추천의 저장은 근거·조건 보존과 중복 방지를 위해 초안의 `/save`를 사용한다.

### 숙련도별 기본 템플릿

`FITNESS` 목표에서는 회원이 선택한 헬스 숙련도와 같은 템플릿을 먼저 사용한다.

| 숙련도 | 기본 구성 |
|---|---|
| 처음 시작 | 머신·케이블·맨몸 위주의 전신 루틴, 낮은 세트와 RPE |
| 6개월 미만 | 기초 복합 운동을 포함한 적응형 루틴 |
| 6개월~1년 | 2분할, 복합 운동과 레그 컬·레터럴 레이즈·푸시다운 등 보조 운동 |
| 1~3년 | 3분할, 부위별 고립 운동을 함께 구성 |
| 3년 초과 | 4분할, 회복을 고려해 부위별 고립 운동을 분산 |

다른 목표에 같은 숙련도의 전용 템플릿이 없고 사용자가 초급이 아니라면, 같은 숙련도의 `FITNESS` 템플릿을 사용한다. 이때 유산소 판단과 추천 문구는 계속 사용자의 실제 목표를 기준으로 한다. 초급 사용자는 해당 목표의 공통 템플릿을 사용한다. 추천 API는 기구 선택과 운동 시간, 운동 횟수로 최종 운동량을 다시 조정한다.

```json
{
  "data": {
    "name": "초급 전신 루틴 · 적응형",
    "daysPerWeek": 3,
    "sessionMinutes": 45,
    "splitCount": 1,
    "cardioMinutes": 0,
    "items": [],
    "reasons": ["주 3일, 회당 45분으로 운동 계획을 구성했어요."],
    "inbodyUsed": true,
    "inbodyMeasuredDate": "2026-09-14",
    "aiSummary": "현재 운동 빈도에 맞춰 천천히 시작해 보세요."
  }
}
```

## 3. Luna 연결 지점

`OpenAiWorkoutRecommendationNarrator`가 `WorkoutRecommendationNarrator`를 구현한다. `WorkoutRecommendationService`가 서버 규칙으로 `name`, `reasons`, `cardioMinutes`를 먼저 확정한 뒤 이 인터페이스를 호출한다.

```kotlin
interface WorkoutRecommendationNarrator {
    fun narrate(name: String, reasons: List<String>, cardioMinutes: Int, inbody: InbodyRecord?): String?
}
```

`FallbackWorkoutRecommendationNarrator`는 비연동 테스트용 구현체다. 운영 구현체는 `OPENAI_API_KEY`가 있을 때 Luna를 호출해 `aiSummary`만 채운다. 호출 실패·시간 초과·형식 오류 때도 `null`을 반환해 기본 추천을 그대로 보여 준다.

## 4. OpenAI Responses API 요청 계약

백엔드에서만 호출한다. Android 앱에 API 키를 넣지 않는다. `store: false`를 사용한다.

```json
{
  "model": "gpt-5.6-luna",
  "input": "아래 추천 근거만 사용해 한국어 한두 문장 안내를 작성하세요. 질환 진단·치료 조언, 새 운동·중량·세트·반복 제안은 하지 마세요.\n추천 이름: 초급 전신 루틴 · 적응형\n유산소 시간: 0\n근거: 주 3일, 회당 45분...",
  "max_output_tokens": 160,
  "store": false
}
```

응답에서는 `output[].content[]` 중 `type: "output_text"`의 `text`만 읽는다. 텍스트가 비어 있거나 길이 제한을 넘으면 버리고 `null`로 처리한다.

## 5. AI에 보내도 되는 정보와 보내면 안 되는 정보

보내는 값은 추천 문장 작성에 필요한 최소 정보로 제한한다.

| 전송 가능 | 전송 금지 |
|---|---|
| 목표, 숙련도, 주 운동 횟수, 시간, 사용 기구 | 이름, 이메일, 전화번호, 회원번호 |
| 서버가 확정한 운동명과 추천 사유 | 인바디 원본 이미지, OCR 원문, QR 코드 |
| 사용자가 명시적으로 선택한 인바디의 요약 수치 | 전체 인바디 기록 목록, 다른 사용자의 기록 |

인바디 수치를 AI에 보낼 경우에도 체지방률, 골격근량처럼 추천 설명에 필요한 항목만 전송한다. 서버는 체지방 감소 목표, 선택한 기록의 체지방률 30% 이상 또는 기록된 BMI 25 이상일 때에만 20분 이하의 편한 강도 유산소를 추가한다. 좌우 팔·다리 수치 차이는 운동 중 한쪽의 난이도나 통증을 확인하라는 안내에만 쓰며, 비대칭을 진단하거나 교정 운동을 처방하지 않는다.

## 6. 연결 전 확인 항목

- 로컬 키·모델·직접 연결 설정은 Git 제외 파일인 `backend/src/main/resources/application-local.yml`에서 관리한다.
- 추천 설명은 인바디와 같은 Luna 키·모델을 참조한다. 필요하면 `fitmate.workout-recommendation-ai.enabled`를 `false`로 바꿔 규칙 기반 추천만 사용할 수 있다.
- 타임아웃, 429, 5xx, JSON 형식 오류는 모두 기본 설명으로 대체한다.
- 호출 로그에는 요청 본문, 토큰, 인바디 값, 응답 전문을 남기지 않는다.
- 개인정보 처리방침과 건강정보의 외부 AI 전송 동의를 서비스 공개 전에 확정한다.
- 실제 연결 후에는 키가 없는 경우, 429 응답, 공급자 장애, 비정상 응답을 포함한 통합 검증을 추가한다.
