# Samsung Health Data SDK 테스트

이 기능은 Health Connect를 거치지 않고 Samsung Health 저장소를 직접 읽습니다. Samsung Health 6.30.2 이상과 Android 10 이상 실제 휴대전화가 필요하며, 에뮬레이터에서는 검증할 수 없습니다.

1. Galaxy Wearable에서 Galaxy Fit2를 연결하고 Samsung Health에서 걸음 수와 운동 기록을 확인합니다.
2. Samsung Health의 설정에서 Samsung Health 정보를 열고 버전 표시 영역을 10회 이상 눌러 Developer mode 메뉴가 표시되는지 확인합니다.
3. **Developer Mode (Samsung Health Data SDK)**에서 **Developer Mode for Data Read**를 활성화합니다.
4. Fitmate를 실행해 걸음 수와 운동 읽기 권한을 허용하고 전체 새로고침을 누릅니다.
5. Samsung Health의 오늘 전체 걸음 수·최근 운동과 Fitmate 화면을 비교합니다.

개발 모드는 테스트와 디버그 전용입니다. 일반 사용자에게 활성화 절차를 제공하면 안 됩니다. 공개 배포 전에는 Samsung 파트너 신청 후 `com.fitmate.app` 및 실제 배포 서명 인증서 SHA-256을 등록해야 합니다.

Samsung Health 앱에서 권한을 변경할 수 있습니다. 개발 모드가 꺼져 있거나 Data Read가 비활성화되면 인증 오류가 표시됩니다. 건강 데이터와 전체 기기 ID는 Logcat이나 스크린샷에 노출하지 마세요.
