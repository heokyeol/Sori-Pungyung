**앱 이름** : 소리풍경(Sori-Pungyung)
**한 줄 소개** : 차량의 사운드 시스템을 사용해 자연의 소리를 3차원 공간감으로 재구성하는 모빌리티 전용 사운드스케이프 앱

**실행 방법**
JDK 17로 빌드: ./gradlew assemblePleosDebug
설치: adb install -r app/build/outputs/apk/pleos/debug/app-pleos-debug.apk
실행: adb shell am start -n dev.homesev.sounddrive/.MainActivity
**테스트 환경 및 필요한 설정**
빌드에 필요한 환경 : JDK 17, Android SDK 34, Pleos SDK 저장소(nexus-playground.pleos.ai)
테스트에 필요한 설정 : 위치 권한 허용 (지역 시그니처 사운드 수집 기능을 위함)
**Pleos Connect 에뮬레이터 버전과 화면 규격**
에뮬레이터 버전 : Pleos Connect release_connect_v2.1.0(Android 14, API 34, arm64)
화면 규격 : 2560 × 1440, 160dpi
**Github Release 링크**
https://github.com/heokyeol/Sori-Pungyung/releases/tag/v0.28
