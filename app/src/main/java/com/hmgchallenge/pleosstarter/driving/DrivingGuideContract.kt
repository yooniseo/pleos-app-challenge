package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.view.View
import com.hmgchallenge.pleosstarter.quests.*
import com.hmgchallenge.pleosstarter.quests.EvidenceSource as QuestEvidenceSource
import com.hmgchallenge.pleosstarter.quests.GuidanceLevel as QuestGuidanceLevel

/** Figma 3 implements the guide; Figma 1 owns quest rules, persistence, and settings. */
interface DrivingGuidePort {
    fun createView(context: Context, host: DrivingGuideHost): View
    fun render(model: GuideUiModel)
    fun onCommand(command: CoachCommand)
    fun release()
}
interface DrivingGuideHost {
    fun startTrip(tripId: String, source: QuestEvidenceSource, at: Long): Boolean
    fun enterSituation(eventId: String, situation: SituationContext): Boolean
    fun reportObservation(eventId: String, situation: SituationContext, at: Long, detail: String): Boolean
    fun reportGuidance(eventId: String, situation: SituationContext, at: Long, used: AssistanceUsed): Boolean
    fun confirmPractice(eventId: String, situation: SituationContext, at: Long, basis: ConfirmationBasis): Boolean
    fun skipSituation(eventId: String, situation: SituationContext, at: Long): Boolean
    fun recordDifficulty(eventId: String, situation: SituationContext, at: Long, detail: String): Boolean
    fun cancelSituation(eventId: String, situation: SituationContext, at: Long): Boolean
    fun markUnverified(eventId: String, situation: SituationContext, at: Long): Boolean
    fun deferSituation(eventId: String, situation: SituationContext, at: Long): Boolean
    fun finishTrip(tripId: String, at: Long): Boolean
}
data class GuideUiModel(val demo: Boolean, val tripId: String?, val tripFinished: Boolean,
    val situations: List<SituationRecord>, val current: SituationContext?,
    val activeQuest: StageQuest?, val completedStageCount: Int,
    val coachingEnabled: Boolean, val guidanceLevel: QuestGuidanceLevel)
fun QuestState.guideUiModel(): GuideUiModel {
    val current = session?.current?.context
    val skill = current?.kind?.skill
    val completed = skill?.let(::completedStages).orEmpty()
    return GuideUiModel(demo, session?.tripId, session?.finishedAt != null, session?.situations.orEmpty(), current,
        skill?.let { StageQuestCatalog.forSkill(it).firstOrNull { quest -> quest.stage !in completed } },
        completed.size, skill != null && skill !in settings.disabled,
        skill?.let(settings::level) ?: QuestGuidanceLevel.DETAILED)
}
