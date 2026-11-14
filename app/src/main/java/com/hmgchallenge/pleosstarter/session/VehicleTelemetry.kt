package com.hmgchallenge.pleosstarter.session

import android.content.Context
import android.os.SystemClock
import com.hmgchallenge.pleosstarter.SpeedSource
import com.hmgchallenge.pleosstarter.driving.*
import com.hmgchallenge.pleosstarter.feature.quests.QuestVehicleSource
import com.hmgchallenge.pleosstarter.feature.quests.Signal

/** Read-only SDK boundary. Missing/stale observations remain unknown, never synthetic zeroes. */
class VehicleTelemetry(private val context: Context) {
    var speedMps: Double?=null; private set
    var speedAt=0L; private set
    private var indicatorValue=Indicator.UNKNOWN
    val indicator get()=if(SystemClock.elapsedRealtime()-indicatorAt in 0..3000)indicatorValue else Indicator.UNKNOWN
    var indicatorAt=0L;private set
    var steeringAngle: Float?=null;private set
    var angleAt=0L;private set
    var brakePressed: Boolean?=null;private set
    var brakeAt=0L;private set
    var questStatus="차량 신호 대기";private set
    private var questSource: QuestVehicleSource?=null
    private val evidence=RuntimeEvidence()
    var navigationStatus="내비 API 대기"; private set
    private var source: PleosCoachSource?=null
    private var speed: SpeedSource?=null
    val fresh get()=speedMps!=null && SystemClock.elapsedRealtime()-speedAt<=5000
    val stationary get()=fresh && kotlin.math.abs(speedMps ?: Double.NaN)<.1
    fun start() {
        if(!context.packageManager.hasSystemFeature("android.hardware.type.automotive"))return
        source=PleosCoachSource(context,object: PleosCoachSource.Listener {
            override fun onNavigation(right: Boolean,meters: Double?,description: String) {navigationStatus=description}
            override fun onIndicator(value: Indicator) {indicatorValue=value;indicatorAt=SystemClock.elapsedRealtime()}
            override fun onRouteInvalidated(reason: String) {navigationStatus=reason}
            override fun onArrival() {navigationStatus="목적지 도착"}
            override fun onStatus(channel: String,message: String) {navigationStatus=message}
        },includeIndicator=false).also {it.start()}
        questSource=QuestVehicleSource(context,
            onSignal={value->
                indicatorValue=when(value){Signal.RIGHT->Indicator.RIGHT;Signal.LEFT->Indicator.LEFT;Signal.OFF->Indicator.OFF;else->Indicator.UNKNOWN}
                indicatorAt=SystemClock.elapsedRealtime()
            },
            onAngle={value->steeringAngle=value;angleAt=SystemClock.elapsedRealtime()},
            onBrake={value->brakePressed=value;brakeAt=SystemClock.elapsedRealtime()},
            onError={questStatus=it},observe={evidence.record(it)}).also {it.start()}
        if(context.checkSelfPermission("android.car.permission.CAR_SPEED")==android.content.pm.PackageManager.PERMISSION_GRANTED)
            speed=SpeedSource(context) {value,_->speedMps=value?.toDouble();speedAt=SystemClock.elapsedRealtime()}.also {it.start()}
    }
    fun questInput()=VehicleQuestInput(
        when(indicator){Indicator.RIGHT->Signal.RIGHT;Indicator.LEFT->Signal.LEFT;Indicator.OFF->Signal.OFF;else->null},indicatorAt,
        steeringAngle,angleAt,brakePressed,brakeAt,speedMps,speedAt)
    fun diagnostics()=evidence.snapshot()
    fun stop() {
        source?.stop();source=null;questSource?.stop();questSource=null;speed?.stop();speed=null
        speedMps=null;speedAt=0;indicatorValue=Indicator.UNKNOWN;indicatorAt=0
        steeringAngle=null;angleAt=0;brakePressed=null;brakeAt=0;questStatus="차량 연결 대기"
    }
}
