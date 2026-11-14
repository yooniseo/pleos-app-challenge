package com.hmgchallenge.pleosstarter.driving

enum class CoachingStep { READY, WAITING, LANE_PREPARE, LANE_MOVE, LANE_COUNTERSTEER, LANE_UNWIND, TURN_PREPARE, TURN, TURN_UNWIND, PARK_POSITION, PARK_CHECK, PARK_REVERSE, PARK_COUNTERSTEER, PARK_ALIGN, COMPLETE }
data class ScenarioCoachingCue(val step: CoachingStep,val full: String,val action: String,val brief: String,val spoken: String?=null,val identity: String=step.name)

/** Preparation uses vehicle input; manoeuvres use the planned, stable hand-movement timeline. */
object ScenarioCoachingCues {
    fun at(s: SimpleRoadScenario): ScenarioCoachingCue {
        val base=phase(s)
        if(base.step !in setOf(CoachingStep.LANE_MOVE,CoachingStep.LANE_COUNTERSTEER,CoachingStep.LANE_UNWIND,
            CoachingStep.TURN,CoachingStep.TURN_UNWIND,CoachingStep.PARK_REVERSE,CoachingStep.PARK_COUNTERSTEER,CoachingStep.PARK_ALIGN))return base
        val movement=s.steeringInstructions.at(s.distance)
        val action=movement.action.text
        val detail=if(s.lesson==RoadLesson.PARKING)"$action. 뒤쪽과 양옆도 확인하세요" else action
        return base.copy(full=detail,action=action,brief=action,spoken=movement.action.spoken,identity=movement.key)
    }
    private fun phase(s: SimpleRoadScenario): ScenarioCoachingCue = when {
        !s.started->cue(CoachingStep.READY,s.cue(),"핸들 그림과 함께 연습해요","자동 시연 준비")
        s.complete->cue(CoachingStep.COMPLETE,"잘했어요! 연습을 마쳤어요","핸들을 가운데로 맞추고 마무리해요","연습 완료")
        s.waitingForSpeed->cue(CoachingStep.WAITING,"차량 속도 수신을 기다리고 있어요","정차 상태를 유지해요","속도 수신 대기")
        s.lesson==RoadLesson.PARKING && s.distance<5->cue(CoachingStep.PARK_POSITION,"주차칸 옆에 천천히 위치를 맞춰요","핸들을 가운데 두고 천천히 가요","주차 위치 맞추기","주차칸 옆에 천천히 위치를 맞춰요")
        s.lesson==RoadLesson.PARKING && s.parkingWaitMs<5000->cue(CoachingStep.PARK_CHECK,"멈춘 뒤 미러와 뒤쪽을 확인해요","뒤쪽과 양옆 공간을 확인해요","정차 · 주변 확인","멈춘 뒤 미러와 뒤쪽을 확인해요")
        s.lesson==RoadLesson.PARKING && s.distance>=s.endDistance-3->cue(CoachingStep.PARK_ALIGN,"차가 곧아지면 핸들을 풀어 가운데로 맞춰요","핸들을 풀고 주차선과 나란하게 맞춰요","핸들 복원 · 정렬","차가 곧아지면 핸들을 풀어 가운데로 맞춰요")
        s.lesson==RoadLesson.PARKING && s.distance>=s.parkingCountersteerDistance->cue(CoachingStep.PARK_COUNTERSTEER,"이제 왼쪽으로 돌려 차를 주차선과 나란하게 맞춰요","왼쪽으로 돌려 차를 나란하게 맞춰요","왼쪽으로 · 나란하게","이제 왼쪽으로 돌려 나란하게 맞춰요")
        s.lesson==RoadLesson.PARKING->cue(CoachingStep.PARK_REVERSE,"오른쪽으로 꺾고 뒤쪽을 보며 천천히 후진해요","오른쪽으로 꺾으며 천천히 후진해요","오른쪽 조향 · 후진","오른쪽으로 꺾으며 천천히 후진해요")
        s.lesson!=RoadLesson.TURN && s.distance<12->preparation(CoachingStep.LANE_PREPARE,s.indicator)
        s.lesson!=RoadLesson.TURN && s.distance<25->cue(CoachingStep.LANE_MOVE,"오른쪽으로 조금 꺾은 뒤 천천히 가운데로 풀어요","오른쪽으로 조금 꺾어요","오른쪽 한 차로 이동","오른쪽으로 조금 꺾어요")
        s.lesson!=RoadLesson.TURN && s.distance<38->cue(CoachingStep.LANE_COUNTERSTEER,"왼쪽으로 조금 돌린 뒤 차가 나란해지면 가운데로 풀어요","왼쪽으로 조금 돌려 나란하게 맞춰요","왼쪽으로 · 나란하게","왼쪽으로 조금 돌려 나란하게 맞춰요")
        s.lesson!=RoadLesson.TURN && s.distance<42->cue(CoachingStep.LANE_UNWIND,"차가 나란해지면 핸들을 가운데로 맞춰요","핸들을 가운데로 맞춰요","핸들 가운데로","핸들을 가운데로 맞춰요")
        s.lesson==RoadLesson.LANE->cue(CoachingStep.LANE_UNWIND,"좋아요! 차로 안에서 핸들을 가운데로 유지해요","핸들 가운데 · 차로 유지","차로 유지")
        s.distance>=s.endDistance-10->cue(CoachingStep.TURN_UNWIND,"새 도로와 나란해지면 핸들을 부드럽게 풀어요","핸들을 풀어 가운데로 맞춰요","우회전 마무리","새 도로와 나란해지면 핸들을 부드럽게 풀어요")
        s.distance>=s.course.turnDistance-3->cue(CoachingStep.TURN,"길을 따라 오른쪽으로 부드럽게 돌려요","오른쪽으로 부드럽게 돌려요","천천히 우회전","길을 따라 오른쪽으로 부드럽게 돌려요")
        else->preparation(CoachingStep.TURN_PREPARE,s.indicator)
    }
    fun preparation(step: CoachingStep,indicator: Indicator): ScenarioCoachingCue {
        val check=if(step==CoachingStep.LANE_PREPARE)"미러와 사각지대를 확인하세요" else "우회전 전, 횡단보도도 살펴보세요"
        val text=when(indicator) {
            Indicator.RIGHT->check
            Indicator.OFF->"오른쪽 깜빡이를 켜고, $check"
            Indicator.LEFT->"오른쪽 깜빡이로 바꾸고, $check"
            Indicator.UNKNOWN->"깜빡이 상태를 확인하고, $check"
        }
        return cue(step,text,text,if(step==CoachingStep.LANE_PREPARE)"오른쪽 한 차로 준비" else "우회전 · 주변 확인",text)
    }
    private fun cue(step: CoachingStep,full: String,action: String,brief: String,spoken: String?=null)=ScenarioCoachingCue(step,full,action,brief,spoken)
}

/** Speak a phase once, after previous speech ends; never queue obsolete instructions. */
class ScenarioSpeechGate(private val minimumGapMs: Long=1000) {
    private var trip: String?=null
    private val delivered=mutableSetOf<String>()
    private var lastAt: Long?=null
    fun next(tripId: String?,cue: ScenarioCoachingCue,now: Long,enabled: Boolean,ready: Boolean,idle: Boolean): String? {
        if(tripId!=trip){trip=tripId;delivered.clear();lastAt=null}
        val text=cue.spoken ?: return null
        if(tripId==null || !enabled || !ready || !idle || cue.identity in delivered)return null
        if(lastAt?.let {now-it<minimumGapMs}==true)return null
        delivered.add(cue.identity);lastAt=now;return text
    }
}
