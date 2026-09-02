# Fitmate Galaxy Fit2 · Samsung Health · Android 연동 가이드

> 작성 기준일: 2026-09-01  
> 권장 MVP 연결 방식: **Galaxy Fit2 → Samsung Health → Health Connect → Fitmate Android 앱 → Fitmate 서버**

## 1. 결론

Fitmate 1차 버전은 Galaxy Fit2에 블루투스로 직접 연결하지 않는다. Fit2가 수집한 운동 데이터를 Samsung Health에 동기화하고, Fitmate Android 앱이 Health Connect를 통해 사용자의 동의를 받아 읽는 구조를 사용한다.

```text
Galaxy Fit2
  └─ 심박수, 걸음 수, 운동 시간 등 측정
       ↓ Galaxy Wearable / Samsung Health 동기화
Samsung Health
  └─ Fit2 데이터와 휴대전화 GPS 데이터로 운동 기록 생성
       ↓ 사용자가 Health Connect 공유 허용
Health Connect
  └─ 표준화된 운동·심박수·거리·속도·경로 데이터 제공
       ↓ Fitmate 앱이 권한 범위 안에서 조회
Fitmate Android 앱
  └─ 조회·정규화·중복 제거·사용자 확인
       ↓ HTTPS API
Fitmate 서버
  └─ 기록 저장, 과거 비교, 운동 리포트 및 추천 생성
```

이 방식을 우선 추천하는 이유는 다음과 같다.

- Fit2 전용 통신 프로토콜을 직접 구현할 필요가 없다.
- Samsung Health뿐 아니라 향후 다른 Health Connect 지원 기기로 확장할 수 있다.
- Samsung Health Data SDK를 공개 앱에서 사용할 때 필요한 Samsung 파트너 승인에 MVP 일정이 의존하지 않는다.
- Android 공식 Jetpack API를 사용하므로 앱 구조와 테스트 방법을 표준화하기 쉽다.

단, **Fit2 심박수를 Fitmate가 실시간 스트리밍으로 직접 받는 구조는 아니다.** 초기 버전은 운동 종료 후 Samsung Health와 Health Connect의 동기화가 완료된 데이터를 가져오는 방식으로 설계한다.

## 2. 기기별 역할

### Galaxy Fit2

- 심박수 측정
- 걸음 수 및 활동량 측정
- 운동 시작·종료와 운동 시간 기록
- 지원되는 운동 종류 기록
- 수면 기록(사용자 설정과 동기화 상태에 따라 제공 범위가 달라질 수 있음)

Galaxy Fit2에는 독립 GPS가 없으므로 야외 러닝 경로는 휴대전화의 위치 정보를 사용한다. 따라서 정확한 경로가 필요한 러닝에서는 사용자가 휴대전화를 함께 소지해야 한다.

### 휴대전화

- Galaxy Wearable과 Fit2 연결
- Samsung Health 실행 및 Fit2 데이터 동기화
- 휴대전화 GPS로 운동 경로 기록
- Samsung Health와 Health Connect 간 데이터 공유
- Fitmate Android 앱 실행

### Samsung Health

- Fit2 측정값과 휴대전화 위치 데이터를 운동 세션으로 구성
- 운동, 심박수, 걸음 수, 수면 등의 데이터를 저장
- 사용자가 허용한 데이터 형식을 Health Connect에 동기화

### Health Connect

- Samsung Health와 Fitmate 사이의 표준 데이터 계층
- 데이터 종류별 읽기 권한 관리
- 운동 기록의 출처 앱, 작성 시간, 수정 여부 등 메타데이터 제공
- Android 14 이상에서는 시스템에 포함되며, Android 13 이하에서는 별도 Health Connect 앱이 필요

## 3. 지원 환경과 개발 기준

Fitmate 프로젝트 권장값은 다음과 같다.

