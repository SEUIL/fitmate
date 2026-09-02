# Fitmate Android Studio 초기 설정 가이드

> 작성 기준일: 2026-09-01  
> 프로젝트 기준: Kotlin + Jetpack Compose + Android 10 이상 + API 36

## 1. 현재 설치 화면에서는 어떻게 해야 하나?

현재 Android Studio 설치 화면에 다음 항목이 표시되어 있다.

```text
Sources for Android 37.0
Android SDK Build-Tools 36
Android SDK Platform 37.0
Android SDK Platform-Tools
Android Emulator
```

이 상태로 라이선스에 동의하고 설치를 진행해도 된다. SDK 37이 컴퓨터에 설치되어 있어도 Fitmate 프로젝트가 자동으로 SDK 37을 사용하는 것은 아니다. 프로젝트의 `build.gradle.kts`에서 `compileSdk`와 `targetSdk`를 36으로 지정하면 API 36 기준으로 개발된다.

각 항목의 의미는 다음과 같다.

| 항목 | 필요 여부 | 설명 |
|---|---|---|
| Sources for Android 37.0 | 선택 | Android 37 내부 소스 확인용이며 Fitmate MVP에는 필수 아님 |
| Android SDK Build-Tools 36 | 필수 | API 36 프로젝트 빌드 도구 |
| Android SDK Platform 37.0 | 선택 | Android 17 테스트용. 설치되어도 문제없음 |
| Android SDK Platform-Tools | 필수 | `adb`와 실제 휴대전화 연결에 필요 |
| Android Emulator | 권장 | 일반 UI 테스트용. Samsung Health 연동 테스트는 불가능 |

설치 용량을 줄이고 싶다면 Android 37 Sources와 Platform은 제외할 수 있다. 이미 설치가 시작되었거나 선택 해제가 어렵다면 그대로 진행해도 된다.

중요한 것은 설치 완료 후 **Android SDK Platform 36**이 설치되어 있는지 확인하는 것이다.

## 2. Android Studio 설치 완료

1. 라이선스 화면에서 `android-sdk-license`를 선택한다.
2. `Accept`를 선택한다.
3. 모든 필수 라이선스가 동의 상태인지 확인한다.
4. `Finish` 또는 `Next`를 눌러 설치를 완료한다.
5. Android Studio가 실행되면 초기 설정 가져오기 화면에서 처음 설치라면 `Do not import settings`를 선택한다.
6. 테마는 원하는 항목을 선택한다.

Android Studio는 Stable 채널을 사용한다. RC, Beta, Canary 버전으로 별도 변경하지 않는다.

## 3. API 36 설치 확인

Android Studio 시작 화면에서 다음 순서로 이동한다.

```text
More Actions → SDK Manager
```

프로젝트가 열린 상태라면 다음 경로를 사용한다.

```text
Tools → SDK Manager
```

### SDK Platforms 탭

다음 항목을 체크한다.

- Android 16.0
- API Level 36
- Android SDK Platform 36

`Show Package Details`를 켰을 때 Android SDK Platform 36이 선택되어 있는지 확인한다.

### SDK Tools 탭

다음 항목을 체크한다.

- Android SDK Build-Tools 36
- Android SDK Platform-Tools
- Android SDK Command-line Tools (latest)
- Android Emulator
- Google USB Driver

설정 후 `Apply → OK`를 눌러 설치한다.

Android 37 플랫폼이 이미 설치되어 있어도 삭제할 필요는 없다. Fitmate는 아래 Gradle 설정으로 API 36을 사용한다.

## 4. 새 프로젝트 생성

Android Studio 시작 화면에서 다음을 선택한다.

```text
New Project → Empty Activity
```

프로젝트 생성값:

| 항목 | 입력값 |
|---|---|
| Name | `Fitmate` |
| Package name | `com.fitmate.app` 또는 팀에서 확정한 고유 패키지명 |
| Save location | Git 저장소 내부 Android 앱 폴더 |
| Language | Kotlin |
| Minimum SDK | API 29: Android 10.0 |
| Build configuration language | Kotlin DSL |

권장 저장 구조:

```text
fitmate/
├─ android/             # Android Studio 프로젝트
├─ backend/             # Spring Boot 서버
├─ docs/                # 기획·설계 문서
└─ README.md
```

현재 workspace를 Android 프로젝트로 바로 덮어쓰지 말고 `android` 하위 폴더에 만드는 것을 권장한다. 기존 `docs` 폴더와 기획 문서를 보존하기 위함이다.

