# 시연 가이드: 주행 → 피드백 → 맞춤 퀘스트 추천

Pleos 에뮬레이터에서 Virtual Console로 차를 움직여, 차근차근 앱이 주행을 기록하고 점수·피드백·추천 퀘스트를 보여 주는 과정을 재현한다. 모든 명령은 **Windows PowerShell** 기준이다.

## 0. 준비 (처음 한 번)

| 항목 | 내용 |
|---|---|
| Android Studio | SDK Manager에서 Android SDK Platform 34, Build-Tools 34.0.0 설치 |
| Pleos 에뮬레이터 이미지 | SDK Update Sites에 `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` 추가 → SDK Platforms(Show Package Details)에서 Android 14 아래 Pleos Connect 이미지 설치 |
| 프로젝트 위치 | **영문 경로**에 clone (예: `C:\Android\pleos-app-challenge`). 한글 경로는 빌드가 거부됨 |
| JDK | Android Studio → Settings → Build Tools → Gradle → Gradle JDK를 **17**로 |

```powershell
git clone https://github.com/yooniseo/pleos-app-challenge.git C:\Android\pleos-app-challenge
cd C:\Android\pleos-app-challenge
git switch feature/driving-analysis
```

### Pleos 가상 기기 만들기

최신 Android Studio의 Device Manager에서는 Pleos 이미지를 고르면 Finish가 눌리지 않는 경우가 있다. 그럴 땐 아래 명령으로 직접 만든다(한 번만).

```powershell
New-Item -ItemType Directory -Force C:\Android\avd\Pleos_API_34.avd | Out-Null
@"
avd.ini.encoding=UTF-8
path=C:\Android\avd\Pleos_API_34.avd
target=android-34
"@ | Set-Content -Encoding ascii C:\Android\avd\Pleos_API_34.ini
@"
AvdId=Pleos_API_34
avd.ini.displayname=Pleos API 34
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=4
image.sysdir.1=system-images\android-34\pleos_car_emulator\x86_64\
tag.id=android-automotive
tag.display=Automotive
hw.device.name=Pleos_Connect_v2_0
hw.lcd.width=2560
hw.lcd.height=1440
hw.lcd.density=160
hw.ramSize=4096
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=host
hw.audioInput=yes
disk.dataPartition.size=6G
fastboot.forceColdBoot=yes
"@ | Set-Content -Encoding ascii C:\Android\avd\Pleos_API_34.avd\config.ini
```

> SDK를 `C:\Android\Sdk`가 아닌 곳에 설치했다면 아래 명령의 경로를 바꾼다.

## 1. 에뮬레이터 켜기 (PowerShell 창 ①, 켜 둔 채로 둠)

```powershell
$env:ANDROID_AVD_HOME="C:\Android\avd"
& "C:\Android\Sdk\emulator\emulator.exe" -avd Pleos_API_34 -no-snapshot
```

- 3~5분 뒤 Pleos 지도 홈("어디로 갈까요?")이 나오면 준비 완료. 지도가 하얗게 비어 있는 건 정상이다(에뮬레이터에 지도 데이터 없음).
- 화면이 계속 검으면 창을 닫고 명령 끝에 ` -wipe-data`를 붙여 다시 켠다(앱·기록 초기화).

## 2. 앱 빌드·설치·실행 (PowerShell 창 ②)

```powershell
cd C:\Android\pleos-app-challenge
.\gradlew.bat :app:assembleDebug
$adb="C:\Android\Sdk\platform-tools\adb.exe"
& $adb install -r -g app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start --user 10 -n com.hmgchallenge.pleosstarter/.app.ChageunActivity
```

- 첫 실행은 1분쯤 걸린다. **앱이 뜬 뒤 1분 기다렸다가** 주행을 시작한다(초기에는 기록 간격이 벌어져 급가속·급감속을 놓친다).
- 앱 아이콘은 에뮬레이터 하단 막대의 파란 안드로이드 로봇.
- "자동 시연 시작"은 누르지 않는다(팀의 시연 모드라 화면이 자동으로 넘어간다).

## 3. 시나리오 실행

**매 주행 전**: 앱 **퀘스트 → 차량 신호 퀘스트** 탭에서 오른쪽 아래 버튼이 **"주행 기록 마치기"** 인지 확인. **"차량 연결 시작"** 이면 먼저 누른다.

### 시나리오 1 · 거친 출발·감속 (약 2분 30초)

```powershell
cd C:\Android\pleos-app-challenge
powershell -ExecutionPolicy Bypass -File scripts\demo-harsh-drive.ps1
```

급출발 1 · 급가속 1 · 급감속 2 → 출발 52점, 감속 52점 → 추천 **"앞차와 3초 거리 두기"**, **"3초 세며 천천히 출발하기"**.

### 시나리오 2 · 깜빡이·회전 감속 (약 2분)

```powershell
cd C:\Android\pleos-app-challenge
powershell -ExecutionPolicy Bypass -File scripts\demo-signal-drive.ps1
```

깜빡이 없이 차선 변경 3번, 깜빡이 켜고 1번, 브레이크 없이 우회전 1번 → 깜빡이 25점, 회전 전 감속 0점 → 추천 **"교차로 우회전 · 감속 먼저"**, **"차선 변경 · 깜빡이 먼저"**. 주행 중 차량 신호 퀘스트 화면의 "재시도 · 성공" 숫자가 실시간으로 오른다.

## 4. 결과 보기

1. **주행 기록 마치기** → 피드백 화면: 한 줄 요약, 기술별 점수, 잘한 점, 연습할 점
2. **맞춤 퀘스트** → 추천 화면: 최근 평균 점수, 추천 퀘스트(이유·진행도)
3. 시나리오 1을 **3번** 반복하면 추천 이유가 "최근 3번 평균 …점", "…번 중 3번 70점 미만"처럼 반복 기준으로 바뀐다. 시연 전에 미리 2번 돌려 두고 현장에서는 1번만 보여 주면 시간이 줄어든다.

## 알아 둘 점

- 스크립트는 Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088`)을 사용한다. 브라우저로 같은 주소를 열면 속도·핸들·깜빡이를 손으로 조절할 수도 있다.
- **핸들 부호**: Virtual Console에서 +값을 보내면 앱에는 −값(왼쪽)으로 들어온다. 퀘스트 판정은 +를 오른쪽으로 보므로 스크립트는 오른쪽 동작을 음수로 보낸다. 실제 차량에서의 부호는 별도 확인이 필요하다.
- PowerShell에서 직접 함수를 만들 때 `H`라는 이름은 쓰지 않는다(`Get-History`의 별칭이라 함수 대신 실행됨).
- 이 Pleos 에뮬레이터 이미지는 프로필 서비스가 반복해서 죽거나 스스로 재부팅되는 등 불안정할 때가 있다. 검은 화면이 오래가면 1단계의 `-wipe-data`로 다시 켠다.
