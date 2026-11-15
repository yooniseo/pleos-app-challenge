package com.hmgchallenge.pleosstarter.quests

data class ProductQuest(val skill: CoachSkill,val title: String,val detail: String,val lesson: String?)

/** Six areas in the submitted product plan. A distance lesson is not a headway sensor measurement. */
object ProductQuestCatalog {
    val all=listOf(
        ProductQuest(CoachSkill.PREPARATION,"출발 준비","벨트·미러·출발 전 점검을 직접 확인해요",null),
        ProductQuest(CoachSkill.LANE_CHANGE,"차선 변경","한 차로씩 이동한 연습이 쌓여요","LANE"),
        ProductQuest(CoachSkill.INTERSECTION,"교차로 통과","우회전 후 교차로를 빠져나와요","TURN"),
        ProductQuest(CoachSkill.SPEED,"속도와 거리 관리","전체 시연의 교차로 감속을 기록해요","COMBINED"),
        ProductQuest(CoachSkill.PARKING,"주차","후진·정렬까지 마친 연습이 쌓여요","PARKING"),
        ProductQuest(CoachSkill.FINISH,"하차 준비","주차 후 하차 점검을 직접 확인해요",null)
    )
}
