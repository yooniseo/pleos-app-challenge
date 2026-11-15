package com.hmgchallenge.pleosstarter.driving

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import java.util.concurrent.Executors

/** Read-only trip context plus an explicit parked ambient-color preview with restoration. */
class VehicleCoachSource(private val context: Context,private val onState: (VehicleCoachState)->Unit,
    private val onStatus: (String)->Unit,private val observe: (ApiObservation)->Unit={}) {
    private val main=Handler(Looper.getMainLooper())
    private var car: Car?=null
    private var manager: CarPropertyManager?=null
    private var vehicle: Vehicle?=null
    private var state=VehicleCoachState()
    @Volatile private var stopped=false
    companion object {
        // Capability discovery performs cold Kotlin reflection inside the SDK.
        // Serialize setup/release off the UI thread so opening another screen stays responsive.
        private val setup=Executors.newSingleThreadExecutor {r->Thread(r,"coach-vehicle-setup").apply {isDaemon=true}}
    }
    private val seat=SeatArea(SeatValue.ROW_1_LEFT)
    private var beltSupported=false
    private var lightArea: SeatArea?=null
    private var originalColor: Int?=null
    private var colorPending=false
    private var restoring=false
    private var permitPulse: ()->Boolean={false}
    private val properties=intArrayOf(VehiclePropertyIds.GEAR_SELECTION,VehiclePropertyIds.PARKING_BRAKE_ON)
    private val beltListener=object : SeatBeltBuckledStateListener {
        override fun onSeatBeltBuckledStateUpdated(value: Boolean?)=post {belt(value)}
        override fun onFailed(e: Exception)=post {belt(null)}
    }
    private val callback=object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {if(!stopped) update(value.propertyId,if(value.status==CarPropertyValue.STATUS_AVAILABLE)value.value else null)}
        override fun onErrorEvent(propertyId: Int,areaId: Int) {if(!stopped)update(propertyId,null)}
    }
    fun start() {
        onState(state)
        car=Car.createCar(context,main,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) {connected,ready->
            if(stopped) return@createCar
            if(!ready) {manager=null;state=state.copy(gear=null,parkingBrake=null);onState(state)}
            else runCatching {
                manager=connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                properties.forEach {id->runCatching {manager?.registerCallback(callback,id,CarPropertyManager.SENSOR_RATE_ONCHANGE)}}
                readCar()
            }.onFailure {onStatus("기어·주차 브레이크 조회 미지원")}
        }
        val connected=Vehicle(context.applicationContext).also {vehicle=it}
        setup.execute {if(!stopped)runCatching {
            connected.initialize()
            connected.getSeat().checkSeatBeltBuckledCapabilities(onComplete={config,areas->post {
                beltSupported=config.accessType in listOf(AccessType.READ,AccessType.READ_WRITE) && areas.contains(seat)
                if(beltSupported) {
                    connected.getSeat().registerSeatBeltBuckledState(seat,beltListener);readBelt()
                } else onStatus("앞좌석 왼쪽 벨트 조회 미지원")
            }},onFailed={post {onStatus("벨트 지원 확인 실패")}})
            connected.getLight().checkAmbientLightBrightnessColorCapabilities(onComplete={config,areas->post {
                lightArea=(areas.firstOrNull {SeatValue.ROW_1_LEFT in it.values} ?: areas.firstOrNull())?.takeIf {config.accessType==AccessType.READ_WRITE}
                observe(ApiObservation("AMBIENT_CAPABILITY","VALUE",mapOf("accessType" to config.accessType.name,
                    "supportedArea" to lightArea?.values?.joinToString {it.name}.orEmpty(),"areas" to areas.joinToString(";") {it.values.joinToString {v->v.name}})))
            }},onFailed={post {onStatus("이 차량은 조명 체험을 지원하지 않아요")}})
        }.onFailure {post {onStatus("차량 부가 상태 연결 실패")}}}
        main.post(poll)
    }
    private val poll=object : Runnable {
        override fun run() {if(stopped)return;readCar();readBelt();if(originalColor!=null && !permitPulse())restoreColor();main.postDelayed(this,3_000)}
    }
    private fun readCar() {properties.forEach {id->
        runCatching {manager?.getProperty<Any>(id,0)?.let {if(it.status==CarPropertyValue.STATUS_AVAILABLE)it.value else null}}
            .onSuccess {update(id,it)}.onFailure {update(id,null)}
    }}
    private fun readBelt() {if(beltSupported)runCatching {vehicle?.getSeat()?.getCurrentSeatBeltBuckledState(seat,{post {belt(it)}},{post {belt(null)}})}.onFailure {belt(null)}}
    private fun belt(value: Boolean?) {state=state.copy(belt=value);observe(ApiObservation("SEAT_BELT",if(value==null)"NULL" else "VALUE",mapOf("frontLeft" to value.toString())));onState(state)}
    private fun update(id: Int,value: Any?) {
        if(id==VehiclePropertyIds.GEAR_SELECTION) state=state.copy(gear=when(value as? Int) {1->"N";2->"R";4->"P";8->"D";else->null})
        if(id==VehiclePropertyIds.PARKING_BRAKE_ON) state=state.copy(parkingBrake=value as? Boolean)
        observe(ApiObservation(if(id==VehiclePropertyIds.GEAR_SELECTION)"GEAR" else "PARKING_BRAKE",if(value==null)"NULL" else "VALUE",mapOf("value" to value.toString())))
        onState(state)
    }
    fun pulse(allowed: ()->Boolean) {
        val area=lightArea
        if(stopped || !allowed() || state.gear!="P") {onStatus("P단 정차 중에 조명을 체험할 수 있어요");return}
        if(area==null) {onStatus("이 차량은 실내 조명 색상 제어를 지원하지 않아요");return}
        if(context.checkSelfPermission("pleos.car.permission.CONTROL_CAR_LIGHTS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onStatus("조명 체험 권한이 없어 변경하지 않았어요");return
        }
        if(colorPending || originalColor!=null) return
        permitPulse=allowed;colorPending=true
        vehicle?.getLight()?.getAmbientLightBrightnessColor(area,onComplete={color->main.post {
            if(stopped || !allowed() || state.gear!="P" || color==null) {colorPending=false;if(stopped)releaseVehicle();return@post}
            originalColor=color
            observe(ApiObservation("AMBIENT","ORIGINAL_COLOR",mapOf("argb" to color.toString())))
            // Preserve the color encoding returned by this service (some emulators report 24-bit RGB).
            val pulseColor=(color and 0xFF000000.toInt()) or 0x65E9C5
            vehicle?.getLight()?.setAmbientLightBrightnessColor(area,pulseColor,onComplete={_,_->main.post {
                colorPending=false;observe(ApiObservation("AMBIENT","PULSE_SET"));onStatus("차량에 조명 응원 요청을 전달했어요")
                vehicle?.getLight()?.getAmbientLightBrightnessColor(area,{value->main.post {
                    observe(ApiObservation("AMBIENT","PULSE_VALUE",mapOf("argb" to value.toString())))
                    if(value?.and(0x00FFFFFF)==0x65E9C5)onStatus("차량 조명의 민트색 응답을 확인했어요")
                    else {observe(ApiObservation("AMBIENT","COLOR_UNCONFIRMED"));onStatus("조명 요청은 접수됐지만 실제 색상 변화는 미확인이에요")}
                }},{})
                if(stopped || !allowed())restoreColor() else main.postDelayed({restoreColor()},1_200)
            }},onFailed={error->main.post {
                observe(ApiObservation("AMBIENT","SET_FAILED",mapOf("error" to (error.message ?: error.javaClass.simpleName))))
                colorPending=false;onStatus("차량 조명 변경을 사용할 수 없어요");restoreColor()
            }})
        }},onFailed={main.post {colorPending=false;onStatus("조명 원래 색상 조회 실패 · 변경하지 않았어요");if(stopped)releaseVehicle()}})
    }
    private fun restoreColor() {
        val color=originalColor ?: return
        val area=lightArea ?: return
        if(restoring || colorPending)return
        restoring=true
        vehicle?.getLight()?.setAmbientLightBrightnessColor(area,color,onComplete={_,_->main.post {
            originalColor=null;restoring=false;observe(ApiObservation("AMBIENT","RESTORED"))
            vehicle?.getLight()?.getAmbientLightBrightnessColor(area,{value->main.post {
                observe(ApiObservation("AMBIENT","RESTORED_VALUE",mapOf("argb" to value.toString())))
                if(stopped)releaseVehicle()
            }},{main.post {if(stopped)releaseVehicle()}})
        }},onFailed={error->main.post {restoring=false;onStatus("조명 원래 색상 복원 실패 · 차량 설정을 확인해 주세요");observe(ApiObservation("AMBIENT","RESTORE_FAILED",mapOf("error" to (error.message ?: error.javaClass.simpleName))));if(stopped)releaseVehicle()}})
    }
    private fun post(action: ()->Unit) {main.post {if(!stopped)runCatching(action).onFailure {onStatus("차량 상태 처리 실패")}}}
    private fun releaseVehicle() {
        val connected=vehicle;vehicle=null
        setup.execute {runCatching {connected?.release()}}
    }
    fun stop() {
        stopped=true;main.removeCallbacks(poll)
        runCatching {manager?.unregisterCallback(callback)};runCatching {car?.disconnect()};manager=null;car=null
        val connected=vehicle
        setup.execute {runCatching {connected?.getSeat()?.unregisterSeatBeltBuckledState(seat,beltListener)}}
        if(originalColor!=null && !colorPending)restoreColor()
        if(originalColor==null && !colorPending)releaseVehicle()
    }
}
