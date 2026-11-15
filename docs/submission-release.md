# 제출용 release 검증

검증일: 2026-11-15. 제출 버전: 0.3.0 (versionCode 3), 태그: `v0.3.0-submission`.

## 환경

- Pleos Connect 2.1.0 / `RELEASE.release_connect_v2.1.0 test-keys`
- Android 14 / API 34 / x86_64 / Android Emulator 37.2.13.0
- 2560×1440px / 160dpi / RAM 4GB / CPU 4코어 / GPU host
- 기존 개발용 AVD와 별개인 새 AVD에서 release APK를 설치했다. Android user 10을 사용했다.
- 앱 ID: `com.hmgchallenge.pleosstarter`

## 결과

| 검사 | 결과 |
| --- | --- |
| release 빌드 | `:app:assembleRelease` 성공 |
| APK 서명 | 별도 배포 키, RSA 3072bit / APK Signature Scheme v2 검증 통과 |
| 디버깅 | APK 및 설치 후 패키지에 DEBUGGABLE 플래그 없음 |
| 단위 테스트 | `:app:testReleaseUnitTest` 136개 통과, 실패·오류 0개 |
| lint | `:app:lintRelease` 오류 0개, 경고 27개 |
| 실제 APK 설치·실행 | 새 Pleos 기기에 설치 성공, 런처 Activity 정상 시작 |
| 네 시연과 기록 | `PASS_PRODUCT_STRUCTURE_AND_FOUR_REPLAYS` |
| 백미러 전환·음성 | 도식 → 후방 카메라 → 도식, SDK P → R → P, 완료·상세 피드백, 기어·핸들 TTS 확인 |

네 시연 검사는 별도 테스트 DB에서 기본 연결 시연·차로 변경·우회전·후진 주차의 완료 근거, 기록 저장, 중복 보상 방지, DB 재오픈과 초기화를 확인한다. 네 시나리오는 가상 시간으로, 별도 차로 변경과 탭 이동·일시정지·재개는 실제 경과 시간으로 검사했다.

백미러 검사는 release APK가 로컬 Virtual Console에 신호를 보내는 상태에서 실제 시간으로 수행했다. 시스템 후방 카메라 전환과 그 위의 안내 카드, 음성 재생 완료, 같은 주행 ID 유지와 완료 화면을 확인했다.

## 재실행

`keystore.properties.example`을 참고하여 본인 서명 키를 설정한다. 서명 키와 비밀번호는 Git에 포함하지 않는다.

```powershell
.\gradlew.bat :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
.\gradlew.bat -PtestBuildType=release :app:assembleReleaseAndroidTest
```

일반 개발용 에뮬레이터 대신 테스트용 AVD를 사용한다. 기기 번호와 Android user ID는 실제 환경에 맞춘다. 앱과 테스트 APK는 같은 키로 서명되어야 한다.

```powershell
adb -s emulator-5576 install -r --user 10 app/build/outputs/apk/release/app-release.apk
adb -s emulator-5576 install -r --user 10 app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb -s emulator-5576 shell pm grant --user 10 com.hmgchallenge.pleosstarter android.car.permission.CAR_SPEED
adb -s emulator-5576 shell appops set --user 10 com.hmgchallenge.pleosstarter SYSTEM_ALERT_WINDOW allow

adb -s emulator-5576 shell am instrument --user 10 -w -r com.hmgchallenge.pleosstarter.test/com.hmgchallenge.pleosstarter.product.ProductSmokeRunner
adb -s emulator-5576 shell am instrument --user 10 -w -r -e mirror parking_modes com.hmgchallenge.pleosstarter.test/com.hmgchallenge.pleosstarter.product.ProductSmokeRunner
```

## 배포 APK

- 파일명: `chageun-0.3.0-release.apk`
- SHA-256: `1d692e35756adbacedea0b65e958622c6f9a47499cef2d79a3170edfd988eb7e`
- 인증서 SHA-256: `016efb8218da8bc10bda299c8cbdd3439cd0732593f99757c23ad8e0775cc379`
- [다운로드와 검증 로그](https://github.com/yooniseo/pleos-app-challenge/releases/tag/v0.3.0-submission)

이 결과는 Pleos 에뮬레이터의 시연 검증이다. 실제 도로·실차 주차 성공 판정이나 차량 탑재 승인을 의미하지 않는다.
