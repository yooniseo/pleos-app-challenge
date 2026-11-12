package com.hmgchallenge.pleosstarter.quests

/** Four circles = four sequential quests per area. Thresholds are editable product proposals. */
object StageQuestCatalog {
    const val REPEAT_PRACTICE_TARGET = 3
    fun forSkill(skill: CoachSkill): List<StageQuest> {
        val subject = when (skill) {
            CoachSkill.PREPARATION -> "출발 준비"
            CoachSkill.LANE_CHANGE -> "오른쪽 차로 변경"
            CoachSkill.INTERSECTION -> "우회전과 교차로 빠져나오기"
            CoachSkill.SPEED -> "속도 변화 돌아보기"
            CoachSkill.PARKING -> "주차"
            CoachSkill.FINISH -> "하차 준비"
        }
        return listOf(
            StageQuest(QuestStage.FIRST, "1단계 · $subject 알아보기", "확인할 항목과 연습 순서를 읽고 준비해요."),
            StageQuest(QuestStage.SECOND, "2단계 · 첫 경험 기록", "$subject 경험을 한 번 확인해요.", 1),
            StageQuest(QuestStage.THIRD, "3단계 · 다시 해보기", "서로 다른 연습 기회에서 경험을 총 3회 쌓아요. 어려움 메모는 진척도를 줄이지 않고 다음 연습에 반영해요.", REPEAT_PRACTICE_TARGET),
            StageQuest(QuestStage.FOURTH, "4단계 · 내 경험 정리", "앞 단계들을 마친 뒤 연습 기록을 돌아보고 다음 안내 수준을 직접 정해요.")
        )
    }
}
