package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.hmgchallenge.pleosstarter.quests.*
import com.hmgchallenge.pleosstarter.quests.EvidenceSource as QuestEvidenceSource
import com.hmgchallenge.pleosstarter.quests.GuidanceLevel as QuestGuidanceLevel
import java.util.UUID

/** Part 1 scaffold only. Replace this port with real guidance; no SDK, navigation, or actuator here. */
class PlaceholderDrivingGuide : DrivingGuidePort {
    private var host: DrivingGuideHost? = null
    private var model: GuideUiModel? = null
    private var status: TextView? = null
    private var command = "명령 대기"
    override fun createView(context: Context, host: DrivingGuideHost): View {
        this.host = host
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            status = TextView(context).apply { textSize = 16f; setTextColor(Color.WHITE) }
            addView(status)
            fun control(text: String, action: () -> Unit) = addView(Button(context).apply {
                this.text = text; isAllCaps = false; minHeight = (56 * resources.displayMetrics.density).toInt()
                setOnClickListener { action() }
            })
            control("시연: 새 주행 시작") {
                if (model?.demo == true) host.startTrip(UUID.randomUUID().toString(), QuestEvidenceSource.SIMULATION, now())
            }
            control("시연: 차로 변경 상황 진입") { enter(SituationKind.LANE_RIGHT) }
            control("시연: 우회전 상황 진입") { enter(SituationKind.TURN_RIGHT) }
            control("시연: 교차로 빠져나오기 진입") { enter(SituationKind.INTERSECTION_EXIT) }
            control("시연: 관측값만 전달") { current { host.reportObservation(id(), it, now(), "관측값 예시 · 수행 완료 근거 아님") } }
            control("시연: 안내 제공 사실 전달") { current {
                val used = when {
                    model?.coachingEnabled != true -> AssistanceUsed.NONE
                    model?.guidanceLevel == QuestGuidanceLevel.DETAILED -> AssistanceUsed.DETAILED
                    model?.guidanceLevel == QuestGuidanceLevel.BRIEF -> AssistanceUsed.BRIEF
                    else -> AssistanceUsed.ON_REQUEST
                }
                host.reportGuidance(id(), it, now(), used)
            } }
            control("시연: 이 상황 어려웠음") { current { host.recordDifficulty(id(), it, now(), "다음에 다시 연습하고 싶어요") } }
            control("시연: 수행 확인 전달") { current { host.confirmPractice(id(), it, now(), ConfirmationBasis.DEMO) } }
            control("시연: 상황 건너뛰기") { current { host.skipSituation(id(), it, now()) } }
            control("시연: 상황 취소 전달") { current { host.cancelSituation(id(), it, now()) } }
            control("시연: 확인 데이터 없음") { current { host.markUnverified(id(), it, now()) } }
            control("시연: 다음 기회에 연습") { current { host.deferSituation(id(), it, now()) } }
            control("시연: 주행 종료") { currentTrip?.let { host.finishTrip(it, now()) } }
        }.also { model?.let(::render) }
    }
    private var currentTrip: String? = null
    override fun render(model: GuideUiModel) {
        this.model = model
        currentTrip = model.tripId
        status?.text = "1번 개발자 연결 자리 · 실제 주행 안내 아님\n" +
            "현재 상황: ${model.current?.kind?.title ?: "상황 대기"}\n" +
            "3번에서 받은 설정: ${if (model.coachingEnabled) model.guidanceLevel.name else "추가 코칭 꺼짐"}\n" +
            "현재 퀘스트: ${model.activeQuest?.title ?: "상황에 맞춰 표시"} · ${model.completedStageCount}/4\n$command"
    }
    override fun onCommand(command: CoachCommand) { this.command = "3번에서 받은 명령: ${command.name}"; model?.let(::render) }
    override fun release() { host = null; status = null }
    private fun enter(kind: SituationKind) {
        val snapshot = model ?: return
        if (!snapshot.demo || snapshot.current != null) return
        val sequence = listOf(SituationKind.LANE_RIGHT, SituationKind.TURN_RIGHT, SituationKind.INTERSECTION_EXIT)
        val next = sequence.firstOrNull { item -> snapshot.situations.none { it.context.kind == item && it.outcome != SituationEventType.CANCELLED } }
        if (snapshot.tripFinished || kind != next) return
        val trip = currentTrip ?: return
        host?.enterSituation(id(), SituationContext(trip, id(), "right-turn-intersection", kind, QuestEvidenceSource.SIMULATION, now()))
    }
    private fun current(action: (SituationContext) -> Unit) { if (model?.demo == true) model?.current?.let(action) }
    private fun id() = UUID.randomUUID().toString()
    private fun now() = System.currentTimeMillis()
}
