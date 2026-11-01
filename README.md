# 현대차 공모전 팀 프로젝트 — 나의 드라이브

속도·네 바퀴 타이어 카드·목적지 안내 요청을 한 화면에서 연습하는 Kotlin 시작 프로젝트입니다. 앱 자체에 지도나 경로 탐색 엔진은 없으며, 실제 길 안내는 Pleos 기본 내비게이션에 요청합니다.

팀 저장소: https://github.com/yooniseo/pleos-app-challenge (비공개, 소유자 yooniseo)

## 실행

1. Android Studio에서 이 폴더를 엽니다.
2. Gradle JDK를 Android Studio에 포함된 JDK 17로 설정하고 Gradle Sync를 진행합니다.
3. Android SDK Platform 34를 설치합니다.
4. 일반 Android 에뮬레이터 또는 공식 Pleos Connect 에뮬레이터를 실행합니다.
5. `app` 실행 대상을 에뮬레이터로 지정하고 Run을 누릅니다.
6. 기본값인 **샘플 데이터로 보기**에서 속도 슬라이더, 네 바퀴 공기압 예시, 목적지 선택을 체험합니다.
7. Pleos Connect 환경에서는 스위치를 끄고 실제 차량 연결을 시도합니다. 속도 권한이 표시되면 허용합니다.

샘플 모드의 숫자는 모두 가상 데이터이며 실제 차량을 측정하지 않습니다. 샘플 모드에서는 내비게이션 SDK 호출도 전송하지 않습니다. 실제 모드에서 서비스·권한·차량 지원이 없으면 미지원/조회 불가로 표시하며 샘플 숫자로 대체하지 않습니다.

## 구현 범위와 SDK 제한

