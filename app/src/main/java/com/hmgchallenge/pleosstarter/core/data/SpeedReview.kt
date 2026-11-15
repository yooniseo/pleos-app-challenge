package com.hmgchallenge.pleosstarter.core.data

/** Review candidates only, not a driver-safety score. Gaps/missing samples never become zero. */
object SpeedReview {
    data class Sample(val at: Long,val kmh: Double?)
    data class Candidate(val at: Long,val type: String,val delta: Double)
    fun analyze(samples: List<Sample>)=samples.zipWithNext().mapNotNull {(a,b)->
        val previous=a.kmh;val current=b.kmh;val dt=(b.at-a.at)/1000.0
        if(previous==null || current==null || !previous.isFinite() || !current.isFinite() || dt !in .5..1.5)return@mapNotNull null
        val delta=(current-previous)/dt
        val type=when {delta>=10 && previous<1->"HARSH_START";delta>=10->"HARSH_ACCEL";delta<=-12->"HARSH_BRAKE";else->return@mapNotNull null}
        Candidate(b.at,type,current-previous)
    }
}
