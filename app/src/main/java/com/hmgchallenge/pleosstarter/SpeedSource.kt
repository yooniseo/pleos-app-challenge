package com.hmgchallenge.pleosstarter

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Callback plus fresh reads: a constant VHAL speed must not expire just because it did not change. */
class SpeedSource(private val context: Context, private val onValue: (Float?, String) -> Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private var car: Car?=null
    @Volatile private var manager: CarPropertyManager?=null
    private var reader: ScheduledExecutorService?=null
    @Volatile private var generation=0
    private val stale=Runnable {onValue(null,"속도 응답 없음 · 차량 서비스를 확인하세요")}
    private fun deliver(value: Float?,message: String,token: Int=generation) {
        handler.post {
            if(token!=generation || reader==null)return@post
            onValue(value,message)
            handler.removeCallbacks(stale);handler.postDelayed(stale,5000)
        }
    }
    private var callback: CarPropertyManager.CarPropertyEventCallback?=null
    private fun callbacks(token: Int)=object: CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if(value.propertyId!=VehiclePropertyIds.PERF_VEHICLE_SPEED)return
            val speed=(value.value as? Float)?.takeIf {value.status==CarPropertyValue.STATUS_AVAILABLE && it.isFinite()}
            deliver(speed,if(speed!=null)"차량 센서 · m/s → km/h" else "현재 속도 값 없음",token)
        }
        override fun onErrorEvent(propertyId: Int,areaId: Int) {
            if(propertyId==VehiclePropertyIds.PERF_VEHICLE_SPEED)deliver(null,"차량 속도 센서 오류",token)
        }
    }
    fun start() {
        stop();val token=generation
        val listener=callbacks(token);callback=listener
        reader=Executors.newSingleThreadScheduledExecutor {r->Thread(r,"vehicle-speed-reader").apply {isDaemon=true}}
        handler.postDelayed(stale,5000)
        car=Car.createCar(context,handler,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) {connected,ready->
            if(token!=generation)return@createCar
            if(!ready) {manager=null;deliver(null,"차량 서비스 연결 끊김",token)}
            else try {
                val next=connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                manager=next
                next.registerCallback(listener,VehiclePropertyIds.PERF_VEHICLE_SPEED,CarPropertyManager.SENSOR_RATE_NORMAL)
            }catch(error: Exception) {deliver(null,"속도 연결 실패: ${error.javaClass.simpleName}",token)}
        }
        reader?.scheduleWithFixedDelay({
            val next=manager
            if(next!=null && token==generation)try {
                val speed=next.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED,0)
                val value=speed.value?.takeIf {speed.status==CarPropertyValue.STATUS_AVAILABLE && it.isFinite()}
                deliver(value,if(value!=null)"차량 속도 실시간 수신" else "현재 속도 값 없음",token)
            }catch(error: Exception){deliver(null,"속도 조회 실패: ${error.javaClass.simpleName}",token)}
        },0,1000,TimeUnit.MILLISECONDS)
    }
    fun stop() {
        generation++;handler.removeCallbacksAndMessages(null)
        reader?.shutdownNow();reader=null
        runCatching {callback?.let {manager?.unregisterCallback(it)}}
        runCatching {car?.disconnect()};manager=null;car=null;callback=null
    }
}
