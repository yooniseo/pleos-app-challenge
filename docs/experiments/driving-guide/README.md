# 쉬운 주행·주차 안내 실험 (Figma 3번)

**실주행 준비 상태:** 현재 주행 코치는 정차용 화면이며 실주행 가능한 완제품이 아닙니다. [주행 중 차단 원인·에뮬레이터 수정·공식 정책 및 제품 요건](driving-availability-2026-11-12.md)을 확인하세요.

운전석처럼 직접 조작하는 최신 체험은 [운전석 조작 체험과 검증](driver-cockpit-2026-11-12.md)을 확인하세요. 주차 출발→좌/우 차로 변경→좌/우회전→정차를 핸들·페달·기어·깜빡이 입력으로 진행합니다.

최신 추가 작업은 [지도·차로 계획·차량 상호작용 및 Tutorial.docx 비교](map-vehicle-2026-11-12.md)를 먼저 확인하세요. 앱 내 실지도·참고 경로·차로 수 표시와 실제 기어/벨트/주차 브레이크 읽기, 정차 중 실내 조명 체험을 추가했습니다. 기본 내비 앱의 흰 지도 문제는 별도로 남아 있습니다.

브랜치: `scratch/driving-guide`. 최신 `main`에서 시작한 독립 실험입니다. 기존 `scratch/quests` 작업을 삭제·이름 변경·병합하지 않았습니다. `main` 및 기존 `MainActivity.kt`를 수정하지 않습니다. 실행 아이콘 **튜토리얼 · 주행 코치**를 추가했습니다. 기존 스타터 아이콘도 유지됩니다.

집 PC에서 재개할 때는 [집 PC 실행·파트 연결 검증 결과](home-pc-validation-2026-11-12.md)를 먼저 읽습니다. 공식 Pleos 에뮬레이터에서 방향지시등 조회·구독, 속도 0/5m/s, 내비 TBT, TTS 시작·완료를 확인했습니다. [HANDOFF.txt](HANDOFF.txt)는 이전 PC의 중단 지점이며, [실제 콜백 검증 방법](runtime-probe.md)으로 재검증할 수 있습니다.

Figma의 번호는 **1 퀘스트 / 2 주행 피드백 / 3 쉬운 안내**입니다. 이 브랜치는 주행·주차 코치를 구현하고, 퀘스트와 피드백은 연결 경계만 제공합니다. 점수, 성장, 단계별 완료 동그라미를 여기서 다시 구현하지 않습니다.

## 확인한 기획