예시 저장 경로:

```text
C:\Users\jaemin\Desktop\workspace\fitmate\android
```

패키지명은 향후 Samsung Health Data SDK 파트너 등록과 앱 배포 서명에 사용될 수 있으므로 팀에서 확정한 뒤 자주 변경하지 않는다.

## 5. Gradle 설정 확인

프로젝트 생성 후 다음 파일을 연다.

```text
android/app/build.gradle.kts
```

핵심 설정을 다음처럼 맞춘다.

```kotlin
android {
    namespace = "com.fitmate.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fitmate.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
```

Android Studio가 새 프로젝트를 다른 문법으로 생성했다면 무조건 전체를 바꾸지 말고 다음 네 값만 우선 확인한다.

```text
compileSdk = 36
minSdk = 29
targetSdk = 36
JVM target = 17
```

수정 후 화면 상단의 `Sync Now`를 누른다.

## 6. Gradle JDK 설정

다음 경로로 이동한다.

```text
File → Settings → Build, Execution, Deployment
→ Build Tools → Gradle
```

`Gradle JDK`는 Android Studio에 포함된 Embedded JDK를 선택한다. 별도로 설치한 오래된 Java 8이나 Java 11을 선택하지 않는다.

프로젝트 소스 호환 버전은 Java 17로 유지한다. Samsung Health Data SDK를 나중에 추가하는 경우에도 Java 17 이상 요구사항을 만족한다.

## 7. 최초 빌드

Gradle 동기화가 끝난 뒤 다음 메뉴를 실행한다.

```text
Build → Make Project
```

정상 기준:

- 하단 Build 창에 `BUILD SUCCESSFUL` 표시
- `app` 실행 구성이 생성됨
- Kotlin 또는 Gradle 오류가 없음

오류가 발생하면 먼저 다음을 확인한다.

- 인터넷 연결 여부
- SDK Platform 36 설치 여부
- Gradle JDK가 Embedded JDK인지
- `compileSdk = 36`인지
- Android Studio가 Offline Mode로 설정되지 않았는지

## 8. 에뮬레이터 설정

일반 UI 개발에는 에뮬레이터를 사용할 수 있다.

```text
Tools → Device Manager → Add a new device
→ Create Virtual Device
```

권장값:

- Device: Pixel 계열 일반 휴대전화
- System Image: API 36
- Google APIs 또는 Google Play 이미지
- Orientation: Portrait

에뮬레이터에서 확인할 기능:

- Compose 화면
- 화면 이동
- 입력 검증
- 서버 API 호출
- 로컬 DB
- 일반 GPS 모의 위치

에뮬레이터에서 최종 확인할 수 없는 기능:

- Galaxy Fit2 실제 연결
- Samsung Health Data SDK
- 실제 Fit2 심박수 동기화
- 실제 야외 GPS 정확도

## 9. 실제 갤럭시 휴대전화 연결

Samsung Health와 Fit2 테스트는 실제 휴대전화가 필요하다.

### 휴대전화 개발자 옵션 활성화

```text
설정 → 휴대전화 정보 → 소프트웨어 정보
→ 빌드 번호를 7회 연속 누르기
```

PIN을 입력하면 개발자 옵션이 활성화된다.

### USB 디버깅 활성화

```text
설정 → 개발자 옵션 → USB 디버깅 켜기
```

1. USB 케이블로 휴대전화와 PC를 연결한다.
2. 휴대전화에 표시되는 `USB 디버깅을 허용하시겠습니까?`에서 허용한다.
3. Android Studio 상단 기기 목록에서 연결된 휴대전화를 선택한다.
4. Run 버튼을 눌러 앱을 설치한다.

기기가 보이지 않으면 다음을 확인한다.

- 충전 전용이 아닌 데이터 전송 가능한 USB 케이블인지
- USB 연결 방식이 파일 전송으로 되어 있는지
- Google USB Driver가 설치되어 있는지
- 휴대전화에서 디버깅 허용 팝업을 승인했는지

## 10. Health Connect 라이브러리 추가

기본 앱이 실제 기기에서 실행되는 것을 확인한 뒤 Health Connect를 추가한다.

`android/app/build.gradle.kts`의 `dependencies`에 다음을 추가한다.

```kotlin
dependencies {
    implementation("androidx.health.connect:connect-client:1.1.0")
}
```

