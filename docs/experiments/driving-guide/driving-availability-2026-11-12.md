# 주행 중 사용 가능 여부: 현재 구현과 실제 제품 요건

## 재현된 차단과 수정

운전석 체험의 Activity에는 debug 전용 DO(distractionOptimized) 메타데이터가 있었으나, 실제 바탕화면 실행 경로는 정차용 DrivingCoachActivity를 태스크 루트로 하고 그 안에 체험을 열었다. Pleos Home screen 로그는 이 태스크를 `distractionOptimized: false`로 분류했고, 가속하면 컨테이너가 주행 중 사용 불가 메시지를 표시했다.

DrivingSimulatorActivity에 별도 taskAffinity를 지정하고 두 진입점(버튼/바탕화면 extra)에서 NEW_TASK로 연다. CockpitSmokeRunner도 Simulator를 직접 열지 않고 실제 Coach 진입 경로를 사용하도록 바꾸었다. Simulator가 태스크 루트인지, D단 속도·깜빡이 SDK 응답과 주행 상태에서 화면이 보이는지 검사한다. 단순히 모든 앱 화면을 DO로 선언하지 않았다.

이는 개발용 에뮬레이터 체험의 수정이다. 정차용 DrivingCoachActivity와 QuestActivity는 여전히 DO 미선언이며, 실제 주행용 코치의 출시 요건을 충족했다고 볼 수 없다.

## 공식 정책 확인 (2026-11-12)

- [지역별 주행 규제 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy): DO 미선언 시 Parking 사용 가능 / Drive 사용 불가. 플랫폼 버전·양산 차량별 차이 가능.
- [주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide): DO 선언 앱도 CarUxRestrictionsManager 리스너 및 ActiveRestrictions를 반영해야 함.
- [주행 안전 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline): 간결한 주행 정보, 최소한의 조작, 움직이는 이미지 제한, 음성/물리 입력 및 오디오 포커스 연동.
- [앱 카테고리](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category): 일부 Maps/Navigation·Mobility Services 등의 주행 관련 기능이 제한적으로 허용될 수 있음. 운전 코칭 앱의 승인까지 보장하는 문서가 아님.
- [Maps/Navigation 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide): 단일 Active Route, 복수 내비 동시 음성·경로 안내 금지, Background/Inactive Route의 경로 안내 금지. 카테고리와 실제 기능이 달라서도 안 됨.
- [심사 가이드](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide): 주행 중 상호작용 앱은 운전자 주의 분산을 최소화해야 하며 심사에서 추가 제한될 수 있음.

Google Play의 템플릿 앱 등록 규칙과 Pleos App Market의 배포 규칙을 같은 것으로 단정하지 않는다. 이 프로젝트는 Pleos 공식 가이드와 실제 승인 범위를 기준으로 검토해야 한다.

## 제품으로 만들 때의 핵심 미해결 항목

1. **주행 전용 화면/동작**: 제한 없는 코치/퀘스트 화면과 분리하여 짧은 확인 안내·최소 정보·간단한 음성 조작만 제공. UX 규제 상태에서 상세 설정/긴 목록/연습 조작을 노출하지 않아야 한다.
2. **카테고리와 OEM 내비 보조 범위**: 운전 코칭이 허용되는 카테고리·오디오·TBT 보조 범위는 Pleos 측 확인 필요. 승인된 내비인 것처럼 APP_MAPS를 추가하거나 OEM 경로 소유권을 임의로 빼앗아 해결하지 않는다. 현재 참고 경로/코치 시연은 이 승인을 증명하지 않는다.
3. **음성·수명주기**: 주행 화면이 가려지거나 다른 앱과 포커스를 주고받을 때 코칭을 언제 중단/재개할지 확정. 단순히 백그라운드 음성 서비스를 켜면 허용된다는 결론을 내릴 수 없다.
4. **안내 내용의 근거**: 체험용 조향각/후진 거리와 실제 차량을 분리. 실차의 조향 지시는 차종·도로 기하·센서 보정 및 검증 없이는 만들 수 없다. 현재 공개 입력만으로 차로 이동·회전 성공을 자동 평가하지 않는다.
5. **실제 차량 검증과 심사**: 개발 에뮬레이터에서 DO 화면이 보이고 센서 콜백을 받는 것과 양산차 사용/앱 심사 승인은 별개다.

현재 상태는 **주행 관련 SDK를 연결한 개발 시연 앱**이다. 주행 중 사용 가능한 완제품이라고 설명하면 안 된다. 실제 주행 코치가 주행 시 차단된다면 핵심 제품 결함이므로, 위 구조와 승인 범위를 다음 우선순위로 다뤄야 한다.
