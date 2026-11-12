package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test

class DrivingSimulatorTest {
    @Test fun allFourDriverScenariosFinishWithTwoLaneChangesAndParkedCar() {
        listOf(false,true).forEach {right->listOf(false,true).forEach {laneRight->
            val m=DrivingSimulator(right,laneRight);driveUntil(m,SimStage.COMPLETE)
            assertEquals(m.targetLane,m.currentLane);assertEquals(0,m.lanesRemaining)
            assertEquals(2,m.practices.count {it.id.startsWith("lane")})
            assertTrue(m.parkingBrake);assertEquals(SimGear.P,m.gear);assertTrue(m.stopped)
            assertTrue(m.practices.any {it.id=="intersection"});assertTrue(m.trace.size>10)
        }}
    }
    @Test fun missingBeltRainWiperAndParkingBrakeBlockDeparture() {
        val m=DrivingSimulator();m.pedals(80.0,0.0);repeat(100){m.tick(0.05)}
        assertEquals(0.0,m.distance,0.001);assertEquals(SimStage.PREPARE,m.stage)
        m.belt=true;m.rain=true;m.pedals(0.0,80.0);m.setParkingBrake(false);m.selectGear(SimGear.R);m.tick(0.05)
        assertEquals(SimStage.PREPARE,m.stage);assertTrue(m.instruction().contains("와이퍼"))
        m.wiper=2;m.tick(0.05);assertEquals(SimStage.REVERSE,m.stage)
    }
    @Test fun gearAndParkingBrakeCannotBeChangedWhileMoving() {
        val m=DrivingSimulator();driveUntil(m,SimStage.REVERSE)
        m.pedals(80.0,0.0);repeat(40){m.tick(0.05)}
        assertFalse(m.stopped);assertFalse(m.selectGear(SimGear.P));assertFalse(m.setParkingBrake(true))
        m.pedals(0.0,100.0);repeat(40){m.tick(0.05)}
        assertTrue(m.selectGear(SimGear.P));assertTrue(m.setParkingBrake(true))
    }
    @Test fun lanePreparationNeedsCorrectBlinkerAndFreshMirrorCheck() {
        val m=DrivingSimulator(laneRight=true);driveUntil(m,SimStage.LANE_PREPARE)
        m.setSignal(SimSignal.OFF);m.checkMirrors();m.tick(0.05)
        assertEquals(SimStage.LANE_PREPARE,m.stage);assertTrue(m.instruction().contains("오른쪽 깜빡이"))
        m.setSignal(SimSignal.RIGHT);m.tick(0.05);assertEquals(SimStage.LANE_PREPARE,m.stage)
        m.checkMirrors();m.tick(0.05);assertEquals(SimStage.LANE_OUT,m.stage)
        assertTrue(m.targetSteering>0)
    }
    @Test fun redLightOrPedestrianCannotAdvanceIntersection() {
        val m=DrivingSimulator(false);driveUntil(m,SimStage.TURN_CHECK)
        m.pedals(0.0,80.0);m.setSignal(SimSignal.LEFT);m.checkMirrors();m.green=false;m.pedestrian=false;m.tick(0.05)
        assertEquals(SimStage.TURN_CHECK,m.stage)
        m.green=true;m.pedestrian=true;m.tick(0.05);assertEquals(SimStage.TURN_CHECK,m.stage)
        m.pedestrian=false;m.tick(0.05);assertEquals(SimStage.TURN,m.stage);assertTrue(m.targetSteering<0)
    }
    @Test fun wheelAndReverseInputsChangeHeadingInOppositeDirections() {
        val forward=DrivingSimulator();driveUntil(forward,SimStage.REVERSE)
        forward.selectGear(SimGear.D);forward.steer(200.0);forward.pedals(50.0,0.0)
        repeat(30){forward.tick(0.05)};assertTrue(forward.heading>0)
        val backward=DrivingSimulator();driveUntil(backward,SimStage.REVERSE)
        backward.steer(200.0);backward.pedals(50.0,0.0);repeat(30){backward.tick(0.05)}
        assertTrue(backward.heading<0);assertTrue(backward.y<0)
        assertTrue(forward.wheelForRadius(7.0)>forward.wheelForRadius(9.0))
    }
    companion object {
        fun driveUntil(m: DrivingSimulator,target: SimStage) {
            repeat(20_000) {
                if(m.stage==target)return
                driveInput(m);m.tick(0.05)
            }
            error("Scenario stalled at ${m.stage}: ${m.instruction()} heading=${m.heading} travel=${m.stageTravel}")
        }
        fun driveInput(m: DrivingSimulator) {
            when(m.stage) {
                SimStage.PREPARE->{m.belt=true;m.wiper=2;m.pedals(0.0,80.0);m.setParkingBrake(false);m.selectGear(SimGear.R)}
                SimStage.REVERSE->{m.steer(0.0);m.pedals(if(m.stageTravel<3)35.0 else 0.0,if(m.stageTravel<3)0.0 else 100.0)}
                SimStage.EXIT_PREPARE->{m.pedals(0.0,80.0);m.selectGear(SimGear.D);m.setSignal(SimSignal.RIGHT);m.checkMirrors()}
                SimStage.EXIT_TURN,SimStage.LANE_OUT,SimStage.LANE_RETURN,SimStage.TURN->{m.steer(m.targetSteering);m.pedals(35.0,0.0)}
                SimStage.EXIT_ALIGN,SimStage.TURN_ALIGN->{m.steer(0.0);m.pedals(25.0,0.0)}
                SimStage.LANE_PREPARE->{m.steer(0.0);m.setSignal(m.expectedSignal);m.checkMirrors()}
                SimStage.APPROACH,SimStage.FINAL_STOP->{
                    m.steer(0.0);if(m.stage==SimStage.APPROACH)m.setSignal(m.expectedSignal)
                    val distance=if(m.stage==SimStage.APPROACH)10 else 3
                    m.pedals(if(m.stageTravel<distance)30.0 else 0.0,if(m.stageTravel<distance)0.0 else 100.0)
                }
                SimStage.TURN_CHECK->{m.pedals(0.0,80.0);m.setSignal(m.expectedSignal);m.green=true;m.pedestrian=false;m.checkMirrors()}
                SimStage.SECURE->{m.pedals(0.0,80.0);m.selectGear(SimGear.P);m.setParkingBrake(true);m.setSignal(SimSignal.OFF);m.checkMirrors()}
                SimStage.COMPLETE->m.freeze()
            }
        }
    }
}
