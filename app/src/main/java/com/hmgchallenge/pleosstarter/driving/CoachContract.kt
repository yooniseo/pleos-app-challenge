package com.hmgchallenge.pleosstarter.driving

/** Shared boundary only. Quest scoring, circles and growth belong to scratch/quests. */
enum class SituationType { LANE_CHANGE_RIGHT, RIGHT_TURN, INTERSECTION_EXIT, PARKING, EXIT_CHECK }
enum class GuidanceLevel { DETAILED, BRIEF, ON_REQUEST }
enum class EvidenceSource { DEMO, NAVIGATION, VEHICLE, USER, VERIFIED_PROVIDER }
enum class CoachEventType {
    STARTED, CUE_SHOWN, SIGNAL_OBSERVED, PHASE_RECORDED, UNVERIFIED,
    CANCELLED, DEFERRED, DIFFICULTY_NOTED, CHECK_CONFIRMED
}

data class CoachEvent(
    val eventId: String,
    val tripId: String,
    /** One ID across lane preparation -> turn -> exit of the SAME intersection. */
    val situationInstanceId: String,
    val situationType: SituationType,
    val eventType: CoachEventType,
    val timestamp: Long,
    val source: EvidenceSource,
    val assistanceUsed: Boolean,
    val guidanceLevel: GuidanceLevel,
    val cueId: String? = null,
    val detail: String = "",
    val schemaVersion: Int = 1
)

data class CoachPreferences(
    val levels: Map<SituationType, GuidanceLevel> = emptyMap(),
    val voiceEnabled: Boolean = false,
    /** Simulation effect only; no public Pleos turn-signal setter exists. */
    val demoAutomaticIndicator: Boolean = false
) {
    fun level(type: SituationType) = levels[type] ?: GuidanceLevel.DETAILED
}

interface QuestCoachPort {
    fun preferences(): CoachPreferences
    fun focusedGoalLabel(): String?
    /** Events are durable before delivery. Consumers deduplicate eventId and reject DEMO as real evidence. */
    fun onCoachEvent(event: CoachEvent)
}

/** Safe default until the quest developer supplies their implementation. No quest completion logic here. */
object UnconnectedQuestPort : QuestCoachPort {
    override fun preferences() = CoachPreferences()
    override fun focusedGoalLabel(): String? = null
    override fun onCoachEvent(event: CoachEvent) = Unit
}

/** Names match scratch/gain's DrivingNote plan; extra context need not alter its Room entity. */
data class FeedbackNoteDraft(
    val noteId: String, val tripId: String, val t: Long, val rawText: String,
    val category: String, val situationInstanceId: String, val source: EvidenceSource
)
interface FeedbackCoachPort {
    fun onCoachEvent(event: CoachEvent)
    fun onNote(note: FeedbackNoteDraft)
}
object UnconnectedFeedbackPort : FeedbackCoachPort {
    override fun onCoachEvent(event: CoachEvent) = Unit
    override fun onNote(note: FeedbackNoteDraft) = Unit
}

/** Install from the integrated app's host before opening DrivingCoachActivity. */
object CoachHostBindings {
    var questPort: QuestCoachPort = UnconnectedQuestPort
    var feedbackPort: FeedbackCoachPort = UnconnectedFeedbackPort
    var activeTripId: String? = null
}

fun CoachEvent.feedbackNote(): FeedbackNoteDraft? {
    if (eventType != CoachEventType.DIFFICULTY_NOTED) return null
    val category = when (situationType) {
        SituationType.LANE_CHANGE_RIGHT -> "LANE_CHANGE"
        SituationType.RIGHT_TURN, SituationType.INTERSECTION_EXIT -> "RIGHT_TURN"
        SituationType.PARKING, SituationType.EXIT_CHECK -> "PARKING"
    }
    return FeedbackNoteDraft(eventId, tripId, timestamp, detail, category, situationInstanceId, source)
}

enum class CoachPhase { WAITING, LANE_PREPARATION, RIGHT_TURN, INTERSECTION_EXIT, FINISHED, CANCELLED }
enum class Indicator { UNKNOWN, OFF, RIGHT, LEFT }
data class GuidanceCue(val id: String, val title: String, val text: String, val lookAt: String)

/** Adapter input, not an SDK claim: a future calibrated producer must prove the completed action. */
data class VerifiedManeuver(
    val situationInstanceId: String,
    val completedPhase: CoachPhase,
    val evidenceId: String
)
