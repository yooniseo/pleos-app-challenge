package com.hmgchallenge.pleosstarter.driving

import com.hmgchallenge.pleosstarter.quests.*
import com.hmgchallenge.pleosstarter.quests.GuidanceLevel as QuestGuidance

enum class CoachingSupport { DETAILED, HINT, ON_REQUEST }
data class LearningDecision(val style: BeginnerStyle,val support: CoachingSupport,val label: String,val reason: String) {
    val automaticSpeech get()=support!=CoachingSupport.ON_REQUEST
}

/** Progress depends on recorded practice, never speed/blinker observations or automatic wheel motion. */
object CoachLearningPolicy {
    /** This simulator uses the existing, explicitly accelerated demo quest stages; never personal data. */
    fun forQuest(state: QuestState,skill: CoachSkill,afterDifficulty: Int,hasDifficulty: Boolean,manual: BeginnerStyle): LearningDecision {
        if(hasDifficulty && afterDifficulty<2)
            return LearningDecision(BeginnerStyle.STEP_BY_STEP,CoachingSupport.DETAILED,"다시 차근차근","어려웠던 상황 · 순서부터 다시 확인")
        if(skill in state.settings.graduated || skill in state.settings.disabled)
            return LearningDecision(BeginnerStyle.BRIEF,CoachingSupport.ON_REQUEST,"도움 쉬는 중","필요한 설명은 원할 때 다시 확인해요")
        val stages=state.completedStages(skill)
        val maximum=if(QuestStage.SECOND in stages)QuestGuidance.BRIEF else QuestGuidance.DETAILED
        val requested=state.settings.levels[skill] ?: when {
            manual==BeginnerStyle.STEP_BY_STEP || manual==BeginnerStyle.VISUAL->QuestGuidance.DETAILED
            manual==BeginnerStyle.BRIEF->QuestGuidance.BRIEF
            QuestStage.THIRD in stages->QuestGuidance.ON_REQUEST
            QuestStage.SECOND in stages->QuestGuidance.BRIEF
            else->QuestGuidance.DETAILED
        }
        val mode=if(maximum==QuestGuidance.BRIEF && requested==QuestGuidance.DETAILED)QuestGuidance.BRIEF else requested
        return when(mode) {
            QuestGuidance.DETAILED->LearningDecision(if(manual==BeginnerStyle.VISUAL)manual else BeginnerStyle.STEP_BY_STEP,CoachingSupport.DETAILED,"차근차근 배우기","체험 퀘스트 단계 · 확인 순서부터 함께해요")
            QuestGuidance.BRIEF->LearningDecision(if(manual==BeginnerStyle.BRIEF)manual else BeginnerStyle.VISUAL,CoachingSupport.HINT,"힌트로 연습","체험 퀘스트 단계 · 필요한 힌트만 줄게요")
            QuestGuidance.ON_REQUEST->LearningDecision(BeginnerStyle.BRIEF,CoachingSupport.ON_REQUEST,"혼자 해보기","체험 퀘스트 단계 · 설명 더 보기는 언제든")
        }
    }
}
