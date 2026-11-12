package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class DrivingSimulatorTest {
    @Test fun bothLegalScenariosFinishWithTwoSeparateLaneChanges() {
        for(right in listOf(false,true)) {
            val m=DrivingSimulator(right);driveUntil(m,SimStage.COMPLETE)
            assertEquals(m.targetLane,m.currentLane);assertEquals(0,m.lanesRemaining)
            assertEquals(2,m.practices.count {it.id.startsWith("lane")})
            assertTrue(m.parkingBrake);assertEquals(SimGear.P,m.gear);assertTrue(m.stopped)
            assertTrue(m.practices.any {it.id=="intersection"})
            assertFalse("Ending a simulation is not evidence of parking",m.practices.any {it.id=="finish"})
        }
    }
    @Test(expected=IllegalArgumentException::class) fun rightTurnCannotBeConfiguredToEndInLeftLane() {DrivingSimulator(true,false)}
    @Test fun missingBeltRainWiperAndParkingBrakeBlockDeparture() {
        val m=DrivingSimulator();m.pedals(80.0,0.0);repeat(100){m.tick(0.05)}
        assertEquals(0.0,m.distance,0.001);assertEquals(SimStage.PREPARE,m.stage)
        m.belt=true;m.rain=true;m.pedals(0.0,80.0);m.setParkingBrake(false);m.selectGear(SimGear.D);m.tick(0.05)
        assertEquals(SimStage.PREPARE,m.stage);assertTrue(m.instruction().contains("와이퍼"))
        m.wiper=2;m.tick(0.05);assertEquals(SimStage.PARKING_EXIT,m.stage)
    }
    @Test fun gearAndParkingBrakeCannotBeChangedWhileMoving() {
        val m=DrivingSimulator();driveUntil(m,SimStage.PARKING_EXIT)
        m.pedals(80.0,0.0);repeat(40){m.tick(0.05)}
        assertFalse(m.stopped);assertFalse(m.selectGear(SimGear.P));assertFalse(m.setParkingBrake(true))
        m.pedals(0.0,100.0);repeat(40){m.tick(0.05)}
        assertTrue(m.selectGear(SimGear.P));assertTrue(m.setParkingBrake(true))
    }
    @Test fun lanePreparationNeedsCorrectBlinkerAndFreshMirrorCheck() {
        val m=DrivingSimulator();driveUntil(m,SimStage.LANE_PREPARE);m.freeze()
        m.setSignal(SimSignal.OFF);m.checkMirrors();m.tick(0.05)
        assertEquals(SimStage.LANE_PREPARE,m.stage);assertTrue(m.instruction().contains("오른쪽 깜빡이"))
        m.setSignal(SimSignal.RIGHT);m.tick(0.05);assertEquals(SimStage.LANE_PREPARE,m.stage)
        m.checkMirrors();m.tick(0.05);assertEquals(SimStage.LANE_OUT,m.stage)
        assertTrue(m.targetSteering>0)
    }
    @Test fun redLightOrPedestrianCannotAdvanceIntersection() {
        val m=DrivingSimulator(false);driveUntil(m,SimStage.TURN_CHECK)
        assertTrue(m.inTurnLane);assertEquals(1,m.road.eastboundLane(m.y))
        m.pedals(0.0,80.0);m.setSignal(SimSignal.LEFT);m.checkMirrors();m.green=false;m.pedestrian=false;m.tick(0.05)
        assertEquals(SimStage.TURN_CHECK,m.stage)
        m.green=true;m.pedestrian=true;m.tick(0.05);assertEquals(SimStage.TURN_CHECK,m.stage)
        m.pedestrian=false;m.tick(0.05);assertEquals(SimStage.TURN,m.stage)
    }
    @Test fun greenAndMirrorConfirmationCannotAuthorizeTurnFromWrongPhysicalLane() {
        val m=DrivingSimulator();driveUntil(m,SimStage.TURN_CHECK)
        m.green=false;m.steer(-400.0);m.pedals(65.0,0.0)
        repeat(70){m.tick(0.05)}
        m.freeze();assertFalse(m.inTurnLane)
        m.green=true;m.pedestrian=false;m.setSignal(SimSignal.RIGHT);m.checkMirrors();m.tick(0.05)
        assertEquals(SimStage.TURN_CHECK,m.stage)
    }
    @Test fun rollingPastRedStopLineDoesNotReceiveTurnAuthorization() {
        val m=DrivingSimulator();driveUntil(m,SimStage.TURN_CHECK)
        m.green=false;m.steer(0.0);m.pedals(65.0,0.0);repeat(60){m.tick(0.05)}
        m.freeze();assertTrue(m.inTurnLane);assertTrue(m.approachRemaining< -0.15)
        m.green=true;m.pedestrian=false;m.setSignal(SimSignal.RIGHT);m.checkMirrors();m.tick(0.05)
        assertEquals(SimStage.TURN_CHECK,m.stage);assertTrue(m.instruction().contains("정지선을 넘었어요"))
    }
    @Test fun wheelAndReverseInputsChangeHeadingInOppositeDirections() {
        val forward=DrivingSimulator();driveUntil(forward,SimStage.PARKING_EXIT)
        forward.steer(200.0);forward.pedals(50.0,0.0);repeat(30){forward.tick(0.05)}
        assertTrue(forward.heading>forward.road.initialHeading)
        val backward=DrivingSimulator();driveUntil(backward,SimStage.PARKING_EXIT)
        backward.selectGear(SimGear.R);backward.steer(200.0);backward.pedals(50.0,0.0);repeat(30){backward.tick(0.05)}
        assertTrue(backward.heading<backward.road.initialHeading);assertTrue(backward.y>backward.road.initialY)
    }
    @Test fun actualPlaybackUsesCorrectCarriagewayStopsBeforeCrosswalkAndKeepsBodyPaved() {
        for(right in listOf(true,false)) {
            val m=DrivingSimulator(right);val auto=TrainingAutoDriver()
            val crossed=mutableSetOf<Int>();var entryStop=false;var mainStop=false;var turn=false
            repeat(6000) {
                if(m.stage!=SimStage.COMPLETE) {
                    auto.update(m,0.05);m.tick(0.05)
                    val road=m.road
                    if(m.stage==SimStage.EXIT_CHECK) {
                        entryStop=true
                        assertTrue(abs(m.y-road.entryStopY)<0.5)
                        assertTrue(abs(m.y)-road.carHalfLength>16.5)
                    }
                    if(m.stage==SimStage.TURN_CHECK) {
                        mainStop=true;assertTrue(m.x+road.carHalfLength<road.junctionX-18)
                        assertEquals(if(right)3 else 1,road.eastboundLane(m.y))
                    }
                    if(m.stage==SimStage.TURN)turn=true
                    if(m.stage in setOf(SimStage.LANE_PREPARE,SimStage.LANE_OUT,SimStage.LANE_RETURN,SimStage.APPROACH)) {
                        assertTrue("wrong carriageway: ${m.x},${m.y}",m.y<0 && m.y>=-road.halfWidth)
                    }
                    if(m.stage in setOf(SimStage.TURN,SimStage.TURN_ALIGN)) {
                        road.mainCrosswalks.forEachIndexed {index,box->if(box.contains(m.x,m.y))crossed.add(index)}
                    }
                    for(a in listOf(-road.carHalfWidth,road.carHalfWidth)) for(b in listOf(-road.carHalfLength,road.carHalfLength)) {
                        val px=m.x+a*cos(m.heading)+b*sin(m.heading);val py=m.y-a*sin(m.heading)+b*cos(m.heading)
                        assertTrue("body outside road right=$right ${m.stage} at $px,$py pose=${m.x},${m.y}",road.paved(px,py))
                    }
                }
            }
            assertEquals("${m.stage} x=${m.x} y=${m.y} heading=${m.heading}",SimStage.COMPLETE,m.stage)
            assertTrue(entryStop && mainStop && turn)
            assertTrue("must cross near and outgoing crosswalks: $crossed",crossed.containsAll(listOf(0,if(right)3 else 2)))
        }
    }
    companion object {
        fun driveUntil(m: DrivingSimulator,target: SimStage) {
            val auto=TrainingAutoDriver()
            repeat(8000) {
                if(m.stage==target)return
                auto.update(m,0.05);m.tick(0.05)
            }
            error("Scenario stalled at ${m.stage}: ${m.instruction()} pose=${m.x},${m.y},${m.heading} travel=${m.stageTravel}")
        }
    }
}
