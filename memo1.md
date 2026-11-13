`chore/add-room` 브랜치에 Room DB와 데이터 모델을 추가하고 커밋

## 이번에 한 작업

- [build.gradle.kts](app/build.gradle.kts): Room 2.6.1과 Coroutines 1.7.3을 추가했습니다. 새 플러그인 버전을 따로 지정하지 않으려고 Kotlin에 포함된 `kapt`를 썼습니다.
- [Entities.kt](app/src/main/java/com/hmgchallenge/pleosstarter/core/data/entity/Entities.kt): 계획서 1-3의 테이블 6개와 분류 목록(enum)을 그대로 옮겼습니다.
- [TripDao.kt](app/src/main/java/com/hmgchallenge/pleosstarter/core/data/TripDao.kt): A는 기록, B는 분석 입력과 결과 저장, C는 최근 리포트·메모와 퀘스트 조회에 필요한 쿼리를 한 파일에 모았습니다.
- [TripDatabase.kt](app/src/main/java/com/hmgchallenge/pleosstarter/core/data/TripDatabase.kt): 앱 전체가 DB 하나를 같이 씁니다. 개발 중에 테이블 구조가 바뀌면 기존 데이터는 지워지도록 했습니다.
