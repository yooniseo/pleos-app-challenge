package com.hmgchallenge.pleosstarter.analysis

import com.hmgchallenge.pleosstarter.feature.quests.QuestIds
import org.junit.Assert.*
import org.junit.Test

class QuestRecommenderTest {
    private fun trip(i: Int,accel: Int?=100,brake: Int?=100,signal: Int?=null,slow: Int?=null,low: Boolean=false)=
        TripScores("t$i",10_000L-i,mapOf(DrivingSkill.SMOOTH_ACCEL to accel,DrivingSkill.BRAKING to brake,
            DrivingSkill.TURN_SIGNAL to signal,DrivingSkill.TURN_SLOWDOWN to slow),low)

    @Test fun noDataNoRecommendation() {
        val r=QuestRecommender.recommend(emptyList(),emptyList())
        assertTrue(r.recommendations.isEmpty());assertEquals(0,r.tripCount)
    }

    @Test fun lowAverageOverThreeTripsRecommendsBrakeQuest() {
        val r=QuestRecommender.recommend(listOf(trip(0,brake=60),trip(1,brake=52),trip(2,brake=76)),emptyList())
        val q=r.recommendations.single()
        assertEquals("R_BRAKE_DISTANCE",q.id);assertEquals(RecommendationKind.AUTO,q.kind)
        assertTrue(q.reason,q.reason.startsWith("최근 3번 평균"))
        assertEquals(0,q.progress);assertEquals(3,q.target)
    }

    @Test fun fewTripsUseStricterThreshold() {
        assertTrue("1~2번이면 65점은 아직 약점 아님",QuestRecommender.recommend(listOf(trip(0,accel=65)),emptyList()).recommendations.isEmpty())
        val r=QuestRecommender.recommend(listOf(trip(0,accel=52)),emptyList())
        assertEquals("R_SMOOTH_START",r.recommendations.single().id)
        assertTrue(r.recommendations.single().reason.contains("아직 기록이 적어요"))
    }

    @Test fun repeatedBelowSeventyCountsEvenIfAverageRecovers() {
        // 가장 최근 두 번은 좋아서 가중 평균은 70 이상이지만, 5번 중 3번이 70 미만이면 반복 약점
        val r=QuestRecommender.recommend(listOf(trip(0,accel=100),trip(1,accel=100),trip(2,accel=68),trip(3,accel=66),trip(4,accel=60)),emptyList())
        val w=r.weaknesses.single {it.key=="SMOOTH_ACCEL"}
        assertTrue(w.average!!>=70);assertEquals(3,w.belowCount)
        assertEquals("최근 2번 연속 달성",2,r.recommendations.single().progress)
        assertTrue(r.recommendations.single().done)
    }

    @Test fun notesAloneCreateWeaknessAndMapToVehicleQuest() {
        val vehicle=mapOf(QuestIds.LANE_CHANGE to VehicleProgress(1,4,30))
        val r=QuestRecommender.recommend(listOf(trip(0)),listOf("LANE_CHANGE","LANE_CHANGE","PARKING","PARKING","OTHER"),vehicle)
        val ids=r.recommendations.map {it.id}
        assertTrue(ids.contains("R_${QuestIds.LANE_CHANGE}"));assertTrue(ids.contains("R_PARK_PRACTICE"))
        val lane=r.recommendations.first {it.vehicleQuestId==QuestIds.LANE_CHANGE}
        assertEquals(4,lane.progress);assertEquals(30,lane.target)
    }

    @Test fun atMostThreeOrderedByPriority() {
        val trips=listOf(trip(0,accel=40,brake=60,signal=50,slow=30),trip(1,accel=40,brake=60,signal=50,slow=30),trip(2,accel=40,brake=60,signal=50,slow=30))
        val r=QuestRecommender.recommend(trips,listOf("U_TURN","U_TURN"))
        assertEquals(3,r.recommendations.size)
        assertEquals(listOf("TURN_SLOWDOWN","SMOOTH_ACCEL","TURN_SIGNAL"),r.recommendations.map {it.weakness})
        assertTrue(r.weaknesses.size>3)
    }

    @Test fun shortTripsWeighLess() {
        // 최근 짧은 주행(40점, 가중치 절반)보다 이전 정상 주행의 영향이 커서 평균이 70을 넘는다
        val r=QuestRecommender.recommend(listOf(trip(0,brake=40,low=true),trip(1,brake=90),trip(2,brake=90)),emptyList())
        assertTrue(r.averages[DrivingSkill.BRAKING]!!>=70)
        assertTrue(r.recommendations.none {it.weakness=="BRAKING"})
    }
    @Test fun unmeasuredTripBreaksConsecutiveSuccessAndDoesNotBecomeAZero() {
        val r=QuestRecommender.recommend(listOf(trip(0,accel=100),trip(1,accel=null),trip(2,accel=100)),listOf("SPEED_CONTROL","SPEED_CONTROL"))
        assertEquals(100,r.averages[DrivingSkill.SMOOTH_ACCEL])
        val weak=QuestRecommender.recommend(listOf(trip(0,brake=100),trip(1,brake=null),trip(2,brake=100)),listOf("SPEED_CONTROL","SPEED_CONTROL"))
        assertEquals(1,weak.recommendations.single().progress)
    }
}
