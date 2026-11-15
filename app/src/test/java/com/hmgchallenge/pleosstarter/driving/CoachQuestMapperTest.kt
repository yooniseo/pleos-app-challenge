package com.hmgchallenge.pleosstarter.driving

import com.hmgchallenge.pleosstarter.quests.CoachSkill
import com.hmgchallenge.pleosstarter.quests.QuestEngine
import com.hmgchallenge.pleosstarter.quests.QuestState
import com.hmgchallenge.pleosstarter.quests.QuestStage
import com.hmgchallenge.pleosstarter.quests.SituationEventType
import org.junit.Assert.*
import org.junit.Test

class CoachQuestMapperTest {
    private fun ready(demo: Boolean)=QuestEngine.learn(QuestEngine.learn(QuestState(demo=demo),CoachSkill.LANE_CHANGE),CoachSkill.INTERSECTION)
    @Test fun fullDemoUpdatesExistingQuestRulesAndOneSharedIntersection() {
        var state=ready(true)
        val engine=CoachEngine(true,{state=CoachQuestMapper.apply(state,it)}, {1_000_000L},"shared-trip")
        engine.startDemo();engine.configureDemoLanes(2,1)
        DemoDriveStep.values().filter {it!=DemoDriveStep.FINISHED}.forEach {engine.demoAction(it)}
        assertEquals(1,state.practiceCount(CoachSkill.LANE_CHANGE));assertEquals(1,state.practiceCount(CoachSkill.INTERSECTION))
        assertEquals(QuestStage.SECOND,state.completedStages(CoachSkill.INTERSECTION).last())
        assertEquals(1,state.session!!.situations.map {it.context.maneuverId}.distinct().size)
        assertEquals(3,state.session!!.situations.size)
        assertNotNull(state.session!!.finishedAt)
    }
    @Test fun sdkSignalAndNavigationCannotCompleteActualQuest() {
        var state=ready(false)
        val engine=CoachEngine(false,{state=CoachQuestMapper.apply(state,it)}, {1_000_000L},"real-trip")
        engine.onNavigation(true,240.0,"우회전");engine.markCueShown(engine.cue()!!)
        engine.onIndicator(Indicator.RIGHT);engine.onNavigation(true,40.0,"우회전")
        assertEquals(0,state.practiceCount(CoachSkill.LANE_CHANGE));assertEquals(0,state.practiceCount(CoachSkill.INTERSECTION))
        assertEquals(SituationEventType.UNVERIFIED,state.session!!.situations.first().outcome)
    }
    @Test fun duplicateDeliveryAndDemoToPersonalProfileCannotAddExperience() {
        var state=ready(true);val events=mutableListOf<CoachEvent>()
        val engine=CoachEngine(true,{events.add(it);state=CoachQuestMapper.apply(state,it)}, {1_000_000L},"demo-trip")
        engine.startDemo();repeat(3){engine.nextDemo()}
        val completed=state
        events.forEach {state=CoachQuestMapper.apply(state,it)}
        assertEquals(completed,state)
        val personal=ready(false)
        assertEquals(personal,events.fold(personal,CoachQuestMapper::apply))
    }
    @Test fun aDifficultyNoteIsRetainedWithoutLoweringQuestProgress() {
        var state=ready(true)
        val engine=CoachEngine(true,{state=CoachQuestMapper.apply(state,it)}, {1_000_000L},"trip")
        engine.startDemo();engine.noteDifficulty("미러 확인이 어려웠어요")
        assertTrue(state.session!!.current!!.difficult)
        assertEquals(setOf(QuestStage.FIRST),state.completedStages(CoachSkill.LANE_CHANGE))
        assertEquals(0,state.practiceCount(CoachSkill.LANE_CHANGE))
    }
}
