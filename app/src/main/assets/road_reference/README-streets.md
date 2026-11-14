# 경기대 후문 짧은 주행 경로 데이터

- 도로: OpenStreetMap의 도로 중심선·건물/녹지 형상(39개 도로, 128개 구역)을 오프라인으로 표시한다.
- 수집 출처: https://www.openstreetmap.org/api/0.6/map?bbox=127.0395,37.294,127.045,37.301 (2026-11-12).
- `kgu-streets.json`은 OSM 유래 데이터로 ODbL에 따른다: https://opendatacommons.org/licenses/odbl/1-0/ . 저작자: OpenStreetMap contributors https://www.openstreetmap.org/copyright . 앱 소스 라이선스와 별개다.
- `scripts/build-teaching-map.py`로 OSM XML extract에서 재생성할 수 있다.
- `kgu-route.json`은 OSRM 도로 중심선이다. 실제 차로 수준의 지도/경로 데이터가 아니다.
- 차로 폭/경계는 교육용 표현이고 차로 수 태그가 없으면 기본값을 사용한다. 횡단보도 두 곳은 가상 배치이며 실제 위치를 검증하지 않았다.
- 마커·나무·횡단보도는 현재 앱의 `ProductIllustrations`에서 직접 그린다. Figma 이미지나 SVG는 포함하지 않는다.
- 네 자동 시연의 선택 정의는 `session/ReplayCatalog.kt`, 진행 모델은 `driving/SimpleRoadScenario.kt`에 있다. 새 브랜치에는 수동 운전/Virtual Console 쓰기가 없다.