| 항목 | 권장값 |
|---|---|
| Android Studio | Quail 3 2026.1.3 Patch 1 Stable |
| 언어 | Kotlin |
| UI | Jetpack Compose |
| Gradle 스크립트 | Kotlin DSL |
| `minSdk` | 29 (Android 10) |
| `compileSdk` | 36 |
| `targetSdk` | 36 |
| Java 호환 버전 | 17 |
| Health Connect 라이브러리 | `androidx.health.connect:connect-client:1.1.0` |
| 테스트 기기 | Android 10 이상 실제 휴대전화 |

Health Connect 자체는 Google Play 서비스가 있는 Android 9(API 28) 이상에서 사용할 수 있지만, Fitmate는 Samsung Health Data SDK 대체 가능성 및 팀 테스트 환경 통일을 위해 `minSdk 29`를 권장한다.

`app/build.gradle.kts` 예시:

```kotlin
android {
    namespace = "com.fitmate.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fitmate.app"
        minSdk = 29
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.health.connect:connect-client:1.1.0")
}
```

알파 버전인 `1.2.0-alphaXX` 대신 안정판 `1.1.0`을 사용한다. 새로운 기능이 반드시 필요한 경우가 아니라면 팀 프로젝트에서 알파 버전을 사용하지 않는다.

## 4. 사용자가 먼저 해야 하는 연결 설정

앱 코드만으로 Fit2 데이터가 자동으로 나타나는 것은 아니다. 테스트 사용자는 다음 순서대로 설정해야 한다.

1. 휴대전화에 Galaxy Wearable을 설치한다.
2. Galaxy Fit2를 Galaxy Wearable과 페어링한다.
3. Samsung Health를 최신 버전으로 업데이트한다.
4. Samsung Health에서 Fit2의 운동·심박수·걸음 수 동기화를 확인한다.
5. Android 13 이하라면 Google Play에서 Health Connect를 설치한다.
6. Android 14 이상이라면 `설정 → 보안 및 개인정보 보호 → Health Connect`에서 접근한다.
7. Samsung Health의 `설정 → Health Connect`에서 공유할 데이터 종류를 허용한다.
8. Fitmate 앱에서 `Samsung Health 데이터 연결`을 선택한다.
9. Health Connect 권한 화면에서 Fitmate에 필요한 읽기 권한을 허용한다.

권한을 허용했더라도 Samsung Health 쪽 공유가 꺼져 있으면 Fitmate에서 데이터가 조회되지 않을 수 있다. 연결 오류 화면에는 **Samsung Health 공유 상태**와 **Fitmate 권한 상태**를 각각 확인할 수 있는 안내가 필요하다.

## 5. Android Manifest 설정

MVP에서는 데이터를 읽기만 하므로 쓰기 권한은 요청하지 않는다. 실제로 사용하는 데이터 종류만 선언한다.

`AndroidManifest.xml` 예시:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.health.READ_EXERCISE" />
    <uses-permission android:name="android.permission.health.READ_EXERCISE_ROUTES" />
    <uses-permission android:name="android.permission.health.READ_HEART_RATE" />
    <uses-permission android:name="android.permission.health.READ_STEPS" />
    <uses-permission android:name="android.permission.health.READ_DISTANCE" />
    <uses-permission android:name="android.permission.health.READ_SPEED" />
    <uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED" />

    <!-- 수면 기능을 실제 MVP에 포함할 때만 선언 -->
    <!-- <uses-permission android:name="android.permission.health.READ_SLEEP" /> -->

    <application
        android:allowBackup="true"
        android:label="Fitmate"
        android:theme="@style/Theme.Fitmate">

        <!-- 사용자가 Health Connect에서 개인정보 처리방침을 누르면 표시할 화면 -->
        <activity
            android:name=".PrivacyPolicyActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
            </intent-filter>
        </activity>

        <!-- 기존 MainActivity 등 -->

    </application>

    <!-- Android 13 이하에서 Health Connect 앱 설치 여부 확인 -->
    <queries>
        <package android:name="com.google.android.apps.healthdata" />
    </queries>

