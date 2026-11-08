# 초보 운전자 주행 피드백 — 개발 계획서


> A. 주행 기록·메모 / B. 주행 돌아보기 / C. 연습 퀘스트
> 
> "속도 + 음성 메모"만으로 동작하는 최소 기능, 빠진 기능은 [6장](#6-생략한-기능-시간이-남으면)에 정리
> 
> API 이름은 Vehicle SDK 2.3.2·SpeechToText 3.3.1 배포 소스와 Pleos 공식 문서 기준

---

## 목차

0. [먼저 할 일: 사전 검증 (2개)](#0-먼저-할-일-사전-검증-2개)
1. [공통: 구조와 데이터 약속](#1-공통-구조와-데이터-약속)
2. [파트 A: 주행 기록과 연습 메모](#2-파트-a-주행-기록과-연습-메모)
3. [파트 B: 주행 돌아보기](#3-파트-b-주행-돌아보기)
4. [파트 C: 연습 퀘스트](#4-파트-c-연습-퀘스트)
5. [순서와 브랜치](#5-순서와-브랜치)
6. [생략한 기능 (시간이 남으면)](#6-생략한-기능-시간이-남으면)

---

## 0. 먼저 할 일: 사전 검증 (2개)

**MVP의 핵심 기능은 "속도"와 "음성 메모" 두 가지뿐입니다.** 둘 다 실패해도 대안이 있으므로, 결과에 따라 설계가 바뀌지는 않고 **입력 경로만** 바뀝니다. 결과는 `docs/vehicle-sdk-notes.md`에 기록합니다.

| # | 확인 항목 | 방법 | 실패 시 대안 |
|---|---|---|---|
| V1 | **차량 속도 수신 + 에뮬레이터에서 속도 바꾸기** | 스타터 앱의 `SpeedSource.kt`(AAOS `PERF_VEHICLE_SPEED`)를 Pleos 에뮬레이터에서 실행하고, 에뮬레이터 차량 값 조절 기능 또는 공식 문서 "ADB Command Reference"로 속도를 바꿔 화면 값이 따라오는지 확인 | 실시간 속도는 포기하고 **CSV 재생기(A-4)로 시연**. 분석·리포트·퀘스트는 그대로 동작 |
| V2 | **STT 동작** (에뮬레이터 마이크) | `SpeechToText(context, Mode.ON_DEVICE)`로 `request()` 후 `onUpdated(..., completed=true)` 결과가 오는지 확인 | 화면의 **텍스트 입력 / 상황 선택 버튼**으로 메모 입력 |

- 두 검증은 각각 반나절 안에 끝내고, 막히면 바로 대안으로 넘어갑니다.
- 권한(`CAR_SPEED`, `STT_SERVICE`)이 공모전 앱에 허용되는지는 주최 측에 **동시에** 문의합니다.

---

## 1. 공통: 구조와 데이터 약속

### 1-1. 전체 흐름

```
[A. 주행 중]                    [B. 주행 후]                  [C. 누적]
DrivingScreen                   TripAnalyzer                  QuestEngine
  ├ SpeedSource ─→ 1초 샘플      ├ 급출발·급가속·급감속 감지     ├ 최근 5회 점수 + 메모 집계
  ├ (또는 TripReplayer)          ├ 점수 계산                    └ 퀘스트 2~3개 선택·진행
  └ NoteRecorder ─→ 메모         └ 리포트 문장 생성
             │                              │                          │
             └──────────── Room DB (TripRepository) ───────────────────┘
```

- **A는 기록만, B는 분석만, C는 누적 판단만** 합니다. 서로는 DB 데이터로만 통신합니다.
- 1-3의 데이터 형식만 합의하면 B와 C는 **가짜 데이터로 바로 개발**할 수 있습니다.

### 1-2. 패키지 구조

```
com/hmgchallenge/pleosstarter/
├ core/vehicle/      SpeedSource.kt (기존)
├ core/voice/        NoteRecorder.kt, NoteClassifier.kt
├ core/data/         TripDatabase.kt, TripRepository.kt, entity/*.kt
├ feature/driving/   DrivingScreen.kt, TripRecorder.kt          ← A
├ feature/review/    TripAnalyzer.kt, ReportBuilder.kt, ReviewScreen.kt ← B
├ feature/quests/    QuestEngine.kt, QuestCatalog.kt, QuestScreen.kt    ← C
└ dev/               TripReplayer.kt (CSV 재생, 디버그 빌드 전용)
```

### 1-3. 데이터 모델 (Room Entity) — **세 파트의 약속**

변경하려면 세 담당자 모두의 합의가 필요합니다.

```kotlin
// ── A가 저장 ──────────────────────────────────────────
@Entity data class Trip(
    @PrimaryKey val tripId: String,       // UUID
    val startedAt: Long, val endedAt: Long?,
    val source: String,                   // "VEHICLE" | "REPLAY"
    val distanceKm: Float?                // 속도 적분으로 계산
)

@Entity(primaryKeys = ["tripId", "t"]) data class TripSample(
    val tripId: String,
    val t: Long,                 // epoch ms, 1초 간격
    val speedKmh: Float?         // 값이 없으면 null (가짜 값 금지)
)

@Entity data class DrivingNote(
    @PrimaryKey val noteId: String,
    val tripId: String,
    val t: Long,                 // 버튼을 누른 시각
    val rawText: String,         // "방금 비보호좌회전 어려웠어"
    val category: String         // NoteCategory 이름
)

enum class NoteCategory { LEFT_TURN, RIGHT_TURN, U_TURN, LANE_CHANGE, PARKING, SPEED_CONTROL, OTHER }

// ── B가 저장 ──────────────────────────────────────────
@Entity data class TripEvent(
    @PrimaryKey val eventId: String,
    val tripId: String,
    val type: String,            // "HARSH_START" | "HARSH_ACCEL" | "HARSH_BRAKE"
    val t: Long,
    val severity: Int,           // 1~3
    val deltaKmh: Float          // 1초간 속도 변화
)

@Entity data class TripReport(
    @PrimaryKey val tripId: String,
    val createdAt: Long,
    val headline: String,
    val goodPoints: String,      // JSON 배열
    val practicePoints: String,  // JSON 배열
    val skillScores: String      // JSON {"SMOOTH_ACCEL":82,"BRAKING":60} ← C의 입력
)

// ── C가 저장 ──────────────────────────────────────────
@Entity data class Quest(
    @PrimaryKey val questId: String,
    val templateId: String,
    val title: String, val reason: String,
    val goalType: String,        // "AUTO" | "SELF_CHECK"
    val target: Int, val progress: Int,
    val status: String,          // "ACTIVE" | "DONE"
    val createdAt: Long
)

enum class Skill { SMOOTH_ACCEL, BRAKING }   // 메모 분류는 NoteCategory로 따로 집계
```

### 1-4. 사용하는 SDK와 권한

| SDK / API | 버전 | 사용 파트 | 권한 (Manifest) |
|---|---|---|---|
| AAOS `CarPropertyManager` | 플랫폼 | A | `android.car.permission.CAR_SPEED` (현재 선언됨) |
| Pleos **SpeechToText** | 3.3.1 | A | `pleos.car.permission.STT_SERVICE` |
| AndroidX **Room**, Coroutines | 신규 추가 | 공통 | — |

- Vehicle SDK의 브레이크·조향각·방향지시등, FusedLocation, TTS, Gleo, NaviHelper는 **이번 범위에서 쓰지 않습니다**(6장).
- Room 추가는 팀 규칙에 따라 **별도 `chore/` PR**로 올립니다.

---

## 2. 파트 A: 주행 기록과 연습 메모

**목표:** "주행 시작"을 누르면 1초 단위 속도가 기록되고, 마이크 버튼 하나로 "어려웠던 순간"을 남길 수 있게 합니다.

### A-1. 주행 시작·종료

- 화면의 **"주행 시작" / "주행 종료" 버튼**으로만 제어합니다. (자동 감지는 생략)
- 종료 시 `TripAnalyzer.analyze(tripId)`를 호출합니다. A와 B의 연결점은 이 함수 하나뿐입니다.
- 앱이 화면에 떠 있는 동안만 기록합니다. (백그라운드 기록은 생략)

### A-2. 속도 기록 (`TripRecorder`)

- 기존 `SpeedSource.kt`의 값을 사용합니다. m/s × 3.6 → km/h. 표시·기록 모두 절댓값을 써도 됩니다(후진 판단 안 함).
- 코루틴 `while (isActive) { delay(1000) }` 루프에서 최신 속도를 `TripSample`로 저장합니다. 값이 없으면 `null`.
- 거리: 종료 시 `Σ speedKmh / 3600`으로 계산합니다.

### A-3. 연습 메모 (`NoteRecorder`, `NoteClassifier`)

- 주행 화면에 **큰 마이크 버튼 하나**. 누르면 `stt.request()`, 결과가 오면 저장합니다.
  ```kotlin
  val stt = SpeechToText(context, Mode.ON_DEVICE)   // 앱 등록 없이 사용 가능
  stt.initialize()
  stt.addListener(object : ResultListener {
      override fun onStartedRecognition() { noteStartT = System.currentTimeMillis() }
      override fun onUpdated(stt: String, completed: Boolean) { if (completed) saveNote(stt) }
      override fun onError(errorType: ErrorType) { showMessage("다시 말씀해 주세요") }
      override fun onReady() {}
      override fun onEndedRecognition() {}
      override fun onUpdatedEpdData(on: Long, off: Long) {}
      override fun onUpdatedRms(rms: Double) {}
  })
  // 화면 종료 시: stt.removeListener(l); stt.release()
  ```
- 저장 후 화면에 "비보호 좌회전, 기록했어요"처럼 **분류 결과를 짧게 표시**합니다.
- V2가 실패하면 같은 `saveNote(text)`로 들어가는 **상황 선택 버튼**(좌회전 / 주차 / 차선변경 …)을 둡니다.
- 분류는 키워드 표로 합니다. 위에서부터 처음 맞는 것:

| 키워드 (공백 제거 후 포함) | 분류 |
|---|---|
| 유턴, U턴 | `U_TURN` |
| 주차, 후진 | `PARKING` |
| 차선, 끼어들 | `LANE_CHANGE` |
| 좌회전, 비보호 | `LEFT_TURN` |
| 우회전 | `RIGHT_TURN` |
| 속도, 과속, 빨리, 느리 | `SPEED_CONTROL` |
| 그 외 | `OTHER` (원문 보관) |

### A-4. 가짜 주행 재생기 (`dev/TripReplayer`) — 시연의 안전망

- `assets/replay/*.csv`(`t,speedKmh` 또는 `note,<t>,<text>`)를 읽어 **실제 기록과 똑같이 DB에 저장**합니다.
- 재생 속도: 1배 / 즉시 두 가지만.
- 시나리오 2개: `smooth.csv`(잘한 주행), `harsh.csv`(급가속·급감속 다수 + 메모 2개).
- V1이 실패해도 이 재생기로 B·C 전체를 시연할 수 있습니다.

### A-5. 주행 화면 (`DrivingScreen`)

- 주행 중 화면은 **"기록 중" 표시, 현재 속도, 마이크 버튼, 종료 버튼만** 둡니다. 처음부터 단순하게 만들어 주행 중 조작 부담을 줄입니다.

### A 완료 기준

- [ ] 주행 1회(실시간 또는 재생기) → DB에 Trip 1개, Sample N개, Note 저장
- [ ] 속도가 안 들어와도 앱이 죽지 않고 `null`로 저장
- [ ] `NoteClassifier` 단위 테스트 (예문 10개 이상)

---

## 3. 파트 B: 주행 돌아보기

**목표:** 주행이 끝나면 "잘한 점 / 연습할 점 / 내가 남긴 메모"를 한 화면에 보여 줍니다.

### B-1. 입력과 출력

```kotlin
class TripAnalyzer(private val repo: TripRepository) {
    suspend fun analyze(tripId: String): TripReport   // 이벤트·리포트 저장 후 반환
}
```

- **순수 Kotlin**으로 작성해 PC 단위 테스트가 가능하게 합니다.
- 속도가 `null`인 칸은 건너뜁니다(보간 안 함).

### B-2. 감지 규칙 (속도만 사용)

`a(t) = speed(t) − speed(t−1)` (km/h/s). 기준값은 `AnalyzerConfig` 상수로 두고 재생 데이터로 조정합니다.

| 이벤트 | 조건 | 심각도 |
|---|---|---|
| `HARSH_START` 급출발 | 직전 속도 ≤ 5 이고 `a ≥ +10` | ≥+13이면 2, ≥+16이면 3 |
| `HARSH_ACCEL` 급가속 | 직전 속도 > 5 이고 `a ≥ +10` | 같음 |
| `HARSH_BRAKE` 급감속 | `a ≤ −10` | ≤−13이면 2, ≤−16이면 3 |

- 3초 안에 연속으로 발생하면 하나로 합칩니다.

### B-3. 점수 (`skillScores`)

| Skill | 계산 |
|---|---|
| `SMOOTH_ACCEL` | 100 − (급출발·급가속 심각도 합 × 8), 최소 0 |
| `BRAKING` | 100 − (급감속 심각도 합 × 8), 최소 0 |

### B-4. 리포트 문장 (`ReportBuilder`)

템플릿 기반, 문장은 `strings.xml`에 둡니다.

- **메모가 있으면 맨 위**에 시각과 함께 표시: "14:32 — '비보호 좌회전 어려웠어'"
- **한 줄 요약**: 낮은 점수 기술 기준. 예) "전반적으로 안정적이었어요. 오늘은 **감속**을 연습해 봐요."
- **잘한 점**: 80점 이상 기술. 예) "출발이 부드러웠어요."
- **연습할 점**: 근거 숫자 포함. 예) "급감속이 4번 있었어요. 앞차와 거리를 조금 더 두면 여유 있게 멈출 수 있어요."
- 말투: 칭찬 먼저, 비난하지 않기.

### B-5. 돌아보기 화면 (`ReviewScreen`)

- "주행 종료"를 누르면 바로 엽니다.
- 위에서 아래로: 한 줄 요약 + 시간·거리 → 메모 카드 → 연습할 점 → 잘한 점 → "연습 퀘스트 보기" 버튼

### B 완료 기준

- [ ] `harsh.csv` → 예상한 개수의 급감속이 나오는 단위 테스트
- [ ] 속도 일부가 `null`이어도 리포트 생성
- [ ] 메모 카드가 맨 위에 표시

---

## 4. 파트 C: 연습 퀘스트

**목표:** 최근 주행에서 반복되는 약점을 찾아 연습 과제 2~3개를 보여 주고 진행을 표시합니다.

### C-1. 약점 판단 + 퀘스트 선택 (`QuestEngine`)

- 입력: 최근 **5회** 리포트의 점수 + 누적 메모 분류
- 약점 조건: 기술 평균 점수 < 70 **또는** 같은 분류의 메모가 2건 이상
- 약점 순서대로 카탈로그에서 퀘스트를 골라 **ACTIVE 최대 3개**를 유지합니다.
- 주행이 1~2회뿐이면 메모 기반 퀘스트만 냅니다.

### C-2. 퀘스트 카탈로그 (`QuestCatalog`, Kotlin 리스트)

| templateId | 약점 | 제목 | 유형 | 달성 조건 |
|---|---|---|---|---|
| `Q_SMOOTH_START` | SMOOTH_ACCEL | 3초 세면서 천천히 출발 — 급출발 0회 주행 2번 | AUTO | 급출발·급가속 0회 주행 2회 |
| `Q_BRAKE_DISTANCE` | BRAKING | 앞차와 3초 거리 두기 — 급감속 1회 이하 주행 3번 | AUTO | 급감속 ≤ 1회 주행 3회 |
| `Q_LEFT_PRACTICE` | 메모 LEFT_TURN | 한산한 시간에 좌회전 교차로 3번 지나기 | SELF_CHECK | "연습했어요" 3번 |
| `Q_PARK_PRACTICE` | 메모 PARKING | 넓은 주차장에서 후면 주차 5번 | SELF_CHECK | "연습했어요" 5번 |
| `Q_LANE_PRACTICE` | 메모 LANE_CHANGE | 한산한 도로에서 차선 변경 5번 | SELF_CHECK | "연습했어요" 5번 |

- 각 퀘스트의 `reason`에 근거를 넣습니다. 예) "최근 5번 중 급감속이 평균 4번 있었어요."

### C-3. 진행

- **AUTO**: 새 리포트가 생길 때마다 조건을 검사해 `progress`를 올리고, 달성하면 `DONE`.
- **SELF_CHECK**: 퀘스트 화면의 "연습했어요" 버튼으로 +1. (정차 중에만 여는 화면이므로 별도 제한 없음)

### C-4. 퀘스트 화면 (`QuestScreen`)

- 진행 중 퀘스트 카드: 제목, 이유, 진행 막대(`progress / target`), SELF_CHECK이면 체크 버튼
- 완료한 퀘스트 목록

### C 완료 기준

- [ ] 가짜 리포트 5개로 퀘스트 선택 결과 단위 테스트
- [ ] AUTO 퀘스트가 새 리포트로 진행·완료됨

---

## 5. 순서와 브랜치

| 순서 | 브랜치 | 담당 | 의존 |
|---|---|---|---|
| 0 | V1, V2 사전 검증 | A | 없음 |
| 0 | `chore/add-room` (Room·Coroutines, Entity 1-3) | A | 없음. **가장 먼저 머지** |
| 1 | `feature/trip-recorder` (A-1, A-2, A-5) + `feature/trip-replay` (A-4) | A | 0 |
| 1 | `feature/trip-analyzer` (B-1~B-4, 단위 테스트) | B | 0 (가짜 데이터) |
| 1 | `feature/quest-engine` (C-1~C-3, 단위 테스트) | C | 0 (가짜 리포트) |
| 2 | `feature/driving-note` (A-3) | A | 1, V2 결과 |
| 2 | `feature/review-screen` (B-5), `feature/quest-screen` (C-4) | B, C | 1 |
| 3 | 통합: 재생기 → 분석 → 퀘스트 → 화면 시연 확인 | 전원 | 2 |

---

## 6. 생략한 기능 (시간이 남으면)

| 생략한 기능 | 이유 | 다시 넣을 때 필요한 것 |
|---|---|---|
| 브레이크·조향각·방향지시등 수집, **깜빡이 없이 회전** 감지 | Vehicle SDK 신호마다 지원 확인 + heading/조향각 기반 회전 감지가 복잡 | Vehicle `TurnSignal`·`SteeringWheel` 검증, FusedLocation heading |
| 회전 감지, 빠른 회전, 좌회전 전 긴 대기 | 회전 감지에 의존 | 위와 같음 |
| 주차 구간 분석 (후진 횟수, 핸들 조작) | 후진 속도 부호 검증 필요, 규칙 복잡 | 후진 시 속도가 음수인지 검증, 조향각 |
| FusedLocation (위치·속도 대체) | 지도 표시가 없고 속도는 재생기로 대체 가능 | `FUSED_LOCATION` 권한 |
| Gleo 음성 비서 연동 | 등록 절차·주최 측 승인이 불확실 | App Schema 등록 |
| TTS 음성 응답 | 화면 표시로 충분 | TextToSpeech SDK |
| Foreground Service (백그라운드 기록) | 시연은 앱을 띄운 상태로 진행 | `FOREGROUND_SERVICE_LOCATION` 권한 |
| 자동 주행 시작·종료 감지 | 버튼으로 충분 | — |
| 메모 ↔ 이벤트 자동 연결 | 회전·주차 감지가 없으면 연결 대상이 거의 없음 | 회전·주차 감지 |
| `CarUxRestrictionsManager` 주행 제한 연동 | 주행 화면을 처음부터 단순하게 만들어 대신함 | 앱마켓 심사 시 필요하면 추가 |
| 퀘스트 만료(14일)·재출제 금지(7일)·기술 졸업 | 데모 기간에는 효과가 드러나지 않음 | — |
| `quests.json` 외부 데이터화, NaviHelper 연습 장소 안내 | 부가 기능 | — |
| 휴대폰 연동, 지도 표시, LLM 문장 다듬기 | 범위 밖 | 서버 / 지도 SDK |

### 열린 질문

| 질문 | 누구에게 | 영향 |
|---|---|---|
| 공모전 앱에 `CAR_SPEED`, `STT_SERVICE` 권한이 허용되는가 | 주최 측 | A |
| Pleos 에뮬레이터에서 속도 값을 바꿀 수 있는가 | 직접 확인 (V1) | 실시간 시연 여부 |

---

### 참고 자료

- [SpeechToText](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro) · [Result listeners](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners)
- [Android `PERF_VEHICLE_SPEED`](https://developer.android.com/reference/android/car/VehiclePropertyIds#PERF_VEHICLE_SPEED)
- [Vehicle SDK](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro) (생략한 기능을 다시 넣을 때)
