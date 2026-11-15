# 집 PC 실행 및 파트 연결 검증 — 2026-11-12

작업 브랜치: `scratch/driving-guide`, 시작 커밋 `0b3f5f3`. main 및 다른 팀 원격 브랜치는 변경하지 않았다.

## 환경과 실행

- 실제 소스: `C:\Users\sy-desktop\Projects\pleos-app-challenge`
- 바탕화면 `현대차 공모전\팀프로젝트`는 이 소스의 junction이다.
- JDK: `C:\Users\sy-desktop\DevTools\jdk-17.0.21+9`
- Android SDK: `C:\Users\sy-desktop\AppData\Local\Android\Sdk`
- 공식 Pleos Connect 2.1.0 x86_64 이미지: SDK의 `system-images\android-34\pleos_car_emulator\x86_64`
- AVD: `%USERPROFILE%\.android\avd\Pleos_Home_2_1.avd`, RAM 4096MB, cold boot, host GPU, 오디오 활성화.
- 호스트: RAM 32GB, RTX 5060 Ti, WHPX 사용 가능. 에뮬레이터 37.2.13, API 34.
- ADB: `emulator-5574`, Android 사용자 `10`.
- Android Studio 전체 대신 명령줄 SDK로 빌드하고 독립 에뮬레이터 창을 띄웠다.

```powershell
.\scripts\run-driving-coach.ps1 -Mode Demo
.\scripts\run-driving-coach.ps1 -Mode Quests
.\scripts\run-driving-coach.ps1 -Mode Live
.\scripts\run-driving-coach.ps1 -Mode Verify
```

첫 빌드 또는 수정 후 실행에는 `-Build`를 추가한다. 바탕화면에는 같은 스크립트를 호출하는 `주행코치 체험.cmd`, `퀘스트 연결보기.cmd`, `Pleos 실제연결.cmd`, `Pleos SDK 재검증.cmd`를 만들었다. 이전 PC용 실행 파일은 보존했다.

## 실제 SDK 콜백: PASS_ACTUAL_CALLBACKS

**실제 차량 실측이 아니라 공식 Pleos 에뮬레이터 서비스에서 받은 SDK 콜백**이다. 앱 내부 데모 값으로 대체하지 않았다.

| 항목 | 확인 결과 |
|---|---|
| 방향지시등 GET | OFF, RIGHT, LEFT |
| 방향지시등 SUBSCRIPTION | OFF → RIGHT → OFF → LEFT → OFF 변화 수신 |
| AAOS 속도 | 0.0m/s, 5.0m/s(18km/h), 다시 0.0m/s |
| NaviHelper TBT | 비어 있지 않은 목록, TURN_RIGHT, 700m, 대왕판교IC 방면 등 유효한 경로 안내 |
| Pleos TTS | CONNECTED, PLAYBACK_STARTED, PLAYBACK_DONE |

Virtual Console의 브라우저 세션이 없으면 PUT 요청이 `503 emulator_inactive`로 실패했다. 공식 콘솔 HTML이 사용하는 `/browser/session` heartbeat를 활성화한 뒤 공식 차량 상태 변경 엔드포인트가 200으로 응답했다. 따라서 이전 PC의 503을 RAM 부족이나 앱 코드 오류만으로 단정하면 안 된다. API 응답과 앱의 SDK 수신을 각각 대조했다.

속도 쿼리 `10.8`은 `400 invalid_speed`로 거부됐다. 정수 `18`로 다시 요청하고 앱에서 `5.0m/s` 수신을 확인했다. 모든 주행 입력을 종료한 후 0km/h, Park, 방향지시등 Off로 되돌렸다. 테스트 클라이언트의 브라우저 세션도 종료한다.

검증 원본은 바탕화면 `output\home-pc-validation-2026-11-12`에 있다:

- `strict-live-dynamic.txt`, `pleos-runtime-proof-final.json`: 방향지시등 변화와 다섯 항목 통과.
- `strict-live-speed-change.txt`, `pleos-runtime-proof-speed-change.json`: 0/5m/s 수신과 다섯 항목 통과.
- `console-dynamic-inputs.json`, `console-speed-change-inputs.json`: 테스트 입력과 응답.

TTS 완료 콜백은 실제 PC 스피커 청취 확인과 다르다. 앱 음성은 기본 OFF였고, 요청에 따라 ON으로 변경해 테스트 문장을 재생했다. 에뮬레이터 스트림/차량 오디오의 mute=false와 활성 출력 thread를 확인했다. 이후 사용자가 PC 스피커 또는 헤드폰에서 테스트 문장을 들었다고 직접 확인했다(2026-11-12). TTS 콜백과 사용자 청취 확인을 모두 확보했다.

## 행동 버튼과 체험 도로

같은 교차로에서 다음 순서로 진행한다:

1. 오른쪽 방향지시등 체험.
2. 미러·사각지대 확인 입력.
3. 오른쪽 한 차로 이동. 여러 개 차로를 이동할 때는 2–3을 반복한다.
4. 신호 앞 일시 정지 입력.
5. 신호·보행자·주변 차량 확인 입력.
6. 우회전.
7. 같은 교차로 빠져나오기.

2/3/4개 차로의 시작·목표 차로를 선택하고 남은 이동 차로 수를 표시한다. 차량 표시는 도식의 경로를 따라 애니메이션한다. 미러/주변 확인에서 접근 차량·보행자를 가정하면 진행을 보류하고 다시 확인해야 한다. 짧은 피드백도 체험 입력을 기준으로 표시·재생한다.

현재 그림은 **체험 도로 도식**이다. 실지도, 실제 차로 인식, 카메라 영상, 보행자 감지, 통행 가능 판정, 차량 조향·방향지시등 제어가 아니다. 실주행 모드에는 이 입력을 주입하지 않는다. 모든 체험 이벤트의 출처는 DEMO다.

## 퀘스트와 피드백 연결

Figma 1=퀘스트, 2=주행 피드백, 3=주행 안내.

`scratch/quests`의 `5e577f8`에서 퀘스트 모델·엔진·저장소·화면·테스트를 검토하여 이 브랜치에 가져왔다. 원격 브랜치를 병합하거나 수정하지 않았다. 오래된 파트 번호 표현은 연결 화면에서 수정했다.

- `CoachQuestMapper`는 기존 QuestEngine 규칙을 사용한다. 코치의 situationInstanceId를 퀘스트의 maneuverId로, 상황별 occurrenceId를 각각 매핑한다.
- 퀘스트에서 선택한 목표와 영역별 안내 수준/끄기를 코치가 받는다. 기존 인터페이스를 설치하는 외부 호스트도 유지한다.
- 버튼 체험의 수행 기록은 `tutorial_quest_demo_v2`에만 쌓인다. 개인 프로필 `tutorial_quest_personal_v2`와 분리한다.
- 실제 방향지시등 점등·TBT·안내 표시만으로 수행 경험을 만들지 않는다. 미확인/취소를 분리한다.
- 우회전과 같은 교차로의 이탈이 확인된 경우 기존 퀘스트 엔진이 교차로 경험 하나로 합친다. 차로 한 칸씩 이동한 체험도 차로 변경 경험 하나로 묶는다.
- 코치의 `주행 피드백`에서 현재 tripId의 상황·기다림·메모를 표시한다. 사용자가 돌아봤다고 확인하면 같은 tripId의 DrivingFeedback와 reviewed 상태를 퀘스트에 전달한다.
- `scratch/gain`의 `fb6b4a7`에는 개발 계획서와 STT probe가 있으며 Room/분석기/회고 화면은 구현되어 있지 않다. 이번 화면은 기존 코치 저널을 사용하는 최소 연결 화면이다. gain의 전체 구현을 합쳤다고 표현하지 않는다.
- gain 계획서에서 휴대폰 연동은 범위 밖이지만, 이후 확인한 전체 기획 `Tutorial.docx`에는 핵심 기능으로 포함되어 있다. 현재 휴대전화 페어링/서버/동기화는 미구현이다. [최신 문서 비교와 지도·차량 연동](map-vehicle-2026-11-12.md)을 참고한다.

단계 동그라미는 기존 퀘스트 규칙을 따른다. 사전 학습 단계 없이 버튼 체험만 했다고 모든 단계를 채우지 않는다.

## 확인 및 남은 작업

- assembleDebug / assembleDebugAndroidTest / lintDebug 통과.
- 초기 통합 단위 테스트 59개, 실패/오류 0. 이후 지도·차량 정책 6개를 추가하여 총 65개가 통과했다.
- 에뮬레이터의 통합 action_demo 검사: PASS_DEMO_ACTIONS. 순서·보류, 같은 교차로, 피드백 열기, reviewed, 퀘스트 화면 이동, 개인 프로필 무변경을 검사했다. 검사 전 시연 프로필과 기존 코치 저널은 검사 후 복원했다.
- 기본 내비는 검색/경로/TBT가 작동하지만 지도 배경이 하얗게 렌더링되는 문제가 남아 있다. Mapbox/OpenGL 경고를 관찰했으나 근본 원인을 확정하지 않았다.
- 확인한 NaviHelper 공개 API/TBT 모델에는 기본 내비 지도 위 임의 경로 오버레이, 현재 차로 번호/전체 차로 수 제공이 없다. 이후 앱 안에 OSM 실지도/OSRM 참고 경로와 정차 중 직접 지정하는 차로 계획을 추가했다. OEM 내비 내부 API나 로그의 비공개 데이터를 기능 입력으로 사용하지 않았다.
- gain 담당과 공통 저장소·STT 메모 연결, 주행 샘플/분석, 기록 보관·삭제, 휴대폰 연동 범위는 후속 합의가 필요하다.