</manifest>
```

권한 이름은 사용 중인 Health Connect 라이브러리와 `compileSdk`에 맞춰 확인한다. Play 배포 시 Manifest에 선언한 Health Connect 데이터 종류와 Play Console의 건강 앱 데이터 사용 신고 내용이 일치해야 한다.

## 6. Health Connect 사용 가능 여부 확인

Health Connect는 기기와 Android 버전에 따라 설치 또는 업데이트가 필요할 수 있다.

```kotlin
import android.content.Context
import androidx.health.connect.client.HealthConnectClient

fun getHealthConnectStatus(context: Context): Int {
    return HealthConnectClient.getSdkStatus(context)
}

fun createHealthConnectClient(context: Context): HealthConnectClient? {
    return when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE ->
            HealthConnectClient.getOrCreate(context)

        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
        HealthConnectClient.SDK_UNAVAILABLE -> null

        else -> null
    }
}
```

앱 화면에서는 상태를 다음처럼 구분한다.

- `SDK_AVAILABLE`: 연결 및 권한 요청 진행
- `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`: Health Connect 설치 또는 업데이트 안내
- `SDK_UNAVAILABLE`: 지원하지 않는 기기 안내

## 7. 읽기 권한 요청

앱 시작 즉시 모든 권한을 요청하지 않는다. 사용자가 `Samsung Health 연결` 버튼을 눌렀을 때 어떤 데이터를 왜 사용하는지 설명한 뒤 요청한다.

```kotlin
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord

val fitmateReadPermissions = setOf(
    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(DistanceRecord::class),
    HealthPermission.getReadPermission(SpeedRecord::class),
    HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    HealthPermission.PERMISSION_READ_EXERCISE_ROUTES,
)
```

Compose 권한 요청 예시:

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    contract = PermissionController.createRequestPermissionResultContract()
) { grantedPermissions ->
    val allGranted = grantedPermissions.containsAll(fitmateReadPermissions)
    // 일부만 허용된 경우에도 허용된 데이터만으로 앱이 동작하도록 처리한다.
}

LaunchedEffect(Unit) {
    val granted = healthConnectClient.permissionController.getGrantedPermissions()
    if (!granted.containsAll(fitmateReadPermissions)) {
        permissionLauncher.launch(fitmateReadPermissions)
    }
}
```

사용자는 언제든 권한을 취소할 수 있으므로 데이터 조회 직전에 현재 권한을 다시 확인해야 한다. 사용자가 일부 권한만 허용해도 앱 전체를 막지 말고 가능한 기능을 제공한다.

## 8. 운동 세션 읽기

먼저 `ExerciseSessionRecord`로 운동 목록을 읽고, 각 세션의 시작·종료 시간 범위로 심박수·거리·속도 등을 추가 조회한다.

```kotlin
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

suspend fun readExerciseSessions(
    client: HealthConnectClient,
    startTime: Instant,
    endTime: Instant,
): List<ExerciseSessionRecord> {
    val result = client.readRecords(
        ReadRecordsRequest(
            recordType = ExerciseSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
        )
    )

    return result.records
}
```

MVP에서는 최근 7일 또는 30일처럼 작은 범위부터 조회한다. 전체 기간을 매번 다시 읽지 않는다.

## 9. 운동별 심박수 읽기

```kotlin
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter

data class HeartRateSample(
    val epochMillis: Long,
    val bpm: Long,
)

suspend fun readHeartRateForSession(
    client: HealthConnectClient,
    session: ExerciseSessionRecord,
): List<HeartRateSample> {
    val result = client.readRecords(
        ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                session.startTime,
                session.endTime,
            ),
        )
    )

    return result.records.flatMap { record ->
        record.samples.map { sample ->
            HeartRateSample(
                epochMillis = sample.time.toEpochMilli(),
                bpm = sample.beatsPerMinute,
            )
        }
    }
}
```

저장 전에 평균·최저·최고 심박수를 계산할 수 있다. 그래프가 필요하지 않다면 모든 원시 샘플을 서버에 저장하기보다 요약값과 제한된 간격의 샘플만 저장하는 편이 비용과 개인정보 측면에서 유리하다.

