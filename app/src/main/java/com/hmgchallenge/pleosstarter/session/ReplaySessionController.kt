package com.hmgchallenge.pleosstarter.session

import android.content.Context
import com.hmgchallenge.pleosstarter.core.data.SharedCoachRepository
import com.hmgchallenge.pleosstarter.driving.*
import com.hmgchallenge.pleosstarter.quests.*
import java.util.UUID

/** Owns one practice independently of the visible screen. No actuator/Virtual Console writes. */
class ReplaySessionController(context: Context, val course: NavigationSimulationRoute) {
    private val repository=SharedCoachRepository.get(context)
    init {check(repository.recoverInterruptedTrips()) {repository.lastError ?: "지난 기록을 복구하지 못했어요"}}
    private val store=QuestStore(context,true)
    var quest=store.load(); private set
    init {
        check(repository.reconcilePracticeQuests("demo")) {repository.lastError ?: "퀘스트 진행도를 복구하지 못했어요"}
        val stale=quest.session?.takeIf {it.finishedAt==null}
        if(stale!=null && repository.snapshot("trip:${stale.tripId}")?.let {org.json.JSONObject(it).optString("status")}=="INTERRUPTED") {
            val at=maxOf(System.currentTimeMillis(),stale.events.lastOrNull()?.at ?: stale.startedAt)
            val recovered=interruptQuest(quest,stale.tripId,at)
            check(store.save(recovered)) {repository.lastError ?: "지난 퀘스트를 복구하지 못했어요"}
            quest=recovered
        }
    }
    var scenario=SimpleRoadScenario(course); private set
    var tripId: String?=null; private set
    var paused=true; private set
    var error: String?=null; private set
    var completedAt: Long?=null; private set
    var onMilestone: (Boolean)->Unit={}
    var onParkingStarted: ()->Unit={}
    var selectedLesson=RoadLesson.COMBINED; private set
    private var roadMetres=0.0
    private var selectedSpeed=ScenarioSpeed.REPLAY
    val travelledMetres get()=roadMetres+scenario.distance-scenario.startDistance
    val continuingToParking get()=selectedLesson==RoadLesson.COMBINED && scenario.lesson==RoadLesson.COMBINED
    private var link: SimpleScenarioQuests?=null
    private var lastSample=0L
    private var startedAt=0L
    private var lastKey=""
    private var finalized=false
    var manualStyle=BeginnerStyle.ADAPTIVE
    var coachingEnabled=true
    var indicatorOverride: Indicator?=Indicator.OFF
    fun updateIndicator(live: Indicator) { scenario.indicator=indicatorOverride ?: live }
    val skill get()=when(scenario.lesson) {
        RoadLesson.PARKING->CoachSkill.PARKING
        RoadLesson.LANE->CoachSkill.LANE_CHANGE
        RoadLesson.TURN->CoachSkill.INTERSECTION
        else->if(scenario.laneDone)CoachSkill.INTERSECTION else CoachSkill.LANE_CHANGE
    }
    fun learning(skill: CoachSkill=this.skill): LearningDecision {
        val difficultAt=repository.difficultyAt("demo",skill.name)
        val clean=repository.completions("demo",skill.name).count {it.at>difficultAt && !it.difficult}
        return CoachLearningPolicy.forQuest(quest,skill,clean,difficultAt>0,manualStyle)
    }
    fun select(lesson: RoadLesson, source: ScenarioSpeed=ScenarioSpeed.REPLAY): Boolean {
        if(!close())return false
        scenario=SimpleRoadScenario(course,lesson,if(lesson==RoadLesson.PARKING)ScenarioSpeed.REPLAY else source)
        selectedLesson=lesson;selectedSpeed=scenario.source;roadMetres=0.0
        tripId=null;link=null;paused=true;completedAt=null;lastKey="";finalized=false;error=null
        return true
    }
    fun start(now: Long=System.currentTimeMillis()): Boolean {
        if(scenario.complete && !select(selectedLesson,selectedSpeed))return false
        if(tripId!=null && scenario.started){paused=false;error=null;return true}
        val id=UUID.randomUUID().toString()
        if(!repository.beginTrip(id,scenario.lesson.name,scenario.source.name,now,if(selectedLesson==RoadLesson.COMBINED)6 else 1))return fail()
        startedAt=now;link=SimpleScenarioQuests(id,now)
        synchronized(QuestIntegration.lock) {
            val next=link!!.begin(store.load(),now)
            if(!store.save(next)){repository.endTrip(id,now,0f,false);return fail()}
            quest=next
        }
        tripId=id;scenario.start();paused=false;lastSample=0;error=null
        return true
    }
    fun togglePause() {if(scenario.started && !scenario.complete)paused=!paused}
    fun pause() {paused=true}
    fun refresh() {quest=store.load()}
    fun tick(elapsedMs: Long, monotonic: Long, vehicleMps: Double?, fresh: Boolean, now: Long=System.currentTimeMillis()) {
        if(!scenario.started || finalized || paused)return
        scenario.advance(elapsedMs,vehicleMps,fresh)
        tripId?.let {id->if(lastSample==0L || monotonic-lastSample>=1000) {
            val kmh=if(scenario.source==ScenarioSpeed.VEHICLE)vehicleMps?.takeIf {fresh && it.isFinite() && it>=0}?.times(3.6)?.toFloat()
                else (scenario.speedMps*3.6).toFloat()
            repository.sample(id,now,kmh,(travelledMetres/1000).toFloat());lastSample=monotonic
        }}
        val key="${scenario.lesson}:${scenario.completedLaneChanges}:${scenario.complete}"
        if(key!=lastKey) {
            synchronized(QuestIntegration.lock) {
                val before=store.load()
                val next=link!!.sync(before,scenario,now,finishTrip=!continuingToParking) {skill->if(!coachingEnabled)AssistanceUsed.NONE else when(learning(skill).support) {
                    CoachingSupport.DETAILED->AssistanceUsed.DETAILED
                    CoachingSupport.HINT->AssistanceUsed.BRIEF
                    CoachingSupport.ON_REQUEST->AssistanceUsed.NONE
                }}
                if(next!=before && !store.save(next)){fail();return}
                quest=next;lastKey=key
                if(next.evidence.size>before.evidence.size)onMilestone(scenario.complete && !continuingToParking)
            }
        }
        if(scenario.complete && continuingToParking) {
            roadMetres=travelledMetres
            scenario=SimpleRoadScenario(course,RoadLesson.PARKING)
            scenario.start();lastKey=""
            onParkingStarted()
            return
        }
        if(scenario.complete) {
            if(!repository.endTrip(tripId!!,now,(travelledMetres/1000).toFloat(),true)){fail();return}
            completedAt=now;finalized=true;paused=true;error=null
        }
    }
    /** A reset archives old evidence; previous trip reports and reward events remain readable. */
    fun resetProgress(): Boolean {
        if(scenario.started && !finalized)return false
        if(!store.resetDemoProgress())return fail()
        refresh();return true
    }
    fun note(difficult: Boolean): Boolean {
        val id=tripId ?: return false
        val saved=repository.addNote(id,if(difficult)"어려웠어요 · ${skill.name}" else "좋았어요 · ${skill.name}",skill.name,skill.name,difficult)
        if(!saved)return fail()
        refresh();return true
    }
    /** One explicit checklist confirmation per trip; never inferred from zero speed or a drawn icon. */
    fun confirmChecklist(skill: CoachSkill,checked: Set<Int>,now: Long=System.currentTimeMillis()): Boolean {
        val id=tripId ?: return false
        val definition=QuestCatalog.get(when(skill){CoachSkill.PREPARATION->"prepare";CoachSkill.FINISH->"finish";else->return false})
        if(checked!=definition.checks.indices.toSet())return false
        if(skill==CoachSkill.FINISH && (completedAt==null || scenario.lesson!=RoadLesson.PARKING))return false
        synchronized(QuestIntegration.lock) {
            val before=store.load()
            val next=QuestEngine.recordPractice(before.copy(completed=before.completed+definition.id),
                PracticeEvidence("$id:checklist:${skill.name}",skill,com.hmgchallenge.pleosstarter.quests.EvidenceSource.SIMULATION,now,id))
            if(!store.save(next))return fail()
            quest=next
        }
        return true
    }
    fun memo(text: String,category: String,difficult: Boolean): Boolean {
        val id=tripId ?: return false
        val skill=when(category){"LANE_CHANGE"->"LANE_CHANGE";"RIGHT_TURN","LEFT_TURN"->"INTERSECTION";"PARKING"->"PARKING";"SPEED_CONTROL"->"SPEED";else->this.skill.name}
        val result=repository.addNote(id,if(difficult)"어려웠어요 · $text" else text,category,skill,difficult)
        if(!result)return fail()
        refresh();return true
    }
    fun close(): Boolean {
        paused=true
        if(tripId==null || finalized)return true
        val now=System.currentTimeMillis()
        synchronized(QuestIntegration.lock) {
            val before=store.load()
            val at=maxOf(now,before.session?.events?.lastOrNull()?.at ?: now)
            val next=interruptQuest(before,tripId!!,at)
            if(!store.save(next))return fail()
            quest=next
        }
        if(!repository.endTrip(tripId!!,now,(travelledMetres/1000).toFloat(),scenario.complete && !continuingToParking))return fail()
        finalized=true;return true
    }
    private fun fail(): Boolean {error=repository.lastError ?: "기록을 저장하지 못했어요. 다시 시도해 주세요";paused=true;return false}
    private fun interruptQuest(state: QuestState,id: String,at: Long): QuestState {
        val current=state.session?.takeIf {it.tripId==id}?.current
        val cancelled=if(current==null)state else QuestEngine.acceptEvent(state,
            SituationEvent("$id:interrupted",current.context,SituationEventType.CANCELLED,at))
        return QuestEngine.finishTrip(cancelled,id,at)
    }
}
