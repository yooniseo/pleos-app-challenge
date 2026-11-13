package com.hmgchallenge.pleosstarter.core.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.hmgchallenge.pleosstarter.core.data.entity.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/** One durable store for all three features. SQLite work is serialized off the UI thread. */
class SharedCoachRepository(val database: TripDatabase) {
    private var workerThread: Thread?=null
    private val worker=Executors.newSingleThreadExecutor {r->Thread(r,"coach-database").also {workerThread=it}}
    private val main=Handler(Looper.getMainLooper())
    private val listeners=CopyOnWriteArrayList<(String)->Unit>()
    private val rawCache=mutableMapOf<String,String?>()
    @Volatile private var completions=emptyList<PracticeCompletion>()
    @Volatile private var difficulty=emptyList<CoachingEvent>()
    @Volatile var lastError: String?=null;private set
    private fun <T> io(action: ()->T): T = if(Thread.currentThread()==workerThread)action() else worker.submit<T> {action()}.get()
    private val dao get()=database.coachDao()
    private fun refresh() {completions=dao.completions("demo")+dao.completions("personal");difficulty=dao.difficulties()}
    private fun notify(key: String) {main.post {listeners.forEach {it(key)}}}
    fun observe(listener: (String)->Unit): Closeable {listeners.add(listener);return Closeable {listeners.remove(listener)}}
    fun snapshot(key: String,legacy: ()->String? = {null}): String? = io {
        if(!rawCache.containsKey(key)) {
            val stored=dao.snapshot(key)?.value
            val value=stored ?: legacy()
            if(stored==null && value!=null)dao.saveSnapshot(AppSnapshot(key,value,System.currentTimeMillis()))
            rawCache[key]=value;refresh()
        }
        rawCache[key]
    }
    fun saveSnapshot(key: String,value: String): Boolean=runCatching {io {
        dao.saveSnapshot(AppSnapshot(key,value,System.currentTimeMillis()));rawCache[key]=value;lastError=null
    };notify(key);true}.getOrElse {lastError=it.cause?.message ?: it.message;false}

    /** Snapshot plus queryable evidence/quests/events commit in one transaction. Legacy prefs stay intact. */
    fun saveQuest(profile: String,raw: String): Boolean=runCatching {io {
        val root=JSONObject(raw);require(root.getBoolean("demo")==(profile=="demo"))
        database.runInTransaction {
            dao.saveSnapshot(AppSnapshot("quest:$profile",raw,System.currentTimeMillis()))
            val records=root.getJSONArray("evidence").objects().map {e->PracticeCompletion(
                "$profile:${e.getString("id")}",profile,e.nullableString("tripId"),e.getString("skill"),e.getString("source"),e.getLong("at"),e.getBoolean("difficult"))}
            dao.clearCompletions(profile);dao.saveCompletions(records)
            records.forEach {e->e.tripId?.let {id->if(dao.trip(id)==null)dao.saveTrip(Trip(id,e.at,e.at,if(e.source=="SIMULATION")"REPLAY" else e.source,null))}}
            val stages=root.getJSONObject("stages");val quests=mutableListOf<Quest>()
            listOf("PREPARATION","LANE_CHANGE","INTERSECTION","SPEED","PARKING","FINISH").forEach {skill->
                val done=stages.optJSONArray(skill)?.strings().orEmpty()
                listOf("FIRST","SECOND","THIRD","FOURTH").forEachIndexed {i,stage->
                    quests.add(Quest("$profile:$skill:$stage","$skill:$stage","$skill · ${i+1}단계","퀘스트 상태와 같은 DB", "SCENARIO",1,if(stage in done)1 else 0,if(stage in done)"DONE" else "ACTIVE",0))
                }
            }
            dao.clearQuests("$profile:%");dao.saveQuests(quests)
            root.optJSONObject("session")?.let {session->
                val id=session.getString("tripId");val prior=dao.trip(id)
                val ended=session.takeUnless {it.isNull("finishedAt")}?.optLong("finishedAt")
                dao.saveTrip(prior?.copy(endedAt=ended ?: prior.endedAt) ?: Trip(id,session.getLong("startedAt"),ended,if(profile=="demo")"REPLAY" else session.getString("source"),null))
                session.getJSONArray("events").objects().forEach {event->
                    dao.saveEvent(CoachingEvent("$profile:${event.getString("eventId")}",id,"SCENARIO_${event.getString("type")}",event.getLong("at"),session.getString("source"),event.toString()))
                }
                if(ended!=null)buildReport(id,ended)
            }
        }
        rawCache["quest:$profile"]=raw;refresh();lastError=null
    };notify("quest:$profile");true}.getOrElse {lastError=it.cause?.message ?: it.message;false}