## 10. 걸음 수 집계

걸음 수는 휴대전화와 Fit2 등 여러 출처가 겹칠 수 있으므로 원시 레코드를 단순 합산하면 중복될 수 있다. 공식 권장 방식처럼 `aggregate()`를 사용한다.

```kotlin
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter

suspend fun readTotalSteps(
    client: HealthConnectClient,
    startTime: Instant,
    endTime: Instant,
): Long {
    val result: AggregationResult = client.aggregate(
        AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
        )
    )

    return result[StepsRecord.COUNT_TOTAL] ?: 0L
}
```

## 11. 운동 경로 읽기

Samsung Health가 휴대전화 GPS로 운동 경로를 기록하고 Health Connect에 공유한 경우, 운동 세션과 별도로 경로를 요청한다.

```kotlin
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.response.ExerciseRouteResponse

suspend fun readExerciseRoute(
    client: HealthConnectClient,
    session: ExerciseSessionRecord,
): List<Pair<Double, Double>> {
    return when (
        val result = client.getExerciseRoute(session.metadata.id)
    ) {
        is ExerciseRouteResponse.Success -> {
            result.exerciseRoute.route.map { point ->
                point.latitude to point.longitude
            }
        }

        is ExerciseRouteResponse.NoData -> emptyList()
        is ExerciseRouteResponse.ConsentRequired -> emptyList()
    }
}
```

> Health Connect 버전의 응답 프로퍼티 이름은 라이브러리 버전에 따라 달라질 수 있다. Android Studio의 `1.1.0` API 자동완성과 공식 API Reference를 기준으로 최종 확인한다.

경로 데이터는 민감도가 높아 별도 동의가 필요할 수 있다. 사용자가 운동 데이터 읽기를 허용했더라도 경로를 거부할 수 있으므로 `경로 없음`, `동의 필요`, `조회 성공`을 별도 상태로 처리한다.

## 12. 가져올 수 있는 정보

Samsung Health가 Health Connect에 실제로 동기화한 데이터만 가져올 수 있다. Fit2 또는 휴대전화가 측정하지 않았거나 사용자가 공유하지 않은 필드는 존재하지 않을 수 있다.

| Fitmate 기능 | Health Connect 레코드 | 대표 정보 | 비고 |
|---|---|---|---|
| 운동 기록 | `ExerciseSessionRecord` | 운동 종류, 시작·종료 시간, 제목, 메모 | 러닝·걷기·근력 운동 등 |
| 심박수 | `HeartRateRecord` | 시간별 BPM | Fit2 설정과 동기화 상태에 따라 샘플 간격이 다름 |
| 걸음 수 | `StepsRecord` | 구간별 걸음 수 | 여러 기기 중복 방지를 위해 집계 API 권장 |
| 거리 | `DistanceRecord` | 운동 구간 거리 | 기록 방식에 따라 없을 수 있음 |
| 속도 | `SpeedRecord` | 시간별 속도 | 러닝 페이스 계산 가능 |
| 칼로리 | `TotalCaloriesBurnedRecord` | 소모 열량 | 추정치이므로 의료·정밀 영양 계산에 사용하지 않음 |
| GPS 경로 | `ExerciseRoute` | 위도, 경도, 시간, 고도(제공 시) | 휴대전화 GPS 및 별도 경로 권한 필요 |
| 수면 | `SleepSessionRecord` | 수면 시작·종료, 수면 단계(제공 시) | MVP 이후 선택 기능 권장 |
| 체중 | `WeightRecord` | 측정 시각, 체중 | Fit2 데이터가 아니라 Samsung Health의 다른 입력 출처일 수 있음 |
| 체지방률 | `BodyFatRecord` | 측정 시각, 체지방률 | 인바디 OCR 데이터와 출처를 구분해야 함 |
| 기초대사량 | `BasalMetabolicRateRecord` | 측정 시각, BMR | 제공되는 경우에만 사용 |