| 항목 | 샘플 모드 | 실제 차량 모드 |
| --- | --- | --- |
| 속도 | 0~120 km/h 슬라이더 | AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED` 구독. m/s의 절댓값 × 3.6으로 표시. 5초간 응답이 없으면 측정값을 숨김 |
| 타이어 | 네 바퀴 가상 공기압. 180 kPa는 화면 체험용 임의 기준 | Pleos Vehicle의 `getLowTirePressure()`로 **심각한 저압 기준값**만 조회. **현재 공기압이나 저압 발생 여부가 아님** |
| 목적지 | 서울시청/서울역 선택 결과 표시 | NaviHelper `requestRoute()` 요청. `onRoutingCompleted()`를 받은 뒤 탐색 완료 표시 |
| 지도 | 제공하지 않음 | `requestNavigationScreen(Foreground)`으로 차량 기본 내비 화면 열기 요청 |

Vehicle SDK **2.3.2의 배포 소스** `ai/pleos/playground/vehicle/api/Tire.kt`에 따르면 현재 공기압을 읽는 `getTirePressure()`는 숨겨진 API이며 `PRIVATE_CAR_SAFETY`(`signature|privileged`) 권한이 필요합니다. 일반 앱용 권한 `CAR_SAFETY`의 `getLowTirePressure()`와 혼동하지 마세요. 이 프로젝트는 제조사 전용 권한을 요청하거나 우회하지 않습니다. 실측 공기압과 마모·펑크 진단은 구현하지 않았습니다. 공모전용 별도 권한 제공 여부는 주최 측 확인이 필요합니다.

- [공식 Vehicle 2.3.2 소스 JAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2-sources.jar): ZIP처럼 열어 `Tire.kt` 확인
- [공개 Tire API 목록](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/tire/entry)
- 속도 연결은 Pleos 전용 SDK가 아닌 Android Automotive 표준 API입니다. 실제 Pleos 환경의 접근 허용 여부는 별도 실행 검증이 필요합니다.

화면을 닫거나 모드를 바꾸면 서비스·구독을 해제하고 이전 콜백을 무시합니다. 타이어 기준값은 연결 시 한 번 조회합니다. 값 갱신/재연결은 샘플 모드를 켰다가 끄면 됩니다. 내비 응답을 못 받은 상태에서는 중복 경로 요청을 막습니다. 이 예제는 개발·정차 상태에서 기능 연결을 학습하기 위한 화면이며 운전 중 사용을 위한 UX 검증은 하지 않았습니다.

### 코드를 읽는 순서

1. `app/src/main/java/com/hmgchallenge/pleosstarter/MainActivity.kt`: 화면 만들기 → 샘플/실제 모드 → 타이어 기준 조회 → 내비 요청
2. `SpeedSource.kt`: 차량 속도 이벤트 구독과 연결 해제
3. `Speed.kt`: m/s를 km/h로 바꾸는 작은 함수
4. `app/src/main/AndroidManifest.xml`: 차량 속도·안전 정보·내비 요청 권한

별도 지도 API 키나 서버는 필요하지 않습니다. 예제 목적지는 `MainActivity.kt`의 `places` 목록에서 바꿀 수 있습니다. 장소명 검색·현재 위치 지도·자체 길 안내·백그라운드 기록은 구현하지 않았습니다.

Windows 터미널에서 빌드하려면 JDK 17과 Android SDK 경로를 설정한 후 실행합니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

## 개발 환경

- Kotlin 1.9.25 / Kotlin DSL
- Android Gradle Plugin 8.1.4 / Gradle Wrapper 8.1.1
- Java 17 / compileSdk·targetSdk 34 / minSdk 28
- Pleos Vehicle SDK 2.3.2 / NaviHelper SDK 3.0.5
- 디버그 APK: `app/build/outputs/apk/debug/app-debug.apk`

### 검증 기록 (2026-11-01)

- Windows / Android Studio JDK 17 / Android SDK 34에서 `assembleDebug`, `lintDebug`, `testDebugUnitTest` 확인
- 속도 변환 단위 테스트 2개: 정방향·후진·정지 및 결측·NaN·무한대·변환 오버플로 처리
- Lint 오류는 없으며, 한국어 문자열의 리소스 분리·앱 아이콘 등 시작 프로젝트의 개선 경고가 남아 있음
- 일반 Android 에뮬레이터 부팅이 완료되지 않아 APK 설치·화면 실행 확인은 미완료
- Pleos 에뮬레이터/실차의 속도, 타이어 기준값, 경로 탐색·내비 화면 전환은 아직 실행 검증하지 못함
- 다운로드용 debug APK는 GitHub Releases의 `v0.2.0-demo` 체험판에 제공. 서명된 제출용 release APK가 아님

설치된 Android Studio Giraffe 계열과 JDK 17에 맞춘 시작용 버전입니다. 최신 버전 조합을 의미하지 않습니다. compileSdk 34에 관해 AGP가 권장 업그레이드 경고를 출력할 수 있습니다. 도구를 업그레이드할 때는 팀 전체가 함께 맞추세요.

## 팀 협업

각자 GitHub 계정을 사용하고 한 팀 저장소에 팀원을 초대합니다. GitHub Desktop 또는 Android Studio의 Git 기능 중 편한 것을 사용하세요.

### 팀원 연결

1. 대표자는 저장소 설정의 Collaborators에서 팀원의 개인 GitHub 계정을 초대합니다.
2. 팀원은 초대를 수락합니다.
3. GitHub Desktop의 `File > Clone repository`에서 위 저장소를 각자 컴퓨터에 복제합니다.
4. Android Studio에서 복제한 폴더를 엽니다. Windows에서는 한글이 없는 로컬 경로를 사용합니다.

대표자 PC의 실제 개발 경로는 `C:/Users/kim/Projects/pleos-app-challenge`입니다. 바탕화면의 `현대차 공모전/팀프로젝트`는 같은 디렉터리를 가리키는 연결 폴더입니다. Android Studio와 GitHub Desktop에는 영문 경로를 등록하세요. 두 위치에 파일을 각각 복사할 필요가 없습니다.

### 매일의 작업

1. 시작 전에 GitHub Desktop에서 `Fetch origin`을 누르고, 변경이 있으면 `Pull origin`으로 받습니다.
2. GitHub Issue에 작업과 담당자를 정하고 작업별 브랜치를 만듭니다. 예: `feature/travel-history`, `fix/door-permission`.
3. 파일을 수정한 뒤 Changes에서 내용을 확인하고 작은 단위로 Commit합니다.
4. `Push origin`으로 팀 저장소에 올립니다.
5. Pull Request에 변경 내용과 확인 결과를 적고 팀원 검토 후 `main`에 합칩니다.
6. 다른 팀원은 최신 `main`을 Pull하여 반영합니다. 작업 브랜치에서는 최신 `main`을 별도로 병합해야 합니다.

Commit은 내 PC에 변경 기록을 저장하는 동작이고, Push는 그 기록을 GitHub에 올리는 동작입니다. Fetch는 팀 변경이 있는지 확인하고, Pull은 변경을 내 폴더에 받아옵니다. 파일 저장만으로 업로드되거나 팀 변경이 자동으로 덮어써지지는 않습니다. GitHub Desktop에서 로컬 수정이 표시되면 먼저 Commit하거나 Stash한 뒤 Pull하세요. 충돌이 생기면 내용을 확인하고 해결합니다.

같은 화면·파일을 동시에 고칠 때는 먼저 담당 범위를 나눕니다. SDK·Gradle·Kotlin 버전은 팀이 함께 변경합니다. 디자인과 아이디어는 팀의 기존 Figma 파일 링크로 공유합니다.

이 프로젝트는 별도 GitHub CLI 인증으로 연결합니다. ChatGPT 플러그인의 Primary 계정은 변경하지 않습니다. GitHub Desktop을 이용할 경우에도 저장소 접근 권한이 있는 계정으로 로그인하세요.

## 업로드에서 제외되는 것

`local.properties`, `.idea/`, 빌드 캐시, API 키용 파일, 서명 키는 `.gitignore`로 제외합니다. 실제 API 키나 비밀번호를 코드·이슈·PR에 넣지 마세요. Gradle Wrapper 파일은 팀원이 같은 빌드 도구를 사용할 수 있도록 함께 올립니다.

## 제출 준비

공식 접수는 기획서와 참가자 전원의 동의서를 이메일로 제출하는 방식입니다. 1차 개발 산출물은 선택·가산점이며, GitHub 제출 시 소스 코드와 실행 가능한 release APK를 함께 준비합니다.

- APK 위치: 저장소 `apk/` 폴더 또는 용량 초과 시 GitHub Releases
- APK 이름: `팀명_앱이름_release.apk`
- README 필수 안내: 앱 이름·소개, 실행 방법, 테스트 환경·설정, APK 위치
- 비공개 저장소: 공지된 심사용 계정을 초대하고 접근 확인
- 이 시작 프로젝트의 debug APK는 제출용 release APK가 아닙니다. 최종 앱은 별도 release 서명과 실제 실행 검증이 필요합니다.

## 공식 문서

- [공모전 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)
- [Pleos 앱 개발 시작하기](https://document.pleos.ai/en/docs/pleos-only/getting-started)
- [Vehicle SDK](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [NaviHelper SDK](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)
- [Android Automotive 차량 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds#PERF_VEHICLE_SPEED)
