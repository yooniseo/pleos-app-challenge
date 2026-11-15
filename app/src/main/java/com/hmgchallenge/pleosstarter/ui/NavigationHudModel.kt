package com.hmgchallenge.pleosstarter.ui

import com.hmgchallenge.pleosstarter.driving.*
import kotlin.math.*

enum class HudManeuver { LANE, TURN, PARKING, STRAIGHT, FINISH }
data class HudCue(val maneuver: HudManeuver,val metres: Double?,val label: String)

/** The navigation cards project the authored route; no sample distance, traffic or battery is invented. */
data class NavigationHudModel(val cues: List<HudCue>,val remainingMetres: Double,val progress: Float,val remainingSeconds: Int?) {
    companion object {
        fun from(s: SimpleRoadScenario,totalMetres: Double,travelledMetres: Double,parkingNext: Boolean): NavigationHudModel {
            val remaining=(totalMetres-travelledMetres).coerceAtLeast(0.0)
            val cues=buildList {
                when {
                    s.complete->add(HudCue(HudManeuver.FINISH,0.0,"연습 완료"))
                    s.lesson==RoadLesson.PARKING->add(HudCue(HudManeuver.PARKING,s.endDistance-s.distance,"후진 주차 · 정렬"))
                    s.nextLaneChange!=null->{
                        val lane=s.nextLaneChange!!
                        add(HudCue(HudManeuver.LANE,((if(s.changingLane)lane.end else lane.start)-s.distance).coerceAtLeast(0.0),"창룡대로 · ${lane.to}차로"))
                        if(s.lesson==RoadLesson.COMBINED)add(HudCue(HudManeuver.TURN,s.frame().turnRemaining,"대학로 · 우회전"))
                    }
                    s.lesson==RoadLesson.LANE->add(HudCue(HudManeuver.STRAIGHT,s.endDistance-s.distance,"3차로 정렬"))
                    s.distance>=s.course.turnDistance+80->{
                        add(HudCue(HudManeuver.STRAIGHT,s.endDistance-s.distance,"대학로 · 경기대 방향"))
                        if(parkingNext)add(HudCue(HudManeuver.PARKING,null,"다음 · 주차 가이드"))
                    }
                    else->{
                        add(HudCue(HudManeuver.TURN,s.frame().turnRemaining,"대학로 · 우회전"))
                        if(parkingNext)add(HudCue(HudManeuver.PARKING,null,"다음 · 주차 가이드"))
                    }
                }
            }
            val seconds=when {
                s.complete->0
                s.source==ScenarioSpeed.VEHICLE->if(s.waitingForSpeed || s.speedMps<=.1)null else ceil(remaining/s.speedMps).toInt()
                else->ceil(replaySeconds(s)+(if(parkingNext)parkingSeconds(0.0,0L) else 0.0)).toInt()
            }
            return NavigationHudModel(cues,remaining,if(totalMetres<=0)0f else (travelledMetres/totalMetres).toFloat().coerceIn(0f,1f),seconds)
        }
        private fun replaySeconds(s: SimpleRoadScenario): Double {
            if(s.lesson==RoadLesson.PARKING)return parkingSeconds(s.distance,s.parkingWaitMs)
            var distance=s.distance;var seconds=0.0
            while(distance<s.endDistance) {
                val step=min(2.0,s.endDistance-distance)
                val kmh=s.roadReplayTargetKmh(distance)
                seconds+=step/(kmh/3.6);distance+=step
            }
            return seconds
        }
        private fun parkingSeconds(distance: Double,waited: Long): Double {
            val length=parkingLength
            return (5-distance).coerceAtLeast(0.0)/(3.0/3.6)+(length-max(5.0,distance)).coerceAtLeast(0.0)/(2.0/3.6)+(5000-waited).coerceAtLeast(0L)/1000.0
        }
        private val parkingLength by lazy {ParkingLessonGeometry.course().length}
    }
}
