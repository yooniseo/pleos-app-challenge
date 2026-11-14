package com.hmgchallenge.pleosstarter.feature.quests

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 퀘스트 판정에 쓰는 차량 값 3가지를 Pleos Vehicle SDK(2.3.2)에서 구독한다.
 *  - 방향지시등: TurnSignal.registerTurnSignalState      (권한 pleos.car.permission.CAR_LIGHTS)
 *  - 앞바퀴 조향각: SteeringWheel.registerSteeringWheelAngle (권한 pleos.car.permission.CAR_DRIVE)
 *  - 브레이크 페달: Brake.registerBrakePedalStatus        (권한 pleos.car.permission.CAR_DRIVE)
 * 콜백은 모두 메인 스레드에서 불린다. 값이 없으면 null을 그대로 넘긴다(가짜 값으로 채우지 않음).
 */
class QuestVehicleSource(
    context: Context,
    private val onSignal: (Signal?) -> Unit,
    private val onAngle: (Float?) -> Unit,
    private val onBrake: (Boolean?) -> Unit,
    private val onError: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context.applicationContext)
    private var stopped = false

    private val signalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = post { onSignal(state.toSignal()) }
        override fun onFailed(e: Exception) = fail("방향지시등", e)
    }
    private val angleListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = post { onAngle(angle) }
        override fun onFailed(e: Exception) = fail("조향각", e)
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = post { onBrake(status) }
        override fun onFailed(e: Exception) = fail("브레이크", e)
    }

    fun start() {
        try { vehicle.initialize() } catch (e: Exception) { fail("차량 서비스", e); return }
        attempt("방향지시등") {
            val api = vehicle.getTurnSignal()
            api.registerTurnSignalState(signalListener)
            api.getCurrentTurnSignalState({ post { onSignal(it.toSignal()) } }, { fail("방향지시등", it) })
        }
        attempt("조향각") {
            val api = vehicle.getSteeringWheel()
            api.registerSteeringWheelAngle(angleListener)
            api.getCurrentSteeringWheelAngle({ post { onAngle(it) } }, { fail("조향각", it) })
        }
        attempt("브레이크") {
            val api = vehicle.getBrake()
            api.registerBrakePedalStatus(brakeListener)
            api.getCurrentBrakePedalStatus({ post { onBrake(it) } }, { fail("브레이크", it) })
        }
    }

    fun stop() {
        stopped = true; handler.removeCallbacksAndMessages(null)
        runCatching { vehicle.getTurnSignal().unregisterTurnSignalState(signalListener) }
        runCatching { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(angleListener) }
        runCatching { vehicle.getBrake().unregisterBrakePedalStatus(brakeListener) }
        runCatching { vehicle.release() }
    }

    private fun attempt(name: String, action: () -> Unit) {
        try { action() } catch (e: Exception) { fail(name, e) }
    }

    private fun fail(name: String, e: Exception) {
        Log.w(TAG, "$name 실패", e)
        post { onError("$name 값을 받을 수 없어요: ${e.javaClass.simpleName} ${e.message ?: ""}") }
    }

    private fun post(action: () -> Unit) { handler.post { if (!stopped) action() } }

    private fun TurnSignalValue?.toSignal() = when (this) {
        TurnSignalValue.LEFT -> Signal.LEFT
        TurnSignalValue.RIGHT -> Signal.RIGHT
        TurnSignalValue.OFF -> Signal.OFF
        null -> null
    }

    private companion object { const val TAG = "QuestVehicleSource" }
}
