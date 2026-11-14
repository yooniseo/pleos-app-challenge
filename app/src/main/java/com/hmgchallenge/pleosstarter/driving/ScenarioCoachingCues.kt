package com.hmgchallenge.pleosstarter.driving

enum class CoachingStep { READY, WAITING, LANE_PREPARE, LANE_MOVE, LANE_UNWIND, TURN_PREPARE, TURN, TURN_UNWIND, PARK_POSITION, PARK_CHECK, PARK_REVERSE, PARK_ALIGN, COMPLETE }
data class ScenarioCoachingCue(val step: CoachingStep,val full: String,val action: String,val brief: String,val spoken: String?=null)

/** Authored actions stay stable within a phase. Path curvature never generates a spoken command. */
object ScenarioCoachingCues {
    fun at(s: SimpleRoadScenario): ScenarioCoachingCue = when {
        !s.started->cue(CoachingStep.READY,s.cue(),"핸들 그림과 함께 연습해요","자동 시연 준비")
        s.complete->cue(CoachingStep.COMPLETE,"잘했어요! 연습을 마쳤어요","핸들을 가운데로 맞추고 마무리해요","연습 완료")
        s.waitingForSpeed->cue(CoachingStep.WAITING,"차량 속도 수신을 기다리고 있어요","정차 상태를 유지해요","속도 수신 대기")
        s.lesson==RoadLesson.PARKING && s.distance<5->cue(CoachingStep.PARK_POSITION,"주차칸 옆에 천천히 위치를 맞춰요","핸들을 가운데 두고 천천히 가요","주차 위치 맞추기","주차칸 옆에 천천히 위치를 맞춰요")
        s.lesson==RoadLesson.PARKING && s.parkingWaitMs<2000->cue(CoachingStep.PARK_CHECK,"멈춘 뒤 미러와 뒤쪽을 확인해요","뒤쪽과 양옆 공간을 확인해요","정차 · 주변 확인","멈춘 뒤 미러와 뒤쪽을 확인해요")
        s.lesson==RoadLesson.PARKING && s.distance>=s.endDistance-3->cue(CoachingStep.PARK_ALIGN,"차가 곧아지면 핸들을 풀어 가운데로 맞춰요","핸들을 풀고 주차선과 나란하게 맞춰요","핸들 복원 · 정렬","차가 곧아지면 핸들을 풀어 가운데로 맞춰요")
        s.lesson==RoadLesson.PARKING->cue(CoachingStep.PARK_REVERSE,"그림의 방향을 보며 천천히 후진해요","후진하며 핸들을 부드럽게 돌려요","천천히 후진","뒤쪽을 확인하며 천천히 후진해요")
        s.lesson!=RoadLesson.TURN && s.distance<12->cue(CoachingStep.LANE_PREPARE,"오른쪽 깜빡이와 미러·사각지대를 확인해요","오른쪽 한 차로 이동을 준비해요","오른쪽 한 차로 준비","깜빡이와 미러를 확인하고, 오른쪽 한 차로 이동해요")
        s.lesson!=RoadLesson.TURN && s.distance<27->cue(CoachingStep.LANE_MOVE,"오른쪽으로 부드럽게 꺾어 한 차로만 이동해요","핸들을 오른쪽으로 부드럽게 꺾어요","오른쪽 한 차로 이동")
        s.lesson!=RoadLesson.TURN && s.distance<42->cue(CoachingStep.LANE_UNWIND,"차가 차로와 나란해지면 핸들을 풀어요","핸들을 풀어 가운데로 맞춰요","핸들 가운데로","차가 차로와 나란해지면 핸들을 풀어요")
        s.lesson==RoadLesson.LANE->cue(CoachingStep.LANE_UNWIND,"좋아요! 차로 안에서 핸들을 가운데로 유지해요","핸들 가운데 · 차로 유지","차로 유지")
        s.distance>=s.endDistance-8->cue(CoachingStep.TURN_UNWIND,"새 도로와 나란해지면 핸들을 부드럽게 풀어요","핸들을 풀어 가운데로 맞춰요","우회전 마무리","새 도로와 나란해지면 핸들을 부드럽게 풀어요")
        s.turning->cue(CoachingStep.TURN,"보행자와 전방을 보며 천천히 우회전해요","오른쪽으로 부드럽게 돌리며 회전해요","천천히 우회전")
        else->cue(CoachingStep.TURN_PREPARE,"3차로를 유지하고 보행자를 확인해요","우회전할 공간과 보행자를 확인해요","우회전 준비","보행자를 확인하고, 3차로에서 천천히 우회전해요")
    }
    private fun cue(step: CoachingStep,full: String,action: String,brief: String,spoken: String?=null)=ScenarioCoachingCue(step,full,action,brief,spoken)
}

/** Speak a phase once, after previous speech ends; never queue obsolete instructions. */
class ScenarioSpeechGate(private val minimumGapMs: Long=6000) {
    private var trip: String?=null
    private val delivered=mutableSetOf<CoachingStep>()
    private var lastAt: Long?=null
    fun next(tripId: String?,cue: ScenarioCoachingCue,now: Long,enabled: Boolean,ready: Boolean,idle: Boolean): String? {
        if(tripId!=trip){trip=tripId;delivered.clear();lastAt=null}
        val text=cue.spoken ?: return null
        if(tripId==null || !enabled || !ready || !idle || cue.step in delivered)return null
        if(lastAt?.let {now-it<minimumGapMs}==true)return null
        delivered.add(cue.step);lastAt=now;return text
    }
}
