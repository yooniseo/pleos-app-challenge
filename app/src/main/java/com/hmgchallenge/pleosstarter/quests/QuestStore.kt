package com.hmgchallenge.pleosstarter.quests

import android.content.Context
import com.hmgchallenge.pleosstarter.driving.DrivingGuideHost
import org.json.JSONArray
import org.json.JSONObject

/** v1 prototype files are preserved; their milestone meaning is not silently reused as quest stages. */
class QuestStore(context: Context, private val demo: Boolean) {
    private val preferences = context.getSharedPreferences(if (demo) "tutorial_quest_demo_v2" else "tutorial_quest_personal_v2", Context.MODE_PRIVATE)
    fun load(): QuestState {
        val raw = preferences.getString("state", null) ?: return QuestState(demo = demo)
        val root = JSONObject(raw)
        require(root.getInt("version") == 2 && root.getBoolean("demo") == demo)
        val options = root.getJSONObject("settings")
        val stages = mutableMapOf<CoachSkill, Set<QuestStage>>()
        val levels = mutableMapOf<CoachSkill, GuidanceLevel>()
        val assessments = mutableMapOf<CoachSkill, SelfAssessment>()
        CoachSkill.values().forEach { skill ->
            root.getJSONObject("stages").optJSONArray(skill.name)?.let { array ->
                val completed = array.strings().map(QuestStage::valueOf).toSet()
                require(completed == QuestStage.values().take(completed.size).toSet())
                stages[skill] = completed
            }
            options.getJSONObject("levels").optString(skill.name).takeIf { it.isNotBlank() }?.let { levels[skill] = GuidanceLevel.valueOf(it) }
            root.getJSONObject("assessments").optJSONObject(skill.name)?.let {
                assessments[skill] = SelfAssessment(it.getString("text"), it.getLong("at"))
            }
        }
        val evidence = root.getJSONArray("evidence").objects().map {
            PracticeEvidence(it.getString("id"), CoachSkill.valueOf(it.getString("skill")), EvidenceSource.valueOf(it.getString("source")),
                it.getLong("at"), it.optionalString("tripId"), it.getBoolean("difficult"))
        }
        require(evidence.size <= 2000 && evidence.map { it.id }.toSet().size == evidence.size && evidence.all { demo == (it.source == EvidenceSource.SIMULATION) })
        return QuestState(demo, root.getJSONArray("completed").strings().toSet(), stages, evidence,
            CoachSettings(levels, options.getJSONArray("disabled").strings().map(CoachSkill::valueOf).toSet(),
                options.getJSONArray("graduated").strings().map(CoachSkill::valueOf).toSet(), options.getBoolean("celebration")),
            root.optionalString("focusGoalId"), root.optionalString("nextGoalId"), root.optJSONObject("session")?.let(::decodeSession),
            root.getJSONArray("feedback").objects().map { report -> DrivingFeedback(report.getString("tripId"), EvidenceSource.valueOf(report.getString("source")),
                report.getLong("endedAt"), report.getJSONArray("difficultSituations").strings().toSet(), report.getJSONArray("noteCategories").strings().toSet(),
                report.optionalInt("abruptAccelerationCount"), report.optionalInt("abruptBrakingCount")) }, assessments)
    }

    fun save(state: QuestState): Boolean {
        require(state.demo == demo)
        val settings = state.settings
        val root = JSONObject().put("version", 2).put("demo", demo).put("completed", JSONArray(state.completed.toList()))
            .put("focusGoalId", state.focusGoalId).put("nextGoalId", state.nextGoalId).put("stages", JSONObject().apply {
                state.stages.forEach { (skill, stages) -> put(skill.name, JSONArray(stages.map { it.name })) }
            }).put("settings", JSONObject().put("levels", JSONObject().apply {
                settings.levels.forEach { (skill, level) -> put(skill.name, level.name) }
            }).put("disabled", JSONArray(settings.disabled.map { it.name })).put("graduated", JSONArray(settings.graduated.map { it.name }))
                .put("celebration", settings.celebration))
            .put("evidence", JSONArray(state.evidence.map {
                JSONObject().put("id", it.id).put("skill", it.skill.name).put("source", it.source.name).put("at", it.at)
                    .put("tripId", it.tripId).put("difficult", it.difficult)
            })).put("assessments", JSONObject().apply { state.assessments.forEach { (skill, assessment) ->
                put(skill.name, JSONObject().put("text", assessment.text).put("at", assessment.at))
            } }).put("feedback", JSONArray(state.feedback.map {
                JSONObject().put("tripId", it.tripId).put("source", it.source.name).put("endedAt", it.endedAt)
                    .put("difficultSituations", JSONArray(it.difficultSituations.toList())).put("noteCategories", JSONArray(it.noteCategories.toList()))
                    .put("abruptAccelerationCount", it.abruptAccelerationCount).put("abruptBrakingCount", it.abruptBrakingCount)
            }))
        state.session?.let { root.put("session", encodeSession(it)) }
        return preferences.edit().putString("state", root.toString()).commit()
    }
    private fun contextJson(ctx: SituationContext) = JSONObject().put("tripId", ctx.tripId).put("occurrenceId", ctx.occurrenceId)
        .put("maneuverId", ctx.maneuverId).put("kind", ctx.kind.name).put("source", ctx.source.name).put("at", ctx.at)
    private fun decodeContext(json: JSONObject) = SituationContext(json.getString("tripId"), json.getString("occurrenceId"),
        json.getString("maneuverId"), SituationKind.valueOf(json.getString("kind")), EvidenceSource.valueOf(json.getString("source")), json.getLong("at"))
    private fun encodeSession(session: PracticeSession) = JSONObject().put("tripId", session.tripId).put("source", session.source.name)
        .put("startedAt", session.startedAt).put("activeOccurrenceId", session.activeOccurrenceId).put("finishedAt", session.finishedAt).put("reviewed", session.reviewed)
        .put("situations", JSONArray(session.situations.map {
            JSONObject().put("context", contextJson(it.context)).put("outcome", it.outcome?.name).put("difficult", it.difficult).put("confirmation", it.confirmation.name)
        })).put("events", JSONArray(session.events.map {
            JSONObject().put("eventId", it.eventId).put("context", contextJson(it.context)).put("type", it.type.name).put("at", it.at).put("detail", it.detail).put("confirmation", it.confirmation.name).put("assistanceUsed", it.assistanceUsed.name)
        }))
    private fun decodeSession(json: JSONObject): PracticeSession {
        val session = PracticeSession(json.getString("tripId"), EvidenceSource.valueOf(json.getString("source")), json.getLong("startedAt"),
            json.optionalString("activeOccurrenceId"), json.getJSONArray("situations").objects().map {
                SituationRecord(decodeContext(it.getJSONObject("context")), it.optionalString("outcome")?.let(SituationEventType::valueOf), it.getBoolean("difficult"), ConfirmationBasis.valueOf(it.getString("confirmation")))
            }, json.getJSONArray("events").objects().map {
                SituationEvent(it.getString("eventId"), decodeContext(it.getJSONObject("context")), SituationEventType.valueOf(it.getString("type")), it.getLong("at"), it.getString("detail"), ConfirmationBasis.valueOf(it.getString("confirmation")), AssistanceUsed.valueOf(it.optString("assistanceUsed", "UNKNOWN")))
            }, json.optionalLong("finishedAt"), json.getBoolean("reviewed"))
        require(demo == (session.source == EvidenceSource.SIMULATION) && session.events.size <= 500)
        require(session.situations.all { it.context.tripId == session.tripId && demo == (it.context.source == EvidenceSource.SIMULATION) })
        require(session.activeOccurrenceId == null || session.current?.outcome == null && session.current != null)
        return session
    }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optionalString(key: String) = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONObject.optionalInt(key: String) = if (has(key) && !isNull(key)) getInt(key) else null
    private fun JSONObject.optionalLong(key: String) = if (has(key) && !isNull(key)) getLong(key) else null

