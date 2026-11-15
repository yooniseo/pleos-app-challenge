package com.hmgchallenge.pleosstarter.driving

import com.hmgchallenge.pleosstarter.quests.*
import com.hmgchallenge.pleosstarter.quests.EvidenceSource as QuestEvidence
import org.junit.Assert.*
import org.junit.Test

class SimpleRoadScenarioTest {
    @Test fun optionalModeDoesNotClaimAutomaticGuidance() {
        val s=SimpleRoadScenario(course(),RoadLesson.LANE);s.start()
        val link=SimpleScenarioQuests("optional",100)
        val state=link.sync(link.begin(QuestState(),100),s,101) {AssistanceUsed.NONE}
        assertTrue(state.session!!.events.none {it.type==SituationEventType.GUIDANCE_PROVIDED})
        assertTrue(state.evidence.isEmpty())
    }
    @Test fun actualSelectedAssistanceIsRecordedWithScenarioEvents() {
        val s=SimpleRoadScenario(course(),RoadLesson.LANE);s.start()
        val link=SimpleScenarioQuests("brief",100)
        val state=link.sync(link.begin(QuestState(),100),s,101) {AssistanceUsed.BRIEF}
        assertEquals(AssistanceUsed.BRIEF,state.session!!.events.single {it.type==SituationEventType.GUIDANCE_PROVIDED}.assistanceUsed)
    }
    private fun course()=NavigationSimulationRoute(listOf(
        GeoPoint(37.296551,127.042500),GeoPoint(37.296757,127.041551),GeoPoint(37.296868,127.041395),
        GeoPoint(37.296979,127.041311),GeoPoint(37.297197,127.041292),GeoPoint(37.297454,127.041496),GeoPoint(37.297553,127.041595)))
    @Test fun actualSpeedIntegratesMetresWithoutTimeWarp() {
        val s=SimpleRoadScenario(course(),source=ScenarioSpeed.VEHICLE);s.start()
        repeat(10){s.advance(1000,20.0/3.6,true)}
        assertEquals(55.5556,s.distance,.001)
        assertEquals(20.0,s.speedMps*3.6,.001)
    }
    @Test fun stopsStaleInvalidAndLifecycleGapsDoNotMove() {
        val s=SimpleRoadScenario(course(),source=ScenarioSpeed.VEHICLE);s.start()
        s.advance(1000,5.0,true);val start=s.distance
        for(v in listOf(null,Double.NaN,Double.POSITIVE_INFINITY,-5.0))s.advance(500,v,true)
        s.advance(1000,5.0,false);s.advance(1000,0.0,true);s.advance(5000,5.0,true)
        assertEquals(start,s.distance,0.0)
        s.advance(250,4.0,true);assertEquals(start+1,s.distance,0.0)
    }
    @Test fun replayCompletesOneLaneBeforeTurningAndUsesValidRoadGeometry() {
        val s=SimpleRoadScenario(course());s.start();var sawLane=false;var sawTurn=false
        repeat(3000){
            val before=s.distance;s.advance(50,null,false)
            assertTrue(s.distance>=before)
            val f=s.frame();assertTrue(f.position.valid && f.planned.all {it.valid})
            if(s.laneDone)sawLane=true
            if(s.turning){assertTrue(s.laneDone);assertEquals(3,s.currentLane);sawTurn=true}
        }
        assertTrue(s.complete && sawLane && sawTurn)
        assertEquals(s.endDistance,s.distance,0.0)
    }
    @Test fun scenarioEventsAloneCreditDemoQuestsOnceAndOnlyAfterMilestones() {
        val s=SimpleRoadScenario(course(),source=ScenarioSpeed.VEHICLE)
        val link=SimpleScenarioQuests("demo-road",100)
        var state=link.begin(QuestState(),100);s.start();state=link.sync(state,s,101)
        assertTrue(state.evidence.isEmpty())
        repeat(100){s.advance(100,0.0,true);state=link.sync(state,s,102+it.toLong())}
        assertTrue(state.evidence.isEmpty())
        var at=1000L
        repeat(25){s.advance(1000,5.0,true);state=link.sync(state,s,at++)}
        assertEquals(2,state.practiceCount(CoachSkill.LANE_CHANGE));assertEquals(0,state.practiceCount(CoachSkill.INTERSECTION))
        repeat(30){s.advance(1000,5.0,true);state=link.sync(state,s,at++)}
        assertTrue(s.complete)
        assertEquals(2,state.practiceCount(CoachSkill.LANE_CHANGE));assertEquals(1,state.practiceCount(CoachSkill.INTERSECTION))
        assertTrue(state.evidence.all {it.source==QuestEvidence.SIMULATION})
        assertNotNull(state.session!!.finishedAt)
        assertEquals(state,link.sync(state,s,at))
    }
    @Test fun laneOnlyAndTurnOnlyCreditOnlyTheirOwnQuest() {
        for(lesson in listOf(RoadLesson.LANE,RoadLesson.TURN)) {
            val s=SimpleRoadScenario(course(),lesson);val link=SimpleScenarioQuests(lesson.name,100)
            var state=link.begin(QuestState(),100);s.start()
            repeat(100){s.advance(1000,null,false);state=link.sync(state,s,101+it.toLong())}
            assertTrue(s.complete)
            assertEquals(if(lesson==RoadLesson.LANE)1 else 0,state.practiceCount(CoachSkill.LANE_CHANGE))
            assertEquals(if(lesson==RoadLesson.TURN)1 else 0,state.practiceCount(CoachSkill.INTERSECTION))
        }
    }
    @Test fun restartingCancelsUnfinishedOccurrenceAndNeverTouchesPersonalState() {
        val s=SimpleRoadScenario(course());s.start();val first=SimpleScenarioQuests("one",100)
        val entered=first.sync(first.begin(QuestState(),100),s,101)
        val restarted=SimpleScenarioQuests("two",200).begin(entered,200)
        assertEquals("two",restarted.session!!.tripId);assertTrue(restarted.evidence.isEmpty())
        val personal=QuestState(demo=false)
        assertEquals(personal,first.begin(personal,100));assertEquals(personal,first.sync(personal,s,101))
    }
    @Test fun parkingStopsForSurroundingsReversesAlignsAndCreditsOnlyParking() {
        val s=SimpleRoadScenario(course(),RoadLesson.PARKING);val link=SimpleScenarioQuests("parking",100)
        var state=link.begin(QuestState(),100);s.start();var waited=false;var reversed=false;var maxSteering=0.0
        repeat(1200) {
            s.advance(50,null,false);state=link.sync(state,s,101+it.toLong())
            if(s.distance==5.0 && s.speedMps==0.0)waited=true
            if(s.parkingGear=="R" && s.speedMps>0)reversed=true
            assertTrue(s.frame().position.valid)
            maxSteering=maxOf(maxSteering,kotlin.math.abs(s.steeringTarget))
        }
        assertTrue(s.complete && waited && reversed);assertTrue(maxSteering>20)
        assertEquals("P",s.parkingGear)
        assertEquals(1,state.practiceCount(CoachSkill.PARKING));assertEquals(1,state.evidence.size)
        assertEquals(ParkingLessonGeometry.point(6.0,-10.0),s.frame().position)
    }
    @Test fun chosenStyleChangesActionableExplanationAndSteeringRemainsFinite() {
        val s=SimpleRoadScenario(course());s.start();repeat(70){s.advance(50,null,false)}
        val a=BeginnerScenarioCoach.guidance(s,BeginnerStyle.STEP_BY_STEP)
        val b=BeginnerScenarioCoach.guidance(s,BeginnerStyle.VISUAL)
        val c=BeginnerScenarioCoach.guidance(s,BeginnerStyle.BRIEF)
        assertNotEquals(a.detail,b.detail);assertNotEquals(b.headline,c.headline)
        repeat(3000){s.advance(50,null,false);assertTrue(s.steeringTarget.isFinite());assertTrue(kotlin.math.abs(s.steeringTarget)<=360)}
    }
    @Test fun coachingUsesTurnsAndTimelyUnwindingInsteadOfDegreeNumbers() {
        assertEquals("반 바퀴",BeginnerScenarioCoach.turnAmount(180))
        assertEquals("한 바퀴",BeginnerScenarioCoach.turnAmount(-360))
        assertEquals("두 바퀴",BeginnerScenarioCoach.turnAmount(720))
        val s=SimpleRoadScenario(course(),RoadLesson.PARKING);s.start();var sawUnwind=false
        repeat(1200) {
            s.advance(50,null,false)
            val g=BeginnerScenarioCoach.guidance(s,BeginnerStyle.STEP_BY_STEP)
            assertFalse(g.headline.contains("°"));assertFalse(g.headline.contains("도 ·"))
            if(!s.complete && s.distance>s.endDistance-1) {
                sawUnwind=true;assertTrue(g.headline.contains("가운데"));assertEquals(0,g.steering)
            }
        }
        assertTrue(sawUnwind)
    }
}
