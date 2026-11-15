# 차량 신호 퀘스트 통합

`scratch/quests`의 `fba2100`에서 모델·감지기·30단계 보드를 가져왔다. 기존 실행 화면·Manifest·STT 구현은 덮어쓰지 않았다. 현재 브랜치는 `refactor/chageun-product-ui`다.

## 실행 흐름

`VehicleTelemetry` → `QuestVehicleSource`(Pleos Vehicle 2.3.2의 TurnSignal / SteeringWheel / Brake) → `VehicleQuestController` → `ManeuverDetector` → `SharedCoachRepository` → Room → 퀘스트 / 코치 / 주행 피드백.

- 속도는 기존 Android Car `SpeedSource`를 유지한다. 세 SDK 신호는 구독과 1초 주기 조회를 함께 사용한다.
- 2026-11-15 사용자 요청으로 퀘스트 화면의 차량 신호 전환과 진단 화면 진입을 제거했다. 퀘스트에는 6개 시연 영역만 표시한다. SDK 신호 감지·코치·Room 저장은 계속 유지한다.
- 신선한 속도·깜빡이·조향각이 있고 움직일 때 기록을 시작한다. 탭을 옮겨도 감지는 계속된다. 앱이 백그라운드로 나가면 진행 중 기록을 중단 상태로 저장하고 연결을 해제한다. 백그라운드 서비스는 이번 범위에 포함되지 않는다.
- 결과를 저장한 뒤 지도 코치 배너와 기존 Pleos TTS에 전달한다. 코치/음성 OFF 설정을 존중한다. 차량 기록이 진행 중이면 가상 시연 음성은 억제한다.
- 앱이 백그라운드로 나갈 때 진행 중 차량 기록을 저장한다. 저장된 차량 기록은 주행 피드백의 지난 기록에서 볼 수 있다. 기존 Pleos STT 메모 입력도 해당 차량 기록에 저장한다. 인식 결과는 운전자가 확인하고 저장한다.

## 저장과 구분

스키마 변경 없이 기존 테이블을 사용한다. `CoachingEvent(VEHICLE_QUEST_ATTEMPT)`에 원래 판정 입력을 저장하고 `AppSnapshot(vehicle-quests:VEHICLE)`과 조회용 `Quest(vehicle:VEHICLE:...)`, `TripReport`를 한 트랜잭션에서 갱신한다. trip과 속도 샘플은 기존 `Trip`·`TripSample`에 저장한다. 이벤트 ID는 재시도에도 중복 증가하지 않는다. 보드는 최근 20건만 캐시하며 전체 시도는 이벤트에 남는다.

SDK 관측은 `VEHICLE`, 테스트 입력은 `SIMULATION`으로 분리한다. 30회 조건 충족 시 다음 레벨로 올라간다. 실패·보류는 진행도를 내리지 않는다. 기존 시연 퀘스트·포인트·주차 후 CLEAR와 별개이며, 단순 조향 임계값에는 기존 연습 보상을 지급하지 않는다. 시연 초기화로 차량 보드를 삭제하지 않는다.

## 판정의 한계

시작 3° / 복귀 1.5° / 회전 분류 15° / 깜빡이 선행 2초 / 브레이크 과거 5초 / 재감지 대기 4초는 팀 코드의 **임시 기준**이다. 실제 차량 조향각의 단위·부호·핸들 회전량과의 관계를 검증해야 한다. 각도만으로 차로 위치, 도로 종류, 실제 교차로 진입, 보행자 확인을 증명하지 않는다. 따라서 UI는 ‘신호 조건 연습’으로 명시한다.

3초가 지난 속도·깜빡이·조향각은 감지를 취소한다. 재연결 시 중립 조향을 확인하기 전에는 새 시도를 만들지 않는다. 우회전 중 브레이크 값이 없으면 성공 대신 판정 보류로 저장한다. 내비 TBT·위치·차로 인식과 결합한 실제 주행 성공 판정은 별도 구현이 필요하다.

## 검증

- JVM: 기존 회귀 테스트와 감지기 테스트, 누락 신호·재연결·누락 브레이크 검사.
- Android: 기존 `ProductSmokeRunner`의 `-e vehicle_quests true` 모드. 별도 `vehicle-quest-validation.db`만 사용한다.
- 에뮬레이터의 실제 SDK에서 세 신호 GET/구독 수신을 확인한다. 움직이는 판정 시퀀스는 명시적인 `SIMULATION` 입력으로 실행하며 물리 차량 검증으로 해석하지 않는다.
- 차선 변경·우회전 각각 1/30, 잘못된/늦은/누락 깜빡이, 브레이크 누락, 오래된 입력, 중복 저장, 30회 레벨 상승, DB 재연결 후 복구와 피드백을 검사한다.
- 기존 네 자동 시연·주차 후 완료·Pleos TTS·탭 전환·기록 보존 회귀 검사를 유지한다. STT 파일은 원본과 동일하며 이번 검증에서 마이크 인식을 다시 수행하지 않았다.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s emulator-5574 shell am instrument --user 10 -w -e vehicle_quests true com.hmgchallenge.pleosstarter.test/com.hmgchallenge.pleosstarter.product.ProductSmokeRunner
```
