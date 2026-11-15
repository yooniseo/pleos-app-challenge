package com.hmgchallenge.pleosstarter.quests

import com.hmgchallenge.pleosstarter.driving.guideUiModel
import org.junit.Assert.*
import org.junit.Test

class QuestEngineTest {
    private fun start(demo: Boolean = true) = QuestEngine.beginTrip(QuestState(demo = demo), "trip", if (demo) EvidenceSource.SIMULATION else EvidenceSource.VEHICLE, 1000)
    private fun ctx(kind: SituationKind = SituationKind.LANE_RIGHT, id: String = kind.id, maneuver: String = "junction-A", source: EvidenceSource = EvidenceSource.SIMULATION) =
        SituationContext("trip", id, maneuver, kind, source, 1001)
    private fun enter(state: QuestState, context: SituationContext) = QuestEngine.acceptEvent(state, SituationEvent("enter-${context.occurrenceId}", context, SituationEventType.ENTERED, 1002))
    private fun finish(state: QuestState, context: SituationContext, basis: ConfirmationBasis = ConfirmationBasis.DEMO) =
        QuestEngine.acceptEvent(state, SituationEvent("finish-${context.occurrenceId}", context, SituationEventType.PRACTICED, 1003, confirmation = basis))
    private fun evidence(state: QuestState, index: Int, skill: CoachSkill = CoachSkill.LANE_CHANGE) =
        QuestEngine.recordPractice(state, PracticeEvidence("e$index", skill, EvidenceSource.SIMULATION, 1000L + index))

