# 경기대 후문 짧은 주행 경로 데이터

- 도로: OpenStreetMap의 도로 중심선·건물/녹지 형상(39개 도로, 128개 구역)을 오프라인으로 표시한다.
- 수집 출처: https://www.openstreetmap.org/api/0.6/map?bbox=127.0395,37.294,127.045,37.301 (2026-11-12).
- `kgu-streets.json`은 OSM 유래 데이터로 ODbL에 따른다: https://opendatacommons.org/licenses/odbl/1-0/ . 저작자: OpenStreetMap contributors https://www.openstreetmap.org/copyright . 앱 소스 라이선스와 별개다.
- `scripts/build-teaching-map.py`로 OSM XML extract에서 재생성할 수 있다.
- `kgu-route.json`은 OSRM 도로 중심선이다. 실제 차로 수준의 지도/경로 데이터가 아니다.
- 차로 폭/경계는 교육용 표현이고 차로 수 태그가 없으면 기본값을 사용한다. 횡단보도 두 곳은 가상 배치이며 실제 위치를 검증하지 않았다.
- 종합 시연은 창룡대로의 분리된 3차로에서 1→2→3차로로 이동해 단방향 연결로로 진입한다. 대학로 합류부터는 양방향 4차로의 오른쪽 바깥 차로를 따른다. 같은 진행 방향에서는 흰 점선, 분리된 도로의 중앙분리대 쪽과 양방향 도로 중앙에서는 황색 실선을 사용한다.
- 시연 차로 폭 3.5m, 차체 1.85×4.65m로 모델링한다. Figma 차량 레이어의 투명 여백을 제외한 폭을 도로 축척에 맞춘다. 도로 지도가 중심 차량의 진행 방향으로 회전한다.
- Figma 차량·나무·횡단보도는 앱에서 개별 레이어로 그린다. 네 가지 시연은 `session/ReplayCatalog.kt`에 있다.
