package com.hmgchallenge.pleosstarter

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/** V2 검증용: ON_DEVICE STT로 한 문장을 인식해 화면에 보여 준다. (앱 등록 없이 동작하는 모드) */
class SttProbe(context: Context, private val onStatus: (String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private var ready = false
    private var released = false

    private val listener = object : ResultListener {
        override fun onReady() = post("듣는 중… 말씀하세요")
        override fun onStartedRecognition() = post("인식 시작")
        override fun onUpdated(stt: String, completed: Boolean) =
            post(if (completed) "✅ 인식 완료: $stt" else "인식 중: $stt")
        override fun onEndedRecognition() = Log.d(TAG, "onEndedRecognition")
        override fun onError(errorType: ErrorType) = post("❌ 오류: $errorType")
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun start() {
        post("STT 초기화 중…")
        try {
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    if (released) return
                    try { stt.addListener(listener); ready = true; post("STT 준비됨 · 마이크 버튼을 누르세요") }
                    catch (error: Exception) { post("❌ 리스너 등록 실패: ${error.javaClass.simpleName} ${error.message}") }
                }
                override fun onFailure(errorType: ErrorType) = post("❌ 초기화 실패: $errorType")
            })
        } catch (error: Exception) { post("❌ 초기화 예외: ${error.javaClass.simpleName} ${error.message}") }
    }

    fun request() {
        if (!ready) { post("아직 준비되지 않았습니다"); return }
        try { stt.request() } catch (error: Exception) { post("❌ request 예외: ${error.javaClass.simpleName} ${error.message}") }
    }

    fun release() {
        released = true; handler.removeCallbacksAndMessages(null)
        runCatching { if (ready) stt.removeListener(listener) }
        runCatching { stt.release() }
        ready = false
    }

    private fun post(message: String) {
        Log.d(TAG, message)
        handler.post { if (!released) onStatus(message) }
    }

    private companion object { const val TAG = "SttProbe" }
}
