# 차근차근 2026 현대차 공모전

차로를 바꿀 때 언제 깜빡이를 켜고, 우회전이나 후진 주차를 할 때 핸들을 얼마나 돌려야 할지 연습하는 차량용 Android 앱입니다. 주행을 마치면 퀘스트 결과와 피드백을 확인할 수 있습니다.

## 실행

Android Studio에서 JDK 17과 Android SDK 34를 설정하고, Pleos 에뮬레이터에서 `app`을 실행합니다.

1. 지도에서 **연습 선택**을 엽니다. 차로 변경＋우회전, 차로 변경, 우회전, 후진 주차 중 하나를 고릅니다.
2. **자동 시연 시작**을 누릅니다. 페달이나 기어를 조작할 필요는 없습니다. 퀘스트·주차·피드백 탭으로 이동해도 연습은 이어집니다.
3. 연습이 끝나면 거리, 시간, 완료한 퀘스트를 확인합니다. 저장한 결과는 **지난 기록**에서 다시 볼 수 있습니다.

## 시연과 차량 기록

자동 시연의 경로·차로·핸들 움직임은 시나리오 값입니다. 설정에서 **차량 SDK 속도**를 선택하면 경로를 따라가는 속도만 차량 신호를 사용합니다. 두 모드 모두 시연으로 저장합니다. 예를 들어 종합 시연에서는 두 차례 차로 변경 중 한 번만 깜빡이를 켜므로 깜빡이 점수는 50점입니다.

차량 주행 기록은 별도입니다. 차량이 움직이면 기록을 시작하고, 주행 후 정차해 P단으로 바꾸면 마칩니다. 정차 중에는 **주행 피드백 → 차량 주행 기록 마치기**로 직접 종료할 수도 있습니다. 기록은 기기 안의 Room DB에 저장합니다. 지도도 앱에 포함되어 있어 별도 지도 API 키가 필요하지 않습니다.

## 팀 작업

| 기여자 | 맡은 작업 | 원본 작업 |
| --- | --- | --- |
| 김서연 · [@yooniseo](https://github.com/yooniseo) | 주행·주차 코치 화면, 시연 흐름, 기능 통합 | [코치 화면과 공통 시연 세션](https://github.com/yooniseo/pleos-app-challenge/commit/2fe16a7706217fe09f9c21f39d1837adfa9b328d) |
| 신가인 · [@ssggiisgi](https://github.com/ssggiisgi) | 차량 속도·음성 인식 실험, Room 데이터 모델, 주행 분석과 맞춤 퀘스트 추천 | [주행 점수와 맞춤 퀘스트](https://github.com/yooniseo/pleos-app-challenge/commit/a6897f099086b2dc144aa89d84c40488e9429860) |
| 박서연 · [@SYParkky](https://github.com/SYParkky) | 퀘스트 보드, 차량 신호를 이용한 주행 동작 감지, 퀘스트 화면 원형 | [퀘스트 브랜치의 원본 구현](https://github.com/yooniseo/pleos-app-challenge/commit/fba21000320126e698b300abe25863944ed27455) |

## 빌드와 검사

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 에뮬레이터 검사 방법과 결과는 [검증 기록](docs/product-redesign/validation.md)에 있습니다.

## 개발 문서

- [앱 구조와 데이터 흐름](docs/product-redesign/architecture.md)
- [화면과 디자인 자료](docs/product-redesign/figma-assets.md)
- [주행 분석과 맞춤 퀘스트](docs/driving-analysis-integration.md)
- [6개 영역별 퀘스트 판정 조건](docs/quests-six-areas-and-figma.md)
