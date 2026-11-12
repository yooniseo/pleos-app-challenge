# Vehicle SDK·STT 사전 검증 기록

> 검증일: 2026-11-12 · 환경: Pleos Connect Emulator v2.1.0 (Android 14, API 34, `user` 빌드)
> 테스트 앱: `scratch/gain`의 스타터 앱 + `SttProbe` (실제 차량 모드로 실행)

## 결과 요약

| # | 항목 | 결과 | 근거 |
|---|---|---|---|
| V1 | 차량 속도 수신 (AAOS `PERF_VEHICLE_SPEED`) | ✅ 통과 | Virtual Console로 0→10→25→50→30→0 km/h 주입 → 앱이 같은 값 수신. 갱신 주기 약 0.1~0.2초 |
| V2 | STT (`SpeechToText` 3.3.1, `Mode.ON_DEVICE`) | ✅ 통과 | **30 km/h 주행 중** "방금 비보호 좌회전이 어려웠어" 인식. 발화 시작 → 최종 결과 약 2.5초 |
| — | 권한 | ✅ | `android.car.permission.CAR_SPEED`, `pleos.car.permission.STT_SERVICE` 모두 `granted=true` (`adb install -g`) |

## V1. 속도

- 기존 `SpeedSource.kt` 그대로 동작합니다. 값은 m/s(`Float`)로 오고, 30 km/h는 `8.333334` m/s → `30.000002` km/h로 표시됩니다(부동소수점 오차, 반올림해서 쓰면 됨).
- 콜백이 초당 5~10회 오므로, 계획대로 **1초 타이머에서 최신 값만 저장**하면 됩니다.
- 기어 P에서 D로 바꾸고 속도를 올려도 앱이 계속 값을 받았습니다.

## V2. STT

- 초기화(`initialize`) → `addListener` → `request()` 순서로 동작합니다. 앱 등록(My Project) 없이 `ON_DEVICE`로 사용 가능합니다.
- **첫 `request()`는 모델 로딩 때문에 약 40초** 걸렸습니다. 이후 요청은 바로 시작됩니다. → 앱 시작 시 미리 `initialize()` 해 두고, 첫 사용이 느릴 수 있음을 염두에 두세요.
- 말하는 동안 `onUpdated(text, completed=false)`가 계속 오고, 끝나면 `onEndedRecognition()` 다음 `onUpdated(text, completed=true)`가 옵니다.
- `onStartedRecognition()`은 부분 결과마다 여러 번 호출됩니다. **메모 시각은 첫 호출 시각만** 쓰세요.
- 인식 결과 예: "방금 비보호 좌회전이 어려웠어" → 키워드 분류표의 "비보호"로 `LEFT_TURN` 분류 가능
- 에뮬레이터에서는 Extended Controls → Microphone → "Virtual microphone uses host audio input"을 켜야 PC 마이크가 들어갑니다.

## ⚠ 필수: 주행 중 앱 차단

차가 움직이면(D 기어 + 속도 > 0) **"주행 중에는 사용할 수 없습니다"** 화면으로 앱이 차단됩니다. 운전 중 기록·메모 화면을 띄우려면 Activity에 아래를 넣어야 합니다.

```xml
<activity android:name=".MainActivity" android:exported="true">
    <meta-data android:name="distractionOptimized" android:value="true" />
    ...
</activity>
```

넣은 뒤에는 30~50 km/h 주행 중에도 앱 화면과 속도 수신, STT가 모두 정상 동작했습니다. 이 설정을 넣으면 주행 중 화면은 단순하게 유지해야 합니다(계획서 A-4).

## 차량 값 바꾸는 방법 (Virtual Console)

`cmd car_service set-property-value` 같은 adb 명령은 `user` 빌드라서 **막혀 있습니다**(`requires non-user build`). 공식 Virtual Console을 씁니다.

