package com.hmgchallenge.pleosstarter.analysis

import com.hmgchallenge.pleosstarter.feature.quests.QuestIds
import kotlin.math.max
import kotlin.math.roundToInt

/** 리포트 1개에서 읽은 점수. 최신 주행이 앞에 온다. */
data class TripScores(val tripId: String,val startedAt: Long,val scores: Map<DrivingSkill,Int?>,val lowConfidence: Boolean,val laneSignals: List<String> = emptyList())

/** 차량 신호 퀘스트(팀 QuestBoard)의 현재 진행. */
data class VehicleProgress(val level: Int,val steps: Int,val target: Int)

enum class RecommendationKind(val label: String) {
    AUTO("다음 주행에서 자동 확인"),
    VEHICLE("차량 신호 퀘스트"),
    PRACTICE("연습 장소에서 직접")
}

data class Weakness(val key: String,val title: String,val average: Int?,val belowCount: Int,val scoredTrips: Int,
                    val noteCount: Int,val priority: Double,val reason: String)

data class Recommendation(val id: String,val weakness: String,val title: String,val goal: String,val reason: String,
                          val kind: RecommendationKind,val progress: Int,val target: Int,val vehicleQuestId: String?=null) {
    val done get()=target>0 && progress>=target
}

data class RecommendationResult(val weaknesses: List<Weakness>,val recommendations: List<Recommendation>,
                                val averages: Map<DrivingSkill,Int?>,val tripCount: Int)

/**
 * 최근 주행 리포트·메모로 반복되는 약점을 찾고, 기준에 맞는 퀘스트를 최대 3개 고른다(계획서 C-1, C-2).
 *
 * 약점 기준 (하나라도 해당하면):
 *  - 가중 평균 점수 < 70 (최근 주행일수록 가중치 큼, 짧은 주행은 절반)
 *  - 최근 5회 중 3회 이상 70점 미만 (반복)
 *  - 같은 분류의 "어려웠어요" 메모 2건 이상 (운전자 본인이 어렵다고 느낌)
 * 점수가 있는 주행이 3회 미만이면 오판을 줄이려고 평균 < 60일 때만 점수 약점으로 본다.
 * 우선순위 = (70 − 평균) + 메모 수 × 10 + 반복 횟수 × 5
 */
object QuestRecommender {
    const val WEAK_SCORE=70
    const val FEW_TRIPS_WEAK_SCORE=60
    const val MIN_TRIPS_FOR_AVERAGE=3
    const val REPEAT_COUNT=3
    const val NOTE_COUNT=2
    const val MAX_RECOMMENDATIONS=3
    const val RECENT_TRIPS=5
    private val RECENCY_WEIGHTS=listOf(1.0,.8,.6,.5,.4)

    /** 메모 분류 → 관련 기술. 기술이 없는 분류는 메모만으로 약점이 된다. */
    private val NOTE_SKILL=mapOf("LANE_CHANGE" to DrivingSkill.TURN_SIGNAL,"RIGHT_TURN" to DrivingSkill.TURN_SLOWDOWN,"SPEED_CONTROL" to DrivingSkill.BRAKING)
    private val NOTE_TITLE=mapOf("LEFT_TURN" to "좌회전","U_TURN" to "유턴","PARKING" to "주차")

