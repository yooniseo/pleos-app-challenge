package com.hmgchallenge.pleosstarter.core.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.hmgchallenge.pleosstarter.analysis.*
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
    @Volatile private var demoResetAt=0L
    @Volatile var lastError: String?=null;private set
    private fun <T> io(action: ()->T): T = if(Thread.currentThread()==workerThread)action() else worker.submit<T> {action()}.get()
    private val dao get()=database.coachDao()
    private fun refresh() {completions=dao.completions("demo")+dao.completions("personal");difficulty=dao.difficulties();demoResetAt=dao.snapshot("demo-reset-at")?.value?.toLongOrNull() ?: 0L}
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
    fun saveQuest(profile: String,raw: String): Boolean=write("quest:$profile") {persistQuest(profile,raw)}
    /** Begin a fresh demo progression, retaining the old evidence for historical reports. */
    fun resetDemoQuest(raw: String): Boolean=write("quest:demo") {
        val root=JSONObject(raw)
        require(root.getBoolean("demo") && root.getJSONArray("evidence").length()==0 && root.getJSONObject("stages").length()==0 && !root.has("session"))
        val at=System.currentTimeMillis();val archive="demo-archive:${UUID.randomUUID()}"
        dao.snapshot("quest:demo")?.let {dao.saveSnapshot(AppSnapshot(archive,it.value,at))}
        dao.archiveDemoCompletions(archive)
        dao.saveSnapshot(AppSnapshot("demo-reset-at",at.toString(),at))
        persistQuest("demo",raw)
    }
    private fun persistQuest(profile: String,raw: String) {
        val root=JSONObject(raw);require(root.getBoolean("demo")==(profile=="demo"))
            dao.saveSnapshot(AppSnapshot("quest:$profile",raw,System.currentTimeMillis()))
            val records=root.getJSONArray("evidence").objects().map {e->PracticeCompletion(
                "$profile:${e.getString("id")}",profile,e.nullableString("tripId"),e.getString("skill"),e.getString("source"),e.getLong("at"),e.getBoolean("difficult"))}
            dao.clearCompletions(profile);dao.saveCompletions(records)
            records.forEach { record ->
                val points=PracticeRewards.points(record.skill)
                if(points>0 && record.tripId!=null)dao.saveEvent(CoachingEvent(
                    record.id+":reward",record.tripId,"PRACTICE_REWARD",record.at,record.source,
                    JSONObject().put("points",points).put("skill",record.skill).put("profile",profile).toString()))
            }
            records.forEach {e->e.tripId?.let {id->if(dao.trip(id)==null)dao.saveTrip(Trip(id,e.at,e.at,if(e.source=="SIMULATION")"REPLAY" else e.source,null))}}
            val stages=root.getJSONObject("stages");val quests=mutableListOf<Quest>()
            listOf("PREPARATION","LANE_CHANGE","INTERSECTION","SPEED","PARKING","FINISH").forEach {skill->
                val done=stages.optJSONArray(skill)?.strings().orEmpty()
                listOf("FIRST","SECOND","THIRD","FOURTH").forEachIndexed {i,stage->
                    quests.add(Quest("$profile:$skill:$stage","$skill:$stage","$skill · ${i+1}단계","퀘스트 상태와 같은 DB", "SCENARIO",1,if(stage in done)1 else 0,if(stage in done)"DONE" else "ACTIVE",0))
                }
            }
            dao.clearQuests("$profile:%");dao.saveQuests(quests)
            persistPracticeRows(profile,records)
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

    /** Backfills queryable counters from existing evidence without adding completions or rewards. */
    fun reconcilePracticeQuests(profile: String): Boolean=write("quest:$profile") {
        require(profile in setOf("demo","personal"))
        persistPracticeRows(profile,dao.completions(profile))
    }
    private fun persistPracticeRows(profile: String,records: List<PracticeCompletion>) {
        val at=records.maxOfOrNull {it.at} ?: 0L
        dao.saveQuests(com.hmgchallenge.pleosstarter.quests.CoachSkill.values().map {skill->
            val progress=com.hmgchallenge.pleosstarter.quests.PracticeProgress(records.count {it.skill==skill.name})
            Quest("$profile:practice:${skill.name}","practice:${skill.name}",skill.name,
                "누적 ${progress.completed}회 · Lv.${progress.level}","PRACTICE",30,progress.steps,"ACTIVE",at)
        })
    }

    fun beginTrip(id: String,lesson: String,speedSource: String,at: Long,targetCount: Int=if(lesson=="COMBINED")2 else 1): Boolean=write("trip") {
        dao.saveTrip(Trip(id,at,null,if(speedSource=="VEHICLE")"VEHICLE" else "REPLAY",0f))
        dao.saveSnapshot(AppSnapshot("trip:$id",JSONObject().put("lesson",lesson).put("profile","demo").put("speedSource",speedSource).put("scenario",true).put("runtime","product-v1").put("targetCount",targetCount).put("status","ACTIVE").toString(),at))
    }
    fun sample(id: String,t: Long,kmh: Float?,distanceKm: Float) {
        // Fire-and-forget only for telemetry; later state writes/readbacks queue behind this work.
        worker.execute {runCatching {
            dao.saveSample(TripSample(id,t,kmh?.takeIf {it.isFinite() && it>=0}))
            dao.trip(id)?.let {dao.saveTrip(it.copy(distanceKm=distanceKm))}
        }.onFailure {lastError=it.message}}
    }
    fun vehicleQuestBoard(source: String="VEHICLE")=io {
        com.hmgchallenge.pleosstarter.feature.quests.QuestBoardCodec.decode(dao.snapshot("vehicle-quests:$source")?.value)
    }
    fun beginVehicleQuestTrip(id: String,at: Long,source: String): Boolean=write("vehicle-quests") {
        require(source in setOf("VEHICLE","SIMULATION"))
        dao.saveTrip(Trip(id,at,null,source,0f))
        dao.saveSnapshot(AppSnapshot("trip:$id",JSONObject().put("runtime","vehicle-quest-v1")
            .put("profile",if(source=="VEHICLE")"personal" else "test").put("source",source)
            .put("lesson","VEHICLE_QUEST").put("status","ACTIVE").put("targetCount",0).toString(),at))
    }
    /** Attempt, board snapshot, queryable quest rows and feedback commit together. Event ID is retry-safe. */
    fun recordVehicleQuestAttempt(id: String,tripId: String,source: String,attempt: com.hmgchallenge.pleosstarter.feature.quests.Attempt): Boolean=write("vehicle-quests") {
        require(source in setOf("VEHICLE","SIMULATION") && dao.trip(tripId)?.source==source)
        require(com.hmgchallenge.pleosstarter.feature.quests.QuestCatalog.find(attempt.questId)!=null)
        val inserted=dao.saveEvent(CoachingEvent(id,tripId,"VEHICLE_QUEST_ATTEMPT",attempt.t,source,
            com.hmgchallenge.pleosstarter.feature.quests.QuestBoardCodec.encodeAttempt(attempt)))
        if(inserted!=-1L) {
            val board=vehicleQuestBoard(source)
            board.record(attempt)
            dao.saveSnapshot(AppSnapshot("vehicle-quests:$source",com.hmgchallenge.pleosstarter.feature.quests.QuestBoardCodec.encode(board),attempt.t))
            dao.saveQuests(com.hmgchallenge.pleosstarter.feature.quests.QuestCatalog.all.map {quest->
                val p=board.progressOf(quest.questId)
                Quest("vehicle:$source:${quest.questId}",quest.questId,quest.title,"차량 신호 조건 연습 · Lv.${p.level}",
                    "VEHICLE_OBSERVATION",30,p.steps,"ACTIVE",attempt.t)
            })
            buildReport(tripId,attempt.t)
        }
    }
    fun endTrip(id: String,at: Long,distanceKm: Float,complete: Boolean): Boolean=write("trip") {
        dao.trip(id)?.let {dao.saveTrip(it.copy(endedAt=at,distanceKm=distanceKm))}
        val meta=dao.snapshot("trip:$id")?.value?.let(::JSONObject) ?: JSONObject()
        meta.put("status",if(complete)"COMPLETE" else "INTERRUPTED")
        dao.saveSnapshot(AppSnapshot("trip:$id",meta.toString(),at));buildReport(id,at)
        if(complete && meta.optString("speedSource")=="REPLAY") {
            val plan=replayPractice(meta.optString("lesson"),null)
            if(plan!=null)dao.saveEvent(CoachingEvent("$id:next-practice",id,"NEXT_PRACTICE",at,"SIMULATION",
                JSONObject().put("lesson",meta.getString("lesson")).put("recommendationId",plan.id).put("skill",plan.weakness).toString()))
        }
    }
    fun addNote(id: String,text: String,category: String,skill: String?,difficult: Boolean,at: Long=System.currentTimeMillis(),contextJson: String?=null,source: String="SIMULATION"): Boolean=write("note") {
        require(source in setOf("SIMULATION","VEHICLE"))
        val profile=if(source=="VEHICLE")"personal" else "demo"
        val noteId="$profile:"+UUID.randomUUID()
        dao.saveNote(DrivingNote(noteId,id,at,text,category))
        contextJson?.let {dao.saveEvent(CoachingEvent("$noteId:context",id,"NOTE_CONTEXT",at,source,JSONObject(it).put("noteId",noteId).toString()))}
        if(difficult && skill!=null)dao.saveEvent(CoachingEvent(noteId,id,"DIFFICULTY",at,source,JSONObject().put("profile",profile).put("skill",skill).put("text",text).toString()))
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
    fun trip(id: String)=io {dao.trip(id)}
    fun completedSkills(id: String)=io {dao.tripCompletions(id).map {it.skill}.toSet()}
    fun points(profile: String)=io {dao.rewards(profile).sumOf {JSONObject(it.payload).getInt("points")}}
    fun tripPoints(id: String)=io {dao.events(id).filter {it.type=="PRACTICE_REWARD"}.sumOf {JSONObject(it.payload).getInt("points")}}
    /** Reconcile this runtime's unfinished records after process death; never invent a completion. */
    fun recoverInterruptedTrips(at: Long=System.currentTimeMillis()): Boolean=write("trip") {
        dao.recentTrips(100).forEach {trip->
            val snapshot=dao.snapshot("trip:${trip.tripId}") ?: return@forEach
            val meta=JSONObject(snapshot.value)
            if(meta.optString("runtime") in setOf("product-v1","vehicle-quest-v1") && meta.optString("status")=="ACTIVE") {
                dao.saveTrip(trip.copy(endedAt=trip.endedAt ?: at))
                dao.saveSnapshot(snapshot.copy(value=meta.put("status","INTERRUPTED").toString(),updatedAt=at))
                buildReport(trip.tripId,at)
            }
        }
    }
    fun questRows()=io {dao.questRows()}
    fun completions(profile: String,skill: String)=completions.filter {it.profile==profile && it.skill==skill}
    fun difficultyAt(profile: String,skill: String): Long= difficulty.firstOrNull {
        val data=JSONObject(it.payload);data.optString("profile")==profile && data.optString("skill")==skill && (profile!="demo" || it.at>demoResetAt)
    }?.at ?: 0L
    fun flush() {io {Unit}}
    fun close() {listeners.clear();main.removeCallbacksAndMessages(null);flush();database.close();worker.shutdown()}

    private fun write(key: String,action: ()->Unit): Boolean=runCatching {io {
        database.runInTransaction {action()};rawCache.clear();refresh();lastError=null
    };notify(key);true}.getOrElse {lastError=it.cause?.message ?: it.message;false}
    private fun buildReport(id: String,at: Long) {
        val samples=dao.samples(id);val notes=dao.notes(id);val practice=dao.tripCompletions(id)
        val trip=dao.trip(id)
        val attempts=dao.events(id).filter {it.type=="VEHICLE_QUEST_ATTEMPT"}.map {
            com.hmgchallenge.pleosstarter.feature.quests.QuestBoardCodec.decodeAttempt(it.payload)
        }
        // 분석: 팀 SpeedReview 기준 + 심각도·3초 병합, 깜빡이·회전 전 감속은 차량 신호 퀘스트 시도에서 계산
        val analysis=DrivingAnalyzer.analyze(samples.map {SpeedReview.Sample(it.t,it.speedKmh?.toDouble())},attempts)
        dao.clearSpeedEvents(id,"$id:speed:%")
        dao.saveSpeedEvents(analysis.events.mapIndexed {i,e->TripEvent("$id:speed:$i",id,e.type,e.t,e.severity,e.deltaKmh.toFloat())})
        val good=practice.groupBy {it.skill}.map {(skill,records)->when(skill){"LANE_CHANGE"->"한 차로 이동 연습 ${records.size}회";"INTERSECTION"->"우회전 연습 완료";"PARKING"->"후진 주차·정렬 연습 완료";"PREPARATION"->"출발 점검 직접 확인";"SPEED"->"교차로 감속 시연 완료 · 차간거리 판정 별도";"FINISH"->"하차 점검 직접 확인";else->"$skill 연습 기록"}}.toMutableList()
        attempts.filter {it.success}.groupBy {it.questId}.forEach {(questId,records)->
            val title=com.hmgchallenge.pleosstarter.feature.quests.QuestCatalog.find(questId)?.title.orEmpty()
            good.add("$title 신호 조건을 ${records.size}회 충족했어요")
        }
        if(notes.any {it.category=="STEERING"})good.add("핸들 따라하기에서 직접 목표를 맞췄어요")
        val next=attempts.filterNot {it.success}.takeLast(3).map {it.result.message}.toMutableList()
        // 자동 시연(REPLAY)은 정해진 속도로 움직이므로 실력 점수를 매기지 않고 기존 문구를 유지한다.
        val replay=trip?.source=="REPLAY" || dao.snapshot("trip:$id")?.value?.let(::JSONObject)?.optBoolean("scenario")==true
        if(replay && analysis.events.isNotEmpty())next.add("속도 변화가 컸던 ${analysis.events.size}개 구간을 당시 상황과 함께 돌아보세요")
        val feedback=FeedbackWriter.write(analysis.takeUnless {replay},
            notes.filter {it.category!="STEERING"}.map {NoteLine(it.t,it.rawText,it.category)},good,next)
        dao.saveReport(TripReport(id,at,if(replay)"차근차근 · ${practice.size}개 동작 연습 기록" else feedback.headline,
            JSONArray(feedback.good).toString(),JSONArray(feedback.next).toString(),if(replay)"{}" else scoresJson(analysis)))
    }

    /**
     * 최근 주행(같은 출처)의 점수·메모와 차량 신호 퀘스트 진행으로 맞춤 퀘스트를 고른다.
     * 저장하지 않고 볼 때마다 다시 계산한다: 리포트·메모·퀘스트 진행이 바뀌면 추천도 바로 따라간다.
     */
    fun recommendations(source: String="VEHICLE"): RecommendationResult=io {computeRecommendations(source)}
    /** 저장된 점수를 다시 읽는다. 예전 리포트("{}")는 모든 점수가 null. */
    fun scores(id: String): TripScores?=io {
        val trip=dao.trip(id);val report=dao.report(id)
        if(trip==null || report==null)null else parseScores(trip,report.skillScores)
    }
    private fun computeRecommendations(source: String): RecommendationResult {
        val trips=dao.recentTripsBySource(source,100).filterNot {
            source=="VEHICLE" && dao.snapshot("trip:${it.tripId}")?.value?.let(::JSONObject)?.optBoolean("scenario")==true
        }.take(QuestRecommender.RECENT_TRIPS)
        val scored=trips.mapNotNull {trip->dao.report(trip.tripId)?.let {parseScores(trip,it.skillScores)}}
        val difficultyIds=trips.flatMap {dao.events(it.tripId)}.filter {it.type=="DIFFICULTY"}.map {it.id}.toSet()
        val notes=trips.flatMap {dao.notes(it.tripId)}.filter {it.rawText.startsWith("어려웠어요") || it.noteId in difficultyIds}.map {
            when(it.category){"INTERSECTION"->"RIGHT_TURN";"SPEED"->"SPEED_CONTROL";else->it.category}
        }
        val board=com.hmgchallenge.pleosstarter.feature.quests.QuestBoardCodec.decode(dao.snapshot("vehicle-quests:$source")?.value)
        val vehicle=com.hmgchallenge.pleosstarter.feature.quests.QuestCatalog.all.associate {q->
            val p=board.progressOf(q.questId)
            q.questId to VehicleProgress(p.level,p.steps,com.hmgchallenge.pleosstarter.feature.quests.QuestProgress.STEPS_PER_LEVEL)
        }
        val result=QuestRecommender.recommend(scored,notes,vehicle)
        if(source!="REPLAY")return result
        val practice=result.recommendations.map {q->
            q.copy(kind=RecommendationKind.PRACTICE,progress=0,target=0,vehicleQuestId=null)
        }.ifEmpty {
            val last=trips.firstNotNullOfOrNull {trip->
                dao.events(trip.tripId).firstOrNull {it.type=="NEXT_PRACTICE"}?.payload?.let(::JSONObject)
                    ?: dao.snapshot("trip:${trip.tripId}")?.value?.let(::JSONObject)?.takeIf {it.optString("status")=="COMPLETE"}
            }
            listOfNotNull(replayPractice(last?.optString("lesson"),notes.firstOrNull {it in setOf("LANE_CHANGE","RIGHT_TURN","LEFT_TURN","U_TURN","PARKING","SPEED_CONTROL")}))
        }
        return result.copy(recommendations=practice)
    }

    private fun replayPractice(lesson: String?,difficulty: String?): Recommendation? {
        if(lesson==null)return null
        val topic=difficulty ?: when(lesson) {
            "LANE"->"LANE_CHANGE"
            "TURN"->"RIGHT_TURN"
            "PARKING","COMBINED"->"PARKING"
            else->return null
        }
        val (skill,title,goal)=when(topic) {
            "LANE_CHANGE"->Triple("TURN_SIGNAL","차로를 바꾸기 전 준비하기","방향지시등을 켜고 미러와 사각지대를 확인해 보세요")
            "RIGHT_TURN"->Triple("TURN_SLOWDOWN","우회전 전 횡단보도 살피기","우회전 연습에서 감속과 주변 확인 순서를 익혀 보세요")
            "SPEED_CONTROL"->Triple("BRAKING","교차로 전에 서서히 감속하기","코치 안내를 들으며 감속을 시작하는 지점을 찾아보세요")
            "LEFT_TURN","U_TURN"->Triple(topic,if(topic=="LEFT_TURN")"좌회전 준비하기" else "유턴 준비하기","다음 주행 전 경로에서 해당 구간을 미리 살펴보세요")
            else->Triple("PARKING","후진 주차 핸들 복원 연습","주차 시연에서 차체가 정렬될 때 핸들을 푸는 순간을 확인해 보세요")
        }
        val reason=if(difficulty!=null)"최근 어렵다고 남긴 상황을 다음 연습으로 골랐어요" else "방금 마친 시연에서 다음에 직접 익혀 볼 동작이에요"
        return Recommendation("REPLAY_$skill",skill,title,goal,reason,RecommendationKind.PRACTICE,0,0)
    }
    private fun scoresJson(a: TripAnalysis)=JSONObject().apply {
        a.scores.forEach {(skill,score)->put(skill.name,score ?: JSONObject.NULL)}
        put("lowConfidence",a.lowConfidence);put("measuredSeconds",a.measuredSeconds)
        put("harshAccel",a.harshAccelCount);put("harshBrake",a.harshBrakeCount)
        put("signalAttempts",a.signalAttempts);put("signalSuccesses",a.signalSuccesses)
    }.toString()
    private fun parseScores(trip: Trip,json: String): TripScores {
        val root=runCatching {JSONObject(json)}.getOrDefault(JSONObject())
        val scores=DrivingSkill.values().associateWith {s->if(root.has(s.name) && !root.isNull(s.name))root.optInt(s.name) else null}
        return TripScores(trip.tripId,trip.startedAt,scores,root.optBoolean("lowConfidence",true))
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