1. `adb forward tcp:8088 tcp:8088`
2. 브라우저에서 `http://127.0.0.1:8088/` 접속 → Motion 도메인에서 기어·속도·가속/브레이크 페달·조향각 조절
3. 브라우저 탭을 닫으면 조절이 꺼집니다(`emulator_inactive`). 스크립트로 쓸 때는 `POST /browser/session?client=<이름>`을 1초마다 보내야 합니다.

스크립트 예시 (Git Bash):

```bash
B=http://127.0.0.1:8088
while true; do curl -s -o /dev/null -X POST "$B/browser/session?client=test"; sleep 1; done &
curl -s -X PUT "$B/vehicle/gear?gear=Drive"
curl -s -X PUT "$B/vehicle/controls?speed=30&accelerator=10&brake=0"     # km/h, %, %
curl -s -X PUT "$B/vehicle/controls?speed=30&accelerator=0&brake=0&steering=-90"  # 조향각(도)
```

- 범위: 속도 0~240 km/h, 페달 0~100%, 조향각 −540~540°
- 이 방법으로 급가속·급감속 시나리오를 그대로 재현할 수 있습니다(재생기 대신 시연에도 사용 가능).

## 추가로 쓸 수 있는 차량 정보

주최 측 권한 목록과 Virtual Console에서 아래 정보도 사용할 수 있음을 확인했습니다. 앱에서 SDK로 실제 값을 받아 보는 것은 아직 하지 않았습니다.

| 권한 | 쓸 만한 API | 활용 아이디어 |
|---|---|---|
| `CAR_DRIVE` | `getCurrentBrakePedalStatus` (브레이크 페달 눌림) | 급감속 판단 보강 (속도 변화 + 페달) |
| `CAR_DRIVE` | `getCurrentSteeringWheelAngle` (전륜 조향각) | 회전 감지 (조향각이 일정 이상 2초 유지) |
| `CAR_DRIVE` | `getCurrentDistanceDriven`, `getTripCurrentDrivingDistance/Time` | 주행 거리·시간 (속도 적분 대신) |
| `CAR_LIGHTS` | `getCurrentTurnSignalState` (방향지시등) | 깜빡이 없이 회전 감지 (조향각과 조합) |
| `CAR_SAFETY` | `hasSeatBeltNotBuckledWarning` 등 | 출발 전 안전 점검 (선택) |

Virtual Console의 Motion 도메인에는 **가속 페달, 브레이크 페달, 조향각** 조절 기능이 있어서 위 기능도 에뮬레이터에서 테스트할 수 있습니다.

## 검증하면서 겪은 환경 문제

| 문제 | 해결 |
|---|---|
| 프로젝트 경로에 한글이 있으면 빌드 거부 (`Your project path contains non-ASCII characters`) | 영문 경로(예: `C:\Android\pleos-app-challenge`)로 옮기기 |
| Android Studio 최신판 Device Manager에서 Pleos API 34 이미지 선택 시 Finish 비활성화 | 이미지 태그(`android-automotive`)와 설치 폴더(`pleos_car_emulator`)가 달라서인 것으로 추정. AVD 설정 파일을 직접 만들어 해결 (아래) |
| Build-Tools 34.0.0 폴더 손상 | 폴더 삭제 후 재설치 |
| Gradle 8.1.1은 JDK 21 이상에서 실행 불가 | Gradle JDK를 17로 설정 |

AVD 직접 만들기: `C:\Android\avd\Pleos_API_34.avd\config.ini`에 `image.sysdir.1=system-images\android-34\pleos_car_emulator\x86_64\`, `tag.id=android-automotive`, `hw.lcd.width=2560`, `hw.lcd.height=1440`, `hw.lcd.density=160`, `hw.ramSize=4096`, `hw.audioInput=yes`, `fastboot.forceColdBoot=yes`를 넣고, 환경 변수 `ANDROID_AVD_HOME=C:\Android\avd`로 `emulator -avd Pleos_API_34`를 실행합니다.
