# 주행 코치 개발자를 위한 연결 규격

대화 기준 **1번=주행 코치 / 3번=퀘스트**입니다. Figma 프레임 번호와 혼동하지 마세요.

## 붙일 위치

공통 파일은 `app/src/main/java/com/hmgchallenge/pleosstarter/driving/DrivingGuideContract.kt`입니다.

- `DrivingGuidePort`: 주행 코치가 구현할 화면 포트. `createView`, `render`, `onCommand`, `release` 네 개.
- `DrivingGuideHost`: 주행 코치가 퀘스트에 상황·관측·확인·취소를 전달하는 인터페이스.
- `GuideUiModel`: 퀘스트가 주행 코치에 돌려주는 현재 단계·현재 퀘스트·영역별 안내 설정.
- `QuestStatusView`: 주행 화면에 붙일 수 있는 읽기 전용 현재 퀘스트/완료 단계 위젯.
- `QuestIntegration`: 위 호스트의 실제 저장·판정 어댑터. Pleos 타입을 요구하지 않음.

`QuestActivity`의 `private val guide: DrivingGuidePort = PlaceholderDrivingGuide()`만 본인 구현으로 바꾸세요. 주행 상황 판단과 실제 화면·음성은 본인 포트 안에서 구현합니다. 퀘스트 엔진과 저장 형식을 바꿀 필요가 없습니다. 3번은 주차 상태 학습 화면이므로 실주행 화면은 별도 적합한 호스트를 사용해도 됩니다.

## 양방향 흐름

| 방향 | 내용 |
| --- | --- |
| 주행 코치 → 퀘스트 | 새 주행, 상황 진입, 안내 제공 사실, 관측값, 명시적 수행 확인, 어려움, 건너뜀, 확인 불가, 다음 기회로 보류, 취소, 주행 종료 |
| 퀘스트 → 주행 코치 | 현재 상황의 단계 퀘스트, 완료 n/4, 추가 코칭 enabled, 자세히/간략히/요청 시 |
| 퀘스트 → 주행 코치 명령 | REPEAT, STOP, RESUME. 실제 음성 중단·재생은 주행 코치의 책임 |
| 회고 → 퀘스트 | `DrivingFeedback`: 같은 주행 ID의 어려움 분류·메모·선택적 속도 관측 요약. 이것만으로 차로 변경 성공을 만들지 않음 |

설정은 단순 화면 이동 시 읽는 값만이 아닙니다. `render(model)` 또는 `QuestIntegration.observe()`로 바뀐 설정을 받아 다음 유효한 안내에 반영합니다. 반복 요청은 만료된 교차로 안내를 다시 읽지 않도록 주행 코치에서 검사해야 합니다. 기본 내비 음성과 OEM 경고는 이 포트가 끄는 대상이 아닙니다.

## 같은 교차로의 세 상황

| 순서 | `SituationKind` | 의미 |
| --- | --- | --- |
| 1 | `LANE_RIGHT` | 교차로 진입 전 우회전을 위한 우측 차로 준비/변경 |
| 2 | `TURN_RIGHT` | 그 교차로에서 우회전 |
| 3 | `INTERSECTION_EXIT` | 회전을 마치고 같은 교차로를 빠져나옴 |

세 상황은 같은 `tripId`와 **같은 `maneuverId`**를 사용합니다. `occurrenceId`는 각 상황별로 다릅니다. 우회전과 출구 수행이 둘 다 확인됐을 때 교차로 경험 한 번으로 합칩니다. 다른 교차로의 출구와 결합하지 않습니다. 차로 변경은 별도 영역의 경험으로 기록됩니다.

## ID와 근거

| 필드 | 규칙 |
| --- | --- |
| tripId | 한 주행을 시작할 때 발급, 세 파트에서 공유 |
| kind | 차로 변경/우회전/출구 등 종류 |
| occurrenceId | 같은 종류가 다시 발생하면 새 ID. 재전송은 기존 ID |
| maneuverId | 같은 교차로의 준비·회전·출구를 묶는 ID |
| eventId | 개별 사실의 ID. 같은 사실 재전송은 기존 ID |
| at | 앱에서 정규화한 epoch ms. 주행 코치가 비동기 신호를 정렬한 뒤 순서대로 전달 |
| source | SIMULATION / USER / VEHICLE. 실제 프로필에 시연 데이터 사용 금지 |
| confirmation | UNKNOWN / USER_CONFIRMATION / VALIDATED_MANEUVER / DEMO. UNKNOWN으로 수행 확인 시 거절 |
| assistanceUsed | UNKNOWN / NONE / DETAILED / BRIEF / ON_REQUEST. 실제 제공 사실을 기록하고 단순 설정값에서 추정하지 않음 |

