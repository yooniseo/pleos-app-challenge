package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Debug emulator only. These are Virtual Console developer inputs, not vehicle actuator APIs. */
class EmulatorCockpitBridge(context: Context,private val status: (String)->Unit) {
    data class Controls(val gear: SimGear,val signal: SimSignal,val steering: Int,val accelerator: Int,val brake: Int,
        val speed: Int,val window: Int,val wiper: Int,val trainingAuto: Boolean=false)
    private val enabled=isAllowed(context)
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadScheduledExecutor()
    private val client="tutorial-cockpit-"+UUID.randomUUID().toString()
    @Volatile private var controls: Controls?=null
    @Volatile private var closed=false
    private var prior: Controls?=null
    private var baseline: JSONObject?=null
    private var lastStatus=""
    fun start() {
        if(!enabled) {status("앱 내부 모형 · 실제 차량 제어 없음");return}
        worker.execute {runCatching {baseline=JSONObject(request("GET","/vehicle/state"))}}
        worker.scheduleWithFixedDelay({
            if(closed)return@scheduleWithFixedDelay
            runCatching {
                request("POST","/browser/session?client=$client")
                val requested=controls ?: return@runCatching
                // OEM reverse camera takes over the app surface in R. Keep the native training
                // cockpit usable during its reverse model; resume vehicle motion input in D.
                val motion=if(requested.gear==SimGear.R)requested.copy(gear=SimGear.P,speed=0,accelerator=0) else requested
                // Auto playback uses model blinkers, keeping OEM camera popups from interrupting
                // a scripted demonstration. Manual mode still sends actual console blinker inputs.
                val value=if(requested.trainingAuto)motion.copy(signal=SimSignal.OFF) else motion
                val old=prior
                if(value.gear!=old?.gear)request("PUT","/vehicle/gear?gear="+when(value.gear){SimGear.P->"Park";SimGear.R->"Reverse";SimGear.N->"Neutral";SimGear.D->"Drive"})
                if(value.signal!=old?.signal)request("PUT","/vehicle/turn-signal?mode="+when(value.signal){SimSignal.OFF->"Off";SimSignal.LEFT->"BlinkLeft";SimSignal.RIGHT->"BlinkRight"})
                if(old==null || value.steering!=old.steering || value.speed!=old.speed || value.accelerator!=old.accelerator || value.brake!=old.brake)
                    request("PUT","/vehicle/controls?speed=${value.speed}&accelerator=${value.accelerator}&brake=${value.brake}&steering=${value.steering}")
                if(old==null || value.window!=old.window || value.wiper!=old.wiper)
                    request("PUT","/vehicle/body?window_fl=${value.window}&front_wiper="+listOf("Off","Auto","Low","High")[value.wiper.coerceIn(0,3)])
                prior=value;report(when {
                    requested.trainingAuto->"자동 시연 · 깜빡이·주변 상황은 앱 모형으로 재생"
                    requested.gear==SimGear.R->"후진 이동은 앱 모형 · 창문·와이퍼·핸들·깜빡이 입력 연동"
                    else->"Pleos 에뮬레이터 입력 연동 · 주차 브레이크·벨트는 앱 모형"
                })
            }.onFailure {android.util.Log.w("TutorialCockpit","Virtual Console input failed",it);report("앱 모형으로 체험 중 · 에뮬레이터 입력 연결 실패")}
        },0,600,TimeUnit.MILLISECONDS)
    }
    fun update(value: Controls) {controls=value}
    private fun report(value: String) {if(lastStatus!=value){lastStatus=value;main.post {if(!closed)status(value)}}}
    private fun request(method: String,path: String): String {
        require(enabled && path.substringBefore('?') in setOf("/vehicle/state","/browser/session","/vehicle/gear","/vehicle/turn-signal","/vehicle/controls","/vehicle/body"))
        val connection=URL("http://127.0.0.1:8088$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method;connection.connectTimeout=1_000;connection.readTimeout=1_500
            val code=connection.responseCode;check(code in 200..299) {"Console $method ${path.substringBefore('?')} $code ${connection.errorStream?.bufferedReader()?.use {it.readText().take(200)}}"}
            return connection.inputStream.bufferedReader().use {it.readText()}
        } finally {connection.disconnect()}
    }
    fun stop() {
        if(closed)return
        closed=true
        if(!enabled){worker.shutdownNow();return}
        worker.execute {
            runCatching {request("POST","/browser/session?client=$client")}
            runCatching {request("PUT","/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0")}
            runCatching {request("PUT","/vehicle/gear?gear=Park")}
            runCatching {request("PUT","/vehicle/turn-signal?mode=Off")}
            baseline?.optJSONObject("body")?.let {body->
                val window=body.optJSONObject("windows")?.optInt("front_left",0) ?: 0
                val mode=body.optJSONObject("wipers")?.optJSONObject("front")?.optString("mode","Off")?.takeIf {it in listOf("Off","Auto","Low","High")} ?: "Off"
                runCatching {request("PUT","/vehicle/body?window_fl=$window&front_wiper=$mode")}
            }
            runCatching {request("POST","/browser/session?client=$client&closing=1")}
            worker.shutdown()
        }
    }
    companion object {
        fun isAllowed(context: Context)=(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)!=0 &&
            Build.HARDWARE in setOf("ranchu","goldfish")
    }
}