Samsung 공식 문서상 운동 세션, 운동 거리, 운동 심박수, 운동 속도, 칼로리, 걸음 수, 수면 등이 Samsung Health와 Health Connect 사이에 동기화될 수 있다. 다만 동기화 범위는 Samsung Health 버전에 따라 변경될 수 있으므로 모든 필드를 필수값으로 설계하지 않는다.

## 13. Fit2 데이터와 휴대전화 데이터 결합 방식

가장 안전한 결합 기준은 운동 세션의 시간 범위다.

```text
운동 세션: 2026-09-01 19:00:00 ~ 19:35:00
  ├─ 같은 시간대 HeartRateRecord → Fit2 심박수 후보
  ├─ 같은 시간대 DistanceRecord  → 운동 거리
  ├─ 같은 시간대 SpeedRecord     → 속도·페이스
  └─ 세션 ID의 ExerciseRoute      → 휴대전화 GPS 경로
```

결합 시 다음을 지킨다.

- 모든 시간은 서버에서 UTC(`Instant`)로 저장하고 화면에서 사용자 시간대로 변환한다.
- 운동 시작·종료가 몇 초 정도 어긋날 수 있으므로 필요하면 작은 허용 범위를 둔다.
- 출처 앱은 `record.metadata.dataOrigin.packageName`으로 확인한다.
- Health Connect에서 출처가 Samsung Health라는 사실은 확인할 수 있지만, 모든 레코드가 반드시 Fit2에서 측정되었다고 단정하지 않는다.
- 동일 운동을 Fitmate와 Samsung Health가 각각 기록했다면 중복 운동이 생길 수 있으므로 원본 레코드 ID와 출처를 저장한다.

## 14. Fitmate 서버 저장 모델 권장안

Health Connect의 원본 구조를 그대로 DB에 복사하지 말고 Fitmate 서비스에 필요한 공통 모델로 정규화한다.

```text
workout_session
  id
  user_id
  source_provider              // HEALTH_CONNECT, MANUAL 등
  source_app_package           // 예: Samsung Health 패키지
  source_record_id             // 중복 방지용
  exercise_type
  started_at_utc
  ended_at_utc
  duration_seconds
  distance_meters
  calories_kcal
  avg_heart_rate_bpm
  min_heart_rate_bpm
  max_heart_rate_bpm
  avg_speed_mps
  route_available
  synced_at_utc
  source_last_modified_at_utc

workout_heart_rate_sample      // 그래프가 필요할 때만
  workout_session_id
  measured_at_utc
  bpm

workout_route_point            // 경로 저장이 정말 필요할 때만
  workout_session_id
  sequence_no
  measured_at_utc
  latitude
  longitude
  altitude_meters
```

`source_record_id + source_app_package + user_id`에 고유 제약을 두면 같은 운동을 여러 번 동기화해도 중복 저장을 방지할 수 있다. 수정된 기록은 `source_last_modified_at_utc`를 비교하여 갱신한다.

## 15. 동기화 전략

### MVP 권장

- 사용자가 앱을 열고 `새 운동 가져오기` 버튼을 누르면 동기화한다.
- 최근 동기화 시각 이후부터 현재까지 조회한다.
- 최초 연결 시 최근 7일 또는 30일만 가져온다.
- 가져온 운동 목록을 사용자에게 보여주고 저장 여부를 확인할 수 있게 한다.
- 동기화 중 일부 데이터가 없어도 운동 세션 자체는 저장할 수 있게 한다.

### 2차 버전

- Health Connect Change Logs를 사용해 생성·수정·삭제된 기록만 증분 동기화한다.
- 백그라운드 동기화가 필요하면 별도 Health Connect 백그라운드 읽기 권한을 요청한다.
- 30일보다 오래된 타 앱 기록이 필요하면 과거 데이터 읽기 권한을 별도로 요청한다.

초기 버전부터 백그라운드·전체 과거 기록 권한을 요청하면 사용자에게 과도한 권한으로 보일 수 있으므로 실제 기능이 생긴 뒤 추가한다.