방향지시등 RIGHT는 `reportObservation`으로 보냅니다. 차로 변경 완료나 미러 주시를 증명하지 않습니다. 실제 차로 변경을 검증할 수 없으면 `markUnverified`로 남기거나 안전하게 주차한 후 사용자가 확인한 사실을 `USER_CONFIRMATION`으로 보냅니다. 차량 맥락에서 발생한 상황도 사용자가 수행을 확인했다면 저장되는 경험 근거는 USER가 됩니다.

## 최소 호출 예제

```kotlin
val bridge = QuestIntegration(context, demo = false)
val startedAt = System.currentTimeMillis()
bridge.startTrip("trip-42", EvidenceSource.VEHICLE, startedAt)

val lane = SituationContext(
    tripId = "trip-42", occurrenceId = "junction-7-lane", maneuverId = "junction-7",
    kind = SituationKind.LANE_RIGHT, source = EvidenceSource.VEHICLE,
    at = System.currentTimeMillis()
)
bridge.enterSituation("lane-enter", lane)
bridge.reportGuidance("lane-guide", lane, System.currentTimeMillis(), AssistanceUsed.BRIEF)
bridge.reportObservation("lane-indicator", lane, System.currentTimeMillis(), "방향지시등 RIGHT 관측")

// 검증된 수행 근거를 실제로 확보한 뒤에만 호출.
bridge.confirmPractice("lane-done", lane, System.currentTimeMillis(), ConfirmationBasis.VALIDATED_MANEUVER)
// 확보하지 못했다면 위 호출 대신 bridge.markUnverified(...) 사용.

val turn = lane.copy(occurrenceId = "junction-7-turn", kind = SituationKind.TURN_RIGHT,
    at = System.currentTimeMillis())
bridge.enterSituation("turn-enter", turn)
// ... 확인한 수행 사실을 전달 ...

val exit = turn.copy(occurrenceId = "junction-7-exit", kind = SituationKind.INTERSECTION_EXIT,
    at = System.currentTimeMillis())
// turn을 종료한 뒤 exit를 시작하고 종료. 같은 maneuverId 유지.
```

처리 결과 Boolean은 **새 상태를 받아 저장했으면 true**, 중복·유효하지 않은 순서·다른 출처·아직 끝나지 않은 상황이면 false입니다. 새 상황을 시작하기 전에 현재 상황을 수행 확인·건너뜀·보류·미확인·취소 중 하나로 종료하세요. 경로가 바뀌면 기존 상황을 취소하고 새 occurrenceId를 발급합니다.

## 별도 주행 화면에서 설정 관찰하기

```kotlin
val bridge = QuestIntegration(context, demo = false)
val statusView = QuestStatusView(context)
val subscription = bridge.observe(
    onState = { state ->
        runOnUiThread {
            val model = state.guideUiModel()
            yourGuide.render(model)
            statusView.render(model)
        }
    },
    onError = { error -> /* 기존 기록 보존, 연결 불가 표시 */ }
)
// 화면 종료 시 subscription.close(), yourGuide.release()
```

다른 `QuestIntegration` 인스턴스나 설정 화면에서 저장한 변경도 SharedPreferences 구독으로 전달됩니다. `render`는 상태를 표시하는 함수이며 동일 사건을 다시 생성하면 안 됩니다. 이벤트는 입력 수신 시점에 한 번만 생성하세요.

## 회고 파트와의 연결

회고 파트는 자신이 저장한 주행·메모를 같은 tripId/occurrenceId로 연결하고 최종 `DrivingFeedback`를 전달합니다. 3번은 확인된 수행 근거와 퀘스트 상태만 보관하므로 회고 저장소를 대체하지 않습니다. 현재 회고 브랜치의 분석 구현과 실제 병합하지 않았습니다. 사용자 메모는 감점에 사용하지 않고 추천 정렬에 사용합니다.
