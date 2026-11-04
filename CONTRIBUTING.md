# 뿌까 팀 개발 규칙

프로젝트 **튜토리얼**의 파일 위치, 이름, 작업 기록 방법을 정리한 팀 검토용 초안입니다. 현재 앱은 속도·타이어 카드·내비게이션 연결을 연습하는 시작 프로젝트이며, 기획한 모든 기능이 구현된 상태는 아닙니다.

## 먼저 지킬 규칙

1. 작업 전 최신 `main`을 받고, 작업 하나마다 브랜치를 만듭니다.
2. 파일은 아래 위치와 이름 규칙에 맞춰 추가합니다.
3. Commit 전에 변경 파일을 확인하고, 기능과 관계없는 파일은 제외합니다.
4. 작업 브랜치를 Push한 뒤 Pull Request(PR)를 엽니다.
5. 팀원이 변경 내용과 확인 결과를 검토한 뒤 `main`에 합칩니다. 작성자 외 1명의 확인을 권장합니다.

이 문서는 팀이 따를 작업 방식입니다. GitHub가 PR이나 승인을 자동으로 강제하도록 설정한 상태를 의미하지는 않습니다.

## 파일과 폴더 위치

| 위치 | 넣는 내용 |
| --- | --- |
| `README.md` | 앱 소개, 실행 방법, 구현 범위, APK와 제출 안내 |
| `CONTRIBUTING.md` | 파일 이름과 협업 규칙 |
| `docs/` | 개발 설명, SDK 검토 기록, 회의록 |
| `docs/images/` | 문서에서 사용하는 화면 캡처와 설명 이미지 |
| `.github/` | PR 양식 등 GitHub 협업 설정 |
| `app/src/main/java/com/hmgchallenge/pleosstarter/` | Kotlin 앱 소스 코드 |
| `app/src/main/res/` | 화면 문자열, 색상, 아이콘 등 Android 리소스 |
| `app/src/main/AndroidManifest.xml` | 앱과 권한 선언 |
| `app/src/test/` | 기기 없이 실행하는 단위 테스트 |
| `app/src/androidTest/` | 기기 또는 에뮬레이터에서 실행하는 테스트가 필요한 경우 사용 |
| `gradle/`, `gradlew`, `gradlew.bat` | 팀이 같은 빌드 도구를 사용하기 위한 Gradle Wrapper |
| `apk/` | 최종 제출용 APK를 저장소에 보관하기로 한 경우 사용 |

`docs/images/`, `androidTest/`, `apk/` 등은 필요한 파일을 만들 때 추가합니다. Git은 빈 폴더만으로는 폴더를 저장하지 않습니다.

Kotlin 코드가 `java/` 아래에 있는 것은 현재 프로젝트의 정상적인 구성입니다. 기존 소스 위치를 기준으로 작업합니다.

### 기능이 늘어날 때의 소스 구성

현재는 몇 개의 Kotlin 파일로 구성되어 있습니다. 새 기능이 생기면 아래와 같이 담당 범위를 나눕니다. 경로는 위 소스 패키지 폴더를 기준으로 합니다.

| 제안 경로 | 담당 내용 | 파일 이름 예시 |
| --- | --- | --- |
| `feature/preparation/` | 출발 준비와 미리보기 | `PreparationScreen.kt` |
| `feature/driving/` | 주행 안내와 연습 메모 | `DrivingScreen.kt`, `DrivingNote.kt` |
| `feature/parking/` | 주차 방법과 주차 연습 | `ParkingScreen.kt` |
| `feature/review/` | 오늘의 주행 돌아보기 | `ReviewScreen.kt` |
| `feature/quests/` | 퀘스트와 보조 졸업 | `QuestScreen.kt` |
| `core/vehicle/` | 차량 SDK 연결과 관측값 처리 | `VehicleConnection.kt` |
| `core/navigation/` | 내비게이션 서비스 연결 | `NavigationClient.kt` |
| `core/voice/` | 음성 입력과 출력 | `VoiceGuide.kt` |
| `ui/components/` | 여러 화면이 공유하는 UI | `CoachCard.kt` |

