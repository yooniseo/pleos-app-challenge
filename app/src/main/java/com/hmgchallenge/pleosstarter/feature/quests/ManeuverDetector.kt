package com.hmgchallenge.pleosstarter.feature.quests

import kotlin.math.abs

/**
 * 판정 기준값. 모두 임시(샘플) 숫자이며 에뮬레이터·실차에서 확인한 뒤 조정한다.
 * 조향각은 Vehicle SDK가 주는 "앞바퀴 조향각(도)"이다. 핸들 회전각이 아니며, 왼쪽이 음수다.
 */
data class DetectorConfig(
    /** 이 각도 이상 꺾이면 조작이 시작된 것으로 본다. */
    val steerStartDeg: Float = 3f,
    /** 이 각도 아래로 돌아오면 조작이 끝난 것으로 본다. */
    val steerReturnDeg: Float = 1.5f,
    /** 최대 각도가 이 값 이상이면 교차로 회전, 미만이면 차선 변경으로 분류한다. */
    val turnDeg: Float = 15f,
    /** 조향 시작 전에 깜빡이를 켜 두어야 하는 최소 시간. */
    val signalLeadMs: Long = 2_000,
    /** 회전 시작 전 이 시간 안에 브레이크를 밟았으면 감속한 것으로 본다. */
    val brakeLookbackMs: Long = 5_000,
    /** 조작이 끝난 뒤 이 시간 안의 작은 조향(되돌리는 핸들)은 새 차선 변경으로 세지 않는다. */
    val cooldownMs: Long = 4_000
)

/**
 * 방향지시등·조향각·브레이크 값의 변화를 받아 "차선 변경"과 "교차로 우회전" 시도를 찾아 판정한다.
 * 값이 바뀔 때마다 on*()을 호출하면 되고, 시도가 끝나면 onAttempt가 한 번 불린다.
 */
class ManeuverDetector(
    private val config: DetectorConfig = DetectorConfig(),
    private val onAttempt: (Attempt) -> Unit
) {
    private var signal = Signal.OFF
    private var signalSince = 0L
    private var brakeSeen = false
    private var brakePressed = false
    private var lastBrakeAt: Long? = null
    private var lastEndAt: Long? = null
    private var current: Maneuver? = null

    private class Maneuver(
        val direction: Direction,
        val signalAtStart: Signal,
        val signalLeadMs: Long?,
        val inCooldown: Boolean,
        var peakDeg: Float,
        var braked: Boolean
    )

    fun onSignal(t: Long, value: Signal) {
        if (value == signal) return
        signal = value; signalSince = t
    }

    fun onBrake(t: Long, pressed: Boolean) {
        if (pressed || brakePressed) lastBrakeAt = t   // 마지막으로 밟고 있던 시각
        brakeSeen = true; brakePressed = pressed
        // 교차로 각도에 도달하기 전까지 밟은 브레이크만 "진입 전 감속"으로 인정한다.
        current?.let { if (pressed && it.peakDeg < config.turnDeg) it.braked = true }
    }

    /** angleDeg: 앞바퀴 조향각(도), 왼쪽 음수. 값이 없으면(null) 호출하지 않는다. */
    fun onAngle(t: Long, angleDeg: Float) {
        if (!angleDeg.isFinite()) return
        val size = abs(angleDeg)
        val direction = if (angleDeg > 0) Direction.RIGHT else Direction.LEFT
        val maneuver = current
        if (maneuver == null) {
            if (size >= config.steerStartDeg) current = Maneuver(
                direction = direction,
                signalAtStart = signal,
                signalLeadMs = if (signal == Signal.OFF) null else t - signalSince,
                inCooldown = lastEndAt?.let { t - it < config.cooldownMs } ?: false,
                peakDeg = size,
                braked = brakePressed || (lastBrakeAt?.let { t - it <= config.brakeLookbackMs } ?: false)
            )
        } else if (direction == maneuver.direction && size >= config.steerReturnDeg) {
            if (size > maneuver.peakDeg) maneuver.peakDeg = size
        } else {
            current = null; lastEndAt = t
            judge(maneuver, t)
        }
    }

    private fun judge(m: Maneuver, t: Long) {
        val isTurn = m.peakDeg >= config.turnDeg
        val questId = when {
            isTurn && m.direction == Direction.RIGHT -> QuestIds.INTERSECTION_RIGHT
            isTurn -> return                    // 좌회전 퀘스트는 아직 없음
            m.inCooldown -> return              // 차선 변경·회전 뒤에 핸들을 되돌리는 동작
            else -> QuestIds.LANE_CHANGE
        }
        val braked = if (isTurn && brakeSeen) m.braked else null
        val wanted = if (m.direction == Direction.RIGHT) Signal.RIGHT else Signal.LEFT
        val result = when {
            m.signalAtStart == Signal.OFF -> AttemptResult.NO_SIGNAL
            m.signalAtStart != wanted -> AttemptResult.WRONG_SIGNAL
            (m.signalLeadMs ?: 0) < config.signalLeadMs -> AttemptResult.SIGNAL_LATE
            braked == false -> AttemptResult.NO_BRAKE
            else -> AttemptResult.SUCCESS
        }
        onAttempt(Attempt(questId, t, result, m.direction, m.peakDeg, m.signalLeadMs, braked))
    }
}
