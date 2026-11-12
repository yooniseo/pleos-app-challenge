package com.hmgchallenge.pleosstarter.quests

enum class QuestPhase { BEFORE, DRIVING, PARKING, AFTER }
enum class CoachSkill { PREPARATION, LANE_CHANGE, INTERSECTION, SPEED, PARKING, FINISH }
enum class QuestStage { FIRST, SECOND, THIRD, FOURTH }
enum class GuidanceLevel { DETAILED, BRIEF, ON_REQUEST }
enum class EvidenceSource { SIMULATION, USER, VEHICLE }
enum class QuestAction { CHECKLIST, LESSON, GOAL, SCENARIO, SELF_CHECK, NOTE, FEEDBACK, REFLECTION, GRADUATION }
enum class CoachCommand { REPEAT, STOP, RESUME }
data class QuestDefinition(val id: String, val title: String, val description: String,
    val phase: QuestPhase, val skill: CoachSkill, val action: QuestAction,
    val checks: List<String> = emptyList(), val prerequisite: String? = null)
data class StageQuest(val stage: QuestStage, val title: String, val description: String, val practiceTarget: Int = 0)

/** Stable business types. None of these are Pleos or Android classes. */
enum class SituationKind(val id: String, val title: String, val skill: CoachSkill) {
    LANE_RIGHT("lane_change_right", "우회전을 위한 오른쪽 차로 변경", CoachSkill.LANE_CHANGE),
    TURN_RIGHT("turn_right", "우회전", CoachSkill.INTERSECTION),
    INTERSECTION_EXIT("intersection_exit", "같은 교차로 빠져나오기", CoachSkill.INTERSECTION),
    PREPARATION("preparation", "출발 준비", CoachSkill.PREPARATION),
    SPEED("speed_review", "속도 변화 돌아보기", CoachSkill.SPEED),
    PARKING("parking", "주차", CoachSkill.PARKING), FINISH("finish", "하차 준비", CoachSkill.FINISH)
}
data class SituationContext(val tripId: String, val occurrenceId: String, val maneuverId: String,
    val kind: SituationKind, val source: EvidenceSource, val at: Long)
enum class SituationEventType { ENTERED, GUIDANCE_PROVIDED, OBSERVED, PRACTICED, SKIPPED, DIFFICULTY, CANCELLED, UNVERIFIED, DEFERRED }
enum class AssistanceUsed { UNKNOWN, NONE, DETAILED, BRIEF, ON_REQUEST }
enum class ConfirmationBasis { UNKNOWN, USER_CONFIRMATION, VALIDATED_MANEUVER, DEMO }
data class SituationEvent(val eventId: String, val context: SituationContext,
    val type: SituationEventType, val at: Long, val detail: String = "", val confirmation: ConfirmationBasis = ConfirmationBasis.UNKNOWN,
    val assistanceUsed: AssistanceUsed = AssistanceUsed.UNKNOWN)
data class SituationRecord(val context: SituationContext, val outcome: SituationEventType? = null,
    val difficult: Boolean = false, val confirmation: ConfirmationBasis = ConfirmationBasis.UNKNOWN)
data class PracticeSession(val tripId: String, val source: EvidenceSource, val startedAt: Long,
    val activeOccurrenceId: String? = null, val situations: List<SituationRecord> = emptyList(),
    val events: List<SituationEvent> = emptyList(), val finishedAt: Long? = null, val reviewed: Boolean = false) {
    val current: SituationRecord? get() = situations.firstOrNull { it.context.occurrenceId == activeOccurrenceId }
}
/** Only explicit confirmed occurrences count; observations and raw sensor values do not. */
data class PracticeEvidence(val id: String, val skill: CoachSkill, val source: EvidenceSource,
    val at: Long, val tripId: String? = null, val difficult: Boolean = false)
data class SelfAssessment(val text: String, val at: Long)
data class DrivingFeedback(val tripId: String, val source: EvidenceSource, val endedAt: Long,
    val difficultSituations: Set<String> = emptySet(), val noteCategories: Set<String> = emptySet(),
    val abruptAccelerationCount: Int? = null, val abruptBrakingCount: Int? = null)
data class CoachSettings(val levels: Map<CoachSkill, GuidanceLevel> = emptyMap(),
    val disabled: Set<CoachSkill> = emptySet(), val graduated: Set<CoachSkill> = emptySet(),
    val celebration: Boolean = true) {
    fun level(skill: CoachSkill) = levels[skill] ?: GuidanceLevel.DETAILED
}
data class QuestState(val demo: Boolean = true, val completed: Set<String> = emptySet(),
    val stages: Map<CoachSkill, Set<QuestStage>> = emptyMap(), val evidence: List<PracticeEvidence> = emptyList(),
    val settings: CoachSettings = CoachSettings(), val focusGoalId: String? = null, val nextGoalId: String? = null,
    val session: PracticeSession? = null, val feedback: List<DrivingFeedback> = emptyList(),
    val assessments: Map<CoachSkill, SelfAssessment> = emptyMap()) {
    fun completedStages(skill: CoachSkill) = stages[skill] ?: emptySet()
    fun practiceCount(skill: CoachSkill) = evidence.count { it.skill == skill }
    val growthPercent get() = CoachSkill.values().sumOf { completedStages(it).size } * 100 / 24
    val levelName get() = when { growthPercent >= 75 -> "MASTER"; growthPercent >= 35 -> "DRIVER"; else -> "STARTER" }
}
