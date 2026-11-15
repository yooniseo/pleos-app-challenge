# 이번 구현에서의 API 역할 분리

확인일: 2026-11-12. 공식 문서와 프로젝트의 Vehicle 2.3.2 / NaviHelper 3.0.5 / navi-data 3.0.4 소스를 대조했습니다.

## 퀘스트에서 직접 사용하는 것

**퀘스트·성장·안내 설정에는 Pleos API가 필요하지 않습니다.** Kotlin 판정 로직, Android View, 프로필별 SharedPreferences/JSON을 사용합니다. 차량 안에서 상세 목록·입력·설정을 잠글 때만 표준 AAOS `CarUxRestrictionsManager`를 사용합니다. Android Automotive 환경이 아닌 일반 에뮬레이터에서도 시연할 수 있습니다.

이번 퀘스트/주행 코치 틀에는 Vehicle·NaviHelper 호출, TTS/STT 또는 실차 자동 점등 코드를 추가하지 않았습니다. 새 Pleos 권한도 선언하지 않았습니다. 기존 MainActivity 샘플의 SDK 사용은 그대로입니다.

## 주행 코치 개발자가 검토할 API

| 목적 | 확인한 공개 API | 제한 |
| --- | --- | --- |
| 다음 회전 안내 | `NaviHelper.getTBTInfo()` / `onTBTInfo(List<TBTInfo>)` | `NAVI_ROUTE` 권한. type·distance(m)·description. 현재 차로·차로 변경 성공·교차로 출구 완료는 이 정보만으로 확인 불가 |
| 현재 위치·경로 식별 | `getCurrentLocationInfo()` / `onCurrentLocationInfo` | latitude·longitude·heading·address·routeId·currentRouteIndex |
| 경로 변경·취소 | `onReRouteEnded`, `onRouteCancelled`, `onDestinationChanged` | 이전 상황/안내 무효화. 실제 전달은 내비 구현과 상태에 따라 다름 |
| 방향지시등 지원 확인 | `Vehicle.getTurnSignal().checkTurnSignalStateCapabilities` | `CAR_LIGHTS`, CapabilityConfig.accessType READ/READ_WRITE 확인 |
| 상태 한 번 읽기 | `getCurrentTurnSignalState` | OFF/RIGHT/LEFT 또는 null. null을 OFF로 바꾸면 안 됨 |
| 변경 구독·해제 | `registerTurnSignalState` / `unregisterTurnSignalState` | 점등 관측을 수행 성공과 구분 |
| 실제 방향지시등 자동 점등 | **공개 TurnSignal 제어 함수 없음** | CONTROL_CAR_LIGHTS 권한이 있다고 제어 함수가 생기는 것은 아님 |
| 속도 | 기존 AAOS `PERF_VEHICLE_SPEED` | 기존 CAR_SPEED 권한. 미수신/오래된 값을 정지로 해석하지 않음 |
| 조향 정보 | Vehicle SteeringWheel 각도 조회·구독 | 각도 하나로 차로 변경·주차 성공·고정 핸들 회전 수를 판정하지 않음 |
| 상세 UI 제한 | `CarUxRestrictionsManager.registerListener`, `currentCarUxRestrictions.isRequiresDistractionOptimization` | 실제 제한/미확인 시 3번 상세 화면 잠금 |

## 시나리오와 최적 시점

기본 시나리오는 **차로 변경 준비 → 우회전 → 같은 교차로 출구**입니다. TBT의 우회전 예정 정보만으로 ‘지금 차로 변경할 수 있다’거나 ‘최적 점등 시점’이라고 단정할 수 없습니다. 도로·현재 차로·운전자 의도·신호 신뢰도 등 별도 입력과 검증이 필요합니다. 고정 거리 하나로 실제 점등 명령을 내리는 기능을 넣지 않습니다.

이번 주행 코치 틀은 테스트 버튼으로 상황을 입력할 뿐입니다. 실제 주행 코치는 해당 근거를 확보한 뒤 공통 인터페이스로 전달해야 하며, 검증하지 못한 상황은 미확인/보류로 처리할 수 있습니다. 센서 값을 ADB로 주입하는 실험을 제품의 실제 제어 API로 취급하지 않습니다.

## 확인 수준

‘문서에 있음’과 ‘참가 환경에서 실행됨’을 구분합니다.

| 항목 | 현재 확인 수준 |
| --- | --- |
| TurnSignal / NaviHelper | 공식 문서 + 배포 라이브러리/소스 확인 |
| 실제 서비스 연결·권한·값 수신 | 이번 변경에서 실행 검증하지 않음 |
| Pleos STT/TTS/Gleo | 이번 범위에서 사용하지 않음. 참가 환경 지원은 별도 확인 필요 |
| 단계 퀘스트·설정 저장·상황 연결 | 일반 Android 에뮬레이터와 단위 테스트로 검증 |

Figma 운영사 이메일은 당시의 답변이며 오늘의 실행 지원을 확정하는 자료로 사용하지 않습니다.

## 출처

- [TurnSignal 공개 메서드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/turn-signal/entry)
- [Vehicle 2.3.2 배포 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2-sources.jar): TurnSignal.kt / TurnSignalStateListener.kt / CapabilityConfig.kt / AccessType.kt
- [NaviHelper 이벤트](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)
- [NaviHelper 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [NaviHelper 3.0.5 배포 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5-sources.jar)
- [navi-data 3.0.4 배포 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/navi-data/3.0.4/navi-data-3.0.4-sources.jar): TBTInfo.kt / CurrentLocationInfo.kt
- [Android 차량 UX 제한](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager)
