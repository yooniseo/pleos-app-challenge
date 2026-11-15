# 지도·차로 계획·차량 상호작용 검증 (2026-11-12)

작업 브랜치: `scratch/driving-guide`. 기존 퀘스트/피드백 연결을 보존했다. 원격 브랜치 변경이나 push는 하지 않았다.

## Tutorial.docx와 현재 구현 비교

바탕화면 작업 폴더의 `Tutorial.docx` 본문과 표를 읽었다. 신청서의 개인정보는 이 문서나 저장소로 옮기지 않았다. 원본 Word 문서는 수정하지 않았다.

기획의 중심은 필요한 순간 확인할 것을 알려주고, 경험에 따라 안내를 줄이는 코치다. 주행 판단이나 차량의 안전 경고를 대신하는 앱이 아니다.

| 기획 요구 | 현재 상태 / 후속 작업 |
|---|---|
| 출발·차로 변경·교차로·속도/거리·주차·하차·고속도로·특수 도로 8영역 | Quest 모델은 앞의 6영역이다. 고속도로/특수 도로 미구현 |
| 기술별 STARTER → DRIVER → PRO → MASTER | 4단계 퀘스트/기술별 안내 설정은 있으나 전체 성장 표시의 `levelName`에는 PRO가 없다. 단계 명칭과 승급 정책 조정 필요 |
| 일상 주행 자동 기록 | 현재 코치 세션/저널 중심. 상시 자동 trip 시작/종료·보관/삭제·분석 파이프라인 필요 |
| “기억해줘”로 위치와 안내어 저장 | 버튼 메모 및 최종 STT 명령을 받는 경계가 있다. 실제 STT 연결과 위치 포함 메모 저장은 후속 작업 |
| 주행 후 리포트·복습·퀘스트 연결 | 같은 trip의 간단 회고 및 reviewed 연결 완료. 전체 분석 리포트는 미구현 |
| 휴대폰에서 리포트/복습/사전 설정 | **전체 기획에 포함**. gain 브랜치 계획에서만 범위 밖이었으며, 현재 휴대폰 앱/동기화는 미구현 |
| 벨트·주차 브레이크 확인, P단 하차 준비 | 실제 상태 읽기와 맥락 안내 추가. 수행 성공이나 퀘스트 완료로 자동 변환하지 않음 |
| 졸업 승인 후 실내 조명·배지 | 정차 중 조명 변경/복원 요청 체험 추가. 명령 접수 응답은 성공했으나 색상 재조회는 원래 값으로 남아 실제 변화 미확인. 자동 졸업 이벤트 연결은 후속 작업 |

제출 문서에는 앱명/프로젝트명, 한 줄 소개, 개발 계획, 참고자료가 비어 있고 본문에 `[앱 이름]`이 남아 있다. 제출 전 현재 구현과 향후 계획을 구분해 채워야 한다.

## 지도와 경로

기본 UMOS 내비 앱은 검색·경로/TBT와 위치 표시가 동작하지만 지도 배경은 계속 하얗다. host GPU, SwiftShader, Vulkan 비활성화, 명시적 DNS로 비교했고 근본 원인은 확정하지 않았다. 기존 내비 데이터나 AVD를 삭제하지 않았다.

우리 앱에 Android Canvas 기반 실지도 화면을 추가했다. 표시 중인 OSM 타일만 HTTPS로 요청하고 앱 User-Agent, 7일 캐시 및 만료 시 조건부 요청, 화면 내 저작자 표시를 사용한다. 키를 요구하지 않는 개발용 지도/경로 서비스이므로 배포 전 서비스 계약·트래픽 한도·개인정보 흐름을 확정해야 한다.

- 시뮬레이션: 판교 대왕판교로 → 판교로의 233m, 우회전 하나가 있는 OSRM 참고 경로. 10개 경로 좌표 위로 표시가 이동한다. 위치와 차로 수는 체험 입력이며 실제 주행 기록과 분리한다.
- 차량 모드: Pleos `CurrentLocationInfo` 좌표/heading으로 위치를 표시한다. 정차 중 `경로 불러오기`를 누르면 현재 위치·Pleos 목적지로 OSRM 참고 경로를 요청한다.
- **OSRM 경로는 차량 내비가 선택한 경로와 같다고 보장하지 않는다.** 화면에도 참고 경로로 표시한다. 코치의 실제 우회전 안내는 계속 Pleos TBT를 사용한다.
- 경로 변경/재탐색/취소/오류/데이터 만료 시 참고 선과 직접 지정 차로 계획을 무효화한다. 외부 경로 응답도 세션·경로 ID가 바뀌면 적용하지 않는다.
- 기존 도식은 `도로 도식 보기`로 전환할 수 있다. 멀티윈도 크기 변경 시 화면을 재배치하고 현재 체험 엔진을 보존한다.

