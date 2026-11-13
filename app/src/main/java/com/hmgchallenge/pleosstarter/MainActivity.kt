package com.hmgchallenge.pleosstarter

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.constant.area.WheelValue
import java.util.Locale

/** First learning screen: UI, vehicle data, and a request to the built-in navigator. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val accent = Color.rgb(97, 230, 198)
    private val muted = Color.rgb(173, 188, 202)
    private var demo = true
    private var active = false
    private var session = 0
    private var vehicle: Vehicle? = null
    private var speedSource: SpeedSource? = null
    private var navigator: NaviHelper? = null
    private var navigationListener: NaviHelperEventListener? = null
    private var navigationReady = false
    private var routePending = false
    private lateinit var speed: TextView
    private lateinit var speedStatus: TextView
    private lateinit var modeStatus: TextView
    private lateinit var navigationStatus: TextView
    private lateinit var routeButton: Button
    private lateinit var openMapButton: Button
    private lateinit var destination: Spinner
    private lateinit var slider: SeekBar
    private lateinit var sttStatus: TextView
    private var sttProbe: SttProbe? = null
    private val tires = mutableListOf<TextView>()
    private val wheels = listOf(WheelValue.LEFT_FRONT, WheelValue.RIGHT_FRONT,
        WheelValue.LEFT_REAR, WheelValue.RIGHT_REAR)
    private val wheelNames = listOf("앞 왼쪽", "앞 오른쪽", "뒤 왼쪽", "뒤 오른쪽")
    private val places = listOf(
        Place("서울시청", 37.5663, 126.9779),
        Place("서울역", 37.5547, 126.9707)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        demo = savedInstanceState?.getBoolean("demo", true) ?: true
        window.statusBarColor = Color.rgb(13, 22, 31)
        window.navigationBarColor = Color.rgb(13, 22, 31)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(13, 22, 31)); addView(content)
        }
        content.addView(label("PLEOS  /  FIRST DRIVE", 13f, accent))
        content.addView(label("차근차근", 30f).apply { setTypeface(null, Typeface.BOLD) })
        content.addView(Switch(this).apply {
            text = "샘플 데이터로 보기"; setTextColor(Color.WHITE); minHeight = dp(48)
            isChecked = demo
            setOnCheckedChangeListener { _, checked -> demo = checked; restart() }
        })
        modeStatus = label("", 14f, muted)
        content.addView(modeStatus)

        val speedCard = card(content)
        speedCard.addView(label("현재 속도", 17f, muted))
        speed = label("—", 68f, accent)
        speedCard.addView(speed)
        speedCard.addView(label("km/h", 18f, muted))
        speedStatus = label("", 14f, muted)
        speedCard.addView(speedStatus)
        slider = SeekBar(this).apply {
            contentDescription = "샘플 속도 조절, 실제 차량과 무관"; max = 120; progress = 48
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                    if (demo) speed.text = value.toString()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        speedCard.addView(slider)

        val tireCard = card(content)
        tireCard.addView(label("타이어 공기압", 21f))
        tireCard.addView(label("단위 kPa · 네 바퀴 보기", 14f, muted))
        repeat(2) { row ->
            val pair = LinearLayout(this)
            repeat(2) { column ->
                val item = label(wheelNames[row * 2 + column], 17f).apply {
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                }
                tires.add(item)
                pair.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
            }
            tireCard.addView(pair)
        }
        tireCard.addView(label("샘플의 180 kPa는 임의의 비교 기준입니다. 실제 모드는 차량이 제공한 심각한 저압 기준만 조회합니다. 실측 공기압·마모·펑크 진단은 제공하지 않습니다.", 13f, muted))

        val naviCard = card(content)
        naviCard.addView(label("어디로 갈까요?", 21f))
        naviCard.addView(label("예제 목적지를 차량 내비게이션에 전달합니다.", 14f, muted))
        destination = Spinner(this).apply {
            setPopupBackgroundDrawable(GradientDrawable().apply { setColor(Color.rgb(29, 43, 55)) })
            adapter = object : ArrayAdapter<String>(this@MainActivity,
                android.R.layout.simple_spinner_item, places.map { it.name }) {
                override fun getView(position: Int, recycled: View?, parent: android.view.ViewGroup): View =
                    super.getView(position, recycled, parent).also { (it as TextView).setTextColor(Color.WHITE) }
                override fun getDropDownView(position: Int, recycled: View?, parent: android.view.ViewGroup): View =
                    super.getDropDownView(position, recycled, parent).also {
                        (it as TextView).setTextColor(Color.WHITE)
                        it.setPadding(dp(12), dp(16), dp(12), dp(16))
                    }
            }
            contentDescription = "예제 목적지 선택"; minimumHeight = dp(48)
        }
        naviCard.addView(destination)
        routeButton = Button(this).apply {
            text = "목적지 안내 요청"; setOnClickListener { requestRoute() }
        }
        naviCard.addView(routeButton)
        openMapButton = Button(this).apply {
            text = "차량 내비 화면 열기"
            setOnClickListener {
                try { navigator?.requestNavigationScreen(NavigationScreenRequest.Foreground) }
                catch (error: Exception) { navigationStatus.text = "내비 화면 요청 실패: ${error.message}" }
            }
        }
        naviCard.addView(openMapButton)
        navigationStatus = label("", 14f, muted)
        naviCard.addView(navigationStatus)

        val sttCard = card(content)
        sttCard.addView(label("음성 메모 테스트 (V2)", 21f))
        sttCard.addView(label("ON_DEVICE STT로 한 문장을 인식합니다.", 14f, muted))
        sttStatus = label("대기 중", 16f, accent)
        sttCard.addView(Button(this).apply {
            text = "🎤 말하기"; minHeight = dp(64)
            setOnClickListener { startStt() }
        })
        sttCard.addView(sttStatus)
        setContentView(scroll)
    }

    override fun onStart() { super.onStart(); active = true; restart() }

    private fun restart() {
        stopSources()
        if (!active) return
        val token = session
        speed.text = "—"
        tires.forEachIndexed { i, view ->
            view.text = "${wheelNames[i]}\n실측 공기압 미제공\n저압 기준 조회 중…"
            view.setTextColor(Color.WHITE)
        }
        slider.visibility = if (demo) View.VISIBLE else View.GONE
        routeButton.isEnabled = demo
        openMapButton.isEnabled = false
        if (demo) {
            modeStatus.text = "샘플 모드 · 아래 숫자는 실제 차량 측정값이 아닙니다."
            speed.text = "48"; slider.progress = 48
            speedStatus.text = "슬라이더를 움직여 화면을 체험하세요."
            listOf(235, 232, 166, 234).forEachIndexed { index, value ->
                tires[index].text = "${wheelNames[index]}\n$value kPa\n${if (value < 180) "저압 예시" else "기준 이상 예시"}"
                tires[index].setTextColor(if (value < 180) Color.rgb(255, 192, 109) else accent)
            }
            navigationStatus.text = "샘플 모드에서는 경로를 검색하거나 안내를 시작하지 않습니다."
            return
        }
        modeStatus.text = "실제 차량 모드 · 차량 서비스와 사용 권한이 필요합니다."
        speedStatus.text = "차량 속도 연결 중…"
        navigationStatus.text = "내비게이션 연결 중…"
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            speedStatus.text = "차량 속도는 Android Automotive에서만 지원합니다."
            wheels.indices.forEach { tireResult(it, null, "Pleos 차량 환경 필요") }
            navigationStatus.text = "실제 연결에는 Pleos Connect 차량 환경이 필요합니다."
            return
        }
        if (checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED) connectSpeed(token)
        else {
            speedStatus.text = "속도 권한을 허용해주세요."
            requestPermissions(arrayOf(SPEED_PERMISSION), 100)
        }
        connectTires(token)
        connectNavigation(token)
    }

    private fun connectSpeed(token: Int) {
        try {
            speedSource = SpeedSource(this) { value, message -> update(token) {
                speed.text = speedKmh(value)?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
                speedStatus.text = message
            } }.also { it.start() }
        } catch (error: Exception) { speedStatus.text = "속도 연결 실패: ${error.message}" }
    }

    private fun connectTires(token: Int) {
        try {
            val connected = Vehicle(applicationContext).also { vehicle = it; it.initialize() }
            connected.getTire().checkLowTirePressureCapabilities(
                onComplete = { _, areas ->
                    // Marshal to UI thread before checking the session and touching the service again.
                    update(token) {
                        wheels.forEachIndexed { i, wheel ->
                            if (areas.none { wheel in it.values }) tireResult(i, null, "차량 미지원")
                            else try {
                                connected.getTire().getLowTirePressure(WheelArea(wheel),
                                    onComplete = { value -> update(token) { tireResult(i, value, "값 없음") } },
                                    onFailed = { error -> update(token) { tireResult(i, null, error.javaClass.simpleName) } })
                            } catch (error: Exception) { tireResult(i, null, error.javaClass.simpleName) }
                        }
                    }
                },
                onFailed = { error -> update(token) {
                    wheels.indices.forEach { tireResult(it, null, error.javaClass.simpleName) }
                } }
            )
        } catch (error: Exception) {
            wheels.indices.forEach { tireResult(it, null, error.javaClass.simpleName) }
        }
        handler.postDelayed({ update(token) {
            tires.forEachIndexed { i, view ->
                if (view.text.contains("조회 중")) tireResult(i, null, "응답 없음 · 서비스 확인")
            }
        } }, 10_000)
    }

    private fun tireResult(index: Int, threshold: Float?, reason: String) {
        val value = threshold?.takeIf { it.isFinite() && it > 0 }
        tires[index].text = "${wheelNames[index]}\n실측 공기압 미제공\n" +
            (value?.let { "심각한 저압 기준 ${String.format(Locale.US, "%.1f", it)} kPa" }
                ?: "기준 조회 불가: $reason")
    }

    private fun connectNavigation(token: Int) {
        try {
            val helper = NaviHelper(applicationContext).also { navigator = it }
            val listener = object : NaviHelperEventListener {
                override fun onRoutingCompleted() = update(token) {
                    if (routePending) {
                        routePending = false; routeButton.isEnabled = true
                        navigationStatus.text = "경로 탐색 완료 · 차량 내비 화면에서 안내를 확인하세요."
                    }
                }
                override fun onError(errorCode: NaviErrorCode) = update(token) {
                    routePending = false; routeButton.isEnabled = navigationReady
                    navigationStatus.text = "내비게이션 오류: $errorCode"
                }
            }
            navigationListener = listener
            helper.initialize(onConnected = { update(token) {
                try {
                    helper.addListener(listener)
                    navigationReady = true; routeButton.isEnabled = true; openMapButton.isEnabled = true
                    navigationStatus.text = "차량 내비게이션 연결됨 · 목적지를 선택하세요."
                } catch (error: Exception) { navigationStatus.text = "내비 연결 실패: ${error.message}" }
            } }, onFailed = { error -> update(token) {
                navigationStatus.text = "내비 연결 실패: ${error.message}"
            } })
            handler.postDelayed({ update(token) {
                if (!navigationReady) navigationStatus.text = "연결 응답 없음 · Pleos 내비 서비스와 권한을 확인하세요."
            } }, 10_000)
        } catch (error: Exception) { navigationStatus.text = "내비 연결 실패: ${error.message}" }
    }

    private fun requestRoute() {
        val place = places[destination.selectedItemPosition]
        if (demo) {
            navigationStatus.text = "체험: ${place.name} 선택 (${place.latitude}, ${place.longitude})\n실제 경로 요청은 전송하지 않았습니다."
            return
        }
        if (!navigationReady || routePending) return
        try {
            routePending = true; routeButton.isEnabled = false
            navigationStatus.text = "${place.name} 경로 탐색 요청 중…"
            navigator?.requestRoute(RouteInfo(latitude = place.latitude, longitude = place.longitude,
                displayLatitude = null, displayLongitude = null, poiName = place.name,
                poiId = "", poiSubId = "", address = ""))
            val token = session
            handler.postDelayed({ update(token) {
                if (routePending) {
                    // Completion events carry no request ID: avoid overlapping requests after timeout.
                    navigationStatus.text = "경로 응답 대기 중 · 차량 내비 화면에서 확인하세요. 재연결하려면 샘플 모드를 켰다가 끄세요."
                }
            } }, 15_000)
        } catch (error: Exception) {
            routePending = false; routeButton.isEnabled = true
            navigationStatus.text = "안내 요청 실패: ${error.message}"
        }
    }

    private fun startStt() {
        // 권한이 기기에 정의돼 있지 않으면(일반 폰 에뮬레이터 등) 예외 → 그냥 진행해서 오류 메시지를 확인
        val dangerous = runCatching {
            packageManager.getPermissionInfo(STT_PERMISSION, 0).protection ==
                android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
        }.getOrDefault(false)
        if (dangerous && checkSelfPermission(STT_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            sttStatus.text = "STT 권한을 허용해주세요."
            requestPermissions(arrayOf(STT_PERMISSION), 101)
            return
        }
        val probe = sttProbe
        if (probe == null) {
            // 첫 클릭: 초기화만. "준비됨"이 뜬 뒤 다시 누르면 인식 시작
            sttProbe = SttProbe(this,onStatus={sttStatus.text=it}).also { it.start() }
        } else probe.request()
    }

    private fun update(token: Int, action: () -> Unit) {
        handler.post { if (active && session == token && !isDestroyed) action() }
    }
    private fun stopSources() {
        session++; handler.removeCallbacksAndMessages(null)
        speedSource?.stop(); speedSource = null
        runCatching { vehicle?.release() }; vehicle = null
        runCatching { navigationListener?.let { navigator?.removeListener(it) } }
        runCatching { navigator?.release() }; navigator = null; navigationListener = null
        navigationReady = false; routePending = false
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 100 && active && !demo) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) connectSpeed(session)
            else speedStatus.text = "속도 권한이 없어 측정값을 표시할 수 없습니다."
        }
        if (code == 101) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startStt()
            else sttStatus.text = "STT 권한이 거부되었습니다."
        }
    }
    override fun onStop() {
        active = false; stopSources()
        sttProbe?.release(); sttProbe = null
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("demo", demo); super.onSaveInstanceState(outState)
    }
    private fun label(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(4), 0, dp(4))
    }
    private fun card(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply { setColor(Color.rgb(24, 38, 50)); cornerRadius = dp(18).toFloat() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private data class Place(val name: String, val latitude: Double, val longitude: Double)
    companion object {
        private const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
        private const val STT_PERMISSION = "pleos.car.permission.STT_SERVICE"
    }
}
