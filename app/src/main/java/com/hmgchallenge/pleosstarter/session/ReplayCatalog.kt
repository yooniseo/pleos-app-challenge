package com.hmgchallenge.pleosstarter.session

import com.hmgchallenge.pleosstarter.driving.RoadLesson

data class ReplayDefinition(val id: String,val title: String,val lesson: RoadLesson,val expectedSkills: Set<String>)

/** Optional demonstration inputs. Screens, SDK adapters and Room do not depend on these four records. */
object ReplayCatalog {
    val all=listOf(
        ReplayDefinition("road-combined-v1","한 차로 변경 → 우회전",RoadLesson.COMBINED,setOf("LANE_CHANGE","INTERSECTION")),
        ReplayDefinition("road-lane-v1","오른쪽 한 차로 변경",RoadLesson.LANE,setOf("LANE_CHANGE")),
        ReplayDefinition("road-turn-v1","3차로에서 우회전",RoadLesson.TURN,setOf("INTERSECTION")),
        ReplayDefinition("parking-reverse-v1","후진 주차 · 가상 연습장",RoadLesson.PARKING,setOf("PARKING"))
    )
}