    @Test fun allFigmaQuestsAndFourStageQuestsHaveStableIds() {
        assertEquals(26, QuestCatalog.all.size)
        assertEquals(26, QuestCatalog.all.map { it.id }.toSet().size)
        assertEquals(listOf(5, 10, 5, 6), QuestPhase.values().map { phase -> QuestCatalog.all.count { it.phase == phase } })
        CoachSkill.values().forEach { assertEquals(QuestStage.values().toList(), StageQuestCatalog.forSkill(it).map { it.stage }) }
    }
    @Test fun learningOnlyCompletesFirstQuestCircle() {
        val state = QuestEngine.learn(QuestState(), CoachSkill.LANE_CHANGE)
        assertEquals(setOf(QuestStage.FIRST), state.completedStages(CoachSkill.LANE_CHANGE))
        assertEquals(4, state.growthPercent)
    }
    @Test fun sequentialStagesRequireOneThenThreeUniqueExperiences() {
        var state = QuestEngine.learn(QuestState(), CoachSkill.LANE_CHANGE)
        state = evidence(state, 1)
        assertEquals(2, state.completedStages(CoachSkill.LANE_CHANGE).size)
        state = evidence(state, 1)
        assertEquals(1, state.practiceCount(CoachSkill.LANE_CHANGE))
        state = evidence(evidence(state, 2), 3)
        assertEquals(3, state.completedStages(CoachSkill.LANE_CHANGE).size)
        assertFalse(QuestEngine.eligibleForGraduation(state, CoachSkill.LANE_CHANGE))
        state = QuestEngine.completeFinalStage(state, CoachSkill.LANE_CHANGE, "다음에는 더 여유 있게 확인할래요", 2000)
        assertEquals(4, state.completedStages(CoachSkill.LANE_CHANGE).size)
    }
    @Test fun cannotCompleteFourthQuestBeforeEarlierQuestsOrWithEmptyReflection() {
        val state = QuestEngine.learn(QuestState(), CoachSkill.LANE_CHANGE)
        assertEquals(state, QuestEngine.completeFinalStage(state, CoachSkill.LANE_CHANGE, "기록", 2000))
        val practiced = evidence(evidence(evidence(state, 1), 2), 3)
        assertEquals(practiced, QuestEngine.completeFinalStage(practiced, CoachSkill.LANE_CHANGE, " ", 2000))
    }
    @Test fun observationsAndEnteringSituationNeverCompletePractice() {
        val context = ctx(); val state = enter(start(), context)
        val observed = QuestEngine.acceptEvent(state, SituationEvent("obs", context, SituationEventType.OBSERVED, 1003, "RIGHT"))
        assertEquals(0, observed.practiceCount(CoachSkill.LANE_CHANGE))
        assertTrue(observed.completed.isEmpty())
        assertNotNull(observed.session!!.current)
    }
    @Test fun unknownEvidenceCannotConfirmPractice() {
        val context = ctx(); val state = enter(start(), context)
        assertEquals(state, finish(state, context, ConfirmationBasis.UNKNOWN))
        assertEquals(state, finish(state, context, ConfirmationBasis.USER_CONFIRMATION))
    }
    @Test fun wrongTripOccurrenceSourceAndOldTimestampAreRejected() {
        val context = ctx(); val state = enter(start(), context)
        listOf(context.copy(tripId = "other"), context.copy(occurrenceId = "other"), context.copy(source = EvidenceSource.USER)).forEach {
            assertEquals(state, finish(state, it))
        }
        assertEquals(state, QuestEngine.acceptEvent(state, SituationEvent("old", context, SituationEventType.OBSERVED, 999)))
    }
    @Test fun duplicateCompletionWithDifferentEventIdCannotCountTwice() {
        val context = ctx(); val state = finish(enter(start(), context), context)
        assertEquals(1, state.practiceCount(CoachSkill.LANE_CHANGE))
        val duplicate = SituationEvent("another-id", context, SituationEventType.PRACTICED, 1004, confirmation = ConfirmationBasis.DEMO)
        assertEquals(state, QuestEngine.acceptEvent(state, duplicate))
    }
    @Test fun newOccurrenceCanRepeatSituationWithoutLosingParallelProgress() {
        var state = start()
        val a = ctx(); state = finish(enter(state, a), a)
        val b = ctx(id = "lane-B", maneuver = "junction-B").copy(at = 1004)
        state = QuestEngine.acceptEvent(state, SituationEvent("enter-B", b, SituationEventType.ENTERED, 1004))
        state = QuestEngine.acceptEvent(state, SituationEvent("finish-B", b, SituationEventType.PRACTICED, 1005, confirmation = ConfirmationBasis.DEMO))
        assertEquals(2, state.practiceCount(CoachSkill.LANE_CHANGE))
    }
    @Test fun rightTurnAndExitSameIntersectionCountOnlyOneExperience() {
        val turn = ctx(SituationKind.TURN_RIGHT)
        var state = finish(enter(start(), turn), turn)
        assertEquals(0, state.practiceCount(CoachSkill.INTERSECTION))
        val exit = ctx(SituationKind.INTERSECTION_EXIT).copy(at = 1004)
        state = QuestEngine.acceptEvent(state, SituationEvent("exit-entry", exit, SituationEventType.ENTERED, 1004))
        state = QuestEngine.acceptEvent(state, SituationEvent("exit-done", exit, SituationEventType.PRACTICED, 1005, confirmation = ConfirmationBasis.DEMO))
        assertEquals(1, state.practiceCount(CoachSkill.INTERSECTION))
        assertTrue("intersection" in state.completed)
    }
    @Test fun exitFromDifferentIntersectionDoesNotCompleteRightTurnQuest() {
        val turn = ctx(SituationKind.TURN_RIGHT)
        var state = finish(enter(start(), turn), turn)
        val exit = ctx(SituationKind.INTERSECTION_EXIT, maneuver = "different").copy(at = 1004)
        state = QuestEngine.acceptEvent(state, SituationEvent("exit-entry", exit, SituationEventType.ENTERED, 1004))
        state = QuestEngine.acceptEvent(state, SituationEvent("exit-done", exit, SituationEventType.PRACTICED, 1005, confirmation = ConfirmationBasis.DEMO))
        assertEquals(0, state.practiceCount(CoachSkill.INTERSECTION))
    }
    @Test fun cancellationAndSkippingDoNotRewardButAllowNextSituation() {
        val context = ctx(); val base = enter(start(), context)
        for (type in listOf(SituationEventType.CANCELLED, SituationEventType.SKIPPED)) {
            val state = QuestEngine.acceptEvent(base, SituationEvent("end", context, type, 1003))
            assertNull(state.session!!.current)
            assertEquals(0, state.practiceCount(CoachSkill.LANE_CHANGE))
        }
    }
    @Test fun honestDifficultyDoesNotReduceConfirmedExperienceProgress() {
        val context = ctx(); var state = enter(start(), context)
        state = QuestEngine.acceptEvent(state, SituationEvent("note", context, SituationEventType.DIFFICULTY, 1002, "어려웠어요"))
        state = finish(state, context)
        assertEquals(1, state.practiceCount(CoachSkill.LANE_CHANGE))
        assertTrue(state.evidence.single().difficult)
        assertTrue("note" in state.completed)
    }
    @Test fun demoEvidenceCannotAdvancePersonalProfile() {
        val personal = QuestState(demo = false)
        assertEquals(personal, evidence(personal, 1))
        assertEquals(personal, QuestEngine.beginTrip(personal, "x", EvidenceSource.SIMULATION, 1000))
    }
    @Test fun userConfirmedResultKeepsItsProvenanceEvenWithVehicleContext() {
        val context = ctx(source = EvidenceSource.VEHICLE)
        val state = finish(enter(start(false), context), context, ConfirmationBasis.USER_CONFIRMATION)
        assertEquals(EvidenceSource.USER, state.evidence.single().source)
    }
    @Test fun settingsFlowBackToPartOneImmediatelyAndStayAreaSpecific() {
        val lane = ctx(); val state = enter(start(), lane).copy(settings = CoachSettings(
            levels = mapOf(CoachSkill.LANE_CHANGE to GuidanceLevel.BRIEF, CoachSkill.INTERSECTION to GuidanceLevel.DETAILED)))
        assertEquals(GuidanceLevel.BRIEF, state.guideUiModel().guidanceLevel)
        assertEquals(GuidanceLevel.DETAILED, state.settings.level(CoachSkill.INTERSECTION))
        assertFalse(state.copy(settings = state.settings.copy(disabled = setOf(CoachSkill.LANE_CHANGE))).guideUiModel().coachingEnabled)
    }
    @Test fun graduationIsExplicitAndReversible() {
        var state = QuestEngine.learn(QuestState(), CoachSkill.LANE_CHANGE)
        state = evidence(evidence(evidence(state, 1), 2), 3)
        assertEquals(state, QuestEngine.graduate(state, CoachSkill.LANE_CHANGE))
        state = QuestEngine.completeFinalStage(state, CoachSkill.LANE_CHANGE, "연습 기록", 2000)
        assertEquals(GuidanceLevel.DETAILED, state.settings.level(CoachSkill.LANE_CHANGE))
        state = QuestEngine.graduate(state, CoachSkill.LANE_CHANGE)
        assertEquals(GuidanceLevel.ON_REQUEST, state.settings.level(CoachSkill.LANE_CHANGE))
        assertEquals(GuidanceLevel.DETAILED, QuestEngine.restoreCoach(state, CoachSkill.LANE_CHANGE).settings.level(CoachSkill.LANE_CHANGE))
    }
    @Test fun unfinishedOccurrencePreventsTripEndAndOverwrite() {
        val state = enter(start(), ctx())
        assertEquals(state, QuestEngine.finishTrip(state, "trip", 2000))
        assertEquals(state, QuestEngine.beginTrip(state, "new-trip", EvidenceSource.SIMULATION, 2000))
        assertEquals(state, QuestEngine.review(state))
    }
    @Test fun feedbackIsIdempotentAndDoesNotInventPracticeFromSpeedOrNotes() {
        val report = DrivingFeedback("report", EvidenceSource.SIMULATION, 2000, noteCategories = setOf("PARKING"), abruptBrakingCount = 2)
        val state = QuestEngine.consumeFeedback(QuestState(), report)
        assertEquals(state, QuestEngine.consumeFeedback(state, report))
        assertTrue(state.evidence.isEmpty())
        assertEquals("parking_reverse", QuestEngine.recommendations(state).first().id)
        assertEquals(QuestState(), QuestEngine.consumeFeedback(QuestState(), report.copy(abruptBrakingCount = -1)))
    }
    @Test fun allChecklistItemsRequiredAndFocusDoesNotLimitOtherAreas() {
        val initial = QuestState()
        assertEquals(initial, QuestEngine.confirmQuest(initial, "prepare", setOf(0), "check", 1000))
        var state = QuestEngine.selectGoal(initial, "lane_right")
        state = QuestEngine.confirmQuest(state, "prepare", setOf(0, 1, 2, 3), "check", 1000)
        assertEquals(2, state.completedStages(CoachSkill.PREPARATION).size)
        assertEquals("lane_right", state.focusGoalId)
    }
    @Test fun missingDataAndDeferringNeverRewardOrTrapTheActiveSituation() {
        val context = ctx()
        for (type in listOf(SituationEventType.UNVERIFIED, SituationEventType.DEFERRED)) {
            val state = QuestEngine.acceptEvent(enter(start(), context), SituationEvent("end", context, type, 1003))
            assertNull(state.session!!.current)
            assertTrue(state.evidence.isEmpty())
            assertNotNull(QuestEngine.finishTrip(state, "trip", 2000).session!!.finishedAt)
        }
    }
    @Test fun guidanceRecordsActualAssistanceWithoutRewardingPractice() {
        val context = ctx(); val state = enter(start(), context)
        val changed = QuestEngine.acceptEvent(state, SituationEvent("spoken", context, SituationEventType.GUIDANCE_PROVIDED,
            1003, assistanceUsed = AssistanceUsed.BRIEF))
        assertEquals(AssistanceUsed.BRIEF, changed.session!!.events.last().assistanceUsed)
        assertTrue(changed.evidence.isEmpty())
    }
    @Test fun nextGoalRequiresReviewAndBecomesNextTripsFocus() {
        val base = start()
        assertEquals(base, QuestEngine.selectNextGoal(base, "intersection"))
        val reviewed = QuestEngine.review(QuestEngine.finishTrip(base, "trip", 2000))
        val reserved = QuestEngine.selectNextGoal(reviewed, "intersection")
        val next = QuestEngine.beginTrip(reserved, "next-trip", EvidenceSource.SIMULATION, 3000)
        assertEquals("intersection", next.focusGoalId)
        assertNull(next.nextGoalId)
    }
    @Test fun manyObservationsDoNotPreventFinalConfirmation() {
        val context = ctx(); var state = enter(start(), context)
        repeat(550) { index -> state = QuestEngine.acceptEvent(state,
            SituationEvent("observation-$index", context, SituationEventType.OBSERVED, 1003L + index)) }
        state = QuestEngine.acceptEvent(state, SituationEvent("final", context, SituationEventType.PRACTICED, 2000, confirmation = ConfirmationBasis.DEMO))
        assertEquals(1, state.practiceCount(CoachSkill.LANE_CHANGE))
        assertEquals(500, state.session!!.events.size)
    }
}
