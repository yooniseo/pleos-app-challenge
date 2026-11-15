package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class ReplayControls(val gear: String, val speed: Int, val steering: Int, val brake: Int, val signal: Indicator) {
    companion object {
        val parked = ReplayControls("Park", 0, 0, 0, Indicator.OFF)

        fun from(scenario: SimpleRoadScenario, override: Indicator?): ReplayControls {
            val s = scenario
            if (!s.started || s.complete) return parked
            val turn = s.lesson == RoadLesson.TURN || s.lesson == RoadLesson.COMBINED
            val signal = override ?: s.replaySignal()
            val reversing = s.lesson == RoadLesson.PARKING && s.parkingGear == "R"
            val braking = s.speedMps == 0.0 || (turn && s.speedMps*3.6 > s.roadReplayTargetKmh(s.distance)+0.1)
            val brake = if (braking) 25 else 0
            return ReplayControls(if (reversing) "Reverse" else "Drive", (s.speedMps * 3.6).roundToInt(),
                -s.steeringTarget.roundToInt(), brake, signal)
        }
    }
}

/** One serial writer for the emulator's local Virtual Console, shared across Activity recreation. */
internal object ReplayConsole {
    // Submission release retains replay on Pleos emulators; physical vehicles never use this writer.
    fun supported(context: Context) =
        context.packageManager.hasSystemFeature("android.hardware.type.automotive") &&
            Build.HARDWARE in setOf("ranchu", "goldfish")

    private const val session = "/browser/session?client=chageun-auto-demo"
    @Volatile private var desired: ReplayControls? = null
    @Volatile private var ownsInput = false
    @Volatile private var releasedAt = 0L
    private var sent: ReplayControls? = null
    private var ready = false
    private var task: ScheduledFuture<*>? = null
    @Volatile var status = ""
        private set
    private val worker = Executors.newSingleThreadScheduledExecutor()

    @Synchronized fun update(frame: ReplayControls?) {
        desired = frame
        if (frame == null) return
        ownsInput = true
        if (task == null) {
            task = worker.scheduleWithFixedDelay(::publish, 0, 350, TimeUnit.MILLISECONDS)
        }
    }

    /** The console exposes display telltales for the belt and parking brake, not buckle/latch sensors. */
    fun publishChecklist(preparation: Boolean,done: (Boolean)->Unit) {
        worker.execute {
            val client="/browser/session?client=chageun-checklist"
            val commands=if(preparation)listOf(
                "/vehicle/telltales/simulator?type=SeatBelt&state=Off&color=Red",
                "/vehicle/lights?feature=headlamp_mode&value=Auto",
                "/vehicle/ev-battery?field=soc&value=82"
            ) else listOf(
                "/vehicle/telltales/simulator?type=ParkingBrake&state=On&color=Red",
                "/vehicle/body?window_fl=0&window_fr=0&window_rl=0&window_rr=0",
                "/vehicle/lights?feature=headlamp_mode&value=Off"
            )
            val result=runCatching {
                request("POST",client)
                try { commands.forEach {request("PUT",it)} }
                finally {request("POST","$client&closing=1")}
            }
            result.exceptionOrNull()?.let {Log.w("ChageunReplay","Checklist console input failed",it)}
            done(result.isSuccess)
        }
    }

    fun filtersVehicleInput(sampleAt: Long) = ownsInput || releasedAt > 0 && sampleAt <= releasedAt + 1500

    private fun publish() {
        val frame = desired
        if (frame == null && !ownsInput) return
        try {
            request("POST", session)
            if (!ready) {
                val state = JSONObject(request("GET", "/vehicle/state"))
                ready = state.getJSONObject("gear").optBoolean("bridge_ready") &&
                    state.getJSONObject("controls").getJSONObject("dds_ready").optBoolean("all")
                if (!ready) { status = "차량 콘솔 연결 중"; return }
            }
            val value = frame ?: ReplayControls.parked
            if (value != sent) {
                if (value.speed == 0) controls(value)
                if (value.gear != sent?.gear) request("PUT", "/vehicle/gear?gear=" + value.gear)
                if (value.signal != sent?.signal) {
                    val mode = when (value.signal) { Indicator.RIGHT -> "BlinkRight"; Indicator.LEFT -> "BlinkLeft"; else -> "Off" }
                    request("PUT", "/vehicle/turn-signal?mode=" + mode)
                }
                if (value.speed != 0) controls(value)
                sent = value
            }
            status = "차량 콘솔 연동"
            if (frame == null) {
                // EVS reacts to confirmed DDS state. Keep the session until the rear and blind-spot triggers clear.
                val state=JSONObject(request("GET","/vehicle/state"))
                val parked=state.optJSONObject("gear")?.optJSONObject("actual")?.optString("value")=="Park"
                val signalOff=state.optJSONObject("lights")?.optString("turn_signal_mode")=="Off"
                if(!parked || !signalOff || state.getJSONObject("controls").getDouble("speed_kph")!=0.0) {
                    sent=null
                    return
                }
                request("POST", session + "&closing=1")
                synchronized(this) {
                    releasedAt = SystemClock.elapsedRealtime()
                    ownsInput = desired != null
                    if (!ownsInput) {
                        task?.cancel(false)
                        task = null
                    }
                }
                ready = false
                sent = null
            }
        } catch (e: Exception) {
            if (status != "차량 콘솔 재연결 중") Log.w("ChageunReplay", "Console unavailable", e)
            status = "차량 콘솔 재연결 중"
            sent = null
            ready = false
        }
    }

    private fun controls(v: ReplayControls) {
        request("PUT", "/vehicle/controls?speed=${v.speed}&steering=${v.steering}&brake=${v.brake}&accelerator=0")
    }

    private fun request(method: String, path: String): String {
        val connection = URL("http://127.0.0.1:8088$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 1000
            connection.readTimeout = 1500
            check(connection.responseCode in 200..299) { "Console HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
