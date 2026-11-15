package com.hmgchallenge.pleosstarter.quests

/** Quest rules stay independent of a route engine, a rendering screen, and Pleos SDK availability. */
object QuestEngine {
    fun learn(state: QuestState, skill: CoachSkill) = recalculate(markStage(state, skill, QuestStage.FIRST), skill)
    fun confirmQuest(state: QuestState, id: String, checked: Set<Int> = emptySet(), evidenceId: String = "", at: Long = 0): QuestState {
        val quest = QuestCatalog.get(id)
        if (quest.prerequisite != null && quest.prerequisite !in state.completed) return state
        if (quest.action == QuestAction.CHECKLIST && checked != quest.checks.indices.toSet()) return state
        if (quest.action !in setOf(QuestAction.CHECKLIST, QuestAction.SELF_CHECK, QuestAction.LESSON)) return state
        if (quest.action != QuestAction.LESSON && (evidenceId.isBlank() || at <= 0)) return state
        var next = state.copy(completed = state.completed + id)
        if (quest.action != QuestAction.SELF_CHECK) next = learn(next, quest.skill)
        if (quest.action != QuestAction.LESSON) next = recordPractice(next,
            PracticeEvidence(evidenceId, quest.skill, if (state.demo) EvidenceSource.SIMULATION else EvidenceSource.USER, at))
        return next
    }
    fun recordPractice(state: QuestState, evidence: PracticeEvidence): QuestState {
        if (evidence.id.isBlank() || evidence.at <= 0 || state.demo != (evidence.source == EvidenceSource.SIMULATION) ||
            state.evidence.any { it.id == evidence.id } || state.evidence.size >= 2000) return state
        return recalculate(state.copy(evidence = state.evidence + evidence), evidence.skill)
    }
    fun selectGoal(state: QuestState, id: String): QuestState =
        if (id in QuestCatalog.goalIds) state.copy(focusGoalId = id, completed = state.completed + "small_goal") else state
    fun selectNextGoal(state: QuestState, id: String): QuestState =
        if (id in QuestCatalog.goalIds && state.session?.reviewed == true)
            state.copy(nextGoalId = id, completed = state.completed + "next_goal") else state
    fun beginTrip(state: QuestState, tripId: String, source: EvidenceSource, at: Long): QuestState {
        if (tripId.isBlank() || at <= 0 || state.demo != (source == EvidenceSource.SIMULATION) ||
            state.session?.let { it.finishedAt == null || it.tripId == tripId } == true ||
            state.evidence.any { it.tripId == tripId }) return state
        return state.copy(focusGoalId = state.nextGoalId ?: state.focusGoalId, nextGoalId = null,
            session = PracticeSession(tripId, source, at))
    }
    fun acceptEvent(state: QuestState, event: SituationEvent): QuestState {
        val session = state.session ?: return state
        val ctx = event.context
        if (session.finishedAt != null || event.eventId.isBlank() || ctx.occurrenceId.isBlank() || ctx.maneuverId.isBlank() ||
            ctx.tripId != session.tripId || state.demo != (ctx.source == EvidenceSource.SIMULATION) ||
            ctx.at < session.startedAt || event.at < ctx.at || event.at < (session.events.lastOrNull()?.at ?: session.startedAt) ||
            session.events.any { it.eventId == event.eventId }) return state
        if (event.type == SituationEventType.ENTERED) {
            if (session.activeOccurrenceId != null || session.situations.any { it.context.occurrenceId == ctx.occurrenceId }) return state
            return state.copy(session = session.copy(activeOccurrenceId = ctx.occurrenceId,
                situations = session.situations + SituationRecord(ctx), events = (session.events + event).takeLast(500)))
        }
        val record = session.current ?: return state
        if (record.context != ctx) return state
        if (event.type == SituationEventType.PRACTICED && (event.confirmation == ConfirmationBasis.UNKNOWN ||
                state.demo != (event.confirmation == ConfirmationBasis.DEMO))) return state
        val changed = when (event.type) {
            SituationEventType.DIFFICULTY -> record.copy(difficult = true)
            SituationEventType.PRACTICED, SituationEventType.SKIPPED, SituationEventType.CANCELLED,
            SituationEventType.UNVERIFIED, SituationEventType.DEFERRED -> record.copy(outcome = event.type, confirmation = event.confirmation)
            else -> record
        }
        val ended = event.type in setOf(SituationEventType.PRACTICED, SituationEventType.SKIPPED, SituationEventType.CANCELLED, SituationEventType.UNVERIFIED, SituationEventType.DEFERRED)
        var next = state.copy(session = session.copy(activeOccurrenceId = if (ended) null else ctx.occurrenceId,
            situations = session.situations.map { if (it.context.occurrenceId == ctx.occurrenceId) changed else it },
            events = (session.events + event.copy(detail = event.detail.take(500))).takeLast(500)))
        if (event.type == SituationEventType.DIFFICULTY) next = next.copy(completed = next.completed + "note")
        if (event.type != SituationEventType.PRACTICED) return next
        // Right turn plus exit of that same maneuver is one intersection experience.
        if (ctx.kind == SituationKind.TURN_RIGHT) return next
        var difficult = changed.difficult
        var userConfirmed = changed.confirmation == ConfirmationBasis.USER_CONFIRMATION
        if (ctx.kind == SituationKind.INTERSECTION_EXIT) {
            val turn = next.session!!.situations.lastOrNull {
                it.context.maneuverId == ctx.maneuverId && it.context.kind == SituationKind.TURN_RIGHT &&
                    it.outcome == SituationEventType.PRACTICED && it.context.at <= ctx.at
            } ?: return next
            difficult = difficult || turn.difficult
            userConfirmed = userConfirmed || turn.confirmation == ConfirmationBasis.USER_CONFIRMATION
        }
        next = recordPractice(next, PracticeEvidence("${ctx.tripId}:${ctx.maneuverId}:${ctx.kind.skill.name}",
            ctx.kind.skill, if (userConfirmed) EvidenceSource.USER else ctx.source, event.at, ctx.tripId, difficult))
        next = next.copy(completed = next.completed + when (ctx.kind) {
            SituationKind.LANE_RIGHT -> "lane_right"
            SituationKind.INTERSECTION_EXIT -> "intersection"
            SituationKind.PARKING -> "parking_reverse"
            SituationKind.PREPARATION -> "prepare"
            SituationKind.SPEED -> "smooth"
            else -> "finish"
        })
        return next
    }
    fun finishTrip(state: QuestState, tripId: String, at: Long): QuestState {
        val session = state.session ?: return state
        if (session.tripId != tripId || session.finishedAt != null || session.activeOccurrenceId != null ||
            at < (session.events.lastOrNull()?.at ?: session.startedAt)) return state
        return state.copy(session = session.copy(finishedAt = at))
    }
    fun consumeFeedback(state: QuestState, report: DrivingFeedback): QuestState {
        if (report.tripId.isBlank() || report.endedAt <= 0 || state.feedback.any { it.tripId == report.tripId } ||
            state.demo != (report.source == EvidenceSource.SIMULATION) ||
            (report.abruptAccelerationCount ?: 0) < 0 || (report.abruptBrakingCount ?: 0) < 0) return state
        return state.copy(feedback = (state.feedback + report).takeLast(50))
    }
    fun review(state: QuestState): QuestState {
        val session = state.session ?: return state
        if (session.finishedAt == null) return state
        return state.copy(completed = state.completed + "review", session = session.copy(reviewed = true))
    }
    fun completeFinalStage(state: QuestState, skill: CoachSkill, text: String, at: Long): QuestState {
        if (text.isBlank() || at <= 0 || QuestStage.THIRD !in state.completedStages(skill)) return state
        return markStage(state.copy(assessments = state.assessments +
            (skill to SelfAssessment(text.trim().take(500), at))), skill, QuestStage.FOURTH)
    }
    fun eligibleForGraduation(state: QuestState, skill: CoachSkill) = state.completedStages(skill).size == 4
    fun graduate(state: QuestState, skill: CoachSkill) = if (!eligibleForGraduation(state, skill)) state else
        state.copy(completed = state.completed + "graduate", settings = state.settings.copy(
            graduated = state.settings.graduated + skill, levels = state.settings.levels + (skill to GuidanceLevel.ON_REQUEST)))
    fun restoreCoach(state: QuestState, skill: CoachSkill) = state.copy(settings = state.settings.copy(
        graduated = state.settings.graduated - skill, disabled = state.settings.disabled - skill,
        levels = state.settings.levels + (skill to GuidanceLevel.DETAILED)))
    fun recommendations(state: QuestState): List<QuestDefinition> {
        val recent = state.feedback.takeLast(5)
        val localNotes = state.session?.takeIf { session -> recent.none { it.tripId == session.tripId } }?.situations.orEmpty().filter { it.difficult }
        val lane = recent.count { "LANE_CHANGE" in it.noteCategories || SituationKind.LANE_RIGHT.id in it.difficultSituations } + localNotes.count { it.context.kind.skill == CoachSkill.LANE_CHANGE }
        val turn = recent.count { it.noteCategories.any { category -> category in setOf("LEFT_TURN", "RIGHT_TURN", "UNPROTECTED_LEFT") } ||
            it.difficultSituations.any { id -> id in setOf(SituationKind.TURN_RIGHT.id, SituationKind.INTERSECTION_EXIT.id) } }
            + localNotes.count { it.context.kind.skill == CoachSkill.INTERSECTION }
        val parking = recent.count { "PARKING" in it.noteCategories } + localNotes.count { it.context.kind.skill == CoachSkill.PARKING }
        val ranked = listOf("lane_right" to lane, "intersection" to turn, "parking_reverse" to parking)
            .sortedByDescending { it.second }.filter { it.second > 0 }.map { QuestCatalog.get(it.first) }
        return (ranked + QuestCatalog.goalIds.map(QuestCatalog::get)).distinctBy { it.id }.take(3)
    }
    private fun recalculate(state: QuestState, skill: CoachSkill): QuestState {
        var next = state
        if (QuestStage.FIRST in next.completedStages(skill) && next.practiceCount(skill) >= 1) next = markStage(next, skill, QuestStage.SECOND)
        if (QuestStage.SECOND in next.completedStages(skill) && next.practiceCount(skill) >= StageQuestCatalog.REPEAT_PRACTICE_TARGET)
            next = markStage(next, skill, QuestStage.THIRD)
        return next
    }
    private fun markStage(state: QuestState, skill: CoachSkill, stage: QuestStage) =
        state.copy(stages = state.stages + (skill to (state.completedStages(skill) + stage)))
}