- [3번 쉬운 안내](https://www.figma.com/design/UTgSBQztO4iDsnoTUyetPL/?node-id=201-4535)
- [별도 주행·주차 상세 메모](https://www.figma.com/design/UTgSBQztO4iDsnoTUyetPL/?node-id=239-4532)
- [상황·안내·기록 연결 스케치](https://www.figma.com/design/UTgSBQztO4iDsnoTUyetPL/?node-id=201-4536)
- 사용자가 제공한 전체 Figma 검토 문서도 반영했습니다. Figma는 회의 보드이고 확정된 주행 화면의 픽셀 명세가 없어, 기존 Android Views와 어두운 차량 앱 스타일로 실행 가능한 화면을 구성했습니다. 차량 계기판이나 시스템 내비를 새로 그리지 않습니다.
- 주행/주차 Notion 상세 링크는 연결 도구에서 모두 `404 / object_not_found`로 접근하지 못했습니다. 접근하지 못한 내용을 구현했다고 간주하지 않습니다.

## 시나리오와 화면

**한 교차로에서 오른쪽 차로 변경 준비 → 우회전 → 그 교차로 빠져나오기**입니다. 별도의 다음 교차로를 통과하는 시나리오가 아닙니다. 도식은 차로 수를 감지하거나 ‘4차선’을 ‘4지 교차로’로 해석한 결과가 아닙니다.

- 주행: 현재 한 가지 안내, 살펴볼 방향, 방향지시등 관측, 내비 거리·설명, 반복 요청/음성 중단, 어려운 상황 기록.
- 시뮬레이션: 작은 진입로와 이번 교차로 구별, 3단계 다음 장면·자동 재생·처음부터. 모든 이벤트의 출처는 `DEMO`입니다.
- 실제 차량: 현재 경로의 Pleos TBT로 우회전 준비·지점 확인 안내. 실제 카메라나 차로 인식처럼 표시하지 않습니다. 그림 속 진입로를 실제 랜드마크로 말하지 않습니다.
- 주차: 후진/전면/평행의 주변 확인 → 시작 위치 이해 → 진입 흐름 → 정렬 → 마무리, 각 단계 도식. 차량별 회전량·센서 기하 정보가 없으므로 ‘핸들 두 바퀴’ 등의 실시간 조작 지시를 만들지 않습니다.
- 하차: 기어 P·주차 브레이크·계기판 경고·주변 확인을 **사용자 직접 확인**으로 기록합니다. 차량에서 자동 검증한 상태라고 표시하지 않습니다. 체크를 누르는 행위가 전체 주차 퀘스트 완료를 의미하지 않습니다.

## 실제로 사용하는 API

| SDK | 호출 | 권한 | 용도와 한계 |
|---|---|---|---|
| NaviHelper 3.0.5 | `initialize`, `addListener`, `getTBTInfo`, `onTBTInfo` | `pleos.car.permission.NAVI_ROUTE` | 다음 안내의 `TurnType`, 미터 단위 거리, 설명. 현재 차로·교통 신호·보행자·안전한 간격은 제공하지 않음 |
| NaviHelper 3.0.5 | `getCurrentLocationInfo`, `onCurrentLocationInfo` | 같은 권한 | `routeId` 변경 시 기존 상황 무효화. 위치/heading만으로 회전 완료를 판정하지 않음 |
| NaviHelper 3.0.5 | 경로 시작/완료, 재탐색 완료, 경로 취소, 목적지 변경/도착, 오류 리스너 | 같은 권한 | 이전 큐·음성을 중단하고 새 입력 대기. 도착만으로 주차/차로 변경 성공 처리 안 함 |
| NaviHelper 3.0.5 | `requestNavigationScreen(Foreground)` | 기존 `NAVI_CUSTOM_MAP` | 정차 중 연결 상태 창에서 차량 내비 열기. 임의 목적지로 경로를 바꾸지 않음 |
| Vehicle 2.3.2 | `checkTurnSignalStateCapabilities`, `getCurrentTurnSignalState`, `registerTurnSignalState`, `unregisterTurnSignalState` | `pleos.car.permission.CAR_LIGHTS` | 읽기 지원 확인 후 RIGHT/LEFT/OFF/null 조회·구독. 점등은 행동의 관측이며 차로 변경 성공 근거가 아님 |
| TextToSpeech 3.3.1 | `TextToSpeech(context, Mode.ON_DEVICE)`, 초기화/리스너, `setLocale(Locale.KOREA)`, `speak`, `stop`, `release` | `pleos.car.permission.TTS_SERVICE` | 실제 Pleos 음성 서비스. 기본 음성 OFF, 정차 중 켜기. 앱 서버 등록/키 불필요. 서비스·언어·권한 오류는 화면에 표시 |
| AAOS | 기존 `SpeedSource`의 `PERF_VEHICLE_SPEED` | 기존 `android.car.permission.CAR_SPEED` | 유효한 최근 속도가 있을 때만 정차 확인. null을 0으로 바꾸지 않음 |
| AAOS | `CarUxRestrictionsManager` | 플랫폼 서비스 | 정차 학습/설정 잠금. 주행 제한 또는 상태 미확인이면 상세 화면 닫음 |

문서뿐 아니라 배포된 Vehicle/NaviHelper 소스와 TextToSpeech AAR의 실제 공개 메서드도 대조했습니다. TTS sources.jar는 404여서 배포 바이너리와 공식 문서로 확인했습니다.

공식 근거: [TurnSignal 공개 메서드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/turn-signal/entry), [NaviHelper 이벤트](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners), [TTS 모드와 사용 순서](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro), [TTS 요청·중단](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/start-and-stop).

시뮬레이션의 자동 방향지시등 옵션은 설정 적용 후 `처음부터`로 체험을 시작하면 반영됩니다.

**실제 방향지시등 자동 점등 API는 공개되어 있지 않습니다.** 존재하지 않는 `setTurnSignal()`을 호출하지 않습니다. 옵션은 시뮬레이션에만 있고 그림의 표시만 바뀝니다. 실제 모드에서는 OFF 관측 시 직접 켜도록 안내하고, 값이 없으면 ‘상태 미확인’으로 표시합니다. 새로 추가한 `CONTROL_CAR_LIGHTS` 일반 권한은 실내 조명 체험에만 사용합니다.

### 실제 안내 전환 정책

1. 다음 TBT가 유효한 `TURN_RIGHT`이고 300m 이내이면 차로 준비 안내를 시작합니다.
2. 80m 이내에서는 우회전 지점 확인 안내로 전환합니다. 이는 **안내 단계 전환**입니다. 차로 변경 완료는 `UNVERIFIED`로 남깁니다.
3. 실제 우회전·교차로 이탈 완료는 이 SDK 입력만으로 확정하지 않습니다. `VerifiedManeuver`를 주입할 수 있는 경계를 준비했고, 기본 차량 어댑터는 그런 데이터를 만들지 않습니다.
4. 다음 안내 변경, 거리 급변, 경로 변경, 내비 오류 또는 8초 이상 갱신 중단이면 큐를 중단합니다. 한 번의 우회전에서 준비·우회전·이탈은 같은 `situationInstanceId`를 씁니다.

300m/80m/8초는 **시연용 조정 가능 정책**이며 최적 타이밍이나 검증된 운전 기준이 아닙니다. TBT에는 고유 maneuver ID가 없어 가까운 유사 교차로 구분의 정확도를 보장할 수 없습니다. 교차로 모양/실시간 상황 기반의 정밀 안내에는 별도 지도·센서 공급자가 필요합니다. 시스템 내비/OEM 경고를 끄거나 변경하지 않습니다.

## 퀘스트·주행 피드백 개발자가 붙이는 방법

코드: `app/src/main/java/com/hmgchallenge/pleosstarter/driving/CoachContract.kt`와 `CoachRepository.kt`.

호스트에서 Activity를 열기 전에 다음 연결을 설정합니다. 퀘스트가 미연결이어도 주행 도움은 작동합니다.

```kotlin
CoachHostBindings.activeTripId = tripRepository.currentTripId // 피드백의 동일 주행 UUID
CoachHostBindings.questPort = object : QuestCoachPort {
    override fun preferences() = CoachPreferences(
        levels = mapOf(SituationType.LANE_CHANGE_RIGHT to GuidanceLevel.BRIEF)
    )
    override fun focusedGoalLabel() = "우회전 전에 차로 미리 준비하기"
    override fun onCoachEvent(event: CoachEvent) {
        // eventId 중복 제거, DEMO 분리, 수행 근거를 보고 퀘스트 파트에서 판단
        // CUE_SHOWN / SIGNAL_OBSERVED / UNVERIFIED만으로 완료 동그라미를 채우지 않기
    }
}
CoachHostBindings.feedbackPort = object : FeedbackCoachPort {
    override fun onCoachEvent(event: CoachEvent) { /* 상황 타임라인 입력 */ }
    override fun onNote(note: FeedbackNoteDraft) {
        // scratch/gain의 DrivingNote(noteId, tripId, t, rawText, category)로 옮겨 저장
        // category: LANE_CHANGE / RIGHT_TURN / PARKING. source=DEMO는 실제 주행에 섞지 않기
    }
}
startActivity(Intent(context, DrivingCoachActivity::class.java))
```

`scratch/gain` 확인 기준 커밋 `fb6b4a7`에는 `docs/driving-feedback-plan.md`, `SttProbe.kt`, 스타터 음성 검증 화면이 있습니다. **Figma 2번 주행 피드백 작업이 맞고, 브랜치명을 변경하지 않았습니다.** 해당 브랜치의 Room/TripRepository/분석 화면은 아직 구현되어 있지 않아, 여기에 존재하지 않는 DB 클래스를 import하지 않았습니다. 실제 통합 시 두 파트의 기록 저장소를 합의해 연결합니다. `gain`을 이 브랜치로 병합하거나 수정하지 않았습니다.

STT의 `completed=true` 문장을 `activity.handleCoachCommand(text)`에 전달하면 다시/반복, 멈춰/그만/중단, 어려움/기억 요청을 처리합니다. 이 브랜치가 마이크를 중복으로 열지 않습니다. 앱이 화면에 떠 있는 동안만 코치를 실행합니다.

연결 후 설정이 바뀌면 `activity.refreshPreferencesFromHost()`로 최신 설정을 적용합니다. 프로세스가 종료되면 메모리의 host binding을 다시 설치해야 합니다.

### 이벤트와 재전달

```json
{
  "schemaVersion": 1,
  "eventId": "UUID",
  "tripId": "shared-trip-UUID",
  "situationInstanceId": "same-intersection-UUID",
  "situationType": "LANE_CHANGE_RIGHT",
  "eventType": "SIGNAL_OBSERVED",
  "timestamp": 1794400000000,
  "source": "VEHICLE",
  "assistanceUsed": true,
  "guidanceLevel": "DETAILED",
  "cueId": null,
  "detail": "RIGHT"
}
```

안내가 화면에 실제 표시됐을 때만 해당 상황의 `assistanceUsed`를 켭니다. 요청 시 모드에서 자동 설명을 사용한 것으로 기록하지 않습니다. 어려움 메모는 수행 실패/감점 이벤트가 아닙니다. `CANCELLED`, `UNVERIFIED`, 사용자 확인과 검증 공급자 수행도 분리됩니다.

이벤트는 앱 내부 `files/driving-coach-events.jsonl`에 먼저 저장한 뒤 각 포트에 보냅니다. 포트 오류로 코치가 죽지 않으며 `CoachRepository.eventsAfter(lastEventId)`로 재전달할 수 있습니다. 소비자는 마지막 성공 eventId를 보관하고 중복 제거해야 합니다. 생산자는 DB 완료/퀘스트 달성을 대신 보장하지 않습니다. 저널은 실험용 로컬 보관이며 실제 통합 시 보관 기간과 삭제 UI를 정해야 합니다.

## 실행·검증

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.hmgchallenge.pleosstarter/.driving.DrivingCoachActivity
```

실제 Pleos에서는 차량 내비에서 경로를 먼저 시작하고, 코치에서 `실제 Pleos 연결`을 선택합니다. 연결 상태 창에서 내비·방향지시등·음성의 상태를 각각 볼 수 있습니다. 일반 Android 기기에서는 가상 값으로 실제 연결을 흉내 내지 않습니다.

검증 결과와 캡처는 완료 후 `validation.md`에 기록합니다.
