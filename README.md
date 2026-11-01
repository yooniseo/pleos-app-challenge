# 현대차 공모전 팀 프로젝트 — PleosStarter

공모전 앱의 최종 아이디어를 정하기 전, Kotlin 앱 실행과 Pleos Vehicle SDK 호출을 연습하는 시작 프로젝트입니다.

팀 저장소: https://github.com/yooniseo/pleos-app-challenge (비공개, 소유자 yooniseo)

## 실행

1. Android Studio에서 이 폴더를 엽니다.
2. Gradle JDK를 Android Studio에 포함된 JDK 17로 설정하고 Gradle Sync를 진행합니다.
3. Android SDK Platform 34를 설치합니다.
4. 공식 가이드에 따라 Pleos Connect 에뮬레이터를 설치·실행합니다.
5. `app` 실행 대상을 에뮬레이터로 지정하고 Run을 누릅니다.
6. 앱의 **문 상태 조회**를 누르면 앞줄 왼쪽 문 위치의 원본 응답 값을 표시합니다.

일반 Android 에뮬레이터에서는 화면을 확인할 수 있지만, 차량 API에는 Pleos의 Vehicle Service가 필요합니다. 화면에 표시하는 값은 SDK에서 실제로 받은 응답입니다.

Windows 터미널에서 빌드하려면 JDK 17과 Android SDK 경로를 설정한 후 실행합니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

## 개발 환경

- Kotlin 1.9.25 / Kotlin DSL
- Android Gradle Plugin 8.1.4 / Gradle Wrapper 8.1.1
- Java 17 / compileSdk·targetSdk 34 / minSdk 28
- Pleos Vehicle SDK 2.3.2
- 디버그 APK: `app/build/outputs/apk/debug/app-debug.apk`

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
