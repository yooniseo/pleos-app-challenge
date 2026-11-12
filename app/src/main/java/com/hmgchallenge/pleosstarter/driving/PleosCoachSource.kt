package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener

/** Actual SDK 2.3.2 / 3.0.5 adapter. No synthesized live vehicle or maneuver values. */
class PleosCoachSource(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onNavigation(right: Boolean, meters: Double?, description: String)
        fun onIndicator(value: Indicator)
        fun onRouteInvalidated(reason: String)
        fun onArrival()
        fun onStatus(channel: String, message: String)
    }
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var generation = 0
    private var navigator: NaviHelper? = null
    private var vehicle: Vehicle? = null
    private var navigationListener: NaviHelperEventListener? = null
    private var signalListener: TurnSignalStateListener? = null
    private var navigationReady = false
    private var signalReadable = false
    private var routing = false
    private var routeId: String? = null
    private var lastNavigationAt = 0L
    private var lastIndicatorAt = 0L

    fun start() {
        stop(); running = true
        val token = generation
        connectNavigation(token); connectIndicator(token)
        handler.post(object : Runnable {
            override fun run() {
                if (!running || token != generation) return
                if (navigationReady && !routing) {
                    runCatching { navigator?.getTBTInfo(); navigator?.getCurrentLocationInfo() }
                        .onFailure { listener.onStatus("내비", "조회 실패 · ${it.javaClass.simpleName}") }
                }
                if (signalReadable) readIndicator(token)
                val now = android.os.SystemClock.elapsedRealtime()
                if (lastNavigationAt != 0L && now - lastNavigationAt > 8_000) {
                    listener.onRouteInvalidated("내비 데이터 응답 없음")
                    listener.onStatus("내비", "유효한 안내 응답 없음 · 코치 대기")
                    lastNavigationAt = 0
                }
                if (lastIndicatorAt != 0L && now - lastIndicatorAt > 6_000) {
                    listener.onIndicator(Indicator.UNKNOWN)
                    listener.onStatus("방향지시등", "응답 없음 · 점등 상태 미확인")
                    lastIndicatorAt = 0
                }
                handler.postDelayed(this, 2_000)
            }
        })
        handler.postDelayed({ post(token) {
            if (lastNavigationAt == 0L) listener.onStatus("내비", "안내 응답 없음 · 차량 내비에서 경로 안내를 시작해 주세요")
            if (lastIndicatorAt == 0L) listener.onStatus("방향지시등", "유효한 상태 응답 없음 · 서비스/권한/지원 확인 필요")
        } }, 10_000)
    }

    private fun connectNavigation(token: Int) {
        listener.onStatus("내비", "Pleos 내비 서비스 연결 중")
        try {
            val helper = NaviHelper(context.applicationContext).also { navigator = it }
            val events = object : NaviHelperEventListener {
                override fun onTBTInfo(tbtInfoList: List<TBTInfo>) = post(token) {
                    if (!routing) {
                        lastNavigationAt = android.os.SystemClock.elapsedRealtime()
                        // Do not pick a later RIGHT past the nearest maneuver. Its identity would be ambiguous.
                        val next = tbtInfoList.firstOrNull()
                        val distance = next?.distance?.takeIf { it.isFinite() && it >= 0 }
                        listener.onNavigation(next?.type == TurnType.TURN_RIGHT, distance, next?.description.orEmpty())
                        listener.onStatus("내비", if (next == null) "다음 안내 없음" else "실제 TBT · ${next.type.name} · ${distance?.toInt() ?: "—"}m")
                    }
                }
                override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = post(token) {
                    val nextId = currentLocationInfo.routeId.takeIf { it.isNotBlank() }
                    if (routeId != null && nextId != routeId) listener.onRouteInvalidated("경로 ID 변경")
                    routeId = nextId
                }
                override fun onRouteStarted(routeStartInfo: RouteStartInfo) = post(token) {
                    routing = true; listener.onRouteInvalidated("경로 탐색 중")
                }
                override fun onRoutingCompleted() = post(token) {
                    routing = false; listener.onRouteInvalidated("새 경로 탐색 완료"); helper.getTBTInfo()
                }
                override fun onReRouteEnded() = post(token) {
                    routing = false; listener.onRouteInvalidated("재탐색 완료"); helper.getTBTInfo()
                }
                override fun onRouteCancelled() = post(token) {
                    routing = true; routeId = null; listener.onRouteInvalidated("경로 안내 취소")
                }
                override fun onDestinationChanged() = post(token) {
                    listener.onRouteInvalidated("목적지 변경")
                }
                override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = post(token) {
                    routing = true; listener.onRouteInvalidated("목적지 도착 · 수행 자동 판정 없음"); listener.onArrival()
                }
                override fun onError(errorCode: NaviErrorCode) = post(token) {
                    listener.onRouteInvalidated("내비 오류: $errorCode")
                    listener.onStatus("내비", "오류: $errorCode")
                }
            }
            navigationListener = events
            helper.initialize(onConnected = { post(token) {
                try {
                    helper.addListener(events); navigationReady = true
                    listener.onStatus("내비", "연결됨 · 실제 TBT 응답 대기")
                    helper.getCurrentLocationInfo(); helper.getTBTInfo()
                } catch (error: Exception) { listener.onStatus("내비", "등록 실패 · ${error.javaClass.simpleName}") }
            } }, onFailed = { error -> post(token) {
                navigationReady = false; listener.onRouteInvalidated("내비 연결 실패")
                listener.onStatus("내비", "연결 실패 · ${error.javaClass.simpleName}")
            } })
        } catch (error: Exception) { listener.onStatus("내비", "연결 실패 · ${error.javaClass.simpleName}") }
    }

    private fun connectIndicator(token: Int) {
        listener.onStatus("방향지시등", "지원 여부 확인 중")
        try {
            val connected = Vehicle(context.applicationContext).also { vehicle = it; it.initialize() }
            val api = connected.getTurnSignal()
            val events = object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = post(token) { indicator(state) }
                override fun onFailed(e: Exception) = post(token) {
                    listener.onIndicator(Indicator.UNKNOWN)
                    listener.onStatus("방향지시등", "구독 실패 · ${e.javaClass.simpleName}")
                }
            }
            signalListener = events
            api.checkTurnSignalStateCapabilities(onComplete = { config -> post(token) {
                if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
                    signalReadable = true
                    runCatching { api.registerTurnSignalState(events); readIndicator(token) }
                        .onFailure { listener.onStatus("방향지시등", "등록 실패 · ${it.javaClass.simpleName}") }
                } else listener.onStatus("방향지시등", "이 차량에서 읽기 미지원 · ${config.accessType}")
            } }, onFailed = { error -> post(token) {
                listener.onStatus("방향지시등", "지원 확인 실패 · ${error.javaClass.simpleName}")
            } })
        } catch (error: Exception) { listener.onStatus("방향지시등", "연결 실패 · ${error.javaClass.simpleName}") }
    }

    private fun readIndicator(token: Int) {
        try {
            vehicle?.getTurnSignal()?.getCurrentTurnSignalState(
                onComplete = { value -> post(token) { indicator(value) } },
                onFailed = { error -> post(token) {
                    listener.onIndicator(Indicator.UNKNOWN)
                    listener.onStatus("방향지시등", "조회 실패 · ${error.javaClass.simpleName}")
                } })
        } catch (error: Exception) {
            listener.onIndicator(Indicator.UNKNOWN)
            listener.onStatus("방향지시등", "조회 실패 · ${error.javaClass.simpleName}")
        }
    }

    private fun indicator(value: TurnSignalValue?) {
        lastIndicatorAt = android.os.SystemClock.elapsedRealtime()
        val state = when (value) { TurnSignalValue.RIGHT -> Indicator.RIGHT; TurnSignalValue.LEFT -> Indicator.LEFT
            TurnSignalValue.OFF -> Indicator.OFF; null -> Indicator.UNKNOWN }
        listener.onIndicator(state)
        listener.onStatus("방향지시등", if (value == null) "값 없음" else "실제 차량 · ${value.name}")
    }

    private fun post(token: Int, action: () -> Unit) {
        handler.post { if (running && generation == token) runCatching(action).onFailure {
            listener.onStatus("연결", "응답 처리 실패 · ${it.javaClass.simpleName}")
        } }
    }

    fun openNavigation() {
        if (!navigationReady) { listener.onStatus("내비", "내비 서비스 연결 후 사용할 수 있어요"); return }
        runCatching { navigator?.requestNavigationScreen(NavigationScreenRequest.Foreground) }
            .onFailure { listener.onStatus("내비", "화면 요청 실패 · ${it.javaClass.simpleName}") }
    }

    fun stop() {
        running = false; generation++; handler.removeCallbacksAndMessages(null)
        runCatching { signalListener?.let { vehicle?.getTurnSignal()?.unregisterTurnSignalState(it) } }
        runCatching { vehicle?.release() }
        runCatching { navigationListener?.let { navigator?.removeListener(it) } }
        runCatching { navigator?.release() }
        navigator = null; vehicle = null; navigationListener = null; signalListener = null
        signalReadable = false; navigationReady = false; routing = false; routeId = null
        lastNavigationAt = 0; lastIndicatorAt = 0
    }
}
