package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class NavigationSimulationRouteTest {
    private fun course()=NavigationSimulationRoute(listOf(
        GeoPoint(37.296551,127.042500),GeoPoint(37.296757,127.041551),GeoPoint(37.296868,127.041395),
        GeoPoint(37.296979,127.041311),GeoPoint(37.297197,127.041292),GeoPoint(37.297454,127.041496),GeoPoint(37.297553,127.041595)))
    @Test fun routeFollowsRealRoadCoordinatesAndTurnBearingChangesRight() {
        val c=course()
        assertEquals(192.2,c.length,1.0);assertEquals(87.2,c.turnDistance,1.0)
        assertTrue(c.bearing(0.0)<0);assertTrue(c.bearing(c.length)>0)
        assertEquals(c.points.first(),c.at(-1.0));assertEquals(c.points.last(),c.at(c.length+1))
    }
    @Test fun authoredLaneChangesTurnAndRemainingDistanceStayConnected() {
        val c=course();val m=DrivingSimulator();m.startRoadPractice();val driver=TrainingAutoDriver()
        val start=c.frame(m).position;var prior=0.0;val lanes=mutableSetOf<Int>();var sawTurn=false;var sawWaiting=false
        repeat(6000) {
            if(m.stage!=SimStage.COMPLETE) {
                driver.update(m,0.05);m.tick(0.05)
                val f=c.frame(m)
                assertTrue(f.position.valid && f.planned.all {it.valid})
                assertTrue("backwards ${m.stage} ${f.distance} < $prior",f.distance>=prior-0.3)
                assertEquals((c.turnDistance-f.distance).coerceAtLeast(0.0),f.turnRemaining,0.01)
                assertTrue(f.planned.size>=2)
                prior=f.distance;lanes.add(m.currentLane)
                if(m.stage==SimStage.TURN_CHECK) {sawWaiting=true;assertEquals(3,m.currentLane);assertTrue(m.stopped)}
                if(m.stage==SimStage.TURN)sawTurn=true
            }
        }
        assertEquals(SimStage.COMPLETE,m.stage);assertEquals(setOf(1,2,3),lanes)
        assertTrue(sawTurn && sawWaiting);assertTrue(NavigationSimulationRoute.metres(start,c.frame(m).position)>100)
        assertTrue(c.frame(m).turnRemaining<0.1)
    }
    @Test fun laneOffsetStaysOnRightHandSideAndShiftsWithTheCar() {
        val c=course();val p=c.at(40.0);val bearing=c.bearing(40.0)
        val left=NavigationSimulationRoute.offset(p,bearing,-3.5);val right=NavigationSimulationRoute.offset(p,bearing,3.5)
        assertEquals(7.0,NavigationSimulationRoute.metres(left,right),0.05)
        assertTrue(right.latitude>left.latitude) // NW approach: driver's right points NE.
    }
}
