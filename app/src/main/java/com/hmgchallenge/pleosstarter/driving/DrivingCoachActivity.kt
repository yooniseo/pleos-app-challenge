package com.hmgchallenge.pleosstarter.driving

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import com.hmgchallenge.pleosstarter.SpeedSource
import com.hmgchallenge.pleosstarter.speedKmh
import java.util.Locale
import java.util.UUID

/** Figma 3: easy driving/parking guide. Figma 1 and 2 attach through CoachHostBindings. */
class DrivingCoachActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val mint = Color.rgb(101,233,197)
    private val muted = Color.rgb(177,195,204)
    private val bg = Color.rgb(13,25,34)
    private lateinit var repository: CoachRepository
    private lateinit var engine: CoachEngine
    private val gate = DetailGate { android.os.SystemClock.elapsedRealtime() }
    private var source: PleosCoachSource? = null
    private var speedSource: SpeedSource? = null
    private var detailSource: CarDetailSource? = null
    private var voice: PleosCoachVoice? = null
    private var active = false
    private var session = 0
    private var demo = true
    private var playing = false
    private var page = "driving"
    private var parkingStyle = ParkingStyle.REVERSE
    private var parkingStep = 0
    private var parkingRequested = false
    private val parkingShown = mutableSetOf<String>()
    private var parkingInstance = UUID.randomUUID().toString()
    private val checks = mutableSetOf<String>()
    private val statuses = linkedMapOf<String,String>()
    private var dialog: AlertDialog? = null
    private lateinit var content: LinearLayout
    private var titleView: TextView? = null
    private var cueView: TextView? = null
    private var lookView: TextView? = null
    private var signalView: TextView? = null
    private var routeView: TextView? = null
    private var liveView: TextView? = null
    private var scene: CoachSceneView? = null
    private var lastSpokenKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        demo = savedInstanceState?.getBoolean("demo", true) ?: !intent.getBooleanExtra("live_mode", false)
        window.statusBarColor = bg; window.navigationBarColor = bg
        repository = CoachRepository(this).apply {
            questPort = CoachHostBindings.questPort; feedbackPort = CoachHostBindings.feedbackPort
        }
        createEngine(); buildPage()
    }

    private fun createEngine() {
        engine = CoachEngine(demo, { event ->
            runCatching { repository.append(event) }.onFailure { statuses["기록"] = "저장 실패 · ${it.javaClass.simpleName}" }
        }, tripId = CoachHostBindings.activeTripId?.takeIf { !demo && it.isNotBlank() } ?: UUID.randomUUID().toString())
        engine.preferences = repository.preferences()
        if (demo) engine.startDemo()
        lastSpokenKey = null
        checks.clear(); parkingInstance = UUID.randomUUID().toString(); parkingStep = 0
    }

    override fun onStart() {
        super.onStart(); active = true
        if (!demo && engine.phase == CoachPhase.CANCELLED) createEngine()
        startSources()
        handler.post(tick)
        if (demo && playing) handler.postDelayed(advanceDemo, 7_000)
        updateDriving()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            engine.expireNavigation(); enforceDetailGate(); updateDriving()
            handler.postDelayed(this, 1_000)
        }
    }

    private fun startSources() {
        if (!active) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            if(!demo) {
                statuses["연결"] = "실제 연결은 Pleos Connect 차량 환경에서 지원해요"
                statuses["속도"] = "차량 데이터 없음"
            }
            updateDriving(); return
        }
        val token = session
        if(!demo) {
        source = PleosCoachSource(this, object : PleosCoachSource.Listener {
            override fun onNavigation(right: Boolean, meters: Double?, description: String) {
                if (!valid(token)) return
                engine.onNavigation(right,meters,description); updateDriving()
            }
            override fun onIndicator(value: Indicator) {
                if (!valid(token)) return
                engine.onIndicator(value); updateDriving()
            }
            override fun onRouteInvalidated(reason: String) {
                if (!valid(token)) return
                engine.cancel(reason); voice?.stop(); lastSpokenKey = null
                statuses["상황"] = reason; updateDriving()
            }
            override fun onArrival() { if (valid(token)) statuses["도착"] = "도착했어요 · 완전히 정차한 후 주차 마무리를 확인해 주세요" }
            override fun onStatus(channel: String, message: String) { if (valid(token)) { statuses[channel] = message; updateDriving() } }
        }).also { it.start() }
        }
        try {
            detailSource = CarDetailSource(this) { allowed -> if (valid(token)) {
                gate.uxAllowed = allowed; enforceDetailGate()
            } }.also { it.start() }
        } catch (_: Exception) { gate.uxAllowed = false }
        if (checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED) startSpeed(token)
        else requestPermissions(arrayOf(SPEED_PERMISSION), 403)
        try {
            voice = PleosCoachVoice(this) { message -> if (valid(token)) statuses["음성"] = message }.also { it.start() }
        } catch (error: Exception) { statuses["음성"] = "음성 서비스 연결 실패 · ${error.javaClass.simpleName}" }
    }

    private fun startSpeed(token: Int) {
        try {
            speedSource = SpeedSource(this) { value, message -> handler.post {
                if (valid(token)) {
                    gate.updateSpeed(value)
                    statuses["속도"] = speedKmh(value)?.let { "${String.format(Locale.US,"%.0f",it)} km/h · 실제 차량" } ?: message
                    enforceDetailGate(); updateDriving()
                }
            } }.also { it.start() }
        } catch (error: Exception) { gate.updateSpeed(null); statuses["속도"] = "조회 실패 · ${error.javaClass.simpleName}" }
    }

    private fun valid(token: Int) = active && session == token && !isDestroyed
    private fun stopSources() {
        session++
        source?.stop(); source = null
        speedSource?.stop(); speedSource = null
        detailSource?.stop(); detailSource = null
        voice?.release(); voice = null
        gate.uxAllowed = false; gate.updateSpeed(null)
    }

    private fun buildPage() {
        titleView=null;cueView=null;lookView=null;signalView=null;routeView=null;liveView=null;scene=null
        content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(14),dp(24),dp(24)) }
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport=true; addView(content) }
        val header = row(content)
        header.addView(label("TUTORIAL  /  뿌까",15f,mint), LinearLayout.LayoutParams(0,-2,1f))
        header.addView(label(if(demo) "● 시뮬레이션" else "● 실제 차량 연결",14f,mint))
        content.addView(label("한 걸음씩, 함께 운전해요",27f).apply { setTypeface(null,Typeface.BOLD) })
        val tabs = row(content)
        button(tabs,"주행 안내","tab_driving") { page="driving";buildPage() }
        button(tabs,"주차 연습","tab_parking") { if(detailsAllowed()) { stopPlayback();page="parking";buildPage() } else blocked() }
        button(tabs,"안내 설정","settings") { showSettings() }
        content.addView(label(repository.focusedGoal()?.let { "오늘 목표 · $it" } ?: "내 속도로 연습해요 · 퀘스트 연결 준비됨",14f,muted))
        if(page=="parking") buildParking() else buildDriving()
        setContentView(scroll)
        updateDriving()
    }

    private fun buildDriving() {
        content.addView(label(if(demo) "같은 교차로 · 차로 준비 → 우회전 → 빠져나오기" else "차량 내비의 우회전 안내에 맞춰 도움을 드려요",15f,muted))
        val body = LinearLayout(this).apply {
            orientation=if(resources.configuration.screenWidthDp>=760) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        content.addView(body)
        val picture = card(body)
        scene=CoachSceneView(this).apply { tag="coach_scene"; demo=this@DrivingCoachActivity.demo }
        picture.addView(scene,LinearLayout.LayoutParams(-1,dp(if(resources.configuration.screenWidthDp>=760) 300 else 225)))
        picture.addView(label(if(demo) "설명용 도식 · 작은 진입로와 이번 교차로를 구분해요" else "설명용 도식 · 실제 지도나 장애물 감지 화면이 아니에요",12f,muted))
        val coaching = card(body)
        coaching.addView(label("지금 필요한 한 가지",14f,mint))
        titleView=label("",23f).apply { setTypeface(null,Typeface.BOLD) }; coaching.addView(titleView)
        cueView=label("",21f);coaching.addView(cueView)
        lookView=label("",15f,muted);coaching.addView(lookView)
        signalView=label("",17f,Color.rgb(255,204,133));coaching.addView(signalView)
        routeView=label("",14f,muted);coaching.addView(routeView)
        if(body.orientation==LinearLayout.HORIZONTAL) {
            picture.layoutParams=LinearLayout.LayoutParams(0,-2,1.05f).apply { rightMargin=dp(14) }
            coaching.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        }
        val actions=row(content)
        button(actions,"다시 듣기","repeat") { handleCoachCommand("다시 말해줘") }
        button(actions,"설명 멈춤","stop_voice") { handleCoachCommand("설명 멈춰") }
        button(actions,"이 상황 기억","note") { engine.noteDifficulty(); toast("주행 후 돌아볼 수 있도록 기록했어요") }
        if(demo) {
            val controls=card(content)
            controls.addView(label("시나리오 체험 · 실제 차량 제어 없음",14f,mint))
            val buttons=row(controls)
            button(buttons,if(playing) "일시 정지" else "자동 재생","demo_play") {
                if(!detailsAllowed()) { blocked();return@button }
                playing=!playing
                handler.removeCallbacks(advanceDemo)
                if(playing) handler.postDelayed(advanceDemo,7_000)
                buildPage()
            }
            button(buttons,"다음 장면","demo_next") { if(detailsAllowed()) { engine.nextDemo();updateDriving() } else blocked() }
            button(buttons,"처음부터","demo_restart") { if(detailsAllowed()) { stopPlayback();engine.startDemo();buildPage() } else blocked() }
        }
        liveView=label("",13f,muted);content.addView(liveView)
        val bottom=row(content)
        button(bottom,if(demo) "실제 Pleos 연결" else "시뮬레이션 보기","mode") {
            if(packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) && !detailsAllowed()) { blocked();return@button }
            stopPlayback();engine.cancel("데이터 모드 전환");stopSources();statuses.clear();demo=!demo;page="driving"
            createEngine();buildPage();startSources()
        }
        button(bottom,"연결 상태","connection") { showConnection() }
    }

    private fun updateDriving(autoSpeak: Boolean = true) {
        if(!::engine.isInitialized || page!="driving") return
        val cue=engine.cue()
        val stage=when(engine.phase) {
            CoachPhase.LANE_PREPARATION -> "01  차로 준비"
            CoachPhase.RIGHT_TURN -> "02  우회전 지점 확인"
            CoachPhase.INTERSECTION_EXIT -> "03  교차로 빠져나오기"
            CoachPhase.FINISHED -> "이번 교차로 체험을 마쳤어요"
            CoachPhase.CANCELLED -> "새로운 안내를 기다려요"
            CoachPhase.WAITING -> "차량 내비에서 경로를 시작해 주세요"
        }
        titleView?.text=cue?.title ?: stage
        cueView?.text=cue?.text ?: when {
            engine.active -> "안내가 필요하면 ‘다시 듣기’를 눌러 주세요."
            engine.phase==CoachPhase.FINISHED -> "잘한 점과 어려웠던 순간은 주행 후 돌아봐요."
            else -> "유효한 우회전 안내를 받으면 상황에 맞춰 도와드릴게요."
        }
        lookView?.text=cue?.let { "살펴볼 곳 · ${it.lookAt}" }.orEmpty()
        signalView?.text=if(!engine.active) "" else when(engine.indicator) {
            Indicator.RIGHT -> if(demo) "오른쪽 방향지시등 켜짐 · 체험" else "오른쪽 방향지시등 켜짐 · 차량 관측"
            Indicator.LEFT -> "방향지시등 방향을 직접 확인해 주세요"
            Indicator.OFF -> if(cue==null) "방향지시등 꺼짐" else "방향지시등을 직접 켜 주세요"
            Indicator.UNKNOWN -> if(cue==null) "방향지시등 상태 미확인" else "방향지시등 상태 미확인 · 직접 확인해 주세요"
        }
        routeView?.text=if(demo) stage else buildString {
            append(stage)
            engine.distanceMeters?.let { append("\n내비 안내 지점까지 ${it.toInt()}m") }
            if(engine.navigationDescription.isNotBlank()) append("\n${engine.navigationDescription}")
        }
        scene?.apply { phase=engine.phase;indicator=engine.indicator;invalidate() }
        liveView?.text=if(demo) "시나리오 값은 실제 주행 기록과 구분해 저장해요." else
            listOfNotNull(statuses["속도"],statuses["내비"],statuses["연결"],statuses["도착"]).joinToString(" · ")
        if(cue!=null) {
            engine.markCueShown(cue)
            val key="${engine.instanceId}:${cue.id}:${engine.preferences.level(engine.type)}"
            if(key!=lastSpokenKey) {
                voice?.stop()
                if(autoSpeak && engine.preferences.voiceEnabled) voice?.speak(cue.text)
                lastSpokenKey=key
            }
        } else if(!engine.active && lastSpokenKey!=null) {
            voice?.stop();lastSpokenKey=null
        }
    }

    /** Feed finalized speech from scratch/gain's STT here; never forwards partial transcripts. */
    fun handleCoachCommand(command: String): Boolean {
        val text=command.replace(" ","")
        return when {
            text.contains("다시") || text.contains("반복") -> {
                engine.requestExplanation();updateDriving(false);engine.cue()?.let { voice?.speak(it.text) }
                if(demo) toast("체험은 화면 안내를 사용해요 · 차량에서 Pleos 음성 연결")
                true
            }
            text.contains("멈춰") || text.contains("그만") || text.contains("중단") -> { voice?.stop();toast("음성 설명을 멈췄어요");true }
            text.contains("어려") || text.contains("기억") -> { engine.noteDifficulty(command.take(300));toast("이 상황을 기록했어요");true }
            else -> false
        }
    }

    /** Public integration boundary. UI does not manufacture verified maneuver completions. */
    fun acceptVerifiedManeuver(input: VerifiedManeuver) { handler.post { if(active) { engine.verified(input);updateDriving() } } }

    fun refreshPreferencesFromHost() { handler.post { if(active) {
        repository.questPort=CoachHostBindings.questPort;repository.feedbackPort=CoachHostBindings.feedbackPort
        engine.preferences=repository.preferences()
        if(!engine.preferences.voiceEnabled) voice?.stop()
        lastSpokenKey=null;buildPage()
    } } }

    /** Host/test diagnostics. Call on the main thread; values are service status, never demo substitutions. */
    fun connectionStatus(): Map<String,String> = statuses.toMap()

    private val advanceDemo=object : Runnable {
        override fun run() {
            if(!active || !demo || !playing) return
            if(!detailsAllowed()) { stopPlayback();return }
            engine.nextDemo();updateDriving()
            if(engine.active) handler.postDelayed(this,7_000) else { playing=false;buildPage() }
        }
    }
    private fun stopPlayback() { playing=false;handler.removeCallbacks(advanceDemo);voice?.stop() }

    private fun buildParking() {
        if(!detailsAllowed()) { page="driving";buildDriving();return }
        val styles=row(content)
        ParkingStyle.values().forEach { style -> button(styles,style.label,"parking_${style.name}") {
            if(!detailsAllowed()) { blocked();return@button }
            parkingStyle=style;parkingStep=0;parkingRequested=false;parkingInstance=UUID.randomUUID().toString();checks.clear();buildPage()
        } }
        val step=ParkingLesson.steps(parkingStyle)[parkingStep]
        val area=card(content)
        area.addView(label("${parkingStyle.label} · ${parkingStep+1} / 5",14f,mint))
        area.addView(label(step.title,25f).apply { setTypeface(null,Typeface.BOLD) })
        val level=engine.preferences.level(SituationType.PARKING)
        if(level==GuidanceLevel.ON_REQUEST && !parkingRequested) {
            area.addView(label("설명이 필요할 때 열어 보세요.",18f,muted))
            val request=row(area)
            button(request,"주차 설명 보기","parking_explain") {if(detailsAllowed()) {parkingRequested=true;buildPage()}}
            return
        }
        val cueKey="$parkingInstance:${parkingStyle.name}:$parkingStep:$level"
        if(parkingShown.add(cueKey)) parkingEvent(CoachEventType.CUE_SHOWN,SituationType.PARKING,"${parkingStyle.name}:lesson.$parkingStep",true)
        val picture=CoachSceneView(this).apply { parking=parkingStyle;this.parkingStep=this@DrivingCoachActivity.parkingStep;tag="parking_scene" }
        area.addView(picture,LinearLayout.LayoutParams(-1,dp(235)))
        area.addView(label(if(level==GuidanceLevel.BRIEF) step.lookAt else step.text,20f));area.addView(label("살펴볼 곳 · ${step.lookAt}",15f,muted))
        area.addView(label("정차 중 보는 학습 도식 · 실제 위치·조향각 측정 아님",12f,muted))
        val controls=row(content)
        button(controls,"이전","parking_previous") { if(detailsAllowed() && parkingStep>0) { parkingStep--;buildPage() } }
        button(controls,if(parkingStep<4) "다음 단계" else "마무리 체크","parking_next") {
            if(!detailsAllowed()) { blocked();return@button }
            if(parkingStep<4) {
                parkingStep++
                parkingRequested=false
                buildPage()
            } else showChecklist()
        }
        button(controls,"다시 맞추기","parking_retry") {
            if(detailsAllowed()) { parkingStep=1;buildPage();toast("몇 번이든 다시 연습해도 괜찮아요") }
        }
    }

    private fun showChecklist() {
        if(!detailsAllowed()) { blocked();return }
        val panel=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(this).apply { text="기어·주차 브레이크·경고 표시는 현재 자동 조회하지 않아요. 각 항목을 직접 확인해 주세요.";textSize=16f })
        val items=listOf("gear_p" to "기어 P 직접 확인", "parking_brake" to "주차 브레이크 직접 확인", "warnings" to "계기판 경고 정보 직접 확인", "surroundings" to "하차할 방향과 소지품 확인")
        items.forEach { (id,title) -> panel.addView(CheckBox(this).apply {
            text=title;minHeight=dp(54);isChecked=id in checks;tag="check_$id"
            setOnCheckedChangeListener { box,value ->
                if(!detailsAllowed()) { box.isChecked=false;return@setOnCheckedChangeListener }
                if(value && checks.add(id)) parkingEvent(CoachEventType.CHECK_CONFIRMED,SituationType.EXIT_CHECK,id,false)
                if(!value) checks.remove(id)
            }
        }) }
        dialog=AlertDialog.Builder(this).setTitle("하차 전, 직접 확인해요").setView(panel)
            .setPositiveButton("닫기",null).create().also { it.show() }
    }

    private fun parkingEvent(kind: CoachEventType,type: SituationType,detail: String,assistance: Boolean) {
        val event=CoachEvent(UUID.randomUUID().toString(),engine.tripId,parkingInstance,type,kind,System.currentTimeMillis(),
            if(demo) EvidenceSource.DEMO else EvidenceSource.USER,assistance,engine.preferences.level(type),detail=detail)
        runCatching { repository.append(event) }.onFailure { toast("기록을 저장하지 못했어요") }
    }

    private fun showSettings() {
        if(!detailsAllowed()) { blocked();return }
        val panel=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(8),dp(18),dp(8))}
        var value=engine.preferences
        val labels=mapOf(SituationType.LANE_CHANGE_RIGHT to "차로 변경",SituationType.RIGHT_TURN to "우회전",SituationType.INTERSECTION_EXIT to "교차로 빠져나오기")
        labels.forEach { (type,title) ->
            panel.addView(TextView(this).apply {text=title;textSize=16f})
            panel.addView(Spinner(this).apply {
                adapter=ArrayAdapter(this@DrivingCoachActivity,android.R.layout.simple_spinner_dropdown_item,listOf("자세히","간략히","요청할 때만"))
                setSelection(value.level(type).ordinal)
                onItemSelectedListener=object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                        value=value.copy(levels=value.levels+(type to GuidanceLevel.values()[position]))
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?)=Unit
                }
            })
        }
        panel.addView(CheckBox(this).apply {text="Pleos 음성 안내 사용";isChecked=value.voiceEnabled;minHeight=dp(48)
            setOnCheckedChangeListener {_,checked->value=value.copy(voiceEnabled=checked)} })
        if(demo) panel.addView(CheckBox(this).apply {text="자동 방향지시등 체험 · 차량 제어 없음";isChecked=value.demoAutomaticIndicator;minHeight=dp(48)
            setOnCheckedChangeListener {_,checked->value=value.copy(demoAutomaticIndicator=checked)} })
        panel.addView(TextView(this).apply {text="퀘스트 연결 시 안내 수준은 퀘스트 쪽 설정을 받아요. 단계별 완료 동그라미와 성장 계산은 퀘스트 파트가 담당해요.";textSize=13f})
        dialog=AlertDialog.Builder(this).setTitle("나에게 맞는 안내").setView(ScrollView(this).apply {addView(panel)})
            .setNegativeButton("취소",null).setPositiveButton("적용") {_,_->
                if(detailsAllowed()) {
                    if(repository.questPort===UnconnectedQuestPort) { repository.savePreferences(value);engine.preferences=value }
                    else { engine.preferences=repository.preferences();toast("퀘스트 파트의 안내 설정을 적용했어요") }
                    if(!value.voiceEnabled) voice?.stop()
                    updateDriving()
                }
            }.create().also {it.show()}
    }

    private fun showConnection() {
        if(!detailsAllowed()) { blocked();return }
        val text=if(demo) "시뮬레이션 재생 중\n실제 차량 API 값과 분리해 저장해요." else
            statuses.entries.joinToString("\n\n") {"${it.key}\n${it.value}"}.ifBlank {"차량 서비스 응답 대기 중"}
        dialog=AlertDialog.Builder(this).setTitle("Pleos 연결 상태").setMessage(text)
            .setPositiveButton("닫기",null).setNeutralButton("차량 내비 열기") {_,_-> source?.openNavigation() }
            .create().also {it.show()}
    }

    private fun detailsAllowed()=gate.allows(demo && !packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
    private fun enforceDetailGate() {
        if(!detailsAllowed()) {
            if(playing) stopPlayback()
            dialog?.dismiss();dialog=null
            if(page!="driving") {page="driving";voice?.stop();buildPage()}
        }
    }
    private fun blocked()=toast("완전히 정차하고 차량 상태가 확인되면 열 수 있어요")
    private fun toast(text: String) {Toast.makeText(this,text,Toast.LENGTH_SHORT).show()}
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun label(value: String,size: Float,color: Int=Color.WHITE)=TextView(this).apply {
        text=value;textSize=size;setTextColor(color);setPadding(0,dp(5),0,dp(5))
    }
    private fun row(parent: LinearLayout)=LinearLayout(this).apply {
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;parent.addView(this,LinearLayout.LayoutParams(-1,-2))
    }
    private fun card(parent: LinearLayout)=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(14),dp(18),dp(14))
        background=GradientDrawable().apply {setColor(Color.rgb(25,43,54));cornerRadius=dp(18).toFloat()}
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12);bottomMargin=dp(8)})
    }
    private fun button(parent: LinearLayout,title: String,key: String,action: ()->Unit)=Button(this).apply {
        text=title;tag=key;isAllCaps=false;textSize=14f;minHeight=dp(52);setTextColor(Color.WHITE)
        backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(41,66,79))
        setOnClickListener {action()}
        parent.addView(this,LinearLayout.LayoutParams(0,-2,1f).apply {rightMargin=dp(5);topMargin=dp(6)})
    }
    override fun onRequestPermissionsResult(code: Int,permissions: Array<out String>,results: IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==403 && active && !demo) {
            if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED) startSpeed(session)
            else {gate.updateSpeed(null);statuses["속도"]="속도 권한 없음 · 정차 상태 미확인";updateDriving()}
        }
    }
    override fun onStop() {
        active=false;handler.removeCallbacksAndMessages(null);dialog?.dismiss();dialog=null
        voice?.stop()
        if(!demo) engine.cancel("화면 종료 · 안내 세션 중단")
        stopSources();super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {outState.putBoolean("demo",demo);super.onSaveInstanceState(outState)}
    companion object {private const val SPEED_PERMISSION="android.car.permission.CAR_SPEED"}
}
