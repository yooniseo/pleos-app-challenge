package com.hmgchallenge.pleosstarter

import android.content.Context

/**
 * STT(음성 인식) 프로브. 현재는 UI 연결 확인용 스텁입니다.
 * Pleos STT SDK가 제공되면 이 클래스를 실제 구현으로 교체하세요.
 */
class SttProbe(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    /** SDK를 초기화하고 준비 상태를 알립니다. */
    fun start() {
        onStatus("STT 준비됨 (스텁 · SDK 연결 전)")
    }

    /** 음성 인식을 요청합니다. */
    fun request() {
        onStatus("STT 인식 시작 (스텁 · SDK 연결 전)")
    }

    /** 리소스를 해제합니다. */
    fun release() {
        // 스텁 — 해제할 리소스 없음
    }
}
