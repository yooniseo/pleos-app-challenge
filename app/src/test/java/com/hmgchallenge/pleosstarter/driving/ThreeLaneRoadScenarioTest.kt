package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test

class ThreeLaneRoadScenarioTest {
    private fun road(): NavigationSimulationRoute {
        val points=listOf(
            GeoPoint(37.295543,127.047783),GeoPoint(37.295667,127.046809),GeoPoint(37.295798,127.046006),
            GeoPoint(37.296075,127.044621),GeoPoint(37.296455,127.042943),GeoPoint(37.296757,127.041551),
            GeoPoint(37.296868,127.041395),GeoPoint(37.296979,127.041311),GeoPoint(37.297197,127.041292),
            GeoPoint(37.297454,127.041496),GeoPoint(37.297553,127.041595))
        return NavigationSimulationRoute.reference(ReferenceRoute(points,674.4,0.0,listOf("창룡대로","대학로"),false,points[5]))
    }
    @Test fun twoSeparateLaneChangesFinishBeforeTheRightTurnAtNormalReplaySpeed() {
        val s=SimpleRoadScenario(road());s.start()
        assertEquals(1,s.currentLane)
        assertTrue(s.course.turnDistance>530)
        assertTrue(s.endDistance>670)
        val lanes=mutableListOf<Int>();var cruise=false;var approach=false;var turn=false
        var previous=0
        repeat(4000) {
            s.advance(50,null,false)
            if(s.currentLane!=previous){lanes.add(s.currentLane);previous=s.currentLane}
            if(s.distance in 180.0..240.0 && s.speedMps*3.6>39)cruise=true
            if(s.distance in 480.0..510.0 && s.speedMps*3.6 in 19.0..21.0)approach=true
            if(s.distance in (s.course.turnDistance-8)..(s.course.turnDistance+40))
                assertTrue("Unexpected left hook at connector: ${s.distance} ${s.steeringTarget}",s.steeringTarget> -12)
            if(s.turning) {
                turn=true;assertEquals(2,s.completedLaneChanges);assertEquals(3,s.currentLane)
                assertTrue(s.speedMps*3.6<=10.1 || s.distance>s.course.turnDistance+80)
            }
        }
        assertEquals(listOf(1,2,3),lanes)
        assertTrue(cruise && approach && turn && s.complete)
    }
    @Test fun roadCompletionKeepsTheTripOpenUntilParkingAndRecordsBothLaneChangesOnce() {
        val road=SimpleRoadScenario(road());val link=SimpleScenarioQuests("road-to-parking",1000)
        var state=link.begin(com.hmgchallenge.pleosstarter.quests.QuestState(),1000)
        road.start();var time=1000L
        repeat(4000){road.advance(50,null,false);time+=50;state=link.sync(state,road,time,finishTrip=false)}
        assertTrue(road.complete)
        assertEquals(2,state.practiceCount(com.hmgchallenge.pleosstarter.quests.CoachSkill.LANE_CHANGE))
        assertEquals(1,state.practiceCount(com.hmgchallenge.pleosstarter.quests.CoachSkill.INTERSECTION))
        assertNull(state.session!!.finishedAt)
        val parking=SimpleRoadScenario(road.course,RoadLesson.PARKING);parking.start()
        repeat(1600){parking.advance(50,null,false);time+=50;state=link.sync(state,parking,time)}
        assertTrue(parking.complete)
        assertEquals(4,state.evidence.size)
        assertNotNull(state.session!!.finishedAt)
        assertEquals(state,link.sync(state,parking,time+1))
    }
}
