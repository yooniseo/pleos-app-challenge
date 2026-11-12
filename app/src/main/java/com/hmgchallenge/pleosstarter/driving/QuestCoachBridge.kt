package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import com.hmgchallenge.pleosstarter.quests.*
import java.io.Closeable
import java.util.UUID

/** Uses the quest team's v2 stores; demo and personal profiles remain separate. */
class QuestCoachBridge(context: Context, private val demo: Boolean, requestedTripId: String? = null,
    onChanged: () -> Unit = {}) : QuestCoachPort, Closeable {
    private val store=QuestStore(context.applicationContext,demo)
    private val localRepository=CoachRepository(context.applicationContext)
    val tripId=requestedTripId?.takeIf {id->store.load().session?.let {it.tripId==id && it.finishedAt==null}==true}
        ?: UUID.randomUUID().toString()
    private val subscription=store.observe({onChanged()},{onChanged()})
    override fun preferences(): CoachPreferences {
        val settings=store.load().settings
        return localRepository.preferences().copy(
            levels=SituationType.values().associateWith {GuidanceLevel.valueOf(settings.level(CoachQuestMapper.skill(it)).name)},
            disabled=SituationType.values().filter {CoachQuestMapper.skill(it) in settings.disabled}.toSet())
    }
    override fun focusedGoalLabel()=store.load().focusGoalId?.let {QuestCatalog.get(it).title}
    override fun onCoachEvent(event: CoachEvent) {
        synchronized(QuestIntegration.lock) {
            val before=store.load();val after=CoachQuestMapper.apply(before,event)
            if(after!=before) check(store.save(after)) {"Quest event could not be saved"}
        }
    }
    fun summary(type: SituationType): String {
        val state=store.load();val skill=CoachQuestMapper.skill(type)
        val stage=state.completedStages(skill).size
        val next=StageQuestCatalog.forSkill(skill).firstOrNull {it.stage !in state.completedStages(skill)}?.title ?: "영역 단계 완료"
        return "${if(demo) "퀘스트 체험 기록" else "퀘스트 개인 기록"} · $stage/4단계 · 확인된 경험 ${state.practiceCount(skill)}회\n$next"
    }
    fun review(events: List<CoachEvent>) {
        synchronized(QuestIntegration.lock) {
            var state=store.load()
            val session=state.session?.takeIf {it.tripId==tripId} ?: return
            val at=maxOf(System.currentTimeMillis(),session.events.lastOrNull()?.at ?: session.startedAt)
            session.current?.let {current->state=QuestEngine.acceptEvent(state,SituationEvent(
                UUID.randomUUID().toString(),current.context,SituationEventType.UNVERIFIED,at,"주행 마무리 · 수행 자동 판정 없음"))}
            state=QuestEngine.finishTrip(state,tripId,at)
            val notes=events.filter {it.tripId==tripId && it.eventType==CoachEventType.DIFFICULTY_NOTED}
            state=QuestEngine.consumeFeedback(state,DrivingFeedback(tripId,
                if(demo) com.hmgchallenge.pleosstarter.quests.EvidenceSource.SIMULATION else com.hmgchallenge.pleosstarter.quests.EvidenceSource.VEHICLE,
                at,notes.map {CoachQuestMapper.kind(it.situationType).id}.toSet(),notes.mapNotNull {it.feedbackNote()?.category}.toSet()))
            state=QuestEngine.review(state)
            check(store.save(state)) {"Could not save review"}
        }
    }
    override fun close()=subscription.close()
}
