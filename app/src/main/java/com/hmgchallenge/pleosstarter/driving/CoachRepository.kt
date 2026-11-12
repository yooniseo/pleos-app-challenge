package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Local event journal and quest integration point. No networking, grading, or quest catalogue. */
class CoachRepository(context: Context) {
    private val file = File(context.filesDir, "driving-coach-events.jsonl")
    private val prefs = context.getSharedPreferences("driving-coach", Context.MODE_PRIVATE)
    var questPort: QuestCoachPort = UnconnectedQuestPort
    var feedbackPort: FeedbackCoachPort = UnconnectedFeedbackPort

    @Synchronized fun append(event: CoachEvent) {
        file.appendText(encode(event).toString() + "\n", Charsets.UTF_8)
        // A broken optional consumer must not prevent the coach from working. Journal supports replay.
        runCatching { questPort.onCoachEvent(event) }
        runCatching { feedbackPort.onCoachEvent(event) }
        event.feedbackNote()?.let { note -> runCatching { feedbackPort.onNote(note) } }
    }

    /** Cursor is eventId from the last successfully handled record. Missing cursors replay from start. */
    @Synchronized fun eventsAfter(eventId: String? = null): List<CoachEvent> {
        if (!file.exists()) return emptyList()
        val all = file.useLines { lines -> lines.mapNotNull { runCatching { decode(JSONObject(it)) }.getOrNull() }.toList() }
        val offset = eventId?.let { id -> all.indexOfFirst { it.eventId == id } } ?: -1
        return all.drop(offset + 1)
    }

    fun preferences(): CoachPreferences {
        if (questPort !== UnconnectedQuestPort) return questPort.preferences()
        return CoachPreferences(SituationType.values().associateWith {
            runCatching { GuidanceLevel.valueOf(prefs.getString(it.name, "DETAILED")!!) }.getOrDefault(GuidanceLevel.DETAILED)
        }, prefs.getBoolean("voice", false), prefs.getBoolean("demo-indicator", false))
    }

    fun savePreferences(value: CoachPreferences) {
        prefs.edit().apply {
            SituationType.values().forEach { putString(it.name, value.level(it).name) }
            putBoolean("voice", value.voiceEnabled); putBoolean("demo-indicator", value.demoAutomaticIndicator)
        }.apply()
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
