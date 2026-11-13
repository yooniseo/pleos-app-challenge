# 공통 DB와 통합 브랜치

브랜치: `integration/chageun-coach`. `scratch/driving-guide`에서 시작해 `scratch/gain@359ca4a`, `chore/add-room@67f2a90`, `scratch/quests@5e577f8`을 병합했다. main과 팀원 브랜치는 수정하지 않는다.

## 현재 저장 구조

Android 앱 내부의 `databases/trips.db`를 공통 저장소로 사용한다. Room 2.6.1이며 `SharedCoachRepository`가 SQL 작업을 단일 IO 실행기에 직렬화한다. UI 호환 상태 조회는 결과를 기다리고, 1Hz 속도 샘플은 비동기로 저장한다. 원격 DB나 휴대폰 동기화 서버는 아니다.

| 테이블 | 저장 내용 | 연결 |
| --- | --- | --- |
| Trip | 시작·종료·거리·속도 출처 | tripId |
| TripSample | epoch 시각·km/h, 누락은 null | tripId + t |
| DrivingNote | 사용자 확인 메모·분류·시각 | tripId |
| TripEvent | 속도 변화 복습 후보 | tripId |
| TripReport | 잘한 점·다음 연습 포인트 | tripId |
| Quest | 단계 퀘스트 조회용 상태 | profile:skill:stage |
| AppSnapshot | 전체 퀘스트 상태, 코치·음성 설정, 주행 메타데이터 | 명시적인 key |
| PracticeCompletion | 확정된 체험 수행 근거, 난이도 표시 | profile + tripId + skill |
| CoachingEvent | 상황·도움 요청·안내 정책·어려움·이전 코치 이벤트 | tripId + eventId |

퀘스트 snapshot, 완료 근거, 단계 행, 상황 이벤트는 한 트랜잭션으로 저장한다. 기존 SharedPreferences와 JSONL은 최초 읽기에 이관하고 원본을 백업으로 남긴다. 이후 읽기·쓰기는 DB가 기준이다. Room v1의 기존 6개 테이블은 유지하고 v2에서 3개 테이블과 인덱스만 추가한다. 파괴적 migration은 사용하지 않는다.

## 흐름

1. 지도에서 연습 시작 → Trip 시작, 체험 Quest session에 같은 tripId 전달.
2. 자동 재생 또는 수신된 차량 SDK 속도로 진행 → TripSample 1Hz. SDK 값이 없거나 오래됐으면 null, 실제 위치·차로는 시나리오라는 출처 유지.
3. 시나리오 완료 근거 → 체험 PracticeCompletion 및 단계 저장. 같은 주행·같은 기술은 중복 가산하지 않음. 직접 핸들 따라하기만 한 것은 메모로 남기고 주행 성공으로 가산하지 않음.
4. 기억할 순간 → 같은 주행의 DrivingNote. 어려움 선택 시 기술별 DIFFICULTY도 저장. STT 결과는 편집·저장 확인 후 반영; 권한/서비스 미지원 시 글 입력 가능.
5. 종료·피드백 → 같은 tripId의 samples·notes·completion으로 TripReport 생성. 속도 변화는 복습 후보이며 위험 운전 점수로 단정하지 않음. 이전 분석 이벤트는 덮어 지우지 않음.
6. 다음 안내 → 같은 DB의 퀘스트 단계·카테고리 설정·어려움 기록을 읽음. 자세히/힌트/요청 시로 변경. 어려움을 기록하면 누적 단계는 유지하고 도움만 복원. 도움 요청은 언제든 가능.

## 현재 체험과 실제 제품 규칙의 경계

시나리오는 기존 체험 퀘스트의 가속 단계를 사용한다. 학습 확인 후 1회/3회 수행으로 시연하는 흐름을 실제 운전의 숙련 승급으로 주장하지 않는다. 최신 노션의 여러 날·성공률 조건과 8개 기술 전체는 별도 제품 작업이다. 개인 프로필과 시연 프로필은 분리한다. SDK 속도로 재생해도 차로·주차 성공은 SIMULATION이다.

UI에서 `기억할 순간 → 음성·글로 메모하기`, `설명 더 보기 → 어려웠어요`, `피드백 → 지난 기록`으로 연결을 확인할 수 있다. 실제 음성 인식 성공 여부는 대상 Pleos 서비스와 권한 확인이 필요하다. 현재 빌드 성공만으로 STT 동작을 확인했다고 쓰지 않는다.

## 한 앱 안에서 화면 이동

사용자 요청에 따라 기존 흰색 지도와 남색 학습 화면의 색·레이아웃은 유지한다. 각 화면 아래에 공통 `주행 · 퀘스트 · 기록 · 더보기` 탭을 붙인다. 퀘스트·코치 화면에는 `← 지도로 돌아가기`도 제공한다. 퀘스트의 시연 연결 버튼은 기존 남색 실험 화면 대신 같은 지도 코치로 돌아간다. 튜토리얼·코치 설정·직접 운전 조작은 더보기에 모은다.

`PracticeRecordsActivity`는 공통 DB를 읽기만 하는 기록 화면이다. 화면을 열었다는 이유로 별도 주행을 시작하지 않는다. 주행 중 수집·집계 로직과 해당 기록을 보여주는 화면을 구분한다.

현재 시뮬레이터 실행은 `AppNavigation`의 앱 Activity 생명주기 추적으로 유지한다. 퀘스트·기록·튜토리얼로 이동해도 같은 시뮬레이터 인스턴스와 tripId로 진행·샘플·집계를 이어가고, 가려진 지도에서는 코치 음성을 중복 재생하지 않는다. 모든 앱 화면이 백그라운드로 나가면 실행과 SDK를 정지하고 연습을 일시정지한다. 돌아오면 같은 연습을 유지한다. 이는 Android foreground service 구현이 아니며, 프로세스가 종료된 뒤에도 실행된다는 뜻은 아니다. 실제 차량 제품의 지속 수집 서비스는 별도 검토 범위다.

기록·퀘스트·학습 화면의 정차 제한은 유지한다. 화면 이동 메뉴가 생겼다고 실차 주행 중 해당 화면을 조작할 수 있도록 규제를 해제하지 않는다.

## 검증 명령

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
adb -s emulator-5574 shell am instrument --user 10 -w -r -e database_probe 1 com.hmgchallenge.pleosstarter.test/com.hmgchallenge.pleosstarter.driving.CoachSmokeRunner
adb -s emulator-5574 shell am instrument --user 10 -w -r -e interaction_probe 1 com.hmgchallenge.pleosstarter.test/com.hmgchallenge.pleosstarter.driving.CoachSmokeRunner
```

`database_probe`는 사용자 DB와 분리된 DB에서만 시연한다. 시나리오 진행에 가상 시간을 사용한 부분과 실제 1Hz 샘플 저장을 증거 JSON에서 구분한다. 별도 테스트 파일을 v1의 6개 테이블 형태로 만든 뒤 migration과 재실행 영속성을 검사한다. 검사 결과는 `validation-2026-11-13.md`에 기록한다.
