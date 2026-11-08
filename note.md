언어 : Kotlin, 개발 도구 : Android Studio

SDK	 | 제공하는 것	| 용도
- Vehicle SDK	차량 상태 조회. Brake(브레이크 페달, 주차 브레이크), TurnSignal(방향지시등), Odometer(주행거리·시간), Safety(차량 경고), DrivingMode 등	급브레이크 감지, 깜빡이 사용 여부, 주차 완료 시점(주차 브레이크 체결) 판단
- FusedLocation SDK	위도·경도, 고도, 진행 방향(heading), 타임스탬프, 지오펜스	주행 경로 기록, 위치 변화로 속도·가감속 계산, 방향 변화로 좌회전·유턴 감지
- SpeechToText SDK	음성을 텍스트로 변환 (request() → 결과 리스너)	"방금 비보호좌회전 어려웠어" 인식
- Gleo Interface	차량 AI 비서(Gleo)가 내 앱의 함수를 호출 (function calling 방식)	가장 자연스러운 음성 기록 방법 (아래 설명)
- TextToSpeech SDK	음성 출력	"기록했어요" 응답, 주행 후 피드백 읽어주기
- NaviHelper SDK	목적지·경로·주행 상태 이벤트	목적지 도착 시점 파악 → 주차 시간 측정 시작

확인할 것
- 속도, 가속도, 차간거리 데이터
- Vehicle SDK 문서 도메인 목록에 차량 속도, 앞차와의 거리(ADAS) 는 명시되어 있지 않음
- 속도, 가감속:
    - FusedLocation의 연속 좌표로 직접 계산할 수 있음(거리 ÷ 시간), 정확도 떨어짐
    - 표준 AAOS Car API(CarPropertyManager의 PERF_VEHICLE_SPEED)를 Pleos가 허용하는지는 확인 필요
- 차간거리:
    - 급브레이크가 잦음 -> 차간거리 부족 가능성
    - Safety 도메인에 전방 충돌 경고가 포함되는지 API Reference에서 확인
