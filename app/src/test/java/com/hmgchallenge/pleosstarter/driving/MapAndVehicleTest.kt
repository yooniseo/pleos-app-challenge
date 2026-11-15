package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test

class MapAndVehicleTest {
    @Test fun projectionMatchesKnownEquatorTile() {
        val center=WebMercator.project(GeoPoint(0.0,0.0),1)
        assertEquals(256.0,center.x,0.0001);assertEquals(256.0,center.y,0.0001)
        assertFalse(GeoPoint(Double.NaN,127.0).valid)
        assertFalse(GeoPoint(90.0,127.0).valid)
    }
    @Test fun fittingContainsRouteWithPadding() {
        val points=listOf(GeoPoint(37.4015,127.1058),GeoPoint(37.4030,127.1059))
        val zoom=WebMercator.fitZoom(points,500,300)
        val projected=points.map {WebMercator.project(it,zoom)}
        assertTrue(kotlin.math.abs(projected[0].y-projected[1].y)<=228)
    }
    @Test fun referencePositionFollowsRoadVerticesRatherThanEndpointShortcut() {
        val route=ReferenceRoute(listOf(GeoPoint(0.0,0.0),GeoPoint(0.0,1.0),GeoPoint(1.0,1.0)),200.0,30.0,emptyList(),false)
        assertEquals(GeoPoint(0.0,1.0),route.positionAt(0.5))
        assertEquals(route.points.first(),route.positionAt(-1.0))
        assertEquals(route.points.last(),route.positionAt(2.0))
    }
    @Test fun driverLanePlanCannotSurviveRouteChangeOrAcceptInvalidLane() {
        val plan=DriverLanePlan("route-a",1,3,3)
        assertTrue(plan.instruction("route-a")!!.contains("오른쪽으로 2개"))
        assertNull(plan.instruction("route-b"))
        assertFalse(DriverLanePlan("route-a",0,3,3).valid)
        assertFalse(DriverLanePlan("",1,2,3).valid)
        assertFalse(DriverLanePlan("a",1,4,3).valid)
        assertTrue(DriverLanePlan("a",3,1,3).instruction("a")!!.contains("왼쪽으로 2개"))
    }
    @Test fun unknownVehicleStateNeverClaimsBeltOrParkingBrakeSuccess() {
        val state=VehicleCoachState()
        assertFalse(state.summary().contains("체결"));assertFalse(state.summary().contains("착용"))
        assertTrue(state.summary().contains("미확인"))
        assertFalse(VehicleCoachState(gear="P").cue(true).contains("내리기"))
        assertTrue(VehicleCoachState(gear="P",parkingBrake=true).cue(true).contains("내리기"))
    }
    @Test fun movingVehicleDoesNotShowParkedLessonForReverseOrPark() {
        assertTrue(VehicleCoachState(gear="R").cue(false).startsWith("주행 중"))
        assertTrue(VehicleCoachState(gear="P",parkingBrake=true).cue(false).startsWith("주행 중"))
        assertTrue(VehicleCoachState(gear="R").cue(true).contains("후진 준비"))
    }
}
