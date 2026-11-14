package com.hmgchallenge.pleosstarter.driving

import kotlin.math.*

enum class BeginnerStyle(val title: String) { STEP_BY_STEP("차근차근"), VISUAL("핸들 중심"), BRIEF("짧게 알려줘"), ADAPTIVE("연습에 맞춰") }
data class BeginnerGuidance(val headline: String,val detail: String,val checklist: String,val steering: Int,
                            val direction: String="가운데",val amount: String="유지")

object BeginnerScenarioCoach {
    fun guidance(s: SimpleRoadScenario,style: BeginnerStyle): BeginnerGuidance {
        val angle=s.steeringTarget.roundToInt()
        val direction=if(angle< -9)"왼쪽" else if(angle>9)"오른쪽" else "가운데"
        val amount=turnAmount(angle)
        val restoring=s.started && !s.complete && ((s.lesson in setOf(RoadLesson.COMBINED,RoadLesson.LANE) && s.distance in 27.0..42.0) ||
            (s.lesson==RoadLesson.PARKING && s.distance>s.endDistance-3))
        val action=when {
            s.complete->"잘했어요! 핸들을 가운데로 맞추고 마무리해요"
            restoring && s.lesson==RoadLesson.PARKING->"차가 곧아졌어요. 핸들을 빠르게 풀어 가운데로!"
            restoring->"차가 차로에 맞춰져요. 핸들을 풀어 가운데로!"
            abs(angle)<10->"핸들을 가운데 두고 천천히 가세요"
            else->steeringAction(angle)
        }
        val detail=when {
            s.complete->"잘했어요. 완료한 동작을 퀘스트에 기록했어요"
            !s.started->"시작하면 핸들 방향과 확인 순서를 보여드려요"
            s.lesson==RoadLesson.PARKING && s.distance<5->"주차칸 옆에 여유를 두고 천천히 전진해요"
            s.lesson==RoadLesson.PARKING && s.parkingWaitMs<2000->"완전히 멈춘 뒤, 미러와 뒤쪽을 확인해요"
            s.lesson==RoadLesson.PARKING->"작게 후진하고, 차가 곧아지면 핸들을 풀어요"
            !s.laneDone && s.lesson!=RoadLesson.TURN->"깜빡이 → 미러 → 사각지대 → 한 차로씩 이동"
            s.turning->"앞·옆을 확인하고, 회전 후 핸들을 풀어요"
            else->"목표 차로 유지 · 보행자와 전방을 직접 확인해요"
        }
        val checklist=when {
            s.lesson==RoadLesson.PARKING->"위치 맞춤 → 정차·주변 확인 → 후진 → 정렬"
            s.turning->"주변 확인 → 천천히 회전 → 핸들 복원"
            s.laneDone || s.lesson==RoadLesson.TURN->"3차로 유지 → 전방 확인 → 우회전 준비"
            else->"깜빡이 → 미러·사각지대 → 한 차로 이동"
        }
        val stable=ScenarioCoachingCues.at(s)
        return BeginnerGuidance(when(style){BeginnerStyle.VISUAL->stable.action;BeginnerStyle.BRIEF->stable.brief;else->stable.full},
            when(style) {
                BeginnerStyle.BRIEF->action
                BeginnerStyle.VISUAL->if(restoring)"그림처럼 핸들을 풀어 가운데에 맞춰요" else if(!s.started)detail else "그림처럼 돌리고, 차가 곧아지면 핸들을 풀어요"
                else->detail
            },checklist,angle,if(restoring)"가운데로" else direction,if(restoring)"풀어주세요" else if(abs(angle) in 10..180)"꺾어주세요" else amount)
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
