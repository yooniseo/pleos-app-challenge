package com.hmgchallenge.pleosstarter.session

import kotlin.math.abs

enum class MotionState { UNKNOWN, STOPPED, MOVING }

/** Same freshness window as the recorder. Missing speed is never interpreted as a parked car. */
object VehicleMotion {
    fun at(speedMps: Double?,receivedAt: Long,now: Long): MotionState = when {
        speedMps==null || !speedMps.isFinite() || receivedAt<=0 || now-receivedAt !in 0..3000 -> MotionState.UNKNOWN
        abs(speedMps)<=.1 -> MotionState.STOPPED
        else -> MotionState.MOVING
    }
}
