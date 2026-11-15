package com.hmgchallenge.pleosstarter.driving

import com.hmgchallenge.pleosstarter.quests.*
import com.hmgchallenge.pleosstarter.quests.GuidanceLevel as QuestGuidance
import org.junit.Assert.*
import org.junit.Test

class CoachLearningPolicyTest {
    private val skill=CoachSkill.LANE_CHANGE
    private fun state(count: Int)=QuestState(stages=mapOf(skill to QuestStage.values().take(count).toSet()))
    private fun decision(s: QuestState,after: Int=0,difficult: Boolean=false,manual: BeginnerStyle=BeginnerStyle.ADAPTIVE)=
        CoachLearningPolicy.forQuest(s,skill,after,difficult,manual)
    @Test fun questStageControlsAssistance() {
        assertEquals(CoachingSupport.DETAILED,decision(state(0)).support)
        assertEquals(CoachingSupport.DETAILED,decision(state(1)).support)
        assertEquals(CoachingSupport.HINT,decision(state(2)).support)
        assertFalse(decision(state(3)).automaticSpeech)
    }
    @Test fun countsAloneCannotSkipLearningOrGraduate() {
        val s=QuestEngine.recordPractice(QuestState(),PracticeEvidence("demo",skill,com.hmgchallenge.pleosstarter.quests.EvidenceSource.SIMULATION,1))
        assertEquals(CoachingSupport.DETAILED,decision(s).support)
        assertTrue(s.settings.graduated.isEmpty())
    }
    @Test fun difficultyRestoresHelpWithoutErasingProgress() {
        val s=state(3)
        assertEquals(CoachingSupport.DETAILED,decision(s,0,true).support)
        assertEquals(CoachingSupport.DETAILED,decision(s,1,true).support)
        assertEquals(CoachingSupport.ON_REQUEST,decision(s,2,true).support)
        assertEquals(3,s.completedStages(skill).size)
    }
    @Test fun manualBriefAndVisualWorkWithinStageRange() {
        assertEquals(CoachingSupport.HINT,decision(state(0),manual=BeginnerStyle.BRIEF).support)
        assertEquals(BeginnerStyle.VISUAL,decision(state(0),manual=BeginnerStyle.VISUAL).style)
        assertEquals(CoachingSupport.HINT,decision(state(2),manual=BeginnerStyle.STEP_BY_STEP).support)
    }
    @Test fun categoryOverrideWinsOverOverallStyle() {
        val s=state(0).copy(settings=CoachSettings(levels=mapOf(skill to QuestGuidance.ON_REQUEST)))
        assertFalse(decision(s,manual=BeginnerStyle.STEP_BY_STEP).automaticSpeech)
    }
    @Test fun disabledOrGraduatedCategoryKeepsOptionalHelp() {
        val s=state(4).copy(settings=CoachSettings(graduated=setOf(skill)))
        assertFalse(decision(s).automaticSpeech)
        assertFalse(decision(s.copy(settings=CoachSettings(disabled=setOf(skill)))).automaticSpeech)
        assertTrue(decision(s,difficult=true).automaticSpeech)
        assertTrue(skill in s.settings.graduated)
    }
}
