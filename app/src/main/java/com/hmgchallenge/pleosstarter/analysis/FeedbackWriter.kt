package com.hmgchallenge.pleosstarter.analysis

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class NoteLine(val t: Long,val text: String,val category: String)

data class Feedback(val headline: String,val good: List<String>,val next: List<String>)

/**
 * 분석 결과로 "한 줄 요약 / 잘한 점 / 연습할 점"을 만든다. 칭찬 먼저, 비난하지 않기, 근거 숫자 포함.
 * 운전자가 직접 남긴 메모는 연습할 점의 맨 앞에 둔다. 기존 연습·퀘스트 문장(extraGood/extraNext)은 그대로 이어 붙인다.
 */
object FeedbackWriter {
    const val GOOD_SCORE=80
    const val PRACTICE_SCORE=80

    fun write(analysis: TripAnalysis?,notes: List<NoteLine>,extraGood: List<String> = emptyList(),extraNext: List<String> = emptyList(),
              timeZone: TimeZone=TimeZone.getDefault()): Feedback {
        val clock=SimpleDateFormat("HH:mm",Locale.KOREA).apply {this.timeZone=timeZone}
        val scored=analysis?.scores.orEmpty().filterValues {it!=null}.mapValues {it.value!!}
        val good=mutableListOf<String>();val next=mutableListOf<String>()
        notes.sortedBy {it.t}.forEach {next+="${clock.format(Date(it.t))} 메모 · ${it.text.removePrefix("어려웠어요 · ")}"}
        if(analysis!=null) {
            scored.filterValues {it>=GOOD_SCORE}.keys.forEach {good+=praise(it,analysis)}
            scored.filterValues {it<PRACTICE_SCORE}.entries.sortedBy {it.value}.forEach {(skill,_)->next+=advice(skill,analysis)}
        }
        good+=extraGood;next+=extraNext
        val headline=when {
            analysis==null || scored.isEmpty()->if(notes.isEmpty())"기록을 모았어요 · 조금 더 달리면 점수를 알려 드릴게요" else "남겨 주신 메모를 함께 돌아봐요"
            scored.values.all {it>=GOOD_SCORE}->"부드럽고 안정적인 주행이었어요!"
            else->{
                val weakest=scored.minBy {it.value}.key
                if(scored.getValue(weakest)<60)"오늘은 '${weakest.title}'을(를) 연습해 봐요"
                else "전반적으로 안정적이었어요 · '${weakest.title}'만 조금 더 다듬어요"
            }
        }
        return Feedback(headline,good.distinct().ifEmpty {listOf("끝까지 기록을 마쳤어요")},
            next.distinct().ifEmpty {listOf("다음 주행에서도 지금처럼 차근차근 달려 봐요")})
    }

    private fun praise(skill: DrivingSkill,a: TripAnalysis)=when(skill) {
        DrivingSkill.SMOOTH_ACCEL->"출발과 가속이 부드러웠어요"
        DrivingSkill.BRAKING->"여유 있게 속도를 줄였어요"
        DrivingSkill.TURN_SIGNAL->"방향을 바꿀 때 깜빡이를 먼저 켰어요 (${a.signalSuccesses}/${a.signalAttempts})"
        DrivingSkill.TURN_SLOWDOWN->"회전 전에 브레이크로 속도를 줄였어요"
    }

    private fun advice(skill: DrivingSkill,a: TripAnalysis)=when(skill) {
        DrivingSkill.SMOOTH_ACCEL->"급출발·급가속이 ${a.harshAccelCount}번 있었어요. 출발할 때 3초 세면서 천천히 밟아 봐요"
        DrivingSkill.BRAKING->"급감속이 ${a.harshBrakeCount}번 있었어요. 앞차와 거리를 조금 더 두면 여유 있게 멈출 수 있어요"
        DrivingSkill.TURN_SIGNAL->"깜빡이 ${a.signalAttempts}번 중 ${a.signalAttempts-a.signalSuccesses}번은 늦거나 없었어요. 핸들을 돌리기 전에 먼저 켜 봐요"
        DrivingSkill.TURN_SLOWDOWN->"우회전 ${a.turnBrakeChecks}번 중 ${a.turnBrakeMisses}번은 감속 없이 들어갔어요. 교차로 전에 브레이크를 살짝 밟아 봐요"
    }
}
