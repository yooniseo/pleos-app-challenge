package com.hmgchallenge.pleosstarter.driving

import kotlin.math.*

enum class BeginnerStyle(val title: String) { STEP_BY_STEP("차근차근"), VISUAL("핸들 중심"), BRIEF("짧게 알려줘"), ADAPTIVE("연습에 맞춰") }
data class BeginnerGuidance(val headline: String,val detail: String,val checklist: String,val steering: Int,
                            val direction: String="가운데",val amount: String="유지")

object BeginnerScenarioCoach {
    fun steeringPosition(angle: Double): String {
        if(abs(angle)<10)return "현재 핸들 · 가운데"
        val side=if(angle>0)"오른쪽" else "왼쪽"
        return when {
            abs(angle)>=350->"현재 핸들 · $side 한 바퀴 위치"
            abs(angle)>180->"현재 핸들 · $side 반 바퀴 이상"
            else->"현재 핸들 · $side 조향 중"
        }
    }
    fun guidance(s: SimpleRoadScenario,style: BeginnerStyle): BeginnerGuidance {
        val angle=s.steeringTarget.roundToInt()
        val direction=if(angle< -9)"왼쪽" else if(angle>9)"오른쪽" else "가운데"
        val amount=turnAmount(angle)
        val cue=ScenarioCoachingCues.at(s)
        val checklist=if(s.lesson==RoadLesson.PARKING)
            "위치 맞춤 → 정차·주변 확인 → 오른쪽 조향·후진 → 왼쪽 조향·정렬 → 가운데"
            else "깜빡이·주변 확인 → 조향 → 나란하게 맞추기 → 핸들 가운데"
        val headline=when(style) {
            BeginnerStyle.VISUAL->cue.action
            BeginnerStyle.BRIEF->cue.brief
            else->cue.full
        }
        val detail=when(style) {
            BeginnerStyle.VISUAL->"핸들 그림의 방향과 차량이 나란해지는 모습을 함께 확인해요"
            BeginnerStyle.BRIEF->cue.action
            else->cue.full
        }
        return BeginnerGuidance(headline,detail,checklist,angle,direction,amount)
    }
    fun steeringAction(angle: Int): String {
        val direction=if(angle<0)"왼쪽" else "오른쪽"
        return when {
            abs(angle)<10->"핸들을 가운데로 풀어 맞추세요"
            abs(angle)<=180->"핸들을 ${direction}으로 꺾으세요"
            abs(angle) in 225..314->"${direction}으로 반 바퀴보다 조금 더 돌려주세요"
            else->"${direction}으로 ${turnAmount(angle)} 정도 돌려주세요"
        }
    }
    /** Deliberately conversational quantities; internal degrees are only for the wheel animation. */
    fun turnAmount(angle: Int)=when(abs(angle)) {
        in 0..9->"유지"
        in 10..49->"살짝"
        in 50..114->"1/4 바퀴"
        in 115..224->"반 바퀴"
        in 225..314->"조금 더"
        in 315..404->"한 바퀴"
        in 405..584->"한 바퀴 반"
        else->"두 바퀴"
    }
}
