package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test

class ScenarioSpeechGateTest {
    private fun route()=NavigationSimulationRoute(listOf(
        GeoPoint(37.296551,127.042500),GeoPoint(37.296757,127.041551),GeoPoint(37.296868,127.041395),
        GeoPoint(37.296979,127.041311),GeoPoint(37.297197,127.041292),GeoPoint(37.297454,127.041496),GeoPoint(37.297553,127.041595)))
    @Test fun smallSteeringChangesDoNotProduceRepeatedSpeech() {
        for(lesson in RoadLesson.values()) {
            val s=SimpleRoadScenario(route(),lesson);val gate=ScenarioSpeechGate();s.start()
            val spoken=mutableListOf<Pair<Long,String>>();var busyUntil=0L
            for(i in 1..1800) {
                s.advance(100,null,false);val now=i*100L;val cue=ScenarioCoachingCues.at(s)
                gate.next("trip",cue,now,true,true,now>=busyUntil)?.let {
                    spoken.add(now to cue.identity);busyUntil=now+3500
                }
                if(s.complete)break
            }
            assertTrue("No coaching for $lesson",spoken.isNotEmpty())
            assertEquals(spoken.size,spoken.map {it.second}.toSet().size)
            assertTrue(spoken.zipWithNext().all {(a,b)->b.first-a.first>=1000})
            assertTrue("Too much speech: $lesson $spoken",spoken.size<=30)
        }
    }
    @Test fun unavailableBusyOrPausedSpeechIsNeverMarkedDeliveredAndObsoleteCuesAreDropped() {
        val gate=ScenarioSpeechGate()
        val old=ScenarioCoachingCue(CoachingStep.LANE_PREPARE,"","","","prepare")
        val current=ScenarioCoachingCue(CoachingStep.TURN_PREPARE,"","","","turn")
        assertNull(gate.next("trip",old,1000,true,false,true))
        assertNull(gate.next("trip",old,2000,true,true,false))
        assertNull(gate.next("trip",old,3000,false,true,true))
        assertEquals("turn",gate.next("trip",current,4000,true,true,true))
        assertNull(gate.next("trip",current,15000,true,true,true))
        assertEquals("turn",gate.next("new-trip",current,16000,true,true,true))
    }
    @Test fun displayTextIsStableWithinAnActionEvenAsTheWheelChanges() {
        val s=SimpleRoadScenario(route());s.start();var priorStep: String?=null;var priorText=""
        repeat(3000) {
            s.advance(50,null,false)
            val cue=ScenarioCoachingCues.at(s)
            // Lane completion has one intentional positive feedback message within the unwind phase.
            if(cue.identity==priorStep && cue.step!=CoachingStep.LANE_UNWIND)assertEquals(priorText,cue.full)
            priorStep=cue.identity;priorText=cue.full
        }
    }
}
