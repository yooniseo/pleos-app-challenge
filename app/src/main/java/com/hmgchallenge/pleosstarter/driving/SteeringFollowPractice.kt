package com.hmgchallenge.pleosstarter.driving

import kotlin.math.abs

/** Validates only the user's on-screen wheel input, never an inferred physical steering action. */
class SteeringFollowPractice(val target: Double) {
    var angle=0.0;private set
    var rewarded=false;private set
    private var moved=false
    private var alignedAt: Long?=null
    val tolerance=(abs(target)*.08).coerceIn(10.0,20.0)
    val instruction get()=BeginnerScenarioCoach.steeringAction(target.toInt())
    fun input(value: Double,now: Long) {
        if(!value.isFinite() || rewarded)return
        angle=value.coerceIn(-450.0,450.0)
        if(abs(angle)>=15)moved=true
        if(moved && abs(angle-target)<=tolerance) {if(alignedAt==null)alignedAt=now} else alignedAt=null
    }
    fun check(now: Long): Boolean {
        if(rewarded || alignedAt==null || now-alignedAt!!<350)return false
        rewarded=true;return true
    }
}
