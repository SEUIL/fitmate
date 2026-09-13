# 인바디 AI 판독 API 프론트엔드 연동 가이드

- 작성일: 2026-09-08
- 최종 수정일: 2026-09-10
- 버전: v0.2
- 상태: 검토 중
- 대상: Android Client, Kotlin Spring Boot Backend

## 1. 구현 범위

인바디 결과지 사진에서 값을 읽고, AI가 읽지 못했거나 의심스러운 항목을 사용자가 직접 수정한 뒤 최종 기록으로 저장한다.

`INBODY 이미지 업로드 -> AI 판독 요청 -> 상태 조회 -> 항목 자동 입력 및 검토 -> 사용자 보정 -> 최종 저장`

AI 공급자는 `InbodyAnalyzer` 인터페이스 뒤에 분리되어 있다. 현재 `OPENAI`와 로컬 흐름 검증용 `MOCK`를 지원한다. Android API 흐름은 기존과 같다.

모든 API는 `Authorization: Bearer {accessToken}`이 필요하다.

## 2. 로컬 실행 설정

`backend/local.properties.example`을 `backend/local.properties`로 복사하고 아래 두 값만 설정한다.

```properties
FITMATE_INBODY_AI_PROVIDER=OPENAI
OPENAI_API_KEY=발급받은_API_키
```

기본 모델은 `gpt-5.6-luna`이며 필요하면 `OPENAI_MODEL`로 바꿀 수 있다. `backend/local.properties`는 Git에서 제외된다. 키 문자열을 소스, 문서, Android 앱, 커밋에 넣지 않는다. 백엔드가 OpenAI를 호출하므로 Android에는 키가 필요하지 않다.

키 없이 전체 API 흐름부터 확인하려면 공급자를 `MOCK`로 설정한다.

```properties
FITMATE_INBODY_AI_PROVIDER=MOCK
```

## 3. 이미지 업로드

`POST /api/v1/media?purpose=INBODY`

- 형식: `multipart/form-data`, 파일 필드명 `file`
- 허용 형식: JPEG, PNG, WEBP
- 최대 크기: 10MB

```json
{
  "data": {
    "mediaId": "01992200-0000-7000-8000-000000000001",
    "purpose": "INBODY",
    "contentType": "image/jpeg",
    "byteSize": 1352400,
    "url": "/api/v1/media/01992200-0000-7000-8000-000000000001"
  }
}
```

응답의 `mediaId`를 다음 판독 요청의 `mediaId`로 사용한다.

## 4. AI 판독 요청과 상태 조회

```http
POST /api/v1/inbody-analyses
Content-Type: application/json

{"mediaId":"01992200-0000-7000-8000-000000000001"}
```

요청 수락 상태는 `202 Accepted`다. 응답의 판독 ID로 상태를 조회한다.

```http
GET /api/v1/inbody-analyses/{analysisId}
```

| 상태 | 의미 | 화면 처리 |
|---|---|---|
| `UPLOADED` | 판독 대기 | 로딩 표시 |
| `PROCESSING` | AI 판독 중 | 1~2초 간격 조회 |
| `COMPLETED` | 필수 항목 정상 인식 | 입력란에 값 표시 |
| `REVIEW_REQUIRED` | 누락 또는 의심값 존재 | 누락은 빈칸, 의심값은 확인 표시 |
| `FAILED` | 공급자/응답 오류 | 실패 안내와 재시도 버튼 |

실패한 판독은 원본 이미지가 남아 있으므로 아래 API로 다시 요청할 수 있다.

```http
POST /api/v1/inbody-analyses/{analysisId}/retry
```

성공한 판독은 개인정보 노출 시간을 줄이기 위해 서버의 원본 이미지 파일을 삭제한다. 구조화된 판독값과 최종 저장값만 유지한다.

## 5. 항목별 화면 처리

판독이 끝나면 `fields`에 지원하는 모든 항목이 반환된다.

```json
{
  "data": {
    "id": "01992200-0000-7000-8000-000000000002",
    "status": "REVIEW_REQUIRED",
    "provider": "OPENAI",
    "providerModel": "gpt-5.6-luna",
    "fields": [
      {
        "code": "WEIGHT_KG",
        "value": "68.2",
        "unit": "kg",
        "rawText": "체중 68.2 kg",
        "status": "RECOGNIZED",
        "issueCode": null
      },
      {
        "code": "BODY_FAT_PERCENT",
        "value": null,
        "unit": "%",
        "rawText": null,
        "status": "NOT_RECOGNIZED",
        "issueCode": null
      }
    ],
    "failure": null,
    "attemptCount": 1
  }
}
```

| 필드 상태 | 화면 표시 | 입력란 초기값 |
|---|---|---|
| `RECOGNIZED` | `AI 인식` | `value` 자동 입력 |
| `REVIEW_REQUIRED` | `확인 필요` | `value`를 넣되 사용자가 확인·수정 |
| `NOT_RECOGNIZED` | `직접 입력 필요` | 빈칸 |

모든 입력란은 상태와 관계없이 사용자가 수정할 수 있어야 한다. 최종 저장의 필수 항목은 측정일, 체중, 골격근량, 체지방률이다.

주요 `issueCode`는 `INVALID_DATE`, `INVALID_NUMBER`, `OUT_OF_RANGE`, `MUSCLE_EXCEEDS_WEIGHT`다.

## 6. 사용자 확인 후 최종 저장

AI가 못 읽은 값도 사용자가 직접 채워 아래 API로 저장한다.

```http
POST /api/v1/inbody-records
Content-Type: application/json
```

```json
{
  "analysisId": "01992200-0000-7000-8000-000000000002",
  "measuredDate": "2026-09-08",
  "weightKg": 68.2,
  "skeletalMuscleMassKg": 29.4,
  "bodyFatPercentage": 18.7,
  "optionalValues": {
    "BMI": 22.1,
    "BASAL_METABOLIC_RATE_KCAL": 1534
  }
}
```

성공 상태는 `201 Created`다. AI 없이 전부 직접 입력할 때는 `analysisId`를 생략하며 저장 결과의 `sourceType`은 `MANUAL`이 된다. 판독을 바탕으로 저장하면 `AI_OCR`이다.

```http
GET /api/v1/inbody-records
GET /api/v1/inbody-records/{recordId}
DELETE /api/v1/inbody-records/{recordId}
```

## 7. 검증과 보안 기준

- 측정일은 미래일 수 없다.
- 체중은 1~500kg, 체지방률은 0~100 범위다.
- 골격근량은 0 이상이며 체중보다 클 수 없다.
- AI는 이미지에서 직접 확인되지 않은 값을 추측하거나 계산하지 않도록 요청한다.
- 이름, 전화번호, 회원번호, QR 코드 등 개인정보는 구조화 결과에 포함하지 않도록 요청한다.
- 사용자는 본인이 업로드한 이미지와 판독/기록만 조회할 수 있다.
- API 키는 백엔드에서만 관리하고 로그와 API 응답에 포함하지 않는다.
- 실제 건강정보를 외부 AI로 전송하기 전 개인정보 처리방침과 공급자의 데이터 사용 조건을 별도로 확인한다.
