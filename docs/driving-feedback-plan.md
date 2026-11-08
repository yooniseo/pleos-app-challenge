# 초보 운전자 주행 피드백 — 개발 계획서

> 상태: **검토용 초안** (2026-11-08, `scratch/gain`)
> 범위: A. 주행 기록·메모 / B. 주행 돌아보기 / C. 연습 퀘스트
> 이 문서의 API 이름은 Vehicle SDK 2.3.2·FusedLocation 1.1.3 **배포 소스**와 Pleos 공식 문서로 확인했습니다. 단, **실제 Pleos 에뮬레이터·실차에서 값이 들어오는지는 아직 아무도 검증하지 않았습니다.** 0장의 사전 검증을 먼저 진행하세요.

---

## 목차

0. [먼저 할 일: 사전 검증](#0-먼저-할-일-사전-검증)
1. [공통: 구조와 데이터 약속](#1-공통-구조와-데이터-약속)
2. [파트 A: 주행 기록과 연습 메모](#2-파트-a-주행-기록과-연습-메모)
3. [파트 B: 주행 돌아보기](#3-파트-b-주행-돌아보기)
4. [파트 C: 연습 퀘스트](#4-파트-c-연습-퀘스트)
5. [일정, 브랜치, 열린 질문](#5-일정-브랜치-열린-질문)

---

## 0. 먼저 할 일: 사전 검증

아래 항목이 실패하면 설계가 바뀝니다. 담당자는 `scratch/본인이름`에서 확인하고 결과를 `docs/vehicle-sdk-notes.md`에 기록합니다.

| # | 확인 항목 | 방법 | 실패 시 대안 |
|---|---|---|---|
| V1 | 차량 속도 수신 | 스타터 앱의 `SpeedSource.kt`(AAOS `PERF_VEHICLE_SPEED`)를 Pleos 에뮬레이터에서 실행 | FusedLocation 좌표로 속도 계산 (A-3) |
| V2 | **후진 시 속도가 음수인지** | 에뮬레이터에서 후진 속도 주입 | 주차 중 후진 횟수 지표 제외 |
| V3 | 브레이크 페달·조향각·방향지시등 리스너 | `registerBrakePedalStatus`, `registerSteeringWheelAngle`, `registerTurnSignalState` | 해당 감지 항목을 "미지원"으로 표시 |
| V4 | FusedLocation 갱신 주기 | `onChanged` 호출 간격 로그 | 1초보다 느리면 회전 감지를 조향각 위주로 |
| V5 | ADB로 차량 값 주입 | 공식 문서 "ADB Command Reference" | CSV 재생기(A-7)만으로 테스트 |
| V6 | STT 동작 (에뮬레이터 마이크) | `SpeechToText` `ON_DEVICE` 모드로 `request()` | 텍스트 입력 버튼으로 대체 (데모용) |
| V7 | Gleo App Schema 등록 가능 여부 | 공식 문서 "Delivering the Schema to Gleo" 확인, 주최 측 문의 | STT 버튼만 사용 |
| V8 | 앱이 백그라운드일 때도 기록 유지 | Foreground Service로 내비 화면 전환 후 로그 확인 | 앱을 띄운 상태에서만 기록 (데모 한정) |

---

## 1. 공통: 구조와 데이터 약속

### 1-1. 전체 흐름

```
[A. 주행 중]                         [B. 주행 후]                    [C. 누적]
TripRecorderService                  TripAnalyzer                    WeaknessProfiler
  ├ VehicleSignalSource ─┐             ├ 전처리 (보간, 평활)            ├ 최근 N회 리포트 집계
  ├ SpeedSource          ├→ 1초 스냅샷 ├ 감지기들 → TripEvent           ├ QuestGenerator
  ├ LocationSource      ─┘  TripSample ├ 메모 ↔ 이벤트 연결              └ QuestProgressTracker
  └ DrivingNoteRecorder → DrivingNote  └ ReportBuilder → TripReport
                  │                                │                             │
                  └──────────── Room DB (TripRepository) ────────────────────────┘
```

- **A는 기록만, B는 분석만, C는 누적 판단만** 합니다. 서로는 DB에 저장된 데이터로만 통신합니다.
- 1-3의 데이터 형식만 먼저 합의하면 B와 C는 **A가 끝나기 전에 가짜 데이터로 개발할 수 있습니다.**

### 1-2. 패키지 구조 (팀 규칙 `CONTRIBUTING.md` 기준)

```
com/hmgchallenge/pleosstarter/
├ core/vehicle/      VehicleSignalSource.kt, SpeedSource.kt(기존 이동)
├ core/location/     LocationSource.kt
├ core/voice/        DrivingNoteRecorder.kt, NoteClassifier.kt, GleoActionReceiver.kt
├ core/data/         TripDatabase.kt, TripRepository.kt, entity/*.kt
├ feature/driving/   TripRecorderService.kt, TripSessionDetector.kt, DrivingScreen.kt   ← A
├ feature/review/    TripAnalyzer.kt, detector/*.kt, ReportBuilder.kt, ReviewScreen.kt ← B
├ feature/quests/    WeaknessProfiler.kt, QuestCatalog.kt, QuestGenerator.kt, QuestScreen.kt ← C
└ dev/               TripReplayer.kt (CSV 재생, 디버그 빌드 전용)
```

### 1-3. 데이터 모델 (Room Entity) — **세 파트의 약속**

변경하려면 세 담당자 모두의 합의가 필요합니다.

```kotlin
// ── A가 저장 ──────────────────────────────────────────
@Entity data class Trip(
    @PrimaryKey val tripId: String,       // UUID
    val startedAt: Long, val endedAt: Long?,
    val endReason: String?,               // "MANUAL", "AUTO_STOPPED", "APP_KILLED"
    val speedSource: String,              // "VEHICLE", "LOCATION" — B가 신뢰도 판단에 사용
    val signalSupport: String,            // 지원된 신호 목록 JSON. 예: {"brake":true,"steering":false}
    val distanceKm: Float?
)

@Entity(primaryKeys = ["tripId", "t"]) data class TripSample(
    val tripId: String,
    val t: Long,                 // epoch ms, 1초 간격
    val lat: Double?, val lng: Double?,
    val heading: Float?,         // 도, 0=북, 시계방향 (LocationData.heading)
    val speedKmh: Float?,        // ⚠ 부호 유지: 후진이면 음수 (V2 확인 전까지 그대로 저장)
    val brakeOn: Boolean?,
    val steeringDeg: Float?,     // 앞바퀴 조향각, 왼쪽이 음수
    val turnSignal: String?      // "OFF" | "LEFT" | "RIGHT"
)

@Entity data class DrivingNote(
    @PrimaryKey val noteId: String,
    val tripId: String,
    val t: Long,                 // 발화 시작 시각
    val lat: Double?, val lng: Double?,
    val rawText: String,         // "방금 비보호좌회전 어려웠어"
    val category: String,        // NoteCategory 이름
    val source: String           // "STT_BUTTON" | "GLEO" | "TEXT"
)

enum class NoteCategory { UNPROTECTED_LEFT, LEFT_TURN, RIGHT_TURN, U_TURN, LANE_CHANGE,
    MERGE, PARKING, NARROW_ROAD, FOLLOWING_DISTANCE, SPEED_CONTROL, OTHER }

// ── B가 저장 ──────────────────────────────────────────
@Entity data class TripEvent(
    @PrimaryKey val eventId: String,
    val tripId: String,
    val type: String,            // EventType 이름
    val startT: Long, val endT: Long,
    val lat: Double?, val lng: Double?,
    val severity: Int,           // 1(경미) ~ 3(심각)
    val metrics: String,         // JSON. 예: {"deltaKmh":-14.2}
    val linkedNoteId: String?
)

enum class EventType { HARSH_START, HARSH_ACCEL, HARSH_BRAKE, HARSH_STOP, TURN_NO_SIGNAL,
    LONG_STOP_BEFORE_LEFT, PARKING_SESSION, OVERSPEED_TURN, USER_DIFFICULTY }

@Entity data class TripReport(
    @PrimaryKey val tripId: String,
    val createdAt: Long,
    val headline: String,        // 한 줄 요약
    val goodPoints: String,      // JSON 배열
    val practicePoints: String,  // JSON 배열
    val skillScores: String      // JSON {"SMOOTH_ACCEL":82,"BRAKING":60,...} ← C의 입력
)

// ── C가 저장 ──────────────────────────────────────────
@Entity data class Quest(
    @PrimaryKey val questId: String,
    val templateId: String,      // QuestCatalog의 ID
    val skill: String,           // Skill 이름
    val title: String, val reason: String,
    val goalType: String,        // "AUTO" | "SELF_CHECK"
    val target: Int, val progress: Int,
    val status: String,          // "ACTIVE" | "DONE" | "EXPIRED"
    val createdAt: Long, val completedAt: Long?
)

enum class Skill { SMOOTH_ACCEL, BRAKING, TURN_SIGNAL, LEFT_TURN, PARKING, SPEED_CONTROL }
```

### 1-4. 사용하는 SDK와 권한

| SDK / API | 버전 | 사용 파트 | 권한 (Manifest) |
|---|---|---|---|
| Pleos **Vehicle** | 2.3.2 (현재 사용 중) | A | `pleos.car.permission.CAR_DRIVE` (브레이크·조향각·주행거리), `pleos.car.permission.CAR_LIGHTS` (방향지시등) |
| AAOS `CarPropertyManager` | 플랫폼 | A | `android.car.permission.CAR_SPEED` (현재 선언됨) |
| Pleos **FusedLocation** | 1.1.3 | A | `pleos.car.permission.FUSED_LOCATION` |
| Pleos **SpeechToText** | 3.3.1 | A | `pleos.car.permission.STT_SERVICE` |
| Pleos **TextToSpeech** | 문서 확인 | A, B | 문서 확인 |
| **Gleo Interface** (App Schema) | — | A | 등록 절차 확인 (V7) |
| Pleos **NaviHelper** | 3.0.5 (현재 사용 중) | C (선택) | `NAVI_ROUTE_SEARCH` (현재 선언됨) |
| AAOS `CarUxRestrictionsManager` | 플랫폼 | A, B, C 화면 | — |
| AndroidX **Room**, Coroutines | 신규 추가 | 공통 | — |
| Foreground Service | 플랫폼 | A | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` (targetSdk 34) |

- 라이브러리 추가(Room 등)는 팀 규칙에 따라 **별도 `chore/` PR**로 올립니다.
- 권한은 Pleos 앱마켓에서 승인받은 범위만 사용할 수 있습니다. 위 목록을 My Project 신청 범위와 맞추세요.

### 1-5. 사용하지 않는 API (오해 방지)

| API | 이유 |
|---|---|
| `Vehicle.getGear()` | 소스에 `@hide`로 표시된 비공개 API입니다. 기어(P/R/D)는 쓰지 않고 **속도 부호**로 후진을 판단합니다(V2). |
| `Safety.checkTelltalesCapabilities` 등 `PRIVATE_*` 권한 API | 제조사 전용 권한이라 사용할 수 없습니다. |
| 앞차와의 거리(ADAS) | 공개 API가 없습니다. 차간거리는 급감속 빈도로 **간접 추정**만 합니다. |
| 주차 브레이크 | 공개된 Brake API는 페달 상태만 제공합니다. |

---

## 2. 파트 A: 주행 기록과 연습 메모

**목표:** 주행 중에 운전자가 아무것도 조작하지 않아도 1초 단위 주행 기록이 남고, 말 한마디로 "어려웠던 순간"을 표시할 수 있게 합니다.

### A-1. 주행 세션 시작·종료 (`TripSessionDetector`)

| 이벤트 | 조건 (MVP) | 비고 |
|---|---|---|
| 시작 | 속도 ≥ 5 km/h가 **3초 이상** 유지 **또는** 화면의 "주행 시작" 버튼 | 자동 시작 전에 앱 실행 상태여야 함 |
| 종료 | 속도 0이 **180초 이상** 유지 **또는** "주행 종료" 버튼 | 신호 대기 중 종료되지 않도록 180초 |
| 비정상 종료 | 서비스가 강제 종료되면 다음 실행 시 `endedAt=null`인 Trip을 마지막 샘플 시각으로 닫음 | `endReason="APP_KILLED"` |

- 종료 직전 180초의 정지 샘플은 잘라내지 말고 그대로 둡니다. 주차 분석(B-3-5)에 필요합니다.
- 주행 종료 시 **B의 `TripAnalyzer.analyze(tripId)`를 호출**합니다. A와 B 사이의 연결점은 이 함수 하나뿐입니다.

### A-2. 차량 신호 수집 (`VehicleSignalSource`)

리스너로 "최신 값"을 갱신하고, 1초 타이머가 그 값을 스냅샷으로 저장합니다(A-4).

```kotlin
class VehicleSignalSource(context: Context) {
    private val vehicle = Vehicle(context)
    @Volatile var brakeOn: Boolean? = null; private set
    @Volatile var steeringDeg: Float? = null; private set
    @Volatile var turnSignal: TurnSignalValue? = null; private set

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) { brakeOn = status }
        override fun onFailed(e: Exception) { brakeOn = null; log(e) }
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) { steeringDeg = angle } // 앞바퀴 각도, 왼쪽 음수
        override fun onFailed(e: Exception) { steeringDeg = null; log(e) }
    }
    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) { turnSignal = state } // OFF/LEFT/RIGHT
        override fun onFailed(e: Exception) { turnSignal = null; log(e) }
    }

    fun start() {
        vehicle.initialize()
        // 지원 여부를 먼저 확인하고, 지원되는 것만 등록. 결과는 Trip.signalSupport에 기록
        vehicle.getBrake().checkBrakePedalStatusCapabilities(
            onComplete = { vehicle.getBrake().registerBrakePedalStatus(brakeListener) },
            onFailed = { markUnsupported("brake") })
        vehicle.getSteeringWheel().checkSteeringWheelAngleCapabilities(
            onComplete = { _, _ -> vehicle.getSteeringWheel().registerSteeringWheelAngle(steeringListener) },
            onFailed = { markUnsupported("steering") })
        vehicle.getTurnSignal().checkTurnSignalStateCapabilities(
            onComplete = { vehicle.getTurnSignal().registerTurnSignalState(turnListener) },
            onFailed = { markUnsupported("turnSignal") })
    }

    fun stop() {
        runCatching { vehicle.getBrake().unregisterBrakePedalStatus(brakeListener) }
        runCatching { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
        runCatching { vehicle.getTurnSignal().unregisterTurnSignalState(turnListener) }
        vehicle.release()   // ⚠ 대기 중인 콜백이 끝난 뒤 호출 (공식 문서 주의사항)
    }
}
```

- `CapabilityConfig`의 지원 여부 필드 사용법은 Vehicle 문서의 "check-capability" 페이지를 따릅니다. 위 코드는 흐름만 보여 줍니다.
- 주행 거리는 종료 시 `getOdometer().getCurrentDistanceDriven()`의 시작·종료 차이로 계산하고, 실패하면 좌표 거리 합으로 대신합니다.

### A-3. 속도 (`SpeedSource`)

| 우선순위 | 출처 | 처리 |
|---|---|---|
| 1 | AAOS `PERF_VEHICLE_SPEED` (기존 `SpeedSource.kt`) | m/s × 3.6. **기존 코드의 `abs()`를 기록용으로는 쓰지 않고 부호를 유지**합니다. 표시용만 절댓값을 씁니다. |
| 2 | FusedLocation 좌표 | 직전 좌표와의 하버사인 거리 ÷ 시간 차이. 3샘플 이동평균. `confidence`가 낮은 점은 제외합니다. |

- 1이 5초 동안 응답하지 않으면 2로 전환하고 `Trip.speedSource="LOCATION"`으로 기록합니다.
- 구독 주기는 기존 코드와 같게 둡니다. 저장은 A-4의 1초 스냅샷에서만 합니다.

### A-4. 1초 샘플 기록 (`TripRecorderService`)

- **Foreground Service**(`foregroundServiceType="location"`)로 실행하고 알림에 "주행 기록 중"을 표시합니다. 운전자가 내비 화면을 보고 있어도 기록이 끊기지 않아야 합니다(V8).
- 코루틴 `while (isActive) { delay(1000) }` 루프에서 `VehicleSignalSource`, `SpeedSource`, `LocationSource`의 최신 값을 모아 `TripSample`을 만듭니다.
- DB 쓰기는 **10개씩 묶어서** `insertAll` 합니다. 종료 시 남은 것을 모두 씁니다.
- 신호가 없는 칸은 `null`로 둡니다. **가짜 값을 채우지 않습니다.** (README의 원칙과 같습니다.)
- 용량 예상: 1시간 주행 = 3,600행. 문제없는 수준입니다.

### A-5. 연습 메모: 음성 (`DrivingNoteRecorder`)

**입력 경로 두 가지.** 둘 다 같은 `saveNote(rawText, source)`로 들어갑니다.

**(1) STT 버튼 — MVP**

- 주행 화면에 **큰 마이크 버튼 하나만** 둡니다. 한 번 누르면 듣기 시작하고, 말이 끝나면 자동으로 종료됩니다.
- 사용 API (SpeechToText 3.3.1):
  ```kotlin
  val stt = SpeechToText(context, Mode.HYBRID)   // 네트워크 없으면 온디바이스로 자동 전환
  stt.initialize()
  stt.addListener(object : ResultListener {
      override fun onStartedRecognition() { noteStartT = System.currentTimeMillis() }
      override fun onUpdated(stt: String, completed: Boolean) { if (completed) saveNote(stt, "STT_BUTTON") }
      override fun onError(errorType: ErrorType) { tts("다시 말씀해 주세요") }
      override fun onReady() {}
      override fun onEndedRecognition() {}
      override fun onUpdatedEpdData(on: Long, off: Long) {}
      override fun onUpdatedRms(rms: Double) {}
  })
  // 버튼: stt.request()  /  화면 종료: stt.removeListener(l); stt.release()
  ```
  `HYBRID`·`SERVER` 모드는 My Project 앱 등록(app-registration)이 필요합니다. 등록 전에는 `ON_DEVICE`로 개발합니다.
- **메모 시각은 버튼을 누른 시각(`noteStartT`)** 으로 저장합니다. 인식이 끝난 시각이 아닙니다.
- 저장 후 TTS로 "비보호 좌회전, 기록했어요"처럼 **분류 결과를 짧게 읽어 줍니다.** 잘못 분류됐는지 운전자가 바로 알 수 있습니다.

**(2) Gleo 음성 비서 — 2단계 (V7 확인 후)**

App Schema 초안입니다(`actionCalls` 최대 3개, 설명은 영어, 함수 이름은 대문자와 밑줄).

```json
{
  "packageName": "com.hmgchallenge.pleosstarter",
  "appDescription": "Driving practice coach that records moments the driver found difficult and gives feedback after driving.",
  "version": "1.0.0",
  "actionCalls": [
    {
      "type": "function",
      "function": {
        "name": "RECORD_DRIVING_DIFFICULTY",
        "description": "Record the current moment as a driving situation the user found difficult, for review after the trip.",
        "parameters": {
          "type": "object",
          "properties": {
            "situation": {
              "type": "string",
              "description": "Type of driving situation the user found difficult.",
              "enum": ["UNPROTECTED_LEFT", "LEFT_TURN", "RIGHT_TURN", "U_TURN", "LANE_CHANGE", "MERGE",
                       "PARKING", "NARROW_ROAD", "FOLLOWING_DISTANCE", "SPEED_CONTROL", "OTHER"]
            },
            "detail": { "type": "string", "description": "The user's own words describing the difficulty." }
          },
          "required": ["situation"]
        },
        "component": "service"
      }
    }
  ]
}
```

- `"component": "service"`를 써서 Gleo가 **브로드캐스트로 호출**하게 합니다. 운전 중에 화면이 바뀌면 안 되기 때문입니다. 수신 클래스는 `core/voice/GleoActionReceiver.kt`이고, 정확한 Intent 형식은 공식 문서의 "Code Details with Examples"를 따릅니다.
- 응답은 Callback 스키마에 따라 "기록했어요" 문구를 돌려줍니다.
- Gleo 경로는 분류를 Gleo가 해 주므로 `NoteClassifier`를 거치지 않습니다.

### A-6. 메모 분류 (`NoteClassifier`)

STT 텍스트를 키워드로 분류합니다. 규칙은 코드 대신 표(리스트)로 관리하고 **위에서부터 처음 맞는 것**을 사용합니다.

| 순서 | 키워드 (공백 제거 후 포함 여부) | 분류 |
|---|---|---|
| 1 | 비보호 | `UNPROTECTED_LEFT` |
| 2 | 유턴, U턴 | `U_TURN` |
| 3 | 주차, 후진, 평행 | `PARKING` |
| 4 | 차선, 끼어들, 차로변경 | `LANE_CHANGE` |
| 5 | 합류, 진입 | `MERGE` |
| 6 | 좌회전 | `LEFT_TURN` |
| 7 | 우회전 | `RIGHT_TURN` |
| 8 | 골목, 좁은 | `NARROW_ROAD` |
| 9 | 차간, 앞차, 거리 | `FOLLOWING_DISTANCE` |
| 10 | 속도, 과속, 빨리, 느리 | `SPEED_CONTROL` |
| — | 그 외 | `OTHER` (원문은 그대로 보관) |

- 단위 테스트에 실제 예문을 20개 이상 넣습니다. 예: "방금 비보호좌회전 어려웠어" → `UNPROTECTED_LEFT`.

### A-7. 가짜 주행 재생기 (`dev/TripReplayer`) — 테스트와 시연의 핵심

- `assets/replay/*.csv`(열 이름은 `TripSample`과 같음)를 읽어 **실제 기록과 똑같이 DB에 저장**합니다. 메모 행(`note,<t>,<text>`)도 넣을 수 있습니다.
- 재생 속도를 1배·10배·즉시 중에서 고를 수 있게 합니다.
- 최소 시나리오 4개를 만듭니다: `smooth.csv`(잘한 주행), `harsh.csv`(급가속·급감속 다수), `left-turn.csv`(대기 후 좌회전 + 비보호 메모), `parking.csv`(전진·후진 반복 주차 4분).
- 디버그 빌드에서만 메뉴가 보이게 합니다.

### A-8. 주행 화면 (`DrivingScreen`)

- `CarUxRestrictionsManager`로 주행 제한 상태를 구독합니다. `isRequiresDistractionOptimization()`이 `true`이면 **"기록 중" 표시와 마이크 버튼만** 보여 줍니다.
- 정차 중에만 "주행 종료" 버튼과 최근 메모 목록을 보여 줍니다.

### A 완료 기준

- [ ] 에뮬레이터(또는 재생기)에서 주행 1회 → DB에 Trip 1개, Sample N개, Note가 저장됨
- [ ] 지원되지 않는 신호는 `null`로 저장되고 앱이 죽지 않음
- [ ] 내비 화면으로 전환해도 기록이 이어짐 (또는 V8 실패를 문서에 기록)
- [ ] `NoteClassifier` 단위 테스트 통과
- [ ] 재생기 시나리오 4개 동작

---

## 3. 파트 B: 주행 돌아보기

**목표:** 주행이 끝나면 30초 안에 읽을 수 있는 요약을 보여 줍니다. "오늘 무엇을 잘했고, 어디서 어려웠고, 다음에 무엇을 조심하면 되는지"를 알려 줍니다.

### B-1. 입력과 출력

```kotlin
class TripAnalyzer(private val repo: TripRepository) {
    suspend fun analyze(tripId: String): TripReport   // 이벤트 저장 + 리포트 저장 후 반환
}
```

- 입력: `Trip`, `List<TripSample>`, `List<DrivingNote>`
- 출력: `List<TripEvent>`, `TripReport` (DB 저장)
- **순수 Kotlin으로 작성합니다.** Android나 SDK 의존성이 없어야 PC에서 단위 테스트를 할 수 있습니다.
- 같은 tripId로 다시 실행하면 기존 이벤트를 지우고 다시 만듭니다. 기준값을 조정한 뒤 재분석할 때 필요합니다.

### B-2. 전처리

1. 시간순 정렬, 중복 시각 제거
2. 속도 결측이 **3초 이하**면 선형 보간, 그보다 길면 구간을 끊어 감지 대상에서 제외
3. 가속도 `a(t) = speed(t) − speed(t−1)` (단위: km/h/s)
4. heading 변화량은 −180~180°로 정규화 (359° → 1°는 +2°)

### B-3. 감지 규칙

모든 기준값은 `AnalyzerConfig` 한 곳에 상수로 둡니다. 아래 숫자는 **시작값**이며, 재생 데이터와 실주행으로 조정합니다(공공기관의 위험운전 기준을 참고해 확정).

| # | 이벤트 | 조건 | 심각도 | 필요 신호 |
|---|---|---|---|---|
| 1 | `HARSH_START` 급출발 | 직전 속도 ≤ 5 km/h 이고 `a ≥ +10` | ≥+13이면 2, ≥+16이면 3 | 속도 |
| 2 | `HARSH_ACCEL` 급가속 | 속도 > 5 km/h 에서 `a ≥ +10` | 같음 | 속도 |
| 3 | `HARSH_BRAKE` 급감속 | `a ≤ −10` | ≤−13이면 2, ≤−16이면 3 | 속도 (+브레이크면 신뢰도 ↑) |
| 4 | `HARSH_STOP` 급정지 | 3번 조건이면서 3초 안에 속도 ≤ 5 km/h | 3번과 같음 | 속도 |
| 5 | `PARKING_SESSION` 주차 | B-3-5 참조 | 소요 시간 기준 | 속도(부호), 조향각 |
| 6 | `TURN_NO_SIGNAL` 깜빡이 없이 회전 | 회전(B-3-6) 시작 전 **5초~회전 중**에 해당 방향 신호가 한 번도 없음 | 1 | heading/조향각, 방향지시등 |
| 7 | `OVERSPEED_TURN` 빠른 회전 | 회전 중 최고 속도 > 30 km/h | 1 (>40이면 2) | 속도, 회전 |
| 8 | `LONG_STOP_BEFORE_LEFT` 좌회전 전 긴 대기 | 좌회전 직전 정지가 ≥ 20초, 출발 후 10초 안에 좌회전 완료 | 1 | 속도, 회전 |
| 9 | `USER_DIFFICULTY` 사용자 메모 | 메모마다 1개 생성 | 2 | 메모 |

- 연속으로 발생한 1~4는 **3초 이내면 하나로 합칩니다.**
- 필요 신호가 `Trip.signalSupport`에서 미지원이면 그 감지기는 실행하지 않고, 리포트에 "이 차량에서는 확인할 수 없었어요"로 표시합니다.

**B-3-5. 주차 구간**

- 정의: 주행의 **마지막 정지(속도 0이 60초 이상) 직전에 속도 < 10 km/h가 연속된 구간**
- 지표:
  - `durationSec`: 구간 길이
  - `reverseCount`: 속도 부호가 +→−로 바뀐 횟수 (V2가 실패하면 측정하지 않음)
  - `steeringSwings`: 조향각이 −15°와 +15°를 넘나든 횟수 (핸들을 좌우로 크게 돌린 횟수)
- 심각도: ≤ 60초는 이벤트 없음(잘한 점으로 표시), ≤ 120초는 1, ≤ 240초는 2, 그 이상은 3
- 중간에 들른 곳의 주차는 MVP에서 다루지 않습니다.

**B-3-6. 회전 감지**

- 기본: 10초 창 안에서 heading 누적 변화가 **+60° 이상이면 우회전, −60° 이하면 좌회전, 절댓값 150° 이상이면 유턴**
- 보조: heading이 없거나 저속(< 10 km/h)이면 조향각이 15° 이상 2초 넘게 유지되는 구간을 회전으로 봅니다.
- 회전 시작 시각은 heading 변화가 처음 15°를 넘은 시점입니다.

**B-3-7. 메모와 이벤트 연결**

- 메모 시각 기준 **60초 이전 ~ 10초 이후** 창 안에서, 메모 분류와 맞는 이벤트를 찾아 `linkedNoteId`를 채웁니다.
  - `UNPROTECTED_LEFT`, `LEFT_TURN` → 가장 가까운 좌회전(8번 포함)
  - `PARKING` → 주차 구간
  - `SPEED_CONTROL`, `FOLLOWING_DISTANCE` → 1~4번
- 맞는 이벤트가 없으면 메모만 단독으로 보여 줍니다. ("방금" 이라고 했지만 감지 못한 경우)

### B-4. 기술별 점수 (`skillScores`)

C가 약점을 판단하는 입력입니다. 각 기술은 0~100점이며 **100에서 감점**합니다.

| Skill | 감점 규칙 (시작값) |
|---|---|
| `SMOOTH_ACCEL` | 급출발·급가속 1회당 심각도 × 8 |
| `BRAKING` | 급감속·급정지 1회당 심각도 × 8. 주행 10km당 3회를 넘으면 추가 10점 (차간거리 간접 지표) |
| `TURN_SIGNAL` | 회전 중 깜빡이 사용률 × 100 (회전이 0회면 측정 안 함) |
| `LEFT_TURN` | 좌회전 관련 이벤트·메모 1건당 15 |
| `PARKING` | 주차 심각도 1/2/3이면 각각 20/40/60 감점, 메모 1건당 15 |
| `SPEED_CONTROL` | 빠른 회전 1회당 10, 관련 메모 1건당 15 |

- 측정할 수 없는 기술은 `null`로 둡니다. 0점과 구분해야 합니다.
- 짧은 주행(< 2 km) 은 점수에 `lowConfidence: true`를 붙입니다. C는 이런 주행에 가중치를 낮게 줍니다.

### B-5. 리포트 문장 (`ReportBuilder`)

**템플릿 기반으로 만듭니다.** 모든 문장은 `strings.xml`에 둡니다(팀 규칙).

- **한 줄 요약**: 가장 낮은 점수의 기술로 정합니다.
  예) "전반적으로 안정적이었어요. 오늘은 **주차**에 시간이 걸렸네요."
- **잘한 점 (최대 2개)**: 80점 이상인 기술
  예) "회전할 때 깜빡이를 빠짐없이 켰어요."
- **연습할 점 (최대 3개)**: 점수가 낮은 순서. 문장에 근거 숫자를 함께 넣습니다.
  예) "급감속이 4번 있었어요. 앞차와 거리를 조금 더 두면 여유 있게 멈출 수 있어요."
- **사용자 메모가 있으면 항상 맨 위에 표시**합니다. 운전자가 직접 말한 것이 가장 중요한 피드백입니다.
  예) "14:32에 말씀하신 '비보호 좌회전' — 20초 기다린 뒤 좌회전했어요."
- 말투: 칭찬 먼저, 비난하지 않기, 한 문장 40자 안팎.
- (선택, 후순위) LLM으로 문장을 다듬는 기능을 넣는다면 **템플릿 결과를 입력으로 주고, 실패하면 템플릿 문장을 그대로** 씁니다.

### B-6. 돌아보기 화면 (`ReviewScreen`)

- 주행 종료 직후 자동으로 열립니다. **주행 제한 상태에서는 열지 않고**, "정차 후 확인하세요" 알림만 보냅니다.
- 화면 구성 (위에서 아래로):
  1. 한 줄 요약 + 주행 시간·거리
  2. 내가 남긴 메모 카드
  3. 연습할 점 카드 (최대 3개, 각 카드를 누르면 해당 이벤트 시각·위치 상세)
  4. 잘한 점
  5. "연습 퀘스트 보기" 버튼 → C 화면
- TTS로 한 줄 요약과 연습할 점 1개를 읽어 주는 "들려주기" 버튼을 둡니다.
- 지도 표시는 하지 않습니다. 스타터 프로젝트에 지도가 없으므로 위치는 "출발 12분 후" 같은 시간 표기로 대신합니다.

### B 완료 기준

- [ ] 재생기 시나리오 4개마다 예상 이벤트가 나오는 단위 테스트 (예: `harsh.csv` → 급감속 4개)
- [ ] 신호 일부가 `null`이어도 예외 없이 리포트 생성
- [ ] 리포트 생성 시간 1초 이내 (1시간 주행 기준)
- [ ] 화면에서 메모 카드가 맨 위에 표시됨

---

## 4. 파트 C: 연습 퀘스트

**목표:** 한 번의 주행이 아니라 **여러 번의 주행에서 반복되는 약점**을 찾아, 구체적인 연습 과제를 2~3개 제시하고 달성 여부를 추적합니다.

### C-1. 약점 판단 (`WeaknessProfiler`)

- 입력: 최근 **5회** `TripReport.skillScores` (`lowConfidence`는 가중치 0.5)
- 기술별로 가중 평균 점수를 계산하고, 사용자 메모 건수를 따로 셉니다.
- **약점 조건 (하나라도 해당되면)**
  - 평균 점수 < 70
  - 최근 5회 중 3회 이상 해당 기술의 점수가 70 미만 (반복성)
  - 해당 분류의 사용자 메모가 누적 2건 이상 (운전자 본인이 어렵다고 느낌)
- 우선순위: `(70 − 평균점수) + 메모건수 × 10 + 반복횟수 × 5` 가 큰 순서
- 주행 기록이 1~2회뿐이면 "아직 데이터가 적어요" 안내와 함께 메모 기반 퀘스트만 냅니다.

### C-2. 퀘스트 카탈로그 (`QuestCatalog`)

퀘스트는 코드가 아니라 **데이터(`assets/quests.json`)** 로 관리합니다. 기획 담당자가 코드를 몰라도 추가할 수 있습니다.

| templateId | Skill | 제목 | 목표 유형 | 달성 조건 |
|---|---|---|---|---|
| `Q_PARK_BIG_LOT` | PARKING | 넓고 한산한 주차장에서 후면 주차 5번 | SELF_CHECK | 사용자가 5번 체크 |
| `Q_PARK_UNDER_2MIN` | PARKING | 다음 주행에서 2분 안에 주차하기 | AUTO | 다음 주행의 주차 구간 ≤ 120초 |
| `Q_SMOOTH_START` | SMOOTH_ACCEL | 출발할 때 3초 세면서 천천히 밟기 — 급출발 0회 주행 2번 | AUTO | 급출발 0회인 주행 2회 |
| `Q_BRAKE_DISTANCE` | BRAKING | 앞차와 3초 거리 두기 — 급감속 1회 이하 주행 3번 | AUTO | 급감속·급정지 ≤ 1회인 주행 3회 |
| `Q_SIGNAL_ALL` | TURN_SIGNAL | 모든 회전에서 깜빡이 켜기 | AUTO | 깜빡이 사용률 100%인 주행 2회 |
| `Q_LEFT_QUIET` | LEFT_TURN | 한산한 시간대에 비보호 좌회전이 있는 교차로 3번 지나기 | SELF_CHECK | 사용자가 3번 체크 |
| `Q_LEFT_NOTE_FREE` | LEFT_TURN | 좌회전 어려움 메모 없이 주행 2번 | AUTO | 좌회전 관련 메모·이벤트 없는 주행 2회 |
| `Q_TURN_SLOW` | SPEED_CONTROL | 회전 전에 충분히 감속하기 | AUTO | 빠른 회전 0회인 주행 2회 |

- 각 퀘스트에 **"왜 이 퀘스트인가"(`reason`)** 를 근거 숫자와 함께 채웁니다.
  예) "최근 5번 중 4번, 주차에 3분 넘게 걸렸어요."
- 같은 기술에 대해 SELF_CHECK(연습 장소) 1개와 AUTO(실제 주행에서 확인) 1개를 짝으로 내면 "연습 → 확인" 흐름이 됩니다.

### C-3. 퀘스트 생성 (`QuestGenerator`)

- 실행 시점: B의 리포트가 저장된 직후
- 규칙:
  1. 진행 중(ACTIVE) 퀘스트는 **최대 3개**
  2. 약점 우선순위가 높은 기술부터, 그 기술의 ACTIVE 퀘스트가 없으면 카탈로그에서 하나를 고릅니다.
  3. 같은 템플릿은 완료 후 **7일 동안** 다시 내지 않습니다.
  4. 14일 동안 진행이 없으면 `EXPIRED`로 바꾸고, 약점이 남아 있으면 다른 템플릿을 냅니다.

### C-4. 진행 추적 (`QuestProgressTracker`)

- **AUTO**: 새 리포트가 생길 때마다 조건을 검사해 `progress`를 올립니다. 달성하면 `DONE`과 함께 축하 문구를 표시합니다.
- **SELF_CHECK**: 퀘스트 화면의 "연습했어요" 버튼으로 1씩 올립니다. **주행 제한 중에는 버튼을 비활성화**합니다.
- 기술 점수가 3회 연속 85점 이상이면 해당 기술을 **"졸업"** 처리하고 관련 퀘스트를 더 내지 않습니다. 팀 기획의 "보조 졸업"과 연결할 수 있는지 기획 담당과 맞춥니다.

### C-5. 퀘스트 화면 (`QuestScreen`)

- 진행 중 퀘스트 카드: 제목, 이유, 진행 막대(`progress / target`), SELF_CHECK이면 체크 버튼
- 기술별 현재 상태 (6개 기술 × 최근 점수 추이 막대)
- 완료한 퀘스트와 졸업한 기술 목록
- (선택) 퀘스트에 연습 장소 좌표가 있으면 NaviHelper `requestRoute()`로 **"연습 장소로 안내"** 버튼. 장소 검색 기능이 없으므로 MVP에서는 사용자가 저장한 장소만 지원합니다.

### C 완료 기준

- [ ] 가짜 리포트 5개(단위 테스트용 데이터)로 약점 순위와 퀘스트 생성 결과 검증
- [ ] AUTO 퀘스트가 새 리포트로 자동 진행·완료됨
- [ ] ACTIVE 3개 제한, 7일 재출제 금지, 14일 만료 동작
- [ ] `quests.json`만 수정해서 퀘스트를 추가할 수 있음

---

## 5. 일정, 브랜치, 열린 질문

### 5-1. 브랜치와 순서

| 순서 | 브랜치 | 담당 | 의존 |
|---|---|---|---|
| 0 | `chore/add-room` (Room·Coroutines 추가, DB와 Entity 1-3) | A | 없음. **가장 먼저 머지** |
| 1 | `feature/trip-recorder` (A-1~A-4, A-8) | A | 0 |
| 1 | `feature/trip-replay` (A-7) | A | 0 |
| 1 | `feature/trip-analyzer` (B-1~B-5, 단위 테스트) | B | 0 (가짜 데이터) |
| 1 | `feature/quest-engine` (C-1~C-4, 단위 테스트) | C | 0 (가짜 리포트) |
| 2 | `feature/driving-note` (A-5 STT, A-6) | A | 1 |
| 2 | `feature/review-screen` (B-6) | B | 1 |
| 2 | `feature/quest-screen` (C-5) | C | 1 |
| 3 | `feature/gleo-note` (A-5 Gleo) | A | V7 |
| 3 | 통합 테스트: 재생기 → 분석 → 퀘스트 → 화면 | 전원 | 2 |

`chore/add-room`이 머지되면 세 파트가 **동시에** 개발을 시작할 수 있습니다.

### 5-2. 이번 범위에서 제외 (2단계)

- 휴대폰 연동 (서버 필요): 우선 차량 화면에서 완성한 뒤 결정
- 지도 위에 이벤트 표시
- 중간 경유지 주차 분석, 차로 변경 감지
- LLM 문장 다듬기

### 5-3. 열린 질문

| 질문 | 누구에게 | 영향 |
|---|---|---|
| 공모전 앱에 `CAR_SPEED`, `CAR_DRIVE`, `CAR_LIGHTS`, `FUSED_LOCATION`, `STT_SERVICE` 권한이 허용되는가 | 주최 측 / My Project | A 전체 |
| Gleo App Schema를 공모전 앱이 등록할 수 있는가 | 주최 측 | A-5 (2) |
| 에뮬레이터에서 속도·브레이크·조향각을 주입할 수 있는가 | 문서 / 직접 확인 (V5) | 테스트 방법 |
| 이 기능의 Notion 요구사항(FR 번호)과 범위가 맞는가 | 팀 기획 | 전체 |
| "보조 졸업"과 기술 졸업(C-4)을 같은 개념으로 볼 것인가 | 팀 기획 | C |

---

### 참고 자료

- Vehicle SDK 2.3.2 소스: `Brake.kt`, `SteeringWheel.kt`, `TurnSignal.kt`, `Odometer.kt`, `Gear.kt`(@hide) — [소스 JAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2-sources.jar)
- FusedLocation 1.1.3 소스: `LocationData(latitude, longitude, altitude, heading, confidence, timestampMillis)` — 속도 필드 없음
- [SpeechToText](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro) · [Result listeners](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners)
- [Gleo App Schema 작성 가이드](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability)
- [주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [Android `PERF_VEHICLE_SPEED`](https://developer.android.com/reference/android/car/VehiclePropertyIds#PERF_VEHICLE_SPEED)
