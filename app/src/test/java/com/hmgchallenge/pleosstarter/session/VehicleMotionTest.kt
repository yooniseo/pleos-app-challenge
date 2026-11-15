package com.hmgchallenge.pleosstarter.session

import org.junit.Assert.*
import org.junit.Test

class VehicleMotionTest {
    @Test fun forwardAndReverseBothMeanMoving() {
        assertEquals(MotionState.MOVING,VehicleMotion.at(10.0,1000,2000))
        assertEquals(MotionState.MOVING,VehicleMotion.at(-2.0,1000,2000))
    }
    @Test fun unknownSpeedNeverMeansParked() {
        for(value in listOf(null,Double.NaN,Double.POSITIVE_INFINITY))assertEquals(MotionState.UNKNOWN,VehicleMotion.at(value,1000,2000))
        assertEquals(MotionState.UNKNOWN,VehicleMotion.at(0.0,1000,4001))
        assertEquals(MotionState.UNKNOWN,VehicleMotion.at(0.0,3000,2000))
        assertEquals(MotionState.UNKNOWN,VehicleMotion.at(0.0,0,2000))
    }
    @Test fun onlyFreshNearZeroMeansStopped() {
        assertEquals(MotionState.STOPPED,VehicleMotion.at(0.0,1000,2000))
        assertEquals(MotionState.STOPPED,VehicleMotion.at(.09,1000,2000))
        assertEquals(MotionState.MOVING,VehicleMotion.at(.2,1000,2000))
    }
}
