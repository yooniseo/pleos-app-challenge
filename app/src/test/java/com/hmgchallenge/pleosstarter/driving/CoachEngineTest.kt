package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test

class CoachEngineTest {
    private val events=mutableListOf<CoachEvent>()
    private var time=1_000_000L
    private fun engine(demo: Boolean=false)=CoachEngine(demo,{events.add(it)},{time},"trip-shared")

    @Test fun demoKeepsSameIntersectionAcrossThreeActions() {
        val e=engine(true);e.startDemo();val id=e.instanceId
        repeat(3){e.nextDemo()}
        assertEquals(CoachPhase.FINISHED,e.phase)
        val actions=events.filter {it.eventType==CoachEventType.PHASE_RECORDED}
        assertEquals(listOf(SituationType.LANE_CHANGE_RIGHT,SituationType.RIGHT_TURN,SituationType.INTERSECTION_EXIT),actions.map {it.situationType})
        assertTrue(actions.all {it.situationInstanceId==id && it.tripId=="trip-shared" && it.source==EvidenceSource.DEMO})
        assertEquals(3,actions.map {it.eventId}.distinct().size)
    }
    @Test fun restartProducesNewOccurrenceWithoutErasingOldRecords() {
        val e=engine(true);e.startDemo();val old=e.instanceId;e.startDemo()
        assertNotEquals(old,e.instanceId)
        assertEquals(CoachEventType.CANCELLED,events.first {it.eventType==CoachEventType.CANCELLED}.eventType)
    }
    @Test fun liveNavigationNeverFabricatesManeuverCompletion() {
        val e=engine();e.onNavigation(true,240.0,"우회전");val id=e.instanceId
        e.onNavigation(true,40.0,"우회전");e.onIndicator(Indicator.RIGHT)
        assertEquals(CoachPhase.RIGHT_TURN,e.phase);assertEquals(id,e.instanceId)
        assertTrue(events.any {it.eventType==CoachEventType.UNVERIFIED})
        assertFalse(events.any {it.eventType==CoachEventType.PHASE_RECORDED})
        e.onNavigation(false,500.0,"직진")
        assertFalse(e.active);assertFalse(events.any {it.eventType==CoachEventType.PHASE_RECORDED})
    }
    @Test fun liveUsesNoDemoLandmarks() {
        val e=engine();e.onNavigation(true,30.0,"우회전")
        assertFalse(e.cue()!!.text.contains("작은 진입로"))
    }
    @Test fun notEveryFutureRightTurnStartsCoaching() {
        val e=engine();e.onNavigation(true,900.0,"우회전");assertEquals(CoachPhase.WAITING,e.phase)
    }
    @Test fun invalidDistancesDoNotStartScenario() {
        listOf(null,Double.NaN,Double.POSITIVE_INFINITY,-1.0).forEach { distance ->
            val e=engine();e.onNavigation(true,distance,"우회전");assertFalse(e.active)
        }
    }
    @Test fun routeChangeCancelsAndNewInputUsesDifferentIdentity() {
        val e=engine();e.onNavigation(true,220.0,"우회전");val old=e.instanceId
        e.cancel("재탐색");assertNull(e.cue())
        assertEquals("",e.navigationDescription)
        e.onNavigation(true,210.0,"우회전");assertNotEquals(old,e.instanceId)
    }
    @Test fun distanceDiscontinuitySeparatesIdenticalNamedIntersections() {
        val e=engine();e.onNavigation(true,30.0,"우회전");val old=e.instanceId
        e.onNavigation(true,230.0,"우회전");assertNotEquals(old,e.instanceId)
    }
    @Test fun staleNavigationCannotKeepOldCueAlive() {
        val e=engine();e.onNavigation(true,220.0,"우회전");time+=8_001;e.expireNavigation()
        assertFalse(e.active);assertNull(e.cue());assertNull(e.distanceMeters)
    }
    @Test fun nullAndExpiredIndicatorStayUnknownRatherThanOff() {
        val e=engine();e.onNavigation(true,220.0,"우회전");assertEquals(Indicator.UNKNOWN,e.indicator)
        e.onIndicator(Indicator.RIGHT);time+=6_001;e.expireNavigation();assertEquals(Indicator.UNKNOWN,e.indicator)
    }
    @Test fun repeatedIndicatorPollingDoesNotDuplicateObservation() {
        val e=engine();e.onNavigation(true,220.0,"우회전")
        repeat(5){e.onIndicator(Indicator.RIGHT)}
        assertEquals(1,events.count {it.eventType==CoachEventType.SIGNAL_OBSERVED})
    }
    @Test fun requestOnlyDoesNotRecordAssistanceUntilRequestedAndShown() {
        val e=engine();e.preferences=CoachPreferences(mapOf(SituationType.LANE_CHANGE_RIGHT to GuidanceLevel.ON_REQUEST))
        e.onNavigation(true,220.0,"우회전");assertNull(e.cue());e.noteDifficulty()
        assertFalse(events.last().assistanceUsed)
        e.requestExplanation();e.markCueShown(e.cue()!!);e.noteDifficulty()
        assertTrue(events.last().assistanceUsed)
    }
    @Test fun renderingSameCueDoesNotDuplicateEvents() {
        val e=engine(true);e.startDemo();repeat(10){e.markCueShown(e.cue()!!)}
        assertEquals(1,events.count {it.eventType==CoachEventType.CUE_SHOWN})
    }
    @Test fun assistanceDoesNotLeakAcrossTypes() {
        val e=engine(true);e.startDemo();e.markCueShown(e.cue()!!);e.nextDemo();e.nextDemo()
        val actions=events.filter {it.eventType==CoachEventType.PHASE_RECORDED}
        assertTrue(actions[0].assistanceUsed);assertFalse(actions[1].assistanceUsed)
    }
    @Test fun verifiedProducerMustMatchOccurrencePhaseAndUniqueEvidence() {
        val e=engine();e.onNavigation(true,240.0,"우회전")
        assertFalse(e.verified(VerifiedManeuver("wrong",e.phase,"observation-1")))
        assertFalse(e.verified(VerifiedManeuver(e.instanceId,CoachPhase.RIGHT_TURN,"observation-1")))
        assertTrue(e.verified(VerifiedManeuver(e.instanceId,e.phase,"observation-1")))
        assertFalse(e.verified(VerifiedManeuver(e.instanceId,e.phase,"observation-1")))
        assertEquals(1,events.count {it.source==EvidenceSource.VERIFIED_PROVIDER})
    }
    @Test fun lateEvidenceCannotCompleteCancelledOrNewScenario() {
        val e=engine();e.onNavigation(true,240.0,"우회전");val old=VerifiedManeuver(e.instanceId,e.phase,"id")
        e.cancel("취소");assertFalse(e.verified(old));e.onNavigation(true,240.0,"우회전");assertFalse(e.verified(old))
    }
    @Test fun autoIndicatorSimulationIsNeverVehicleEvidence() {
        val e=engine(true);e.preferences=CoachPreferences(demoAutomaticIndicator=true);e.startDemo()
        assertEquals(Indicator.RIGHT,e.indicator)
        assertTrue(events.all {it.source==EvidenceSource.DEMO})
    }
    @Test fun difficultMomentFitsGainDrivingNoteWithoutLoweringScore() {
        val e=engine();e.onNavigation(true,200.0,"우회전");e.noteDifficulty("차선 변경이 어려웠어")
        val note=events.last().feedbackNote()!!
        assertEquals("LANE_CHANGE",note.category);assertEquals("trip-shared",note.tripId)
        assertEquals(e.instanceId,note.situationInstanceId);assertEquals(time,note.t)
        assertEquals("차선 변경이 어려웠어",note.rawText)
        assertEquals(CoachEventType.DIFFICULTY_NOTED,events.last().eventType)
    }
    @Test fun unknownOrStaleSpeedBlocksDetailsEvenWhenUxAllows() {
        val gate=DetailGate {time};gate.uxAllowed=true
        assertFalse(gate.allows(false));gate.updateSpeed(0f);assertTrue(gate.allows(false))
        time+=5_001;assertFalse(gate.allows(false))
        gate.updateSpeed(null);assertFalse(gate.allows(false))
    }
    @Test fun reverseMotionAndInvalidValuesAreNotParked() {
        val gate=DetailGate {time};gate.uxAllowed=true
        listOf(-1f,1f,Float.NaN,Float.POSITIVE_INFINITY).forEach {gate.updateSpeed(it);assertFalse(gate.allows(false))}
        gate.updateSpeed(0f);gate.uxAllowed=false;assertFalse(gate.allows(false))
    }
    @Test fun completedScenarioCanStartAnotherAfterNextInstructionChanges() {
        val e=engine();e.onNavigation(true,240.0,"우회전");val old=e.instanceId
        repeat(3){assertTrue(e.verified(VerifiedManeuver(e.instanceId,e.phase,"e$it")))}
        e.onNavigation(false,400.0,"직진");e.onNavigation(true,250.0,"우회전")
        assertNotEquals(old,e.instanceId);assertTrue(e.active)
    }
}
