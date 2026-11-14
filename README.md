# 차근차근 · Pleos Connect 주행 코치

현재 개발 브랜치: **refactor/chageun-product-ui**. 실행 아이콘은 **차근차근** 하나이며 지도·퀘스트·주차·주행 피드백을 앱 안에서 전환합니다.

- [코드 구조·실행 흐름·데이터 구조 그림](docs/product-redesign/architecture.md)
- [피그마 화면·이미지 반영 기준](docs/product-redesign/figma-assets.md)
- [검증 방법과 결과](docs/product-redesign/validation.md)
- [주행 분석·맞춤 퀘스트 통합](docs/driving-analysis-integration.md)

수동 페달·기어·운전석 및 Virtual Console 쓰기를 제거하고, 자동 시연과 실제 SDK 읽기를 분리했습니다. 이전 구현에서 사용하던 회전/복원 코칭 핸들은 유지했습니다. 기존 Room DB와 주행 기록을 보존하며 신규 계정·서버 연결은 하지 않습니다.

## 실행

Android Studio에서 프로젝트를 열고 JDK 17 / Android SDK 34를 선택한 뒤 `app`을 Pleos 에뮬레이터에 실행합니다.

1. 지도에서 **연습 선택**: 차로 변경＋우회전 / 차로 변경 / 우회전 / 후진 주차.
2. **자동 시연 시작**을 누릅니다. 페달이나 기어 입력은 필요 없습니다.
3. 아래 탭으로 퀘스트·주차·피드백을 오갈 수 있습니다. 앱 내부 전환 중에도 같은 연습이 이어집니다.
4. 연습 완료 시 저장된 거리·시간·완료 퀘스트·보상을 표시합니다. **지난 기록**에서 다시 볼 수 있습니다.
5. **퀘스트**를 누르면 주행 점수와 맞춤 퀘스트가 먼저 열립니다. **종합 기록**에서 기존 6개 영역을, **차량 연결**에서 실제 신호 퀘스트와 주행 기록 시작·종료를 확인합니다.
6. 종합 기록의 **초기화**는 현재 시연 성장·포인트를 0부터 시작하고 지난 주행/개인 기록을 보존합니다.

시작 전에는 완료 기록이나 점수를 자동 생성하지 않습니다. 주차/피드백의 빈 상태도 별도로 제공합니다. 화면과 저장 구조는 `ReplayCatalog`의 네 선택 데이터와 분리되어 있습니다.

## 실제 데이터와 시연

기본 자동 시연은 도로 위치·속도·차로·핸들을 계산한 예시입니다. 설정에서 **차량 SDK 속도**를 선택하면 수신한 실제 속도만큼 진행하지만 경로·차로·핸들은 계속 가상이며, 완료 기록도 `SIMULATION`/`demo`입니다. 속도·깜빡이만으로 실제 운전 성공을 판정하지 않습니다. 퀘스트는 출발 준비·차선 변경·교차로·속도와 거리 관리·주차·하차 준비의 6개 영역입니다. 시연 횟수는 저장된 수행 근거에서 1/30씩 올라갑니다. 출발·하차 점검은 직접 확인하며, 전체 자동 시연의 감속 완료를 실제 차간거리 판정과 구분합니다. 자세한 조건은 [6개 퀘스트 연결](docs/quests-six-areas-and-figma.md)을 참고하세요.

Pleos TTS/STT, Vehicle, NaviHelper 및 AAOS 속도 어댑터를 사용합니다. 지도는 번들된 OSM/OSRM 기하를 그려 네트워크 없이 표시합니다. 별도의 지도 API 키는 필요하지 않습니다. 실제 차량의 주행 중 앱 승인·백그라운드 코칭·실제 차로/조향 검증·서버 동기화는 추가 개발 범위입니다.

## 빌드와 검사

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`입니다. 에뮬레이터 설치는 `adb install -r`을 사용해 기존 데이터를 유지합니다.

네이티브 자동 검사는 별도 `product-validation.db`에서 빈 화면·네 시연·실시간 진행·화면 전환·중복 보상·DB 재오픈·초기화를 확인합니다. 사용자 `trips.db`를 초기화하지 않습니다.

```powershell
adb shell am instrument --user 10 -w com.hmgchallenge.pleosstarter.test/com.hmgchallenge.pleosstarter.product.ProductSmokeRunner
```

Kotlin 1.9.25 · AGP 8.1.4 · Gradle 8.1.1 · Java 17 · compile/target 34 · min 28 · Room 2.6.1.

`docs/experiments`와 `docs/integration`은 이전 브랜치 실험 기록이며 현재 화면/실행 설명은 위 문서를 기준으로 합니다. 다른 팀원 브랜치에는 변경을 적용하지 않습니다.
