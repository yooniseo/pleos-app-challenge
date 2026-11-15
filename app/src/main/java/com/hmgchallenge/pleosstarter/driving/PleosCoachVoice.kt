package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

/** Pleos speech service, not Android TTS. No replay of old guidance after reconnecting. */
class PleosCoachVoice(context: Context, private val status: (String) -> Unit,
    private val observe: (ApiObservation) -> Unit = {}) {
    private val handler = Handler(Looper.getMainLooper())
    private val sdk = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private var released = false
    private var ready = false
    private var speaking = false
    private var stopping = false
    private var next: String? = null
    private var currentText = ""
    val isReady get()=ready && !released
    val isBusy get()=speaking || stopping
    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = post { observe(ApiObservation("TTS","PLAYBACK_STARTED",mapOf("text" to currentText)));speaking = true; status("Pleos 음성 안내 중") }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = post { observe(ApiObservation("TTS","PLAYBACK_DONE",mapOf("text" to currentText)));speaking = false; stopping = false; status("Pleos 음성 준비됨"); drain() }
        override fun onStop() = post { speaking = false; stopping = false; status("음성 안내 멈춤"); drain() }
        override fun onError(errorType: ErrorType) = post {
            observe(ApiObservation("TTS","ERROR",mapOf("error" to errorType.name)))
            speaking = false; stopping = false; next = null; status("Pleos 음성 오류 · $errorType")
        }
    }
    fun start() {
        status("Pleos 음성 서비스 연결 중")
        try {
            sdk.initialize(object : OnInitializeListener {
                override fun onSuccess() = post {
                    runCatching { sdk.addEventListener(events); sdk.setLocale(Locale.KOREA) }
                        .onSuccess { ready = true; observe(ApiObservation("TTS","CONNECTED"));status("Pleos ON_DEVICE 음성 준비됨") }
                        .onFailure { status("음성 등록 실패 · ${it.javaClass.simpleName}") }
                }
                override fun onFailure(errorType: ErrorType) = post {
                    observe(ApiObservation("TTS","ERROR",mapOf("error" to errorType.name)));status("Pleos 음성 연결 실패 · $errorType")
                }
            })
            handler.postDelayed({ if (!released && !ready) status("Pleos 음성 응답 없음 · 화면 안내 사용") }, 10_000)
        } catch (error: Exception) { status("Pleos 음성 연결 실패 · ${error.javaClass.simpleName}") }
    }
    fun speak(text: String) {
        if (!ready || released) { status("음성 서비스 미준비 · 화면에서 안내를 확인해 주세요"); return }
        next = text
        observe(ApiObservation("COACH_SPEECH","REQUEST",mapOf("text" to text)))
        if (speaking) requestStop()
        else drain()
    }
    private fun drain() {
        val text = next ?: return
        next = null; speaking = true; currentText = text
        runCatching { sdk.speak(text) }.onFailure { speaking = false; status("음성 요청 실패 · ${it.javaClass.simpleName}") }
    }
    fun stop() { next = null; if(ready && speaking) requestStop() }
    private fun requestStop() {
        if(stopping) return
        stopping = true
        runCatching { sdk.stop() }.onFailure { next = null; status("음성 중단 요청 실패 · ${it.javaClass.simpleName}") }
    }
    fun release() {
        stop(); released = true; handler.removeCallbacksAndMessages(null)
        runCatching { if (ready) sdk.removeEventListener(events) }; runCatching { sdk.release() }
        ready = false; speaking = false
    }
    private fun post(action: () -> Unit) { handler.post { if (!released) action() } }
}
