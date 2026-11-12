package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test

class TrainingAutoDriverTest {
    @Test fun automaticPlaybackCompletesBothLegalScenariosWithoutManualInput() {
        listOf(false,true).forEach {right->
            val m=DrivingSimulator(right);val auto=TrainingAutoDriver();val seen=mutableSetOf<SimStage>()
            repeat(10_000) {if(m.stage!=SimStage.COMPLETE){seen.add(m.stage);auto.update(m,0.05);m.tick(0.05)}}
            assertEquals("Stalled at ${m.stage}",SimStage.COMPLETE,m.stage)
            assertEquals(m.targetLane,m.currentLane);assertEquals(0,m.lanesRemaining)
            assertEquals(2,m.practices.count {it.id.startsWith("lane")})
            assertTrue(m.stopped);assertTrue(m.parkingBrake);assertEquals(SimGear.P,m.gear)
            assertTrue(seen.containsAll(listOf(SimStage.PARKING_EXIT,SimStage.LANE_OUT,SimStage.TURN_CHECK,SimStage.TURN,SimStage.SECURE)))
        }
    }
    @Test fun automaticIntersectionShowsWaitingBeforeChangingAuthoredTrafficConditions() {
        val m=DrivingSimulator();val auto=TrainingAutoDriver()
        repeat(10_000) {if(m.stage!=SimStage.TURN_CHECK){auto.update(m,0.05);m.tick(0.05)}}
        assertEquals(SimStage.TURN_CHECK,m.stage)
        repeat(20){auto.update(m,0.05);m.tick(0.05)}
        assertFalse(m.green);assertTrue(m.pedestrian);assertEquals(SimStage.TURN_CHECK,m.stage)
        repeat(20){auto.update(m,0.05);m.tick(0.05)}
        assertTrue(m.green);assertTrue(m.pedestrian);assertEquals(SimStage.TURN_CHECK,m.stage)
        repeat(30){auto.update(m,0.05);m.tick(0.05)}
        assertEquals(SimStage.TURN,m.stage)
    }
    @Test fun automaticDepartureHandlesRainBeforeMoving() {
        val m=DrivingSimulator();m.rain=true;val auto=TrainingAutoDriver()
        repeat(15){auto.update(m,0.05);m.tick(0.05)}
        assertEquals(SimGear.P,m.gear);assertEquals(0.0,m.distance,0.001)
        repeat(45){auto.update(m,0.05);m.tick(0.05)}
        assertTrue(m.belt);assertEquals(2,m.wiper);assertFalse(m.parkingBrake);assertEquals(SimStage.PARKING_EXIT,m.stage)
    }
    @Test fun motionHintsExplainBrakeAndPedalWithoutChangingTheCar() {
        val m=DrivingSimulator();DrivingSimulatorTest.driveUntil(m,SimStage.PARKING_EXIT)
        assertTrue(m.movementHint()!!.contains("브레이크"))
        m.pedals(0.0,0.0);val before=m.distance
        assertTrue(m.movementHint()!!.contains("액셀 켜기"));assertEquals(before,m.distance,0.0)
        m.pedals(45.0,0.0);assertNull(m.movementHint())
    }
    @Test fun confirmingSurroundingsDoesNotActuateTheAccelerator() {
        val m=DrivingSimulator();DrivingSimulatorTest.driveUntil(m,SimStage.EXIT_PREPARE)
        m.pedals(0.0,80.0);m.selectGear(SimGear.D);m.setSignal(m.expectedSignal);m.checkMirrors()
        val before=m.distance;repeat(40){m.tick(0.05)}
        assertEquals(SimStage.EXIT_APPROACH,m.stage);assertEquals(before,m.distance,0.001)
        assertTrue(m.movementHint()!!.contains("브레이크"))
        m.pedals(45.0,0.0);repeat(40){m.tick(0.05)};assertTrue(m.distance>before)
    }
}
