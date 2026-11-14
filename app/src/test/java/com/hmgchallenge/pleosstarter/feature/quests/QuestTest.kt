package com.hmgchallenge.pleosstarter.feature.quests

import org.junit.Assert.*
import org.junit.Test

class QuestTest {
    private val attempts = mutableListOf<Attempt>()
    private val detector = ManeuverDetector { attempts += it }

    /** 깜빡이를 켜고 leadMs 뒤에 핸들을 peak까지 꺾었다가 되돌린다. 끝난 시각을 돌려준다. */
    private fun steer(start: Long, signal: Signal, leadMs: Long, peak: Float, brake: Boolean? = null): Long {
        detector.onSignal(start, signal)
        var t = start + leadMs
        if (brake != null) { detector.onBrake(t - 1_000, brake); detector.onBrake(t - 500, false) }
        listOf(peak / 2, peak, peak / 2, 0f).forEach { detector.onAngle(t, it); t += 500 }
        detector.onSignal(t, Signal.OFF)
        return t
    }

    @Test fun laneChangeWithEarlySignalSucceeds() {
        steer(0, Signal.LEFT, 3_000, -6f)
        val a = attempts.single()
        assertEquals(QuestIds.LANE_CHANGE, a.questId)
        assertEquals(AttemptResult.SUCCESS, a.result)
        assertEquals(Direction.LEFT, a.direction)
        assertEquals(6f, a.peakAngleDeg, 0.001f)
        assertEquals(3_000L, a.signalLeadMs)
    }

    @Test fun laneChangeFailsWithoutLateOrWrongSignal() {
        steer(0, Signal.OFF, 3_000, 6f)
        steer(20_000, Signal.RIGHT, 500, 6f)
        steer(40_000, Signal.LEFT, 3_000, 6f)
        assertEquals(
            listOf(AttemptResult.NO_SIGNAL, AttemptResult.SIGNAL_LATE, AttemptResult.WRONG_SIGNAL),
            attempts.map { it.result }
        )
        assertTrue(attempts.all { it.questId == QuestIds.LANE_CHANGE })
    }

    @Test fun counterSteerAfterLaneChangeIsNotASecondAttempt() {
        val end = steer(0, Signal.RIGHT, 3_000, 6f)
        detector.onAngle(end + 500, -5f); detector.onAngle(end + 1_000, 0f)
        assertEquals(1, attempts.size)
    }

    @Test fun rightTurnNeedsSignalAndBraking() {
        steer(0, Signal.RIGHT, 3_000, 25f, brake = true)
        steer(30_000, Signal.RIGHT, 3_000, 25f, brake = false)
        steer(60_000, Signal.OFF, 3_000, 25f, brake = true)
        assertTrue(attempts.all { it.questId == QuestIds.INTERSECTION_RIGHT })
        assertEquals(
            listOf(AttemptResult.SUCCESS, AttemptResult.NO_BRAKE, AttemptResult.NO_SIGNAL),
            attempts.map { it.result }
        )
        assertEquals(listOf(true, false, true), attempts.map { it.braked })
    }

    @Test fun brakeDuringTurnInCountsButAfterTurnAngleDoesNot() {
        detector.onSignal(0, Signal.RIGHT)
        detector.onBrake(0, false)
        detector.onAngle(3_000, 5f); detector.onBrake(3_200, true)
        detector.onAngle(3_500, 25f); detector.onAngle(4_000, 0f)
        assertEquals(AttemptResult.SUCCESS, attempts.single().result)

        detector.onSignal(30_000, Signal.OFF); detector.onSignal(30_001, Signal.RIGHT)
        detector.onBrake(30_002, false)
        detector.onAngle(40_000, 5f); detector.onAngle(40_500, 25f)
        detector.onBrake(40_700, true); detector.onAngle(41_000, 0f)
        assertEquals(AttemptResult.NO_BRAKE, attempts.last().result)
    }

    @Test fun missingBrakeDataDoesNotFailTheTurn() {
        steer(0, Signal.RIGHT, 3_000, 25f)
        assertEquals(AttemptResult.SUCCESS, attempts.single().result)
        assertNull(attempts.single().braked)
    }

    @Test fun leftTurnAndTinyWheelMovementsAreIgnored() {
        steer(0, Signal.LEFT, 3_000, -25f, brake = true)
        steer(30_000, Signal.RIGHT, 3_000, 2f)
        detector.onAngle(60_000, Float.NaN)
        assertTrue(attempts.isEmpty())
    }

    @Test fun demoDriveLaneChangeThenRightTurnAdvancesBothQuestsByOneThirtieth() {
        val board = QuestBoard()
        val end = steer(0, Signal.RIGHT, 3_000, 6f)
        steer(end + 10_000, Signal.RIGHT, 3_000, 25f, brake = true)
        attempts.forEach { board.record(it) }
        assertEquals(listOf(QuestIds.LANE_CHANGE, QuestIds.INTERSECTION_RIGHT), attempts.map { it.questId })
        QuestCatalog.all.forEach {
            val p = board.progressOf(it.questId)
            assertEquals(1, p.steps); assertEquals(1, p.level)
            assertEquals(1f / 30f, p.fraction, 0.0001f)
        }
    }

    @Test fun failuresNeverLowerProgressAndThirtySuccessesLevelUp() {
        val board = QuestBoard()
        fun attempt(result: AttemptResult) =
            Attempt(QuestIds.LANE_CHANGE, 0, result, Direction.LEFT, 6f, 3_000, null)
        repeat(29) { board.record(attempt(AttemptResult.SUCCESS)) }
        repeat(5) { board.record(attempt(AttemptResult.NO_SIGNAL)) }
        var p = board.progressOf(QuestIds.LANE_CHANGE)
        assertEquals(1, p.level); assertEquals(29, p.steps); assertEquals(5, p.failCount)

        p = board.record(attempt(AttemptResult.SUCCESS))
        assertEquals(2, p.level); assertEquals(0, p.steps); assertEquals(30, p.successCount)
        p = board.record(attempt(AttemptResult.SIGNAL_LATE))
        assertEquals(2, p.level); assertEquals(0, p.steps)
        assertEquals(0, board.progressOf(QuestIds.INTERSECTION_RIGHT).steps)
        assertEquals(36, board.attempts.size)
    }
}