## 차로 개수

공개 NaviHelper TBT에는 현재 차로 번호와 전체 차로 수가 없다. 테스트한 OSM 경로도 차로 데이터를 제공하지 않았다.

시뮬레이션은 선택한 2/3/4개 차로와 행동 입력으로 남은 이동 수를 표시한다. 차량 모드에서는 정차 중 현재/목표/전체 차로를 직접 지정한다. 경로 ID에 묶어 다른 경로에 재사용하지 않는다. GPS나 방향지시등만으로 실제 차로 이동 성공을 만들지 않는다.

## 차량과의 상호작용

| 입력/출력 | 공개 API / 권한 | 동작 |
|---|---|---|
| 선택 기어 | AAOS GEAR_SELECTION / CAR_POWERTRAIN | P/R/N/D 읽기. 후진 준비/출발 전 맥락 안내 |
| 주차 브레이크 | AAOS PARKING_BRAKE_ON / CAR_POWERTRAIN | 실제 체결 여부 읽기. 브레이크 페달과 구분 |
| 앞좌석 왼쪽 벨트 | Vehicle Seat / CAR_SEATS | capability 확인 후 조회·구독. null/실패는 미확인 |
| 실내 조명 색상 | Vehicle Light / CAR_LIGHTS + CONTROL_CAR_LIGHTS | 지원 구역·READ_WRITE 확인. P단·유효한 정차/UX 상태에서 버튼으로 민트색 1.2초 체험 후 원래 색 복원 |

CONTROL_CAR_LIGHTS는 이 에뮬레이터에서 `normal` 권한이다. SDK 배포 소스의 Light 인터페이스 주석과 실제 구현의 권한 요구가 달랐다. 읽기 권한만으로는 `need Permission` 오류가 났고, 구현이 요구하는 공개 제어 권한을 선언한 후 변경·복원 **명령 접수**가 성공했다. 다만 재조회 색상은 원래 흰색으로 남아 실제 색상 변화는 확인하지 못했다. 화면에도 미확인으로 표시한다. PRIVATE 권한이나 privileged 권한 부여는 사용하지 않았다. 이후 운전석 화면의 기어/깜빡이 등은 실차 setter가 아니라 별도 문서의 개발용 Virtual Console 입력이다.

조명은 명시적 체험 버튼만 사용하며 졸업 판정과 자동 연결되어 있지 않다. 화면 종료 또는 정차 조건 해제 시에도 원래 색 복원을 요청한다. 상태 조회·원래 색 조회가 실패하면 변경하지 않는다.

## 검증

- assembleDebug / assembleDebugAndroidTest / testDebugUnitTest / lintDebug 통과.
- 지도·차량 추가 시 단위 테스트 65개, 실패/오류 0. 이후 운전석 시나리오 6개를 더해 71개가 통과했다.
- `map_probe`: 실제 타일 렌더링, 경로 좌표, 행동 입력 후 2→1→0 차로 수, 지도 표시 이동, 기어/벨트/주차 브레이크 값, 조명 요청/복원 및 원래 색상 재조회, 개인 프로필 보존을 검사한다. `ambientColorConfirmed=false`는 실제 색상 변화가 확인되지 않았음을 명시한다.
- `action_demo`: 기존 퀘스트·피드백 통합 검사를 유지한다. 테스트는 시연 프로필과 코치 저널을 복원한다.
- 이전 실제 TBT/방향지시등/속도/TTS 검증은 [집 PC 검증](home-pc-validation-2026-11-12.md)을 참고한다. 이번 지도 체험을 실제 차량 주행 검증으로 표현하지 않는다.

바탕화면 `output/map-vehicle-integration-2026-11-12`에 화면과 `map-vehicle-proof.json`을 보관한다.

```powershell
adb -s emulator-5574 shell am instrument --user 10 -w -r -e map_probe 1 com.hmgchallenge.pleosstarter.test/com.hmgchallenge.pleosstarter.driving.CoachSmokeRunner
```

## 공식 자료

- [NaviHelper 3.0.5](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)
- [Vehicle SDK 2.3.2](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [AAOS vehicle properties](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/)
- [OSRM route API](https://project-osrm.org/docs/v5.24.0/api/)
