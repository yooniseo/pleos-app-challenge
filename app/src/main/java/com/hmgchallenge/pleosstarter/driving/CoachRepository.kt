package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Local event journal and quest integration point. No networking, grading, or quest catalogue. */
class CoachRepository(context: Context) {
    private val database=com.hmgchallenge.pleosstarter.core.data.SharedCoachRepository.get(context)
    private val file = File(context.filesDir, "driving-coach-events.jsonl")
    private val prefs = context.getSharedPreferences("driving-coach", Context.MODE_PRIVATE)
    var questPort: QuestCoachPort = UnconnectedQuestPort
    var feedbackPort: FeedbackCoachPort = UnconnectedFeedbackPort

    @Synchronized fun append(event: CoachEvent) {
        check(database.recordEvent(event.eventId,event.tripId,"LEGACY_COACH",event.source.name,encode(event).toString(),event.timestamp)) {"코치 이벤트 DB 저장 실패"}
        // A broken optional consumer must not prevent the coach from working. Journal supports replay.
        runCatching { questPort.onCoachEvent(event) }
        runCatching { feedbackPort.onCoachEvent(event) }
        event.feedbackNote()?.let { note -> runCatching { feedbackPort.onNote(note) } }
    }

    /** Cursor is eventId from the last successfully handled record. Missing cursors replay from start. */
    @Synchronized fun eventsAfter(eventId: String? = null): List<CoachEvent> {
        if(database.snapshot("legacy-journal-imported")==null) {
            file.takeIf {it.exists()}?.useLines {lines->lines.forEach {line->runCatching {decode(JSONObject(line))}.getOrNull()?.let {e->
                check(database.recordEvent(e.eventId,e.tripId,"LEGACY_COACH",e.source.name,encode(e).toString(),e.timestamp))
            }}}
            check(database.saveSnapshot("legacy-journal-imported","true"))
        }
        val all=database.journal().mapNotNull {runCatching {decode(JSONObject(it.payload))}.getOrNull()}
        val offset = eventId?.let { id -> all.indexOfFirst { it.eventId == id } } ?: -1
        return all.drop(offset + 1)
    }

    fun preferences(): CoachPreferences {
        if (questPort !== UnconnectedQuestPort) return questPort.preferences()
        val settings=JSONObject(database.snapshot("settings:voice") {
            JSONObject().apply {SituationType.values().forEach {put(it.name,prefs.getString(it.name,"DETAILED"))};put("voice",prefs.getBoolean("voice",false));put("demo-indicator",prefs.getBoolean("demo-indicator",false))}.toString()
        }!!)
        return CoachPreferences(SituationType.values().associateWith {
            runCatching {GuidanceLevel.valueOf(settings.optString(it.name,"DETAILED"))}.getOrDefault(GuidanceLevel.DETAILED)
        },settings.optBoolean("voice"),settings.optBoolean("demo-indicator"))
    }

    fun savePreferences(value: CoachPreferences) {
        check(database.saveSnapshot("settings:voice",JSONObject().apply {
            SituationType.values().forEach {put(it.name,value.level(it).name)}
            put("voice",value.voiceEnabled);put("demo-indicator",value.demoAutomaticIndicator)
        }.toString())) {"음성 설정 DB 저장 실패"}
    }

    fun focusedGoal() = questPort.focusedGoalLabel()

    companion object {
        fun encode(e: CoachEvent) = JSONObject().apply {
            put("schemaVersion", e.schemaVersion); put("eventId", e.eventId); put("tripId", e.tripId)
            put("situationInstanceId", e.situationInstanceId); put("situationType", e.situationType.name)
            put("eventType", e.eventType.name); put("timestamp", e.timestamp); put("source", e.source.name)
            put("assistanceUsed", e.assistanceUsed); put("guidanceLevel", e.guidanceLevel.name)
            put("cueId", e.cueId ?: JSONObject.NULL); put("detail", e.detail)
        }
        fun decode(o: JSONObject) = CoachEvent(o.getString("eventId"), o.getString("tripId"),
            o.getString("situationInstanceId"), SituationType.valueOf(o.getString("situationType")),
            CoachEventType.valueOf(o.getString("eventType")), o.getLong("timestamp"),
            EvidenceSource.valueOf(o.getString("source")), o.getBoolean("assistanceUsed"),
            GuidanceLevel.valueOf(o.getString("guidanceLevel")),
            if (o.isNull("cueId")) null else o.getString("cueId"), o.optString("detail"), o.getInt("schemaVersion"))
    }
}
