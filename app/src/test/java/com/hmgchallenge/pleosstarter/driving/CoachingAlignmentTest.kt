package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class CoachingAlignmentTest {
    @Test fun aFullRotationIsDistinguishedFromTheCenteredWheel() {
        assertTrue(BeginnerScenarioCoach.steeringPosition(360.0).contains("오른쪽 한 바퀴"))
        assertTrue(BeginnerScenarioCoach.steeringPosition(-360.0).contains("왼쪽 한 바퀴"))
        assertTrue(BeginnerScenarioCoach.steeringPosition(0.0).contains("가운데"))
    }
    @Test fun everyNewTurnAndUnwindCanSpeakAgainWithoutRepeatingTheSameMovement() {
        val timeline=SteeringCoachingTimeline(80.0,false) {d->
            val t=d%40
            if(t<5 || t>25)0.0 else 100*sin((t-5)/20*PI)
        }
        val gate=ScenarioSpeechGate()
        val played=mutableListOf<SteeringCoachingTimeline.Action>()
        for(i in 0..800) {
            val movement=timeline.at(i/10.0)
            val text=movement.action.spoken
            val cue=ScenarioCoachingCue(CoachingStep.TURN,text,text,text,text,movement.key)
            gate.next("repeat-turns",cue,i*100L,true,true,true)?.let {played.add(movement.action)}
        }
        assertEquals(2,played.count {it==SteeringCoachingTimeline.Action.RIGHT_MORE})
        assertEquals(2,played.count {it==SteeringCoachingTimeline.Action.LEFT_UNWIND})
    }
    private fun route()=NavigationSimulationRoute(listOf(
        GeoPoint(37.296551,127.042500),GeoPoint(37.296757,127.041551),GeoPoint(37.296868,127.041395),
        GeoPoint(37.296979,127.041311),GeoPoint(37.297197,127.041292),GeoPoint(37.297454,127.041496),GeoPoint(37.297553,127.041595)))
    @Test fun anAlreadyActiveRightSignalSuppressesOnlyTheSignalReminder() {
        for(step in listOf(CoachingStep.LANE_PREPARE,CoachingStep.TURN_PREPARE)) {
            val off=ScenarioCoachingCues.preparation(step,Indicator.OFF)
            val on=ScenarioCoachingCues.preparation(step,Indicator.RIGHT)
            val unknown=ScenarioCoachingCues.preparation(step,Indicator.UNKNOWN)
            assertTrue(off.spoken!!.contains("오른쪽 깜빡이를 켜고"))
            assertFalse(on.full.contains("깜빡이"))
            assertTrue(on.spoken!!.contains(if(step==CoachingStep.LANE_PREPARE)"사각지대" else "횡단보도"))
            assertTrue(unknown.full.contains("상태를 확인"))
            assertFalse(unknown.full.contains("켜고"))
        }
    }
    @Test fun theWheelAndBodyYawAgreeInForwardAndReverseAcrossAllFourReplays() {
        for(lesson in RoadLesson.values()) {
            val s=SimpleRoadScenario(route(),lesson);s.start()
            var prior=s.frame();var right=false;var left=false
            repeat(4000) {
                val beforeWheel=s.steeringTarget
                s.advance(50,null,false)
                val wheel=(beforeWheel+s.steeringTarget)/2
                val frame=s.frame()
                val travel=NavigationSimulationRoute.metres(prior.position,frame.position)
                val yaw=(frame.bearing-prior.bearing+540)%360-180
                if(travel>.001 && abs(yaw)>.025 && abs(wheel)>5) {
                    val expected=if(lesson==RoadLesson.PARKING && s.parkingGear=="R")-yaw else yaw
                    // Curvature sampling spans the phase boundary. Exclude the narrow zero crossing.
                    if(abs(wheel)>15 && abs(yaw)>.05)assertTrue("$lesson at ${s.distance}: yaw=$yaw wheel=$wheel",expected*wheel>0)
                }
                if(wheel>10)right=true
                if(wheel< -10)left=true
                prior=frame
            }
            assertTrue("$lesson incomplete",s.complete)
            assertTrue("$lesson never turned right",right)
            if(lesson==RoadLesson.LANE || lesson==RoadLesson.PARKING)assertTrue("Missing countersteer $lesson",left)
            assertEquals(0.0,s.steeringTarget,0.0)
        }
    }
}