    fun recommend(recentTrips: List<TripScores>,noteCategories: List<String>,
                  vehicle: Map<String,VehicleProgress> = emptyMap()): RecommendationResult {
        val trips=recentTrips.sortedByDescending {it.startedAt}.take(RECENT_TRIPS)
        val notes=noteCategories.groupingBy {it}.eachCount()
        val averages=mutableMapOf<DrivingSkill,Int?>()
        val weaknesses=mutableListOf<Weakness>()
        DrivingSkill.values().forEach {skill->
            val scored=trips.mapIndexedNotNull {i,t->t.scores[skill]?.let {Triple(it,RECENCY_WEIGHTS[i]*(if(t.lowConfidence).5 else 1.0),t)}}
            val average=if(scored.isEmpty())null else (scored.sumOf {it.first*it.second}/scored.sumOf {it.second}).roundToInt()
            averages[skill]=average
            val below=scored.count {it.first<WEAK_SCORE}
            val noteCount=NOTE_SKILL.filterValues {it==skill}.keys.sumOf {notes[it] ?: 0}
            val threshold=if(scored.size>=MIN_TRIPS_FOR_AVERAGE)WEAK_SCORE else FEW_TRIPS_WEAK_SCORE
            val lowAverage=average!=null && average<threshold
            val repeated=below>=REPEAT_COUNT
            val felt=noteCount>=NOTE_COUNT
            if(lowAverage || repeated || felt) {
                val parts=mutableListOf<String>()
                if(average!=null)parts+="최근 ${scored.size}번 평균 ${average}점"
                if(repeated)parts+="${scored.size}번 중 ${below}번 70점 미만"
                if(noteCount>0)parts+="어려움 메모 ${noteCount}건"
                if(scored.size in 1 until MIN_TRIPS_FOR_AVERAGE)parts+="아직 기록이 적어요"
                weaknesses+=Weakness(skill.name,skill.title,average,below,scored.size,noteCount,
                    max(0,WEAK_SCORE-(average ?: WEAK_SCORE)).toDouble()+noteCount*10+below*5,parts.joinToString(" · "))
            }
        }
        NOTE_TITLE.forEach {(category,title)->
            val count=notes[category] ?: 0
            if(count>=NOTE_COUNT)weaknesses+=Weakness(category,title,null,0,0,count,count*10.0,"'$title' 어려움 메모 ${count}건")
        }
        val ordered=weaknesses.sortedByDescending {it.priority}
        val recommendations=ordered.mapNotNull {quest(it,trips,vehicle)}.take(MAX_RECOMMENDATIONS)
        return RecommendationResult(ordered,recommendations,averages,trips.size)
    }

    private fun quest(w: Weakness,trips: List<TripScores>,vehicle: Map<String,VehicleProgress>): Recommendation? = when(w.key) {
        DrivingSkill.SMOOTH_ACCEL.name->auto(w,"R_SMOOTH_START","3초 세며 천천히 출발하기","급출발·급가속 없는 주행 2번 연속",trips,DrivingSkill.SMOOTH_ACCEL,100,2)
        DrivingSkill.BRAKING.name->auto(w,"R_BRAKE_DISTANCE","여유 있게 속도 줄이기","급감속 1번 이하인 주행 3번 연속",trips,DrivingSkill.BRAKING,92,3)
        DrivingSkill.TURN_SIGNAL.name->vehicleQuest(w,QuestIds.LANE_CHANGE,"차선 변경 · 깜빡이 먼저","바꾸려는 쪽 깜빡이를 켜고 잠시 뒤 핸들 돌리기",vehicle)
        DrivingSkill.TURN_SLOWDOWN.name->vehicleQuest(w,QuestIds.INTERSECTION_RIGHT,"교차로 우회전 · 감속 먼저","오른쪽 깜빡이 → 브레이크로 감속 → 우회전",vehicle)
        "LEFT_TURN"->Recommendation("R_LEFT_PRACTICE",w.key,"한산한 시간에 좌회전 연습","좌회전 교차로를 한산한 시간에 3번 지나 보기",w.reason,RecommendationKind.PRACTICE,0,0)
        "U_TURN"->Recommendation("R_UTURN_PRACTICE",w.key,"넓은 길에서 유턴 연습","유턴 구역이 넓은 곳에서 천천히 2번 해 보기",w.reason,RecommendationKind.PRACTICE,0,0)
        "PARKING"->Recommendation("R_PARK_PRACTICE",w.key,"넓은 주차장에서 후면 주차","주차 가이드 시연을 본 뒤 한산한 주차장에서 5번",w.reason,RecommendationKind.PRACTICE,0,0)
        else->null
    }

    /** 최근 주행부터 거꾸로 세어 기준을 연속으로 만족한 횟수 = 진행도. 기록이 쌓이면 자동으로 올라간다. */
    private fun auto(w: Weakness,id: String,title: String,goal: String,trips: List<TripScores>,skill: DrivingSkill,pass: Int,target: Int): Recommendation {
        val streak=trips.map {it.scores[skill]}.takeWhile {it!=null && it>=pass}.size
        return Recommendation(id,w.key,title,goal,w.reason,RecommendationKind.AUTO,streak.coerceAtMost(target),target)
    }

    private fun vehicleQuest(w: Weakness,questId: String,title: String,goal: String,vehicle: Map<String,VehicleProgress>): Recommendation {
        val p=vehicle[questId]
        return Recommendation("R_$questId",w.key,title,goal,w.reason,RecommendationKind.VEHICLE,p?.steps ?: 0,p?.target ?: 30,questId)
    }
}
