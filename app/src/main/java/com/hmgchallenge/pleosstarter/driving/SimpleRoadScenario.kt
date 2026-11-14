package com.hmgchallenge.pleosstarter.driving

import kotlin.math.*

enum class ScenarioSpeed { REPLAY, VEHICLE }
enum class RoadLesson(val title: String) {
    COMBINED("1→2→3차로 · 우회전 · 주차"), LANE("오른쪽 한 차로 변경"), TURN("3차로에서 우회전"), PARKING("후진 주차 · 가상 연습장")
}

/** Metres along an OSM road centreline. Lane/position are authored, even with SDK speed. */
class SimpleRoadScenario(roadCourse: NavigationSimulationRoute, val lesson: RoadLesson = RoadLesson.COMBINED,
                         val source: ScenarioSpeed = ScenarioSpeed.REPLAY) {
    init {require(lesson!=RoadLesson.PARKING || source==ScenarioSpeed.REPLAY)}
    val course=if(lesson==RoadLesson.PARKING)ParkingLessonGeometry.course() else roadCourse.drivingCourse()
    data class LaneChange(val from: Int,val to: Int,val start: Double,val end: Double)
    private val changeStart=minOf(90.0,course.turnDistance*.16)
    private val changeLength=minOf(80.0,course.turnDistance*.14)
    private val changeGap=minOf(100.0,course.turnDistance*.18)
    val laneChanges=when(lesson) {
        RoadLesson.COMBINED->listOf(LaneChange(1,2,changeStart,changeStart+changeLength),
            LaneChange(2,3,changeStart+changeLength+changeGap,changeStart+2*changeLength+changeGap))
        RoadLesson.LANE->listOf(LaneChange(2,3,changeStart,changeStart+changeLength))
        else->emptyList()
    }
    val startDistance = if(lesson==RoadLesson.TURN)(course.turnDistance-160).coerceAtLeast(0.0) else 0.0
    val endDistance = if(lesson==RoadLesson.LANE)laneChanges.last().end+minOf(40.0,course.turnDistance*.1) else course.length
    var distance = startDistance; private set
    var turnEntryKmh: Double?=null
        private set
    private val laneStartSignals=mutableMapOf<Int,Indicator>()
    fun laneStartSignal(index: Int)=laneStartSignals[index]
    fun replaySignal(): Indicator {
        val lead=(speedMps*4).coerceIn(18.0,50.0)
        val lane=nextLaneChange
        val signalLane=lane!=null && !(lesson==RoadLesson.COMBINED && completedLaneChanges==0) &&
            distance in (lane.start-lead)..lane.end
        val signalTurn=(lesson==RoadLesson.TURN || lesson==RoadLesson.COMBINED) &&
            distance in (course.turnDistance-lead)..(course.turnDistance+54)
        return if(signalLane || signalTurn)Indicator.RIGHT else Indicator.OFF
    }
    var indicator = Indicator.UNKNOWN
    var started = false; private set
    var speedMps = 0.0; private set
    var waitingForSpeed = false; private set
    var parkingWaitMs=0L;private set
    val complete get() = started && distance >= endDistance
    val completedLaneChanges get() = laneChanges.count {distance>=it.end}
    val laneDone get() = laneChanges.isNotEmpty() && completedLaneChanges==laneChanges.size
    val currentLane get() = laneChanges.getOrNull(completedLaneChanges)?.from ?: 3
    val nextLaneChange get() = laneChanges.getOrNull(completedLaneChanges)
    val changingLane get() = nextLaneChange?.let {distance>=it.start}==true
    val laneChangeProgress get() = nextLaneChange?.let {((distance-it.start)/(it.end-it.start)).coerceIn(0.0,1.0)} ?: 1.0
    val turning get() = lesson in setOf(RoadLesson.COMBINED,RoadLesson.TURN) && distance >= course.turnDistance && !complete
    val parkingGear get()=if(complete || !started)"P" else if(distance<5)"D" else "R"
    val progress get() = (distance-startDistance)/(endDistance-startDistance)
    fun start() { started = true }
    fun advance(elapsedMs: Long, vehicleMps: Double?, fresh: Boolean, signalOverride: Indicator?=null) {
        if(lesson==RoadLesson.PARKING && started && distance>=5 && parkingWaitMs<5000) {
            if(elapsedMs in 1..1000)parkingWaitMs+=elapsedMs
            speedMps=0.0;return
        }
        speedMps = when {
            !started || complete -> 0.0
            lesson==RoadLesson.PARKING -> if(distance<5)3.0/3.6 else 2.0/3.6
            source == ScenarioSpeed.REPLAY -> {
                val target=when {
                    lesson==RoadLesson.LANE->40.0/3.6
                    distance>=course.turnDistance+80->20.0/3.6
                    distance>=course.turnDistance-30->10.0/3.6
                    distance>=course.turnDistance-140->20.0/3.6
                    else->40.0/3.6
                }
                val dt=elapsedMs.coerceIn(0,1000)/1000.0
                target.coerceIn((speedMps-2*dt).coerceAtLeast(0.0),speedMps+1.5*dt)
            }
            fresh && vehicleMps != null && vehicleMps.isFinite() && vehicleMps >= 0 -> vehicleMps
            else -> 0.0
        }
        waitingForSpeed = started && !complete && source == ScenarioSpeed.VEHICLE &&
            (!fresh || vehicleMps == null || !vehicleMps.isFinite() || vehicleMps < 0)
        // Do not extrapolate over a lifecycle interruption or a stalled UI thread.
        if (elapsedMs in 1..1000) {
            val next=(distance + speedMps*elapsedMs/1000.0).coerceAtMost(endDistance)
            if(source==ScenarioSpeed.REPLAY && lesson in setOf(RoadLesson.COMBINED,RoadLesson.TURN) &&
                distance<course.turnDistance && next>=course.turnDistance)turnEntryKmh=speedMps*3.6
            if(source==ScenarioSpeed.REPLAY)nextLaneChange?.let {lane->
                if(distance<lane.start && next>=lane.start)laneStartSignals[completedLaneChanges]=signalOverride ?: replaySignal()
            }
            distance=if(lesson==RoadLesson.PARKING && distance<5 && next>=5)5.0 else next
        }
        if (complete) speedMps = 0.0
    }
    fun cue(): String = when {
        complete -> "좋아요! ${lesson.title} 연습을 마쳤어요"
        !started -> "${lesson.title} 연습을 시작해요"
        lesson==RoadLesson.PARKING && distance<5 -> "주차칸 옆에 천천히 위치를 맞추세요"
        lesson==RoadLesson.PARKING && parkingWaitMs<5000 -> "정차 · 뒤쪽과 양옆을 확인하는 단계예요"
        lesson==RoadLesson.PARKING && distance<endDistance-3 -> "R단 연습 · 핸들을 돌려 천천히 후진하세요"
        lesson==RoadLesson.PARKING -> "핸들을 가운데로 풀고 주차칸에 정렬하세요"
        waitingForSpeed -> "차량 속도 수신을 기다리고 있어요"
        source == ScenarioSpeed.VEHICLE && speedMps == 0.0 -> "정차 중 · 속도가 들어오면 경로가 진행돼요"
        nextLaneChange!=null && !changingLane -> "오른쪽 깜빡이를 켜고 차로 변경을 준비하세요"
        lesson != RoadLesson.TURN && !laneDone -> "미러·사각지대 확인 후 오른쪽 한 차로 이동하세요"
        lesson == RoadLesson.LANE -> "좋아요! 3차로에 맞춰 직진하세요"
        turning -> "3차로에서 천천히 우회전하세요"
        else -> "3차로 유지 · 전방과 횡단보도를 확인하세요"
    }
    private fun laneOffset(d: Double): Double {
        if(lesson==RoadLesson.PARKING)return 0.0
        fun ease(t: Double): Double {val x=t.coerceIn(0.0,1.0);return x*x*x*(10-15*x+6*x*x)}
        val initial=if(lesson==RoadLesson.COMBINED)-7.0 else if(lesson==RoadLesson.LANE)-3.5 else 0.0
        return initial+laneChanges.sumOf {3.5*ease((d-it.start)/(it.end-it.start))}
    }
    fun frame(): NavigationSimulationFrame {
        fun point(d: Double) = NavigationSimulationRoute.offset(course.at(d),course.bearing(d),laneOffset(d))
        val planned = mutableListOf(point(distance))
        var d = distance+2
        while(d<endDistance) { planned.add(point(d));d+=2 }
        planned.add(point(endDistance))
        return NavigationSimulationFrame(point(distance),vehicleHeading(distance),distance,
            (course.turnDistance-distance).coerceAtLeast(0.0),planned)
    }
    private fun vehicleHeading(d: Double): Double {
        val lower=if(lesson==RoadLesson.PARKING && d>=5)5.05 else 0.0
        val upper=if(lesson==RoadLesson.PARKING && d<5)4.95 else endDistance
        val a=NavigationSimulationRoute.offset(course.at((d-.5).coerceIn(lower,upper)),course.bearing(d),laneOffset(d-.5))
        val b=NavigationSimulationRoute.offset(course.at((d+.5).coerceIn(lower,upper)),course.bearing(d),laneOffset(d+.5))
        val motion=Math.toDegrees(atan2((b.longitude-a.longitude)*cos(Math.toRadians(a.latitude)),b.latitude-a.latitude))
        return motion+if(lesson==RoadLesson.PARKING && d>=5)180 else 0
    }
    val parkingCountersteerDistance get() = 5+(endDistance-8)/2
    /** Illustrative bicycle-model wheel animation: assumed 2.7m/14.5 ratio, never calibrated vehicle steering. */
    val steeringTarget: Double get() = if(!started || complete || (lesson==RoadLesson.PARKING && parkingWaitMs<5000 && distance>=5))0.0 else steeringAt(distance)
    val steeringInstructions by lazy {SteeringCoachingTimeline(endDistance,lesson==RoadLesson.PARKING,::steeringAt)}
    private fun steeringAt(distance: Double): Double {
        val span=if(lesson==RoadLesson.PARKING).35 else .5
        val a=vehicleHeading((distance-span).coerceAtLeast(if(lesson==RoadLesson.PARKING && distance>=5)5.1 else 0.0))
        val b=vehicleHeading((distance+span).coerceAtMost(if(lesson==RoadLesson.PARKING && distance<5)4.9 else endDistance))
        val delta=(b-a+540)%360-180
        val reverse=if(lesson==RoadLesson.PARKING && distance>=5)-1 else 1
        return (Math.toDegrees(atan(2.7*Math.toRadians(delta)/(2*span)))*14.5*reverse).coerceIn(-360.0,360.0)
    }
}

/** Clearly virtual parking pad; not a claimed real-world parking space or vehicle calibration. */
object ParkingLessonGeometry {
    val anchor=GeoPoint(37.297553,127.041595)
    fun point(x: Double,y: Double)=GeoPoint(anchor.latitude+y/111320,anchor.longitude+x/(111320*cos(Math.toRadians(anchor.latitude))))
    fun course(): NavigationSimulationRoute {
        val points=mutableListOf(point(0.0,0.0),point(0.0,5.0))
        for(i in 1..40) {
            val t=i/40.0;val u=1-t
            points.add(point(18*u*t*t+6*t*t*t,5*u*u*u-3*u*u*t-3*u*t*t-7*t*t*t))
        }
        points.add(point(6.0,-10.0)) // Straight final alignment: unwind the wheel before stopping.
        return NavigationSimulationRoute(points)
    }
}
