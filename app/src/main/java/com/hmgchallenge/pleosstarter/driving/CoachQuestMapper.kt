package com.hmgchallenge.pleosstarter.driving

import com.hmgchallenge.pleosstarter.quests.*
import com.hmgchallenge.pleosstarter.quests.EvidenceSource as QuestSource

/** Translate coach facts through the existing quest rules, without inventing practice evidence. */
object CoachQuestMapper {
    fun skill(type: SituationType) = when(type) {
        SituationType.LANE_CHANGE_RIGHT -> CoachSkill.LANE_CHANGE
        SituationType.RIGHT_TURN, SituationType.INTERSECTION_EXIT -> CoachSkill.INTERSECTION
        SituationType.PARKING -> CoachSkill.PARKING
        SituationType.EXIT_CHECK -> CoachSkill.FINISH
    }
    fun kind(type: SituationType) = when(type) {
        SituationType.LANE_CHANGE_RIGHT -> SituationKind.LANE_RIGHT
        SituationType.RIGHT_TURN -> SituationKind.TURN_RIGHT
        SituationType.INTERSECTION_EXIT -> SituationKind.INTERSECTION_EXIT
        SituationType.PARKING -> SituationKind.PARKING
        SituationType.EXIT_CHECK -> SituationKind.FINISH
    }
    fun apply(initial: QuestState, event: CoachEvent): QuestState {
        if (event.eventId.isBlank() || event.tripId.isBlank() || event.situationInstanceId.isBlank() ||
            initial.demo != (event.source==EvidenceSource.DEMO)) return initial
        var state=initial
        val source=if(state.demo) QuestSource.SIMULATION else QuestSource.VEHICLE
        if(state.session?.tripId!=event.tripId) {
            state.session?.takeIf {it.finishedAt==null}?.let {old->
                val at=maxOf(event.timestamp,old.events.lastOrNull()?.at ?: old.startedAt)
                old.current?.let {current->state=QuestEngine.acceptEvent(state,
                    SituationEvent("${event.eventId}:close",current.context,SituationEventType.CANCELLED,at))}
                state=QuestEngine.finishTrip(state,old.tripId,at)
            }
            state=QuestEngine.beginTrip(state,event.tripId,source,event.timestamp)
        }
        val session=state.session?.takeIf {it.tripId==event.tripId && it.finishedAt==null} ?: return state
        if(session.events.any {it.eventId==event.eventId}) return state
        val occurrence="${event.situationInstanceId}:${event.situationType.name}"
        var context=session.situations.firstOrNull {it.context.occurrenceId==occurrence}?.context
        if(context==null) {
            session.current?.let {current->state=QuestEngine.acceptEvent(state,
                SituationEvent("${event.eventId}:unverified",current.context,SituationEventType.UNVERIFIED,event.timestamp))}
            context=SituationContext(event.tripId,occurrence,event.situationInstanceId,kind(event.situationType),source,event.timestamp)
            state=QuestEngine.acceptEvent(state,SituationEvent("${event.eventId}:enter",context,SituationEventType.ENTERED,event.timestamp))
        }
        if(state.session?.current?.context!=context) return state
        val ctx=context
        val questEvent=when(event.eventType) {
            CoachEventType.STARTED -> SituationEvent(event.eventId,ctx,SituationEventType.OBSERVED,event.timestamp,"상황 시작")
            CoachEventType.CUE_SHOWN -> SituationEvent(event.eventId,ctx,SituationEventType.GUIDANCE_PROVIDED,event.timestamp,
                detail=event.detail,assistanceUsed=AssistanceUsed.valueOf(event.guidanceLevel.name))
            CoachEventType.PHASE_RECORDED -> {
                val basis=when(event.source) {
                    EvidenceSource.DEMO -> ConfirmationBasis.DEMO
                    EvidenceSource.VERIFIED_PROVIDER -> ConfirmationBasis.VALIDATED_MANEUVER
                    else -> ConfirmationBasis.UNKNOWN
                }
                SituationEvent(event.eventId,ctx,if(basis==ConfirmationBasis.UNKNOWN) SituationEventType.UNVERIFIED else SituationEventType.PRACTICED,
                    event.timestamp,event.detail,basis)
            }
            CoachEventType.UNVERIFIED -> SituationEvent(event.eventId,ctx,SituationEventType.UNVERIFIED,event.timestamp,event.detail)
            CoachEventType.CANCELLED -> SituationEvent(event.eventId,ctx,SituationEventType.CANCELLED,event.timestamp,event.detail)
            CoachEventType.DIFFICULTY_NOTED -> SituationEvent(event.eventId,ctx,SituationEventType.DIFFICULTY,event.timestamp,event.detail)
            // Buttons, turn signals and temporary waiting are observations; they do not finish a quest.
            else -> SituationEvent(event.eventId,ctx,SituationEventType.OBSERVED,event.timestamp,event.detail)
        }
        state=QuestEngine.acceptEvent(state,questEvent)
        if(event.eventType==CoachEventType.PHASE_RECORDED && event.situationType==SituationType.INTERSECTION_EXIT)
            state=QuestEngine.finishTrip(state,event.tripId,event.timestamp)
        return state
    }
}
