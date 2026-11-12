# Pleos 실제 콜백 검증 방법

이 검증은 앱 내부 시뮬레이션을 사용하지 않습니다. `live_mode=true`로 실제 SDK 연결을 실행하고 콜백에서만 관측 증거를 수집합니다. Pleos 에뮬레이터의 값은 실제 차량 실측이 아니라 **에뮬레이터 서비스가 제공하는 차량 데이터**입니다.

## 실행 환경

[Pleos 공식 설치 안내](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)는 Cold boot와 RAM 4GB를 안내합니다. 이전 2GB 실행과 USB 디스크의 원본은 그대로 두고, 이번 검증에는 별도의 SSD 복사본을 사용했습니다. 시스템 이미지의 원본과 복사본 SHA-256을 비교했습니다.

원본/SSD 복사본 시스템 이미지 SHA-256:
`cdb8f0d23043eb5a42cfe0ec7cd2105d5a92a19c8d2beee3fad15c0992e19769`

로컬 이미지·AVD·실행 로그는 Git에서 제외되는 `app/build/pleos-runtime/` 아래에 있습니다. 이 경로의 디스크 이미지는 Git에 올리지 않습니다. 빌드 후 Gradle과 이번 작업의 Kotlin 컴파일러를 종료하고 에뮬레이터를 따로 실행합니다. 부팅은 6분 제한으로 관찰하며 호스트 가상 메모리 여유가 부족하면 이번 작업의 에뮬레이터만 종료합니다.

## 엄격한 API 검증 실행

```powershell
adb -s emulator-5574 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5574 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5574 shell am instrument -w -r -e live_probe 1 -e strict_live 1 -e seconds 120 com.hmgchallenge.pleosstarter.test/com.hmgchallenge.pleosstarter.driving.CoachSmokeRunner
```

관측 파일:
`/sdcard/Android/data/com.hmgchallenge.pleosstarter/files/coach-smoke/pleos-runtime-proof.json`

같은 내용을 앱 내부 `files/pleos-runtime-proof.json`에도 씁니다. AAOS 사용자 10에서 외부 저장소가 거부되면 아래처럼 디버그 앱 권한으로 읽습니다.

```powershell
adb -s <serial> shell am get-current-user
adb -s <serial> shell run-as com.hmgchallenge.pleosstarter --user <userId> cat files/pleos-runtime-proof.json
```

계측 실행에도 실제 사용자 ID를 `am instrument --user <userId>`로 지정하고, 실행 전에 해당 사용자의 `android.car.permission.CAR_SPEED`를 허용합니다. 이관 시점까지 실제 SDK 수신 검증은 미완료입니다. 자세한 중단 지점은 [HANDOFF.txt](HANDOFF.txt)를 참고하세요.

이전의 `live_probe=1` 단독 실행은 관찰이 끝났다는 뜻으로 `OBSERVATION_FINISHED_NOT_VERIFIED`를 반환합니다. 연결 관찰만으로 PASS를 표시하지 않도록 수정했습니다.

`strict_live=1`은 아래 다섯 항목이 모두 확인되어야 `PASS_ACTUAL_CALLBACKS`를 반환합니다. 하나라도 빠지면 `INCOMPLETE_ACTUAL_CALLBACKS`와 실패 결과 코드를 반환합니다.

| 항목 | 필요한 실제 콜백 | 인정하지 않는 상태 |
|---|---|---|
| 방향지시등 조회 | SDK가 제공한 OFF/LEFT/RIGHT 값 | 초기값 UNKNOWN, null, 연결 성공만 수신 |
| 방향지시등 구독 | 등록한 `TurnSignalStateListener`에 유효한 상태 수신 | get 조회만 성공 |
| 속도 | AAOS 속성의 유한한 m/s 값 | null, NaN, 앱 임의 속도 |
| 내비 안내 | `onTBTInfo`의 비어 있지 않은 목록과 유효한 거리 | 연결 완료, 빈 목록, 앱 시나리오 이벤트 |
| 음성 | Pleos TTS의 재생 시작 및 완료 콜백 | SDK 초기화 성공만 수신 |

테스트의 음성 확인 문장은 디버그 가능한 ranchu/goldfish 에뮬레이터에서만 요청할 수 있습니다. 실제 차량에 테스트 문장을 자동 재생하는 기능을 추가하지 않았습니다. `onDone`은 SDK 재생 완료 증거이며, PC 스피커에서 실제 소리를 청취했다는 뜻은 아닙니다.

방향지시등 변화는 [공식 Virtual Console](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide)의 Convenience → Turn Signal State에서 바꾸고, 앱의 GET 및 SUBSCRIPTION 관측이 따라오는지 확인합니다. 차량 앱에 실제 방향지시등 제어 API를 만든 것이 아닙니다. 내비 데이터는 실제 내비 경로가 있어야 하며, 빈 목록을 받았다면 ‘TBT 콜백 수신’과 ‘유효한 경로 안내 수신’을 구분합니다.

관측 배열에는 API 종류, 콜백 종류, 수신 값, 수신 시각이 남습니다. 성공/실패 상태를 화면 문구에서 추측하지 않습니다. 메모리 관측은 최대 512개로 제한하며, JSON 파일은 검증 러너를 실행할 때만 앱 외부 전용 폴더에 저장합니다.