표의 파일 이름은 새 기능을 추가할 때 참고할 예시이며, 현재 모두 존재하는 파일 목록이 아닙니다. 폴더를 옮길 때는 Kotlin `package`, import, Manifest 등의 참조를 함께 확인합니다. 새 폴더를 만드는 변경과 기존 파일을 대량 이동하는 변경은 팀원이 이해할 수 있는 범위로 나눕니다.

## 이름 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 폴더와 Kotlin 패키지 | 영문 소문자. 패키지에는 하이픈을 넣지 않음 | `preparation`, `navigation` |
| Kotlin 클래스와 파일 | 단어 첫 글자를 대문자로 연결 | `DrivingNote.kt`, `CoachCard.kt` |
| Kotlin 함수와 변수 | 첫 단어는 소문자, 다음 단어 첫 글자는 대문자 | `saveDrivingNote`, `guideLevel` |
| Kotlin 상수 | 대문자와 밑줄 | `MAX_NOTE_LENGTH` |
| Android 리소스 이름 | 영문 소문자와 밑줄 | `ic_coach.xml`, `guide_repeat_label` |
| 문서 파일 | 영문 소문자와 하이픈 | `vehicle-sdk-notes.md` |
| 회의록 | 날짜와 영문 주제 | `2026-11-04-guidance-priority.md` |
| 문서용 이미지 | 내용을 알 수 있는 영문 소문자와 하이픈 | `driving-note-preview.png` |

- 파일과 폴더 이름에 공백을 넣지 않습니다. 설명과 문서 본문, 앱 표시 문구는 한국어로 작성해도 됩니다.
- `최종`, `진짜최종`, `수정본2`, `copy` 같은 복사 파일을 쌓지 않습니다. 이전 버전은 Git 기록으로 확인합니다.
- `MainActivity.kt`처럼 이미 있는 이름과 Android가 정한 `AndroidManifest.xml`, `strings.xml` 같은 이름을 존중합니다.
- 기능 이름은 같은 의미로 통일합니다. 예를 들어 연습 메모 기능에 `DrivingNote`를 쓰기로 했다면 같은 개념을 다른 파일에서 `Memo`, `Record`, `History`로 섞어 부르지 않습니다.

## 코드 편집

- 인코딩은 UTF-8, 들여쓰기는 공백 4칸을 기본으로 합니다.
- XML·Markdown·YAML·JSON은 `.editorconfig`의 공백 2칸 설정을 사용합니다.
- 저장소의 `.editorconfig`를 읽는 편집기에서 공통 편집 설정을 적용할 수 있습니다. 기존 파일 전체를 한 번에 포맷하는 작업은 기능 변경과 분리합니다.
- 새 화면의 사용자 표시 문구는 `res/values/strings.xml`에서 관리합니다.
- 한 파일은 이해할 수 있는 책임 범위로 유지합니다. 화면, 차량 연결, 음성 처리가 커지면 해당 기능이나 공통 연결 코드로 분리합니다.
- 기존 코드를 먼저 읽고 같은 방식으로 작성합니다. SDK·Gradle·Kotlin 버전 변경은 영향을 설명하는 별도 PR로 검토합니다.

## 브랜치 이름

| 작업 | 형식 | 예시 |
| --- | --- | --- |
| 기능 추가 | `feature/짧은-영문-주제` | `feature/driving-note` |
| 오류 수정 | `fix/짧은-영문-주제` | `fix/navigation-retry` |
| 문서 정리 | `docs/짧은-영문-주제` | `docs/run-guide` |
| 개발 설정 | `chore/짧은-영문-주제` | `chore/build-settings` |
| Codex가 만드는 작업 브랜치 | `codex/짧은-영문-주제` | `codex/team-conventions` |

`main`은 팀이 공유하는 기준 버전입니다. 개인 작업 브랜치에서 개발하고 PR로 합치는 것을 기본 흐름으로 사용합니다. 같은 기능을 여러 명이 맡으면 작업 범위를 먼저 나눕니다.

## Commit 메시지

