package com.hmgchallenge.pleosstarter.feature.quests

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.hmgchallenge.pleosstarter.driving.ApiObservation

/** The quest team's SDK inputs, with fresh reads and callbacks scoped to each connection. */
class QuestVehicleSource(
    private val context: Context,
    private val onSignal: (Signal?) -> Unit,
    private val onAngle: (Float?) -> Unit,
    private val onBrake: (Boolean?) -> Unit,
    private val onError: (String) -> Unit,
    private val observe: (ApiObservation)->Unit = {}
) {
    private val handler=Handler(Looper.getMainLooper())
    private var vehicle: Vehicle?=null
    private var generation=0
    private var signalListener: TurnSignalStateListener?=null
    private var angleListener: SteeringWheelAngleListener?=null
    private var brakeListener: BrakePedalStatusListener?=null
    private var signalReadable=false
    private var angleReadable=false
    private var brakeReadable=false

    fun start() {
        stop()
        val token=generation
        val sdk=Vehicle(context.applicationContext)
        vehicle=sdk
        fun post(action: ()->Unit) {
            handler.post {if(token==generation && vehicle!=null)action()}
        }
        fun signal(value: TurnSignalValue?) = post {
            observe(ApiObservation("QUEST_SIGNAL",if(value==null)"NULL" else "VALUE",mapOf("value" to value?.name.orEmpty())))
            onSignal(when(value) {
                TurnSignalValue.RIGHT->Signal.RIGHT
                TurnSignalValue.LEFT->Signal.LEFT
                TurnSignalValue.OFF->Signal.OFF
                null->null
            })
        }
        fun angle(value: Float?) = post {
            val valid=value?.takeIf {it.isFinite()}
            observe(ApiObservation("QUEST_STEERING",if(valid==null)"NULL" else "VALUE",mapOf("value" to valid.toString())))
            onAngle(valid)
        }
        fun brake(value: Boolean?) = post {
            observe(ApiObservation("QUEST_BRAKE",if(value==null)"NULL" else "VALUE",mapOf("value" to value.toString())))
            onBrake(value)
        }
        fun fail(channel: String,error: Throwable) = post {
            observe(ApiObservation(channel,"ERROR",mapOf("error" to error.javaClass.simpleName)))
            when(channel) {
                "QUEST_SIGNAL"->onSignal(null)
                "QUEST_STEERING"->onAngle(null)
                "QUEST_BRAKE"->onBrake(null)
            }
            onError("$channel · ${error.javaClass.simpleName}")
        }
        fun read() {
            if(signalReadable)runCatching {
                sdk.getTurnSignal().getCurrentTurnSignalState({signal(it)},{fail("QUEST_SIGNAL",it)})
            }.onFailure {fail("QUEST_SIGNAL",it)}
            if(angleReadable)runCatching {
                sdk.getSteeringWheel().getCurrentSteeringWheelAngle({angle(it)},{fail("QUEST_STEERING",it)})
            }.onFailure {fail("QUEST_STEERING",it)}
            if(brakeReadable)runCatching {
                sdk.getBrake().getCurrentBrakePedalStatus({brake(it)},{fail("QUEST_BRAKE",it)})
            }.onFailure {fail("QUEST_BRAKE",it)}
        }
        try {sdk.initialize()} catch(e: Exception) {
            fail("QUEST_CONNECTION",e)
            return
        }
        signalListener=object: TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {signal(state)}
            override fun onFailed(e: Exception) {fail("QUEST_SIGNAL",e)}
        }
        angleListener=object: SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(value: Float?) {angle(value)}
            override fun onFailed(e: Exception) {fail("QUEST_STEERING",e)}
        }
        brakeListener=object: BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(value: Boolean?) {brake(value)}
            override fun onFailed(e: Exception) {fail("QUEST_BRAKE",e)}
        }
        runCatching {
            sdk.getTurnSignal().checkTurnSignalStateCapabilities({config->post {
                signalReadable=config.accessType in setOf(AccessType.READ,AccessType.READ_WRITE)
                observe(ApiObservation("QUEST_SIGNAL","CAPABILITY",mapOf("access" to config.accessType.name)))
                if(signalReadable) {
                    runCatching {sdk.getTurnSignal().registerTurnSignalState(signalListener!!)}.onFailure {fail("QUEST_SIGNAL",it)}
                    read()
                } else onError("방향지시등 읽기 미지원")
            }},{fail("QUEST_SIGNAL",it)})
        }.onFailure {fail("QUEST_SIGNAL",it)}
        runCatching {
            sdk.getSteeringWheel().checkSteeringWheelAngleCapabilities({config,_->post {
                angleReadable=config.accessType in setOf(AccessType.READ,AccessType.READ_WRITE)
                observe(ApiObservation("QUEST_STEERING","CAPABILITY",mapOf("access" to config.accessType.name)))
                if(angleReadable) {
                    runCatching {sdk.getSteeringWheel().registerSteeringWheelAngle(angleListener!!)}.onFailure {fail("QUEST_STEERING",it)}
                    read()
                } else onError("조향각 읽기 미지원")
            }},{fail("QUEST_STEERING",it)})
        }.onFailure {fail("QUEST_STEERING",it)}
        runCatching {
            sdk.getBrake().checkBrakePedalStatusCapabilities({config->post {
                brakeReadable=config.accessType in setOf(AccessType.READ,AccessType.READ_WRITE)
                observe(ApiObservation("QUEST_BRAKE","CAPABILITY",mapOf("access" to config.accessType.name)))
                if(brakeReadable) {
                    runCatching {sdk.getBrake().registerBrakePedalStatus(brakeListener!!)}.onFailure {fail("QUEST_BRAKE",it)}
                    read()
                } else onError("브레이크 읽기 미지원")
            }},{fail("QUEST_BRAKE",it)})
        }.onFailure {fail("QUEST_BRAKE",it)}
        handler.post(object: Runnable {
            override fun run() {
                if(token!=generation || vehicle==null)return
                read()
                handler.postDelayed(this,1000)
            }
        })
    }
    fun stop() {
        generation++
        handler.removeCallbacksAndMessages(null)
        vehicle?.let {sdk->
            runCatching {signalListener?.let {sdk.getTurnSignal().unregisterTurnSignalState(it)}}
            runCatching {angleListener?.let {sdk.getSteeringWheel().unregisterSteeringWheelAngle(it)}}
            runCatching {brakeListener?.let {sdk.getBrake().unregisterBrakePedalStatus(it)}}
            runCatching {sdk.release()}
        }
        vehicle=null;signalListener=null;angleListener=null;brakeListener=null
        signalReadable=false;angleReadable=false;brakeReadable=false
    }
}
