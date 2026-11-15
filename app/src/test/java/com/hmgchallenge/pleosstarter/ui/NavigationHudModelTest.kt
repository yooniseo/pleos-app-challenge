package com.hmgchallenge.pleosstarter.ui

import com.hmgchallenge.pleosstarter.driving.*
import org.junit.Assert.*
import org.junit.Test

class NavigationHudModelTest {
    private fun course()=NavigationSimulationRoute(listOf(GeoPoint(37.0,127.0),GeoPoint(37.006,127.0),GeoPoint(37.006,127.003)))
    private fun hud(s: SimpleRoadScenario,parking: Boolean=false)=NavigationHudModel.from(s,s.endDistance-s.startDistance,s.distance-s.startDistance,parking)
    @Test fun combinedShowsActualLaneDistanceThenUpcomingTurn() {
        val s=SimpleRoadScenario(course())
        val h=hud(s,true)
        assertEquals(HudManeuver.LANE,h.cues[0].maneuver)
        assertEquals(s.nextLaneChange!!.start,h.cues[0].metres!!,.001)
        assertEquals(HudManeuver.TURN,h.cues[1].maneuver)
        assertEquals(s.frame().turnRemaining,h.cues[1].metres!!,.001)
    }
    @Test fun laneOnlyNeverInventsAnotherTurn() {
        val s=SimpleRoadScenario(course(),RoadLesson.LANE)
        assertEquals(1,hud(s).cues.size)
        s.start();repeat(15){s.advance(1000,null,false)}
        assertFalse(hud(s).cues.any {it.maneuver==HudManeuver.TURN})
    }
    @Test fun stationaryVehicleDoesNotInventAnArrivalTime() {
        val s=SimpleRoadScenario(course(),source=ScenarioSpeed.VEHICLE);s.start();s.advance(1000,0.0,true)
        assertNull(hud(s).remainingSeconds)
        s.advance(1000,10.0,true)
        assertTrue(hud(s).remainingSeconds!!>0)
    }
    @Test fun replayArrivalAndDistanceDecreaseDuringProgress() {
        val s=SimpleRoadScenario(course());val before=hud(s,true);s.start()
        repeat(20){s.advance(1000,null,false)}
        val after=hud(s,true)
        assertTrue(after.remainingSeconds!!<before.remainingSeconds!!)
        assertTrue(after.remainingMetres<before.remainingMetres)
        assertTrue(after.progress>before.progress)
    }
    @Test fun combinedProgressDoesNotResetWhenParkingStarts() {
        val road=SimpleRoadScenario(course());val parking=SimpleRoadScenario(course(),RoadLesson.PARKING)
        val total=road.endDistance+parking.endDistance
        val h=NavigationHudModel.from(parking,total,road.endDistance,false)
        assertEquals(parking.endDistance,h.remainingMetres,.001)
        assertTrue(h.progress in .9f..1f)
    }
    @Test fun pastTurnShowsExitRoadAndCompletedRouteHasNoRemainingDistance() {
        val s=SimpleRoadScenario(course(),RoadLesson.TURN);s.start()
        while(s.distance<s.course.turnDistance+100)s.advance(1000,null,false)
        assertEquals(HudManeuver.STRAIGHT,hud(s).cues[0].maneuver)
        while(!s.complete)s.advance(1000,null,false)
        val h=hud(s)
        assertEquals(0.0,h.remainingMetres,0.0);assertEquals(1f,h.progress)
        assertEquals(HudManeuver.FINISH,h.cues[0].maneuver)
    }
}
