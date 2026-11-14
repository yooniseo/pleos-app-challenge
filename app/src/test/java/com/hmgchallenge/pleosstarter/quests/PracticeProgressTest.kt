package com.hmgchallenge.pleosstarter.quests

import org.junit.Assert.*
import org.junit.Test

class PracticeProgressTest {
    @Test fun sixSavedPracticesShowSixThirtiethsWithoutLessonStageCompletion() {
        val state=QuestState(evidence=(1..6).map {PracticeEvidence("lane:$it",CoachSkill.LANE_CHANGE,EvidenceSource.SIMULATION,it.toLong())})
        assertTrue(state.stages.isEmpty())
        assertEquals(6,state.practiceProgress(CoachSkill.LANE_CHANGE).steps)
        assertEquals(.2f,state.practiceProgress(CoachSkill.LANE_CHANGE).fraction,0f)
        assertEquals(0,state.practiceProgress(CoachSkill.PARKING).steps)
    }
    @Test fun thirtyCompletionsAdvanceALevelAndKeepTheLifetimeCount() {
        val p=PracticeProgress(36)
        assertEquals(2,p.level);assertEquals(6,p.steps);assertEquals(36,p.completed)
    }
    @Test fun productCatalogMatchesTheSixAreasAndHasNoRoundaboutSubstitution() {
        assertEquals(CoachSkill.values().toSet(),ProductQuestCatalog.all.map {it.skill}.toSet())
        assertEquals(6,ProductQuestCatalog.all.size)
    }
}
