package com.hmgchallenge.pleosstarter.analysis

import com.hmgchallenge.pleosstarter.core.data.SpeedReview
import com.hmgchallenge.pleosstarter.feature.quests.Attempt
import com.hmgchallenge.pleosstarter.feature.quests.AttemptResult
import com.hmgchallenge.pleosstarter.feature.quests.QuestIds
import kotlin.math.abs
import kotlin.math.roundToInt

/** 주행 1회를 돌아볼 때 보는 기술. 점수는 0~100, 측정할 수 없으면 null(0점과 다름). */
enum class DrivingSkill(val title: String) {
    SMOOTH_ACCEL("부드러운 출발"),
    BRAKING("여유 있는 감속"),
    TURN_SIGNAL("깜빡이 먼저 켜기"),
    TURN_SLOWDOWN("회전 전 감속")
}

data class DrivingEvent(val type: String,val t: Long,val severity: Int,val deltaKmh: Double)

data class TripAnalysis(
    val events: List<DrivingEvent>,
    val scores: Map<DrivingSkill,Int?>,
    val measuredSeconds: Int,
    val distanceKm: Double,
    val lowConfidence: Boolean,
    val harshAccelCount: Int,
    val harshBrakeCount: Int,
    val signalAttempts: Int,
    val signalSuccesses: Int,
    val turnBrakeChecks: Int,
    val turnBrakeMisses: Int
) {
    val measured get()=measuredSeconds>=DrivingAnalyzer.MIN_MEASURED_SECONDS
}

/**
 * 1초 속도 샘플과 차량 신호 퀘스트 시도로 주행 1회를 분석한다. Android 의존성이 없는 순수 Kotlin.
 * 급출발·급가속·급감속 후보는 팀의 [SpeedReview] 기준을 그대로 쓰고, 심각도와 3초 병합만 더한다.
 */
object DrivingAnalyzer {
    const val MIN_MEASURED_SECONDS=30       // 이보다 짧으면 속도 기반 점수를 내지 않음
    const val LOW_CONFIDENCE_SECONDS=120     // 2분 미만 또는 1km 미만이면 참고용(가중치 낮춤)
    const val LOW_CONFIDENCE_KM=1.0
    const val MERGE_WINDOW_MS=3000L
    const val PENALTY_PER_SEVERITY=8

    fun analyze(samples: List<SpeedReview.Sample>,attempts: List<Attempt> = emptyList()): TripAnalysis {
        val sorted=samples.sortedBy {it.at}
        val events=merge(SpeedReview.analyze(sorted).map {DrivingEvent(it.type,it.at,severity(it.type,it.delta),it.delta)})
        val valid=sorted.filter {it.kmh!=null && it.kmh.isFinite() && it.kmh>=0}
        val measuredSeconds=valid.size
        val distanceKm=sorted.zipWithNext().sumOf {(a,b)->
            val dt=(b.at-a.at)/1000.0
            if(a.kmh==null || b.kmh==null || !a.kmh.isFinite() || !b.kmh.isFinite() || dt !in .5..1.5)0.0 else (a.kmh+b.kmh)/2*dt/3600
        }
        val measured=measuredSeconds>=MIN_MEASURED_SECONDS
        val accel=events.filter {it.type=="HARSH_START" || it.type=="HARSH_ACCEL"}
        val brake=events.filter {it.type=="HARSH_BRAKE"}
        // 깜빡이: 판정 보류(UNVERIFIED)와 브레이크 문제(NO_BRAKE)는 깜빡이 점수에서 제외
        val signal=attempts.filter {it.result!=AttemptResult.UNVERIFIED && it.result!=AttemptResult.NO_BRAKE}
        val signalOk=signal.count {it.success}
        // 회전 전 감속: 우회전 시도 중 브레이크 값을 확인할 수 있었던 것만
        val turnChecks=attempts.filter {it.questId==QuestIds.INTERSECTION_RIGHT && it.braked!=null && it.result!=AttemptResult.UNVERIFIED}
        val turnMisses=turnChecks.count {it.braked==false}
        val scores=mapOf(
            DrivingSkill.SMOOTH_ACCEL to if(measured)(100-accel.sumOf {it.severity}*PENALTY_PER_SEVERITY).coerceAtLeast(0) else null,
            DrivingSkill.BRAKING to if(measured)(100-brake.sumOf {it.severity}*PENALTY_PER_SEVERITY).coerceAtLeast(0) else null,
            DrivingSkill.TURN_SIGNAL to if(signal.isEmpty())null else (signalOk*100.0/signal.size).roundToInt(),
            DrivingSkill.TURN_SLOWDOWN to if(turnChecks.isEmpty())null else ((turnChecks.size-turnMisses)*100.0/turnChecks.size).roundToInt()
        )
        return TripAnalysis(events,scores,measuredSeconds,distanceKm,
            lowConfidence=measuredSeconds<LOW_CONFIDENCE_SECONDS || distanceKm<LOW_CONFIDENCE_KM,
            harshAccelCount=accel.size,harshBrakeCount=brake.size,
            signalAttempts=signal.size,signalSuccesses=signalOk,turnBrakeChecks=turnChecks.size,turnBrakeMisses=turnMisses)
    }

    /** 1초 속도 변화(km/h)의 크기로 1~3단계. 감속은 팀 기준(−12)에서 시작한다. */
    fun severity(type: String,delta: Double): Int {
        val size=abs(delta)
        return if(type=="HARSH_BRAKE") when {size>=18->3;size>=15->2;else->1}
        else when {size>=16->3;size>=13->2;else->1}
    }

    /** 같은 종류가 3초 안에 이어지면 한 번으로 보고 가장 심한 값을 남긴다. */
    fun merge(events: List<DrivingEvent>): List<DrivingEvent> {
        val out=mutableListOf<DrivingEvent>()
        events.sortedBy {it.t}.forEach {e->
            val last=out.lastOrNull()
            val sameKind=last!=null && (last.type=="HARSH_BRAKE")==(e.type=="HARSH_BRAKE")
            if(last!=null && sameKind && e.t-last.t<=MERGE_WINDOW_MS) {
                out[out.lastIndex]=if(e.severity>last.severity)e.copy(t=last.t,type=last.type) else last
            }else out+=e
        }
        return out
    }
}
