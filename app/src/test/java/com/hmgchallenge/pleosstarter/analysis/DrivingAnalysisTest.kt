package com.hmgchallenge.pleosstarter.analysis

import com.hmgchallenge.pleosstarter.core.data.SpeedReview
import com.hmgchallenge.pleosstarter.feature.quests.Attempt
import com.hmgchallenge.pleosstarter.feature.quests.AttemptResult
import com.hmgchallenge.pleosstarter.feature.quests.Direction
import com.hmgchallenge.pleosstarter.feature.quests.QuestIds
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class DrivingAnalysisTest {
    /** 1초 간격 속도 열(km/h)을 샘플로 만든다. */
    private fun trip(vararg kmh: Double?)=kmh.mapIndexed {i,v->SpeedReview.Sample(1_000L*(i+1),v)}
    private fun ramp(from: Double,to: Double,step: Double)=generateSequence(from) {it+step}.takeWhile {if(step>0)it<=to else it>=to}.toList()
    private fun attempt(quest: String,result: AttemptResult,braked: Boolean?=null)=
        Attempt(quest,0L,result,Direction.RIGHT,20f,if(result==AttemptResult.NO_SIGNAL)null else 2000L,braked)

    @Test fun smoothTripScoresFullMarks() {
        val speeds=ramp(0.0,60.0,3.0)+List(150){60.0}+ramp(60.0,0.0,-5.0)
        val a=DrivingAnalyzer.analyze(trip(*speeds.toTypedArray()))
        assertTrue(a.events.isEmpty())
        assertEquals(100,a.scores[DrivingSkill.SMOOTH_ACCEL]);assertEquals(100,a.scores[DrivingSkill.BRAKING])
        assertNull("신호 시도가 없으면 측정 안 함",a.scores[DrivingSkill.TURN_SIGNAL])
        assertFalse(a.lowConfidence)
        assertEquals("가속·정속·감속 구간 사다리꼴 합",10020.0/3600,a.distanceKm,0.01)
    }

    @Test fun harshEventsGetSeverityAndMerge() {
        // 0→15 급출발(2단계), 50→36 급감속(1단계), 이어서 2초 뒤 36→17 급감속(3단계)은 하나로 합쳐짐
        val speeds=listOf(0.0,15.0)+ramp(18.0,50.0,2.0)+List(40){50.0}+listOf(36.0,30.0,11.0)+List(40){11.0}
        val a=DrivingAnalyzer.analyze(trip(*speeds.toTypedArray()))
        assertEquals(listOf("HARSH_START","HARSH_BRAKE"),a.events.map {it.type})
        assertEquals(2,a.events[0].severity)
        assertEquals("병합 후 가장 심한 단계",3,a.events[1].severity)
        assertEquals(100-2*8,a.scores[DrivingSkill.SMOOTH_ACCEL])
        assertEquals(100-3*8,a.scores[DrivingSkill.BRAKING])
        assertEquals(1,a.harshAccelCount);assertEquals(1,a.harshBrakeCount)
    }

    @Test fun shortOrMissingDataGivesNoSpeedScore() {
        val a=DrivingAnalyzer.analyze(trip(0.0,null,10.0,20.0,null))
        assertNull(a.scores[DrivingSkill.SMOOTH_ACCEL]);assertNull(a.scores[DrivingSkill.BRAKING])
        assertTrue(a.lowConfidence)
    }

    @Test fun signalAndTurnSlowdownComeFromVehicleAttempts() {
        val attempts=listOf(
            attempt(QuestIds.LANE_CHANGE,AttemptResult.SUCCESS),
            attempt(QuestIds.LANE_CHANGE,AttemptResult.NO_SIGNAL),
            attempt(QuestIds.INTERSECTION_RIGHT,AttemptResult.NO_BRAKE,braked=false),
            attempt(QuestIds.INTERSECTION_RIGHT,AttemptResult.SUCCESS,braked=true),
            attempt(QuestIds.INTERSECTION_RIGHT,AttemptResult.UNVERIFIED,braked=null))
        val a=DrivingAnalyzer.analyze(emptyList(),attempts)
        assertEquals("깜빡이: NO_BRAKE·보류 제외 3번 중 2번",67,a.scores[DrivingSkill.TURN_SIGNAL])
        assertEquals("회전 전 감속: 브레이크 확인된 2번 중 1번",50,a.scores[DrivingSkill.TURN_SLOWDOWN])
    }

    @Test fun feedbackPutsNotesFirstAndExplainsWithNumbers() {
        val speeds=listOf(0.0,15.0)+ramp(18.0,50.0,2.0)+List(150){50.0}+listOf(36.0)+List(30){36.0}+listOf(20.0)+List(30){20.0}
        val a=DrivingAnalyzer.analyze(trip(*speeds.toTypedArray()))
        val f=FeedbackWriter.write(a,listOf(NoteLine(5*3600_000L+32*60_000L,"어려웠어요 · 비보호 좌회전","LEFT_TURN")),
            timeZone=TimeZone.getTimeZone("UTC"))
        assertEquals("05:32 메모 · 비보호 좌회전",f.next.first())
        assertTrue(f.next.any {it.startsWith("급감속이 2번")})
        assertTrue("급출발 1번(84점)은 아직 잘한 점",f.good.contains("출발과 가속이 부드러웠어요"))
        assertEquals("전반적으로 안정적이었어요 · '여유 있는 감속'만 조금 더 다듬어요",f.headline)
    }

    @Test fun feedbackPraisesCleanTrip() {
        val speeds=ramp(0.0,60.0,3.0)+List(150){60.0}+ramp(60.0,0.0,-5.0)
        val f=FeedbackWriter.write(DrivingAnalyzer.analyze(trip(*speeds.toTypedArray())),emptyList())
        assertEquals("부드럽고 안정적인 주행이었어요!",f.headline)
        assertTrue(f.good.containsAll(listOf("출발과 가속이 부드러웠어요","여유 있게 속도를 줄였어요")))
    }
}
