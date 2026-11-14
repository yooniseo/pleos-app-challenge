package com.hmgchallenge.pleosstarter.driving

import com.hmgchallenge.pleosstarter.quests.*
import com.hmgchallenge.pleosstarter.quests.EvidenceSource as QuestEvidence

/** Only authored scenario milestones produce demo evidence. Raw sensor callbacks never do. */
class SimpleScenarioQuests(val tripId: String, private val startedAt: Long) {
    fun begin(state: QuestState, now: Long): QuestState {
        if (!state.demo) return state
        var next = state
        next.session?.takeIf { it.finishedAt == null }?.let { old ->
            val at = maxOf(now,old.events.lastOrNull()?.at ?: old.startedAt)
            old.current?.let { next = QuestEngine.acceptEvent(next,SituationEvent("${old.tripId}:cancel",it.context,SituationEventType.CANCELLED,at)) }
            next = QuestEngine.finishTrip(next,old.tripId,at)
        }
        return QuestEngine.beginTrip(next,tripId,QuestEvidence.SIMULATION,startedAt)
    }
    fun sync(state: QuestState, scenario: SimpleRoadScenario, now: Long,finishTrip: Boolean=true,
             assistance: (CoachSkill)->AssistanceUsed = {AssistanceUsed.DETAILED}): QuestState {
        if (!state.demo || !scenario.started || state.session?.tripId != tripId) return state
        var next = state
        fun event(kind: SituationKind, type: SituationEventType,maneuver: String="road") {
            // Stable context permits duplicate-safe replay of this small event sequence.
            val ctx = SituationContext(tripId,"$tripId:$maneuver:${kind.id}","$tripId:$maneuver",kind,QuestEvidence.SIMULATION,startedAt)
            next = QuestEngine.acceptEvent(next,SituationEvent("${ctx.occurrenceId}:$type",ctx,type,now,
                confirmation=if(type==SituationEventType.PRACTICED) ConfirmationBasis.DEMO else ConfirmationBasis.UNKNOWN,
                assistanceUsed=assistance(kind.skill)))
        }
        fun enter(kind: SituationKind,maneuver: String="road") {
            event(kind,SituationEventType.ENTERED,maneuver)
            if(assistance(kind.skill)!=AssistanceUsed.NONE)event(kind,SituationEventType.GUIDANCE_PROVIDED,maneuver)
        }
        if(scenario.lesson==RoadLesson.PARKING) {
            enter(SituationKind.PARKING)
            if(scenario.complete)event(SituationKind.PARKING,SituationEventType.PRACTICED)
        }
        if (scenario.lesson in setOf(RoadLesson.COMBINED,RoadLesson.LANE)) {
            scenario.laneChanges.forEachIndexed {i,change->
                if(i<=scenario.completedLaneChanges)enter(SituationKind.LANE_RIGHT,"lane-$i")
                if(scenario.distance>=change.end)event(SituationKind.LANE_RIGHT,SituationEventType.PRACTICED,"lane-$i")
            }
        }
        if (scenario.lesson in setOf(RoadLesson.COMBINED,RoadLesson.TURN) && (scenario.lesson == RoadLesson.TURN || scenario.laneDone)) {
            enter(SituationKind.TURN_RIGHT)
            if (scenario.complete) {
                event(SituationKind.TURN_RIGHT,SituationEventType.PRACTICED)
                enter(SituationKind.INTERSECTION_EXIT)
                event(SituationKind.INTERSECTION_EXIT,SituationEventType.PRACTICED)
            }
        }
        if (scenario.complete && finishTrip) next = QuestEngine.finishTrip(next,tripId,now)
        return next
    }
}
