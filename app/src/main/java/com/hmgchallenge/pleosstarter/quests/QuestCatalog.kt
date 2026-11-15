package com.hmgchallenge.pleosstarter.quests

/** All 26 named quests on Figma page 0:1. IDs are stable across all three teams. */
object QuestCatalog {
    val all = listOf(
        QuestDefinition("prepare", "출발 준비 완료!", "출발 전 필요한 항목을 하나씩 직접 확인해요.", QuestPhase.BEFORE, CoachSkill.PREPARATION, QuestAction.CHECKLIST,
            listOf("시트와 페달 위치", "미러와 시야", "안전벨트", "전조등과 연료·배터리")),
        QuestDefinition("preview", "오늘의 길 미리보기", "우회전을 위한 오른쪽 차로 변경 → 우회전 → 같은 교차로 빠져나오기 순서를 확인해요.", QuestPhase.BEFORE, CoachSkill.INTERSECTION, QuestAction.LESSON),
        QuestDefinition("car_controls", "내 차 알아가기", "정차 상태에서 차량 설명서를 보며 조작 위치를 익혀요.", QuestPhase.BEFORE, CoachSkill.PREPARATION, QuestAction.CHECKLIST,
            listOf("와이퍼 조작 위치 확인", "전조등 조작 위치 확인", "성에 제거 조작 위치 확인")),
        QuestDefinition("weather", "날씨 준비 끝!", "비·눈·야간에 필요한 준비 사항을 미리 확인해요. 해당 환경에서 주행하도록 요구하지 않아요.", QuestPhase.BEFORE, CoachSkill.PREPARATION, QuestAction.CHECKLIST,
            listOf("시야와 조명 확인", "차량 설명서의 환경별 준비 사항 확인", "부담스러우면 일정·경로 조정")),
        QuestDefinition("small_goal", "오늘의 작은 도전", "오늘은 한 가지에 집중해요. 연습 목표를 직접 골라 주세요.", QuestPhase.BEFORE, CoachSkill.PREPARATION, QuestAction.GOAL),
        QuestDefinition("first_drive", "나만의 첫 드라이브", "주행을 마친 뒤 혼자 목적지까지 이동한 경험을 직접 확인해요.", QuestPhase.DRIVING, CoachSkill.INTERSECTION, QuestAction.SELF_CHECK),
        QuestDefinition("distance", "여유로운 드라이브", "도로와 교통 상황에 맞는 거리를 돌아봐요. 급감속 횟수로 차간거리를 판정하지 않아요.", QuestPhase.DRIVING, CoachSkill.SPEED, QuestAction.SELF_CHECK),
        QuestDefinition("smooth", "부드러운 드라이버", "필요한 감속·제동을 우선하고, 주행 후 속도 변화를 돌아봐요.", QuestPhase.DRIVING, CoachSkill.SPEED, QuestAction.FEEDBACK),
        QuestDefinition("focus", "집중 드라이버", "주행 후 불필요한 화면 조작을 줄였는지 스스로 돌아봐요.", QuestPhase.DRIVING, CoachSkill.SPEED, QuestAction.SELF_CHECK),
        QuestDefinition("lane_right", "차선 변경 첫걸음", "기본 시나리오의 오른쪽 차로 변경 경험을 기록해요.", QuestPhase.DRIVING, CoachSkill.LANE_CHANGE, QuestAction.SCENARIO),
        QuestDefinition("merge", "합류 구간 탐험가", "합류를 실제로 경험한 뒤 기록해요. 기본 시나리오에는 합류가 없어요.", QuestPhase.DRIVING, CoachSkill.LANE_CHANGE, QuestAction.SELF_CHECK),
        QuestDefinition("intersection", "교차로 한 걸음", "우회전하고 같은 교차로를 빠져나오는 경험을 기록해요.", QuestPhase.DRIVING, CoachSkill.INTERSECTION, QuestAction.SCENARIO),
        QuestDefinition("new_route", "새로운 길 탐험가", "처음 이용한 경로를 주행한 뒤 경험을 직접 확인해요.", QuestPhase.DRIVING, CoachSkill.INTERSECTION, QuestAction.SELF_CHECK),
        QuestDefinition("reroute", "침착한 드라이버", "경로 이탈을 경험한 뒤 무리한 복귀 대신 재탐색 경로를 따랐는지 돌아봐요.", QuestPhase.DRIVING, CoachSkill.INTERSECTION, QuestAction.SELF_CHECK),
        QuestDefinition("note", "기억해 줘, 이 순간", "시나리오의 어려운 단계를 남겨 주행 후 다시 확인해요.", QuestPhase.DRIVING, CoachSkill.INTERSECTION, QuestAction.NOTE),
        QuestDefinition("parking_space", "주차 자리 탐색가", "주차를 마친 뒤 차량 크기와 주변 공간을 확인한 경험을 기록해요.", QuestPhase.PARKING, CoachSkill.PARKING, QuestAction.SELF_CHECK),
        QuestDefinition("parking_slow", "천천히, 한 칸", "주변을 직접 확인하며 여유 있게 주차한 경험을 돌아봐요.", QuestPhase.PARKING, CoachSkill.PARKING, QuestAction.SELF_CHECK),
        QuestDefinition("parking_reverse", "후진 주차 첫걸음", "학습 → 연습 → 복습 → 자기평가 순서로 후진 주차 경험을 쌓아요.", QuestPhase.PARKING, CoachSkill.PARKING, QuestAction.SELF_CHECK),
        QuestDefinition("parking_parallel", "평행 주차 첫걸음", "충분한 공간에서 경험한 평행 주차를 기록해요.", QuestPhase.PARKING, CoachSkill.PARKING, QuestAction.SELF_CHECK),
        QuestDefinition("parking_retry", "다시 맞춰도 괜찮아", "시간이나 수정 횟수로 감점하지 않아요. 필요한 재조정을 했는지 돌아봐요.", QuestPhase.PARKING, CoachSkill.PARKING, QuestAction.SELF_CHECK),
        QuestDefinition("finish", "마지막 확인까지", "차량 설명서에 따른 주차 조치와 하차 준비를 직접 확인해요.", QuestPhase.AFTER, CoachSkill.FINISH, QuestAction.CHECKLIST,
            listOf("차량 설명서에 따른 주차 조치", "창문과 조명", "소지품과 주변 확인")),
        QuestDefinition("review", "리플레이 드라이버", "이번 주행에서 기록한 상황을 하나씩 돌아봐요.", QuestPhase.AFTER, CoachSkill.INTERSECTION, QuestAction.FEEDBACK),
        QuestDefinition("reflection", "오늘도 한 걸음", "잘한 점·어려웠던 점과 자신감을 기록해요.", QuestPhase.AFTER, CoachSkill.INTERSECTION, QuestAction.REFLECTION),
        QuestDefinition("maintenance", "내 차 컨디션 체크", "차량 설명서와 점검 주기를 직접 확인해요. 앱이 차량 이상을 진단하지 않아요.", QuestPhase.AFTER, CoachSkill.FINISH, QuestAction.CHECKLIST,
            listOf("차량 경고 표시 직접 확인", "차량 설명서의 점검 항목 확인", "필요한 점검 일정 확인")),
        QuestDefinition("next_goal", "다음 도전 예약", "복습한 경험을 바탕으로 다음 연습 목표를 하나 선택해요.", QuestPhase.AFTER, CoachSkill.PREPARATION, QuestAction.GOAL, prerequisite = "review"),
        QuestDefinition("graduate", "코칭 졸업!", "학습·연습·복습·자기평가를 마친 영역의 추가 코칭을 직접 줄일 수 있어요.", QuestPhase.AFTER, CoachSkill.FINISH, QuestAction.GRADUATION)
    )

    fun get(id: String) = all.first { it.id == id }
    val goalIds = listOf("lane_right", "intersection", "smooth", "parking_reverse", "prepare")
}
