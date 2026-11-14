package com.hmgchallenge.pleosstarter.quests

/** Product progress is derived from saved evidence, independently of the old lesson checklist stages. */
data class PracticeProgress(val completed: Int) {
    init {require(completed>=0)}
    val level get()=completed/TARGET+1
    val steps get()=completed%TARGET
    val fraction get()=steps.toFloat()/TARGET
    val label get()="Lv.$level · $steps / $TARGET"
    companion object {const val TARGET=30}
}

fun QuestState.practiceProgress(skill: CoachSkill)=PracticeProgress(practiceCount(skill))
