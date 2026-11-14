package com.hmgchallenge.pleosstarter.driving

import kotlin.math.*

enum class ScenarioSpeed { REPLAY, VEHICLE }
enum class RoadLesson(val title: String) {
    COMBINED("한 차로 변경 → 우회전"), LANE("오른쪽 한 차로 변경"), TURN("3차로에서 우회전"), PARKING("후진 주차 · 가상 연습장")
}

/** Metres along an OSM road centreline. Lane/position are authored, even with SDK speed. */
class SimpleRoadScenario(roadCourse: NavigationSimulationRoute, val lesson: RoadLesson = RoadLesson.COMBINED,
                         val source: ScenarioSpeed = ScenarioSpeed.REPLAY) {
    init {require(lesson!=RoadLesson.PARKING || source==ScenarioSpeed.REPLAY)}
    val course=if(lesson==RoadLesson.PARKING)ParkingLessonGeometry.course() else roadCourse.rounded()
    val startDistance = if (lesson == RoadLesson.TURN) course.turnDistance - 25.0 else 0.0
    val endDistance = if (lesson == RoadLesson.LANE) 48.0 else course.length
    var distance = startDistance; private set
    var indicator = Indicator.UNKNOWN
    var started = false; private set
    var speedMps = 0.0; private set
    var waitingForSpeed = false; private set
    var parkingWaitMs=0L;private set
    val complete get() = started && distance >= endDistance
    val laneDone get() = lesson in setOf(RoadLesson.COMBINED,RoadLesson.LANE) && distance >= 42.0
    val currentLane get() = if (lesson == RoadLesson.TURN || laneDone) 3 else 2
    val turning get() = lesson in setOf(RoadLesson.COMBINED,RoadLesson.TURN) && distance >= course.turnDistance && !complete
    val parkingGear get()=if(complete || !started)"P" else if(distance<5)"D" else "R"
    val progress get() = (distance-startDistance)/(endDistance-startDistance)
    fun start() { started = true }
    fun advance(elapsedMs: Long, vehicleMps: Double?, fresh: Boolean) {
        if(lesson==RoadLesson.PARKING && started && distance>=5 && parkingWaitMs<5000) {
            if(elapsedMs in 1..1000)parkingWaitMs+=elapsedMs
            speedMps=0.0;return
        }
        speedMps = when {
            !started || complete -> 0.0
            lesson==RoadLesson.PARKING -> if(distance<5)3.0/3.6 else 2.0/3.6
            source == ScenarioSpeed.REPLAY -> if (distance >= course.turnDistance-8) 6.0/3.6 else 8.0/3.6
            fresh && vehicleMps != null && vehicleMps.isFinite() && vehicleMps >= 0 -> vehicleMps
            else -> 0.0
        }
        waitingForSpeed = started && !complete && source == ScenarioSpeed.VEHICLE &&
            (!fresh || vehicleMps == null || !vehicleMps.isFinite() || vehicleMps < 0)
        // Do not extrapolate over a lifecycle interruption or a stalled UI thread.
        if (elapsedMs in 1..1000) {
            val next=(distance + speedMps*elapsedMs/1000.0).coerceAtMost(endDistance)
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
        lesson != RoadLesson.TURN && distance < 12 -> "오른쪽 깜빡이를 켜고 차로 변경을 준비하세요"
        lesson != RoadLesson.TURN && !laneDone -> "미러·사각지대 확인 후 오른쪽 한 차로 이동하세요"
        lesson == RoadLesson.LANE -> "좋아요! 3차로에 맞춰 직진하세요"
        turning -> "3차로에서 천천히 우회전하세요"
        else -> "3차로 유지 · 전방과 횡단보도를 확인하세요"
    }
    private fun laneOffset(d: Double): Double {
        if (lesson in setOf(RoadLesson.TURN,RoadLesson.PARKING) || d >= 42) return 0.0
        val t = ((d-12)/30).coerceIn(0.0,1.0)
        return -3.5*(1-t*t*t*(10-15*t+6*t*t))
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
