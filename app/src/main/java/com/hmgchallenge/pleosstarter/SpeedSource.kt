package com.hmgchallenge.pleosstarter

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/** Separate class: ordinary Android devices can display the demo without android.car. */
class SpeedSource(private val context: Context, private val onValue: (Float?, String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var stopped = false
    private val stale = Runnable { if (!stopped) onValue(null, "속도 응답 없음 · 차량 서비스를 확인하세요.") }
    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (stopped || value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED) return
            val speed = (value.value as? Float)?.takeIf {
                value.status == CarPropertyValue.STATUS_AVAILABLE && it.isFinite()
            }
            onValue(speed, if (speed != null) "차량 센서 · m/s → km/h" else "현재 속도 값 없음")
            handler.removeCallbacks(stale); handler.postDelayed(stale, 5_000)
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            if (!stopped) onValue(null, "차량 속도 센서 오류")
        }
    }
    fun start() {
        stopped = false; handler.postDelayed(stale, 5_000)
        car = Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
            if (stopped) return@createCar
            if (!ready) {
                manager = null; onValue(null, "차량 서비스 연결 끊김")
            } else try {
                manager = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                if (manager?.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED,
                        CarPropertyManager.SENSOR_RATE_NORMAL) != true) {
                    onValue(null, "이 차량에서는 속도 구독을 지원하지 않습니다.")
                }
            } catch (error: Exception) { onValue(null, "속도 조회 실패: ${error.javaClass.simpleName}") }
        }
    }
    fun stop() {
        stopped = true; handler.removeCallbacksAndMessages(null)
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }; manager = null; car = null
    }
}