## 16. 실시간 러닝 기능을 추가할 경우

Health Connect 가져오기 기능과 Fitmate 자체 GPS 러닝 기록은 별개의 기능이다.

Fitmate에서 직접 `러닝 시작` 버튼을 제공하려면 다음이 추가로 필요하다.

- `FusedLocationProviderClient`를 사용한 휴대전화 위치 수집
- `ACCESS_FINE_LOCATION`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`
- `android:foregroundServiceType="location"`
- 운동 중 지속 알림
- 배터리 최적화와 위치 정확도 테스트

사용자가 앱 화면에서 러닝을 시작하고 Location Foreground Service를 실행하면 화면이 꺼지거나 다른 앱으로 이동한 뒤에도 기록을 계속할 수 있다. MVP에서는 불필요한 `ACCESS_BACKGROUND_LOCATION`을 요청하지 않는다.

Fit2의 실시간 심박수를 Fitmate 러닝 화면에 바로 표시하는 기능은 Health Connect의 운동 종료 후 가져오기와 다르다. Galaxy Fit2는 일반적인 Wear OS 앱용 Health Services 대상 기기가 아니므로, 실시간 심박수 기능은 별도 기술 검증 없이 일정에 포함하지 않는다.

## 17. 중요한 유의사항

### 17.1 동기화는 즉시 완료된다고 보장할 수 없음

- Fit2와 휴대전화 연결 상태에 따라 Samsung Health 반영이 늦을 수 있다.
- Samsung Health에서 Health Connect로 넘어오는 데 추가 시간이 걸릴 수 있다.
- 화면에 `마지막 동기화 시각`과 `다시 불러오기` 기능을 제공한다.

### 17.2 값이 항상 존재하지 않음

- 사용자가 휴대전화를 두고 달리면 GPS 경로가 없을 수 있다.
- 위치 권한이나 경로 공유를 거부하면 경로를 읽을 수 없다.
- Fit2 착용 상태가 좋지 않으면 심박수 샘플이 비거나 튈 수 있다.
- 자동 인식 운동과 수동 시작 운동의 제공 필드가 다를 수 있다.

따라서 거리, 속도, 심박수, 경로 등은 DB에서 nullable로 설계한다.

### 17.3 데이터 출처와 중복

- 걸음 수는 휴대전화와 Fit2 양쪽에서 생성될 수 있다.
- Samsung Health와 다른 앱이 같은 운동을 Health Connect에 기록할 수 있다.
- 걸음 수·칼로리 합계는 가능한 한 Health Connect 집계 API를 사용한다.
- 원본 레코드 ID와 출처 앱을 저장해 중복 반영을 막는다.

### 17.4 정확도와 서비스 표현

- 심박수, 칼로리, 수면, GPS는 소비자용 웰니스 데이터이며 오차가 있다.
- Fitmate의 AI 추천은 의료 진단·처방으로 표현하지 않는다.
- 심박수 이상 등을 질병으로 판단하거나 확정적인 경고 문구로 표현하지 않는다.
- AI 리포트 계산값은 서버의 규칙 기반 계산 결과를 사용하고, LLM은 자연어 설명에 활용한다.

### 17.5 개인정보와 위치정보

- 건강 데이터와 GPS 경로는 민감한 개인정보로 취급한다.
- 필요한 데이터만 요청하고 목적을 권한 요청 전에 설명한다.
- 앱 설정에 Health Connect 동기화 켜기/끄기와 권한 관리 이동 버튼을 제공한다.
- 서버 전송은 HTTPS를 사용한다.
- 서버 저장 시 접근 권한을 분리하고 보관 기간과 삭제 정책을 정의한다.
- 회원 탈퇴 시 서버에 저장한 운동·심박수·경로 데이터 삭제 절차를 제공한다.
- 상세 GPS 경로가 리포트에 필요하지 않다면 서버에 저장하지 않거나 좌표 정밀도를 낮추는 방안을 검토한다.
- 프로젝트 계획서의 이름·학번·이메일·전화번호가 포함된 원본은 공개 GitHub 저장소에 올리지 않는다.

### 17.6 Google Play 배포

- Manifest의 Health Connect 권한과 Play Console 건강 앱 신고 항목을 일치시킨다.
- 앱 안에서 개인정보 처리방침과 권한 사용 이유를 제공한다.
- 사용하지 않는 건강 데이터 권한을 미리 요청하지 않는다.
- 권한 거부·일부 허용·나중에 취소한 경우에도 앱이 비정상 종료되지 않도록 처리한다.

## 18. Samsung Health Data SDK를 직접 사용하는 대안

다음 요구가 생기면 Health Connect 대신 또는 보조 수단으로 Samsung Health Data SDK를 검토한다.

- 원본 측정 기기가 Galaxy Fit인지 더 정확하게 구분해야 함
- Health Connect에 동기화되지 않는 Samsung Health 데이터가 반드시 필요함
- Samsung Health의 변경 기록 API나 Samsung 전용 메타데이터가 필요함

Samsung Health Data SDK 사용 조건:

- Android 10(API 29) 이상
- Samsung Health 6.30.2 이상
- Java 17 이상
- 에뮬레이터 미지원, 실제 기기 필요
- 개발 중에는 Samsung Health 개발자 모드로 읽기 테스트 가능
- 공개 배포 전 Samsung 파트너 요청과 앱 패키지명·배포 서명 SHA-256 등록 필요

따라서 학교 프로젝트 MVP에서는 Health Connect를 우선 사용하고, 정확한 Fit2 원본 기기 식별이 핵심 평가 요건일 때만 Samsung Health Data SDK를 추가한다. Samsung Data SDK를 향후 사용할 가능성이 있으므로 `applicationId`와 배포 서명 키를 초기에 정하고 안전하게 관리한다.

## 19. 테스트 체크리스트

### 연결 전

- [ ] Galaxy Fit2가 Galaxy Wearable에 연결되어 있다.
- [ ] Samsung Health가 최신 버전이다.
- [ ] Samsung Health에서 Fit2 심박수·운동 기록이 보인다.
- [ ] Health Connect가 설치 또는 활성화되어 있다.
- [ ] Samsung Health가 Health Connect에 운동·심박수·거리 등을 공유하도록 허용되어 있다.

### 정상 시나리오

- [ ] Fit2에서 걷기 또는 러닝을 시작한다.
- [ ] 휴대전화를 소지하고 실외에서 운동한다.
- [ ] 운동 종료 후 Samsung Health에 기록이 나타난다.
- [ ] Health Connect 데이터 및 접근 화면에서 해당 기록을 확인한다.
- [ ] Fitmate에서 운동 세션을 읽는다.
- [ ] 시작·종료 시각, 운동 종류, 거리, 심박수, 경로를 확인한다.
- [ ] 같은 운동을 다시 동기화해도 중복 저장되지 않는다.

### 예외 시나리오

- [ ] 심박수 권한만 거부했을 때 나머지 운동 정보는 표시된다.
- [ ] 경로 권한을 거부했을 때 지도 없이 운동 기록이 표시된다.
- [ ] Samsung Health 공유를 끈 경우 사용자가 해결 방법을 알 수 있다.
- [ ] Fit2 연결이 끊긴 운동도 가능한 데이터만 저장된다.
- [ ] 인터넷이 끊겼을 때 로컬 조회 후 서버 업로드를 재시도한다.
- [ ] 사용자가 Health Connect 권한을 취소하면 다음 조회 시 정상 안내한다.
- [ ] 시간대가 바뀌어도 운동 시간이 정확히 표시된다.
- [ ] 동일 시간대에 여러 운동이 있어도 잘못 결합되지 않는다.

## 20. 권장 구현 순서

1. Android 프로젝트 생성 및 `minSdk 29`, `targetSdk 36`, Java 17 설정
2. Health Connect 안정판 `1.1.0` 의존성 추가
3. Health Connect 사용 가능 여부 확인 화면 구현
4. 개인정보 처리방침 및 권한 설명 화면 구현
5. 운동 세션 읽기 권한과 `ExerciseSessionRecord` 조회 구현
6. 운동 시간 범위별 심박수·거리·속도 조회 구현
7. 걸음 수 집계 구현
8. GPS 경로 권한 및 경로 조회 구현
9. 로컬 화면에서 데이터 검증
10. Fitmate 서버 정규화 모델과 중복 방지 API 구현
11. 실제 Fit2 운동 10건 이상으로 정상·누락·중복 사례 테스트
12. AI 리포트는 원천 데이터 동기화가 안정된 이후 추가

## 21. 프로젝트 수행계획서 반영 권장 문구

현재 수행계획서의 `모바일 네이티브 앱 제외`, `웨어러블 기기 직접 연동 제외` 표현은 다음과 같이 구분해서 수정하는 것이 좋다.

### 포함 범위

> Kotlin 기반 Android 앱을 구현하고 Health Connect를 통해 사용자가 허용한 Samsung Health 운동 데이터를 조회한다. Galaxy Fit2에서 Samsung Health로 동기화된 운동 시간, 심박수, 걸음 수와 휴대전화 GPS 기반 거리·속도·경로 정보를 Fitmate 운동 기록으로 저장한다.

### 제외 범위

> Galaxy Fit2와 Fitmate 앱 사이의 직접 Bluetooth 통신, 실시간 Fit2 심박수 스트리밍, 의료 진단 및 처방 기능은 1차 개발 범위에서 제외한다.

### 위험 항목 추가

| 위험 항목 | 가능성 | 영향도 | 대응 방안 |
|---|---:|---:|---|
| Samsung Health 동기화 지연 | 중간 | 중간 | 수동 새로고침과 마지막 동기화 시각 제공 |
| 일부 건강 데이터 권한 거부 | 높음 | 중간 | 허용된 데이터만으로 기능 제공 |
| GPS 경로 누락 | 중간 | 중간 | 휴대전화 소지·위치 권한 안내, 경로 없는 기록 허용 |
| Fit2/휴대전화 걸음 수 중복 | 중간 | 중간 | Health Connect 집계 API 사용 |
| 공개 배포 정책 변경 | 낮음 | 높음 | Play Console 건강 앱 정책과 공식 문서 정기 확인 |
| 실시간 심박수 제공 불가 | 높음 | 중간 | MVP를 운동 종료 후 동기화 방식으로 명시 |

## 22. 공식 참고 문서

- [Samsung Health와 Health Connect 데이터 동기화 FAQ](https://developer.samsung.com/health/health-connect-faq.html)
- [Samsung Health 데이터의 Health Connect 접근 방법](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect)
- [Health Connect 시작하기](https://developer.android.com/health-and-fitness/health-connect/get-started)
- [Health Connect 사용 가능 환경](https://developer.android.com/health-and-fitness/health-connect/availability)
- [Health Connect 데이터 형식과 권한](https://developer.android.com/health-and-fitness/health-connect/data-types)
- [운동 경험 구현 가이드](https://developer.android.com/health-and-fitness/health-connect/experiences/workouts)
- [Health Connect 원시 데이터 읽기](https://developer.android.com/health-and-fitness/health-connect/read-data)
- [운동 경로 읽기·쓰기](https://developer.android.com/health-and-fitness/health-connect/features/exercise-routes)
- [Health Connect Jetpack 릴리스](https://developer.android.com/jetpack/androidx/releases/health-connect)
- [Samsung Health Data SDK 소개](https://developer.samsung.com/health/data/guide/introduction.html)
- [Samsung Health Data SDK 앱 등록·배포 절차](https://developer.samsung.com/health/data/process.html)

---

이 문서는 Fitmate 1차 MVP의 기술 기준이다. SDK, Android 권한, Samsung Health 동기화 데이터 범위와 Google Play 정책은 변경될 수 있으므로 실제 배포 직전에 공식 문서를 다시 확인한다.