추가 후 `Sync Now`를 누른다.

알파 버전 대신 안정판 `1.1.0`을 사용한다.

상세 권한 및 조회 코드는 다음 문서를 따른다.

- [Fit2_SamsungHealth_Android_연동_가이드.md](./Fit2_SamsungHealth_Android_연동_가이드.md)

## 11. Samsung Health 테스트 준비

실제 휴대전화에서 다음을 준비한다.

1. Galaxy Wearable 설치 및 로그인
2. Galaxy Fit2 페어링
3. Samsung Health 최신 버전 설치
4. Fit2 운동 기록이 Samsung Health에 표시되는지 확인
5. Android 13 이하에서는 Health Connect 앱 설치
6. Android 14 이상에서는 시스템 설정의 Health Connect 확인
7. Samsung Health에서 Health Connect 데이터 공유 허용

이 준비가 끝나기 전에는 Fitmate 코드가 정상이어도 운동 데이터가 0건으로 조회될 수 있다.

## 12. 초기 설정 완료 체크리스트

- [ ] Android Studio Stable 버전 설치 완료
- [ ] Android SDK Platform 36 설치 완료
- [ ] Android SDK Build-Tools 36 설치 완료
- [ ] Android SDK Platform-Tools 설치 완료
- [ ] Android SDK Command-line Tools 설치 완료
- [ ] Google USB Driver 설치 완료
- [ ] `fitmate/android` 폴더에 프로젝트 생성
- [ ] Kotlin 및 Jetpack Compose 선택
- [ ] `minSdk = 29`
- [ ] `compileSdk = 36`
- [ ] `targetSdk = 36`
- [ ] Java/JVM 17 설정
- [ ] Gradle Sync 성공
- [ ] Build 성공
- [ ] 에뮬레이터에서 기본 앱 실행 성공
- [ ] 실제 휴대전화 USB 디버깅 연결 성공
- [ ] 실제 휴대전화에서 기본 앱 실행 성공
- [ ] Health Connect `1.1.0` 의존성 추가 성공

## 13. 자주 묻는 질문

### SDK 37이 설치됐는데 삭제해야 하나?

삭제할 필요 없다. 여러 Android SDK 버전은 동시에 설치할 수 있다. Fitmate의 `compileSdk`와 `targetSdk`만 36으로 지정한다.

### Android 37로 바로 개발하면 안 되나?

Android 17/API 37 전용 기능을 테스트하려는 목적이 아니라면 초기 팀 프로젝트에서는 API 36이 안정적이다. API 37은 별도 호환성 테스트용으로만 사용하고, Fitmate MVP의 기준은 API 36으로 통일한다.

### 에뮬레이터만으로 개발하면 안 되나?

일반 화면과 서버 연동은 가능하지만 Fit2·Samsung Health 최종 연동은 실제 휴대전화가 필요하다.

### Minimum SDK를 36으로 해야 하나?

아니다. `minSdk`는 앱을 설치할 수 있는 가장 낮은 Android 버전이다. Fitmate는 Android 10 이상을 지원하므로 `minSdk = 29`로 설정한다. `compileSdk`와 `targetSdk`만 36을 사용한다.

### SDK 37이 설치됐는데 Build-Tools 36을 사용해도 되나?

가능하다. SDK 플랫폼과 Build-Tools는 여러 버전이 함께 설치될 수 있다. 프로젝트 설정과 AGP가 적절한 도구를 선택한다.

## 14. 공식 참고 문서

- [Android Studio 설치](https://developer.android.com/studio/install)
- [Android 16 SDK 설정](https://developer.android.com/about/versions/16/setup-sdk)
- [Android 17 SDK 설정](https://developer.android.com/about/versions/17/setup-sdk)
- [Health Connect 사용 가능 환경](https://developer.android.com/health-and-fitness/health-connect/availability)
- [Health Connect Jetpack 릴리스](https://developer.android.com/jetpack/androidx/releases/health-connect)
- [Samsung Health Data SDK 요구사항](https://developer.samsung.com/health/data/guide/introduction.html)

---

Fitmate 팀원은 같은 `minSdk`, `compileSdk`, `targetSdk`, Java 버전을 사용해야 한다. Android Studio 자체의 패치 버전이 조금 다른 것은 가능하지만, Gradle 및 SDK 기준은 Git에 저장된 프로젝트 설정을 따른다.