형식은 `종류: 변경 내용을 설명하는 한 문장`입니다. 설명은 한국어로 작성해도 됩니다.

| 종류 | 사용 시점 | 예시 |
| --- | --- | --- |
| `feat` | 기능 추가 | `feat: 어려운 구간 음성 메모 추가` |
| `fix` | 오류 수정 | `fix: 연결이 끊긴 속도값 표시 수정` |
| `docs` | 문서 변경 | `docs: 팀원 실행 방법 보완` |
| `refactor` | 기능을 유지하며 코드 구조 개선 | `refactor: 차량 연결 처리 분리` |
| `test` | 테스트 변경 | `test: 누락된 차량값 처리 확인` |
| `chore` | 개발 도구와 설정 변경 | `chore: 편집기 공통 설정 추가` |

한 Commit에는 설명할 수 있는 하나의 변경 묶음을 담습니다. `수정`, `업데이트`, `123`처럼 변경 이유를 알 수 없는 메시지는 피합니다.

## 작업을 공유하는 순서

1. 현재 작업을 Commit하거나 Stash합니다. `main`으로 전환해 `Fetch origin`과 `Pull origin`으로 최신 변경을 받습니다.
2. 작업할 Issue 또는 작업 항목과 담당 범위를 확인합니다. 관련 요구사항이 있으면 `FR-47`처럼 ID를 연결합니다.
3. 최신 `main`에서 작업 브랜치를 만듭니다.
4. 수정하고, Changes에서 올릴 파일과 내용을 확인합니다.
5. 변경에 맞는 확인을 수행하고 Commit → Push합니다.
6. PR을 열어 변경 내용, 확인 방법과 결과, 관련 요구사항을 적습니다. 화면 변경은 캡처를 첨부합니다.
7. 팀원이 확인하고 `main`에 합칩니다. 다른 팀원은 최신 `main`을 Pull합니다.

GitHub 웹에서 파일을 고칠 때도 새 브랜치와 PR을 사용하는 것을 기본으로 합니다. GitHub의 파일 저장, 내 PC의 Commit, GitHub로 보내는 Push는 서로 다른 단계입니다.

### 확인 범위

- 앱 코드나 빌드 설정을 바꾸면 README의 빌드·Lint·단위 테스트 명령 중 영향을 받는 항목을 확인합니다.
- 화면을 바꾸면 대상 에뮬레이터에서 확인하고 사용한 기기를 PR에 적습니다.
- 문서만 바꾸면 링크, 경로, 설명과 실제 프로젝트가 맞는지 확인합니다. 앱 실행을 확인하지 않았다면 실행한 것으로 적지 않습니다.
- 데모 데이터, 일반 Android 화면 실행, Pleos 차량 API 실행 결과는 구분해서 기록합니다.

## 올리는 파일과 제외하는 파일

**함께 올리는 것:** 앱 소스, Android 리소스, 필요한 테스트, 빌드 설정, Gradle Wrapper, 문서와 문서용 이미지입니다.

**제외하는 것:** `.gitignore`에 정한 개인 설정과 생성 파일입니다. `local.properties`, `.idea/`, `.gradle/`, `build/`, API 키와 비밀번호, 서명 키를 Commit하지 않습니다. APK는 기존 규칙에 따라 `apk/`의 제출 파일 또는 GitHub Releases에서 관리합니다.

`.gitignore`는 이미 추적 중인 파일을 자동으로 제거하지 않습니다. 실수로 올린 파일은 팀에 알리고 별도 변경으로 처리합니다.

## 충돌과 규칙 변경

같은 파일에서 충돌이 나면 양쪽 내용을 읽고 담당자와 합의해 해결합니다. 내용을 확인하지 않고 한쪽 버전을 통째로 덮어쓰거나 공유 브랜치에 강제 Push하지 않습니다.

규칙을 바꿀 때는 이 문서를 PR에서 수정하고 변경 이유를 적습니다. 팀 운영 규칙과 GitHub에서 자동으로 강제하는 설정은 구분합니다. 브랜치 보호 등 자동 강제 설정은 저장소의 요금제와 팀 합의를 확인한 뒤 별도로 정합니다.
