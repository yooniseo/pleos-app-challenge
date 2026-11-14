package com.hmgchallenge.pleosstarter.session

import android.content.Context
import android.os.SystemClock
import com.hmgchallenge.pleosstarter.SpeedSource
import com.hmgchallenge.pleosstarter.driving.*

/** Read-only SDK boundary. Missing/stale observations remain unknown, never synthetic zeroes. */
class VehicleTelemetry(private val context: Context) {
    var speedMps: Double?=null; private set
    var speedAt=0L; private set
    var indicator=Indicator.UNKNOWN; private set
    var navigationStatus="내비 API 대기"; private set
    private var source: PleosCoachSource?=null
    private var speed: SpeedSource?=null
    val fresh get()=speedMps!=null && SystemClock.elapsedRealtime()-speedAt<=5000
    val stationary get()=fresh && kotlin.math.abs(speedMps ?: Double.NaN)<.1
    fun start() {
        if(!context.packageManager.hasSystemFeature("android.hardware.type.automotive"))return
        source=PleosCoachSource(context,object: PleosCoachSource.Listener {
            override fun onNavigation(right: Boolean,meters: Double?,description: String) {navigationStatus=description}
            override fun onIndicator(value: Indicator) {indicator=value}
            override fun onRouteInvalidated(reason: String) {navigationStatus=reason}
            override fun onArrival() {navigationStatus="목적지 도착"}
            override fun onStatus(channel: String,message: String) {navigationStatus=message}
        }).also {it.start()}
        if(context.checkSelfPermission("android.car.permission.CAR_SPEED")==android.content.pm.PackageManager.PERMISSION_GRANTED)
            speed=SpeedSource(context) {value,_->speedMps=value?.toDouble();speedAt=SystemClock.elapsedRealtime()}.also {it.start()}
    }
    fun stop() {source?.stop();source=null;speed?.stop();speed=null;speedMps=null;speedAt=0;indicator=Indicator.UNKNOWN}
}