    fun observe(onState: (QuestState) -> Unit, onError: (Exception) -> Unit): java.io.Closeable {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "state") try { onState(load()) } catch (error: Exception) { onError(error) }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try { onState(load()) } catch (error: Exception) { onError(error) }
        return java.io.Closeable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

/** Public team adapter. True = new state was accepted and saved; false = duplicate/rejected event. */
class QuestIntegration(context: Context, demo: Boolean = false,
    private val onChanged: ((QuestState) -> Unit)? = null) : DrivingGuideHost {
    private val store = QuestStore(context.applicationContext, demo)
    override fun startTrip(tripId: String, source: EvidenceSource, at: Long) = update { QuestEngine.beginTrip(it, tripId, source, at) }
    override fun enterSituation(eventId: String, situation: SituationContext) = dispatch(eventId, situation, SituationEventType.ENTERED, situation.at)
    override fun reportObservation(eventId: String, situation: SituationContext, at: Long, detail: String) = dispatch(eventId, situation, SituationEventType.OBSERVED, at, detail)
    override fun reportGuidance(eventId: String, situation: SituationContext, at: Long, used: AssistanceUsed) =
        update { QuestEngine.acceptEvent(it, SituationEvent(eventId, situation, SituationEventType.GUIDANCE_PROVIDED, at, assistanceUsed = used)) }
    override fun confirmPractice(eventId: String, situation: SituationContext, at: Long, basis: ConfirmationBasis) =
        update { QuestEngine.acceptEvent(it, SituationEvent(eventId, situation, SituationEventType.PRACTICED, at, confirmation = basis)) }
    override fun skipSituation(eventId: String, situation: SituationContext, at: Long) = dispatch(eventId, situation, SituationEventType.SKIPPED, at)
    override fun recordDifficulty(eventId: String, situation: SituationContext, at: Long, detail: String) = dispatch(eventId, situation, SituationEventType.DIFFICULTY, at, detail)
    override fun cancelSituation(eventId: String, situation: SituationContext, at: Long) = dispatch(eventId, situation, SituationEventType.CANCELLED, at)
    override fun markUnverified(eventId: String, situation: SituationContext, at: Long) = dispatch(eventId, situation, SituationEventType.UNVERIFIED, at)
    override fun deferSituation(eventId: String, situation: SituationContext, at: Long) = dispatch(eventId, situation, SituationEventType.DEFERRED, at)
    override fun finishTrip(tripId: String, at: Long) = update { QuestEngine.finishTrip(it, tripId, at) }
    fun onFeedback(report: DrivingFeedback) = update { QuestEngine.consumeFeedback(it, report) }
    fun snapshot() = synchronized(lock) { store.load() }
    fun observe(onState: (QuestState) -> Unit, onError: (Exception) -> Unit) = store.observe(onState, onError)
    private fun dispatch(id: String, ctx: SituationContext, type: SituationEventType, at: Long, detail: String = "") =
        update { QuestEngine.acceptEvent(it, SituationEvent(id, ctx, type, at, detail)) }
    private fun update(transform: (QuestState) -> QuestState): Boolean {
        val next = synchronized(lock) {
            val before = store.load(); val changed = transform(before)
            if (changed == before || !store.save(changed)) return false
            changed
        }
        onChanged?.invoke(next)
        return true
    }
    companion object { internal val lock = Any() }
}
