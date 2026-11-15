package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test

class DemoDriveTest {
    @Test fun fullActionSequenceKeepsOneIntersectionAndDemoSource() {
        val events=mutableListOf<CoachEvent>()
        val engine=CoachEngine(true,events::add)
        engine.startDemo();engine.configureDemoLanes(2,1);val id=engine.instanceId
        DemoDriveStep.values().filter {it!=DemoDriveStep.FINISHED}.forEach {assertTrue(engine.demoAction(it))}
        assertEquals(CoachPhase.FINISHED,engine.phase)
        assertEquals(DemoDriveStep.FINISHED,engine.demoStep)
        assertEquals(7,events.count {it.eventType==CoachEventType.CHECK_CONFIRMED})
        assertEquals(3,events.count {it.eventType==CoachEventType.PHASE_RECORDED})
        assertTrue(events.all {it.source==EvidenceSource.DEMO && it.situationInstanceId==id})
    }

    @Test fun approachingTrafficHoldsTheStepUntilRechecked() {
        val events=mutableListOf<CoachEvent>();val engine=CoachEngine(true,events::add)
        engine.startDemo();assertTrue(engine.demoAction(DemoDriveStep.SIGNAL))
        assertTrue(engine.setDemoObstruction(true))
        assertFalse(engine.demoAction(DemoDriveStep.MIRROR))
        assertEquals(CoachPhase.LANE_PREPARATION,engine.phase)
        assertTrue(events.any {it.eventType==CoachEventType.DEFERRED && it.source==EvidenceSource.DEMO})
        assertTrue(engine.setDemoObstruction(false));assertTrue(engine.demoAction(DemoDriveStep.MIRROR))
        assertEquals(DemoDriveStep.LANE_CHANGE,engine.demoStep)
    }

    @Test fun wrongOrderRepeatedInputAndLiveModeCannotFabricateCompletion() {
        val events=mutableListOf<CoachEvent>();val engine=CoachEngine(true,events::add)
        engine.startDemo();assertFalse(engine.demoAction(DemoDriveStep.TURN))
        assertTrue(engine.demoAction(DemoDriveStep.SIGNAL));assertFalse(engine.demoAction(DemoDriveStep.SIGNAL))
        val live=CoachEngine(false,events::add);live.onNavigation(true,240.0,"우회전")
        assertFalse(live.demoAction(DemoDriveStep.SIGNAL));assertFalse(live.setDemoObstruction(true))
        assertFalse(events.any {it.eventType==CoachEventType.PHASE_RECORDED})
    }

    @Test fun restartingClearsAssumedTrafficAndCreatesAnotherOccurrence() {
        val engine=CoachEngine(true,{})
        engine.startDemo();val old=engine.instanceId
        engine.demoAction(DemoDriveStep.SIGNAL);engine.setDemoObstruction(true);engine.startDemo()
        assertNotEquals(old,engine.instanceId);assertFalse(engine.demoObstruction)
        assertEquals(DemoDriveStep.SIGNAL,engine.demoStep);assertEquals(Indicator.OFF,engine.indicator)
    }

    @Test fun twoLaneChangesRequireAnotherMirrorCheckAndOnlyOneLaneExperience() {
        val events=mutableListOf<CoachEvent>();val engine=CoachEngine(true,events::add)
        engine.startDemo();assertTrue(engine.configureDemoLanes(4,2))
        engine.demoAction(DemoDriveStep.SIGNAL);engine.demoAction(DemoDriveStep.MIRROR)
        engine.demoAction(DemoDriveStep.LANE_CHANGE)
        assertEquals(3,engine.demoCurrentLane);assertEquals(1,engine.demoLaneChangesRemaining)
        assertEquals(DemoDriveStep.MIRROR,engine.demoStep)
        assertFalse(engine.demoAction(DemoDriveStep.LANE_CHANGE))
        engine.demoAction(DemoDriveStep.MIRROR);engine.demoAction(DemoDriveStep.LANE_CHANGE)
        assertEquals(4,engine.demoCurrentLane);assertEquals(DemoDriveStep.STOP,engine.demoStep)
        assertEquals(1,events.count {it.eventType==CoachEventType.PHASE_RECORDED})
    }
}
