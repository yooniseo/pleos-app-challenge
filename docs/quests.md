# 연습 퀘스트 (파트 C) — 데모 버전

짧은 데모용으로 퀘스트를 2개로 줄였습니다. 진행은 항상 자동이고, 실패해도 점수는 내려가지 않습니다.

## 퀘스트

| ID | 퀘스트 | 성공 조건 | 실패로 기록되는 경우 |
|---|---|---|---|
| `Q_LANE_CHANGE` | 차선 변경 | 바꾸려는 방향 깜빡이를 먼저 켜고(기준 시간 이상) 핸들을 움직임 | 깜빡이 없음 / 반대쪽 깜빡이 / 너무 늦게 켬 |
| `Q_INTERSECTION_RIGHT` | 교차로 우회전 | 오른쪽 깜빡이를 먼저 켜고, 진입 전에 브레이크를 밟고, 우회전 | 위와 같음 + 브레이크 안 밟음 |

- 성공 1번 = 막대 1/30. 30번 채우면 다음 레벨(막대는 0으로). 실패는 기록만 남고 감점 없음.
- 진행도는 앱이 켜져 있는 동안만 유지됩니다(DB 연결 전).

## 사용하는 차량 데이터 (Pleos Vehicle SDK 2.3.2)

| 값 | API | 권한 |
|---|---|---|
| 방향지시등 | `vehicle.getTurnSignal().registerTurnSignalState` | `pleos.car.permission.CAR_LIGHTS` |
| 앞바퀴 조향각(도, 왼쪽 음수) | `vehicle.getSteeringWheel().registerSteeringWheelAngle` | `pleos.car.permission.CAR_DRIVE` |
| 브레이크 페달 | `vehicle.getBrake().registerBrakePedalStatus` | `pleos.car.permission.CAR_DRIVE` |

조향각은 핸들 회전각이 아니라 **앞바퀴 각도**입니다.

## 기준값 (임시 숫자 — `ManeuverDetector.kt`의 `DetectorConfig`에서 변경)

| 이름 | 값 | 뜻 |
|---|---|---|
| `steerStartDeg` | 3° | 이 이상 꺾이면 조작 시작 |
| `steerReturnDeg` | 1.5° | 이 아래로 돌아오면 조작 끝 → 판정 |
| `turnDeg` | 15° | 최대 각도가 이 이상이면 교차로 회전, 미만이면 차선 변경 |
| `signalLeadMs` | 2000 | 조향 전에 깜빡이를 켜 두어야 하는 시간 |
| `brakeLookbackMs` | 5000 | 회전 시작 전 이 시간 안에 밟은 브레이크를 감속으로 인정 |
| `cooldownMs` | 4000 | 조작 직후 핸들을 되돌리는 작은 움직임은 무시 |

## 데모 순서

1. 메인 화면 → **연습 퀘스트 열기**
2. 차선 변경: 오른쪽(또는 왼쪽) 깜빡이 켜기 → 2초 뒤 조향각 약 5° → 0°로 복귀 → `차선 변경` 막대 +1
3. 교차로 우회전: 오른쪽 깜빡이 켜기 → 브레이크 밟기 → 조향각 20° 이상 → 0°로 복귀 → `교차로 우회전` 막대 +1

화면 아래 "지금 차량 값"에 깜빡이·조향각·브레이크가 실시간으로 보이므로 에뮬레이터 값이 들어오는지 바로 확인할 수 있습니다.

## 파일

- `feature/quests/QuestModels.kt` — 퀘스트 ID, 카탈로그, 시도·진행 모델
- `feature/quests/ManeuverDetector.kt` — 차량 값으로 시도 감지 + 성공/실패 판정 (순수 Kotlin)
- `feature/quests/QuestBoard.kt` — 레벨·진행도 누적 (순수 Kotlin)
- `feature/quests/QuestVehicleSource.kt` — Vehicle SDK 구독
- `feature/quests/QuestActivity.kt` — 퀘스트 화면
- 테스트: `app/src/test/.../feature/quests/QuestTest.kt`