    fun beginTrip(id: String,lesson: String,speedSource: String,at: Long): Boolean=write("trip") {
        dao.saveTrip(Trip(id,at,null,if(speedSource=="VEHICLE")"VEHICLE" else "REPLAY",0f))
        dao.saveSnapshot(AppSnapshot("trip:$id",JSONObject().put("lesson",lesson).put("profile","demo").put("speedSource",speedSource).put("scenario",true).put("status","ACTIVE").toString(),at))
    }
    fun sample(id: String,t: Long,kmh: Float?,distanceKm: Float) {
        // Fire-and-forget only for telemetry; later state writes/readbacks queue behind this work.
        worker.execute {runCatching {
            dao.saveSample(TripSample(id,t,kmh?.takeIf {it.isFinite() && it>=0}))
            dao.trip(id)?.let {dao.saveTrip(it.copy(distanceKm=distanceKm))}
        }.onFailure {lastError=it.message}}
    }
    fun endTrip(id: String,at: Long,distanceKm: Float,complete: Boolean): Boolean=write("trip") {
        dao.trip(id)?.let {dao.saveTrip(it.copy(endedAt=at,distanceKm=distanceKm))}
        val meta=dao.snapshot("trip:$id")?.value?.let(::JSONObject) ?: JSONObject()
        meta.put("status",if(complete)"COMPLETE" else "INTERRUPTED")
        dao.saveSnapshot(AppSnapshot("trip:$id",meta.toString(),at));buildReport(id,at)
    }
    fun addNote(id: String,text: String,category: String,skill: String?,difficult: Boolean,at: Long=System.currentTimeMillis(),contextJson: String?=null): Boolean=write("note") {
        val noteId="demo:"+UUID.randomUUID()
        dao.saveNote(DrivingNote(noteId,id,at,text,category))
        contextJson?.let {dao.saveEvent(CoachingEvent("$noteId:context",id,"NOTE_CONTEXT",at,"SIMULATION",JSONObject(it).put("noteId",noteId).toString()))}
        if(difficult && skill!=null)dao.saveEvent(CoachingEvent(noteId,id,"DIFFICULTY",at,"SIMULATION",JSONObject().put("profile","demo").put("skill",skill).put("text",text).toString()))
        dao.trip(id)?.endedAt?.let {buildReport(id,it)}
    }
    fun recordEvent(id: String,tripId: String,type: String,source: String,payload: String,at: Long): Boolean=write("event") {
        dao.saveEvent(CoachingEvent(id,tripId,type,at,source,payload))
    }
    fun journal()=io {dao.journal()}
    fun trips()=io {dao.recentTrips(20)}
    fun notes(id: String)=io {dao.notes(id)}
    fun report(id: String)=io {dao.report(id)}
    fun previewReport(id: String): Boolean=write("report") {buildReport(id,System.currentTimeMillis())}
    fun events(id: String)=io {dao.events(id)}
    fun samples(id: String)=io {dao.samples(id)}
    fun questRows()=io {dao.questRows()}
    fun completions(profile: String,skill: String)=completions.filter {it.profile==profile && it.skill==skill}
    fun difficultyAt(profile: String,skill: String): Long= difficulty.firstOrNull {
        val data=JSONObject(it.payload);data.optString("profile")==profile && data.optString("skill")==skill
    }?.at ?: 0L
    fun flush() {io {Unit}}
    fun close() {listeners.clear();main.removeCallbacksAndMessages(null);flush();database.close();worker.shutdown()}

    private fun write(key: String,action: ()->Unit): Boolean=runCatching {io {
        database.runInTransaction {action()};rawCache.clear();refresh();lastError=null
    };notify(key);true}.getOrElse {lastError=it.cause?.message ?: it.message;false}
    private fun buildReport(id: String,at: Long) {
        val samples=dao.samples(id);val notes=dao.notes(id);val practice=dao.completions("demo").filter {it.tripId==id}
        val review=SpeedReview.analyze(samples.map {SpeedReview.Sample(it.t,it.speedKmh?.toDouble())})
        dao.clearSpeedEvents(id,"$id:speed:%");dao.saveSpeedEvents(review.mapIndexed {i,e->TripEvent("$id:speed:$i",id,e.type,e.at,1,e.delta.toFloat())})
        val good=practice.map {when(it.skill){"LANE_CHANGE"->"한 차로 이동 연습을 마쳤어요";"INTERSECTION"->"우회전 연습을 마쳤어요";"PARKING"->"후진 주차·정렬을 연습했어요";else->"${it.skill} 연습을 기록했어요"}}.toMutableList()
        if(notes.any {it.category=="STEERING"})good.add("핸들 따라하기에서 직접 목표를 맞췄어요")
        val next=notes.filter {it.rawText.startsWith("어려웠어요")}.map {it.rawText.removePrefix("어려웠어요 · ")}.toMutableList()
        if(review.isNotEmpty())next.add("속도 변화가 컸던 ${review.size}개 구간을 당시 상황과 함께 돌아보세요")
        if(good.isEmpty())good.add("완료 전 기록이에요. 이어서 연습할 수 있어요")
        if(next.isEmpty())next.add("다음 연습에서는 도움을 한 단계 줄여볼 수 있어요")
        dao.saveReport(TripReport(id,at,"차근차근 · ${practice.size}개 동작 연습 기록",JSONArray(good).toString(),JSONArray(next).toString(),"{}"))
    }
    private fun JSONArray.objects()=(0 until length()).map {getJSONObject(it)}
    private fun JSONArray.strings()=(0 until length()).map {getString(it)}
    private fun JSONObject.nullableString(key: String)=if(isNull(key))null else getString(key)
    companion object {
        @Volatile private var instance: SharedCoachRepository?=null
        @Volatile var testOverride: SharedCoachRepository?=null
        fun get(context: Context)=testOverride ?: instance ?: synchronized(this) {
            instance ?: SharedCoachRepository(TripDatabase.get(context)).also {instance=it}
        }
    }
}
