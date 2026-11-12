package com.hmgchallenge.pleosstarter.driving

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.Intent
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
    private val runtimeEvidence = RuntimeEvidence()
    private var dialog: AlertDialog? = null
    private lateinit var content: LinearLayout
    private var titleView: TextView? = null
    private var cueView: TextView? = null
    private var lookView: TextView? = null
    private var signalView: TextView? = null
    private var routeView: TextView? = null
    private var liveView: TextView? = null
    private var scene: CoachSceneView? = null
    private val demoButtons = linkedMapOf<DemoDriveStep,Button>()
    private var demoStepView: TextView? = null
    private var demoObstructionButton: Button? = null
    private var questBridge: QuestCoachBridge? = null
    private var questStatusView: TextView? = null
    private var goalView: TextView? = null
    private var feedbackView: TextView? = null
    private var voiceStatusView: TextView? = null
    private var lastSpokenKey: String? = null
    private var mapView: CoachMapView?=null
    private var mapCaption: TextView?=null
    private var laneView: TextView?=null
    private var vehicleView: TextView?=null
    private var vehicleSource: VehicleCoachSource?=null
    private var vehicleState=VehicleCoachState()
    private var routeClient: ReferenceRouteClient?=null
    private var referenceRoute: ReferenceRoute?=null
    private var mapStatus="지도 연결 중"
    private var referenceStatus=""
    private var showMap=true
    private var livePosition: GeoPoint?=null
    private var liveHeading=0.0
    private var destination: GeoPoint?=null
    private var destinationName=""
    private var activeRouteId=""
    private var lanePlan: DriverLanePlan?=null
    private var lastMapProgress=-1.0

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
        questBridge?.close();questBridge=null
        if(CoachHostBindings.questPort===UnconnectedQuestPort) {
            runCatching { QuestCoachBridge(this,demo,intent.getStringExtra("quest_trip_id")) {
                handler.post {if(active && ::engine.isInitialized) {engine.preferences=repository.preferences();updateDriving()}}
            } }.onSuccess {questBridge=it}.onFailure {statuses["퀘스트"]="퀘스트 저장소 연결 실패 · 기존 기록 보존"}
        }
        repository.questPort=questBridge ?: CoachHostBindings.questPort
        engine = CoachEngine(demo, { event ->
            runCatching { repository.append(event) }.onFailure { statuses["기록"] = "저장 실패 · ${it.javaClass.simpleName}" }
        }, tripId = questBridge?.tripId ?: CoachHostBindings.activeTripId?.takeIf { !demo && it.isNotBlank() } ?: UUID.randomUUID().toString())
        engine.preferences = repository.preferences()
        if (demo) engine.startDemo()
        lastSpokenKey = null
        checks.clear(); parkingInstance = UUID.randomUUID().toString(); parkingStep = 0
    }

    override fun onStart() {
        super.onStart(); active = true
        if (!demo && engine.phase == CoachPhase.CANCELLED) createEngine()
        engine.preferences=repository.preferences()
        startSources()
        if(demo && referenceRoute==null) loadReferenceRoute()
        handler.post(tick)
        if (demo && playing) handler.postDelayed(advanceDemo, 7_000)
        updateDriving()
        if(intent.getBooleanExtra("open_cockpit",false) && EmulatorCockpitBridge.isAllowed(this)) {
            intent.removeExtra("open_cockpit")
            handler.post {if(active)openSimulator()}
        }
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
                clearMapRoute()
                statuses["상황"] = reason; updateDriving()
            }
            override fun onArrival() { if (valid(token)) statuses["도착"] = "도착했어요 · 완전히 정차한 후 주차 마무리를 확인해 주세요" }
            override fun onStatus(channel: String, message: String) { if (valid(token)) { statuses[channel] = message; updateDriving() } }
            override fun onPosition(point: GeoPoint,heading: Double,routeId: String) {
                if(!valid(token))return
                livePosition=point;liveHeading=heading;activeRouteId=routeId
                mapView?.setPosition(point,heading,referenceRoute==null)
            }
            override fun onDestination(point: GeoPoint,name: String) {
                if(!valid(token))return
                if(destination!=null && destination!=point)clearMapRoute()
                destination=point;destinationName=name;updateDriving(false)
            }
        },runtimeEvidence::record).also { it.start() }
        }
        try {
            detailSource = CarDetailSource(this) { allowed -> if (valid(token)) {
                gate.uxAllowed = allowed; enforceDetailGate()
            } }.also { it.start() }
        } catch (_: Exception) { gate.uxAllowed = false }
        if (checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED) startSpeed(token)
        else requestPermissions(arrayOf(SPEED_PERMISSION), 403)
        try {
            voice = PleosCoachVoice(this,{ message -> if (valid(token)) statuses["음성"] = message },runtimeEvidence::record).also { it.start() }
        } catch (error: Exception) { statuses["음성"] = "음성 서비스 연결 실패 · ${error.javaClass.simpleName}" }
        vehicleSource=VehicleCoachSource(this,{state->if(valid(token)) {
            vehicleState=state;updateDriving(false)
        }},{message->if(valid(token)) {statuses["차량 상호작용"]=message;toast(message);updateDriving(false)}},runtimeEvidence::record).also {it.start()}
    }

    private fun startSpeed(token: Int) {
        if(speedSource!=null) return
        try {
            speedSource = SpeedSource(this) { value, message -> handler.post {
                if (valid(token)) {
                    gate.updateSpeed(value)
                    runtimeEvidence.record(ApiObservation("SPEED",if(value?.isFinite()==true) "VALUE" else "NULL",
                        mapOf("metersPerSecond" to value?.toString().orEmpty(),"status" to message)))
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
        vehicleSource?.stop();vehicleSource=null;vehicleState=VehicleCoachState()
        gate.uxAllowed = false; gate.updateSpeed(null)
    }

    private fun buildPage() {
        titleView=null;cueView=null;lookView=null;signalView=null;routeView=null;liveView=null;scene=null
        demoButtons.clear();demoStepView=null;demoObstructionButton=null
        questStatusView=null;goalView=null
        feedbackView=null;voiceStatusView=null
        mapView=null;mapCaption=null;laneView=null;vehicleView=null;lastMapProgress=-1.0
        content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(14),dp(24),dp(24)) }
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport=true; addView(content) }
        val header = row(content)
        header.addView(label("TUTORIAL  /  뿌까",15f,mint), LinearLayout.LayoutParams(0,-2,1f))
        header.addView(label(if(demo) "● 시뮬레이션" else "● 차량 모드",14f,if(demo) mint else muted))
        content.addView(label("한 걸음씩, 함께 운전해요",27f).apply { setTypeface(null,Typeface.BOLD) })
        val tabs = row(content)
        button(tabs,"주행 안내","tab_driving") { page="driving";buildPage() }
        button(tabs,"운전석 체험","simulator") {
            if(!detailsAllowed()) {blocked();return@button}
            stopPlayback();openSimulator()
        }
        button(tabs,"주차 연습","tab_parking") { if(detailsAllowed()) { stopPlayback();page="parking";buildPage() } else blocked() }
        button(tabs,"안내 설정","settings") { showSettings() }
        button(tabs,"퀘스트 · 목표","quests") { openQuests() }
        button(tabs,"주행 피드백","feedback") { if(detailsAllowed()) {stopPlayback();page="feedback";buildPage()} else blocked() }
        goalView=label("",14f,muted);content.addView(goalView)
        questStatusView=label("",14f,mint);content.addView(questStatusView)
        when(page) {"parking"->buildParking();"feedback"->buildFeedback();else->buildDriving()}
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
        val mapTabs=row(picture)
        button(mapTabs,if(showMap) "도로 도식 보기" else "지도 보기","map_toggle") {showMap=!showMap;buildPage()}
        button(mapTabs,"경로 불러오기","map_route") {if(detailsAllowed())loadReferenceRoute() else blocked()}
        if(showMap) {
            mapView=CoachMapView(this).apply {
                tag="coach_map";onMapStatus={mapStatus=it;updateMapCaption()}
                referenceRoute?.let {setRoute(it)}
                if(!demo)livePosition?.let {setPosition(it,liveHeading,referenceRoute==null)}
            }
            picture.addView(mapView,LinearLayout.LayoutParams(-1,dp(if(resources.configuration.screenWidthDp>=760)300 else 225)))
            mapCaption=label("",12f,muted);picture.addView(mapCaption)
            laneView=label("",17f,mint);picture.addView(laneView)
            val mapActions=row(picture)
            button(mapActions,"차로 계획 지정","lane_plan") {showLanePlan()}
            button(mapActions,"지도 다시 불러오기","map_retry") {mapView?.retry()}
        }
        scene=CoachSceneView(this).apply { tag="coach_scene"; demo=this@DrivingCoachActivity.demo }
        scene?.visibility=if(showMap)View.GONE else View.VISIBLE
        picture.addView(scene,LinearLayout.LayoutParams(-1,dp(if(resources.configuration.screenWidthDp>=760) 300 else 225)))
        if(!showMap)picture.addView(label(if(demo) "설명용 도식 · 작은 진입로와 이번 교차로를 구분해요" else "설명용 도식 · 실제 지도나 장애물 감지 화면이 아니에요",12f,muted))
        val coaching = card(body)
        coaching.addView(label("지금 필요한 한 가지",14f,mint))
        titleView=label("",23f).apply { setTypeface(null,Typeface.BOLD) }; coaching.addView(titleView)
        cueView=label("",21f);coaching.addView(cueView)
        lookView=label("",15f,muted);coaching.addView(lookView)
        signalView=label("",17f,Color.rgb(255,204,133));coaching.addView(signalView)
        routeView=label("",14f,muted);coaching.addView(routeView)
        feedbackView=label("",16f,mint);coaching.addView(feedbackView)
        if(body.orientation==LinearLayout.HORIZONTAL) {
            picture.layoutParams=LinearLayout.LayoutParams(0,-2,1.05f).apply { rightMargin=dp(14) }
            coaching.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        }
        val actions=row(content)
        button(actions,"다시 듣기","repeat") { handleCoachCommand("다시 말해줘") }
        button(actions,"설명 멈춤","stop_voice") { handleCoachCommand("설명 멈춰") }
        button(actions,"이 상황 기억","note") { engine.noteDifficulty(); toast("주행 후 돌아볼 수 있도록 기록했어요") }
        button(actions,"음성 켜기 / 끄기","voice_toggle") {
            if(!detailsAllowed()) {blocked();return@button}
            val value=engine.preferences.copy(voiceEnabled=!engine.preferences.voiceEnabled)
            repository.savePreferences(value);engine.preferences=repository.preferences()
            if(engine.preferences.voiceEnabled) voice?.speak("주행 코치 음성 안내를 켰어요.") else voice?.stop()
            updateDriving(false)
        }
        voiceStatusView=label("",13f,muted);content.addView(voiceStatusView)
        val vehicleCard=card(content)
        vehicleCard.addView(label("차량과 함께 준비해요 · 실제 차량 상태",14f,mint))
        vehicleView=label("",15f);vehicleCard.addView(vehicleView)
        val vehicleActions=row(vehicleCard)
        button(vehicleActions,"출발 전 상태 듣기","vehicle_check") {
            val text=vehicleState.summary()+". "+vehicleState.cue(detailsAllowed())
            if(engine.preferences.voiceEnabled)voice?.speak(text);toast(text)
        }
        button(vehicleActions,"정차 후 조명 응원 요청","vehicle_light") {
            vehicleSource?.pulse {active && detailsAllowed() && vehicleState.gear=="P"} ?: toast("Pleos 차량 환경에서 체험할 수 있어요")
        }
        if(demo) {
            val controls=card(content)
            controls.addView(label("행동 버튼 시뮬레이션 · 실제 차량 제어 없음",14f,mint))
            demoStepView=label("",15f);controls.addView(demoStepView)
            controls.addView(label("미러·카메라·신호는 사용자 확인을 가정해요. 실제 영상 분석이나 통행 가능 판정이 아니에요.",12f,muted))
            val lanes=row(controls)
            listOf(2 to 1,3 to 1,4 to 2).forEach {(count,start)->
                button(lanes,"체험 ${count}차로 · ${start}→${count}","demo_lanes_$count") {
                    if(!detailsAllowed()) {blocked();return@button}
                    if(!engine.configureDemoLanes(count,start)) toast("처음부터를 누른 뒤 체험 도로를 선택해 주세요")
                    updateDriving(false)
                }
            }
            val columns=if(resources.configuration.screenWidthDp>=760) 4 else 2
            DemoDriveStep.values().filter {it!=DemoDriveStep.FINISHED}.toList().chunked(columns).forEach {steps->
                val actionRow=row(controls)
                steps.forEach {step->
                    demoButtons[step]=button(actionRow,"${step.ordinal+1}. ${step.action}","demo_action_${step.name}") {
                        if(!detailsAllowed()) { blocked();return@button }
                        stopPlayback();engine.demoAction(step);updateDriving(false)
                        if(engine.preferences.voiceEnabled) voice?.speak(engine.demoFeedback)
                    }
                }
            }
            val conditionRow=row(controls)
            demoObstructionButton=button(conditionRow,"접근 차량·보행자 가정","demo_obstruction") {
                if(!detailsAllowed()) { blocked();return@button }
                stopPlayback();engine.setDemoObstruction(!engine.demoObstruction);updateDriving(false)
                if(engine.preferences.voiceEnabled) voice?.speak(engine.demoFeedback)
            }
            controls.addView(label("빠른 미리보기 · 아래 기능은 행동 입력을 생략하고 장면만 넘겨요.",12f,muted))
            val buttons=row(controls)
            button(buttons,if(playing) "미리보기 정지" else "장면 자동 재생","demo_play") {
                if(!detailsAllowed()) { blocked();return@button }
                playing=!playing
                handler.removeCallbacks(advanceDemo)
                if(playing) handler.postDelayed(advanceDemo,7_000)
                buildPage()
            }
            button(buttons,"장면 건너뛰기","demo_next") { if(detailsAllowed()) { engine.nextDemo();updateDriving() } else blocked() }
            button(buttons,"처음부터","demo_restart") { if(detailsAllowed()) { stopPlayback();engine.startDemo();buildPage() } else blocked() }
        }
        liveView=label("",13f,muted);content.addView(liveView)
        val bottom=row(content)
        button(bottom,if(demo) "실제 Pleos 연결" else "시뮬레이션 보기","mode") {
            if(packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) && !detailsAllowed()) { blocked();return@button }
            stopPlayback();engine.cancel("데이터 모드 전환");stopSources();statuses.clear();demo=!demo;page="driving"
            clearMapRoute();livePosition=null;destination=null;activeRouteId=""
            createEngine();buildPage();startSources()
            if(demo)loadReferenceRoute()
        }
        button(bottom,"연결 상태","connection") { showConnection() }
    }

    private fun updateDriving(autoSpeak: Boolean = true) {
        if(!::engine.isInitialized || page!="driving") return
        goalView?.text=repository.focusedGoal()?.let {"오늘 목표 · $it"} ?: "퀘스트 · 목표에서 오늘 집중할 목표를 선택해요"
        questStatusView?.text=runCatching {questBridge?.summary(engine.type)}.getOrNull() ?: statuses["퀘스트"].orEmpty()
        voiceStatusView?.text="음성 ${if(engine.preferences.voiceEnabled) "ON" else "OFF"} · ${statuses["음성"] ?: "Pleos 서비스 응답 대기"}"
        feedbackView?.text=if(demo && engine.demoFeedback.isNotBlank()) "체험 피드백 · ${engine.demoFeedback}" else ""
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
        demoStepView?.text=if(engine.phase==CoachPhase.FINISHED) "7 / 7 · 행동 체험을 마쳤어요" else
            "${engine.demoStep.ordinal+1} / 7 · ${if(engine.demoObstruction) "진행 보류 · 다시 확인할 상황을 선택해요" else engine.demoStep.title}\n체험 ${engine.demoLaneCount}개 차로 · 현재 ${engine.demoCurrentLane}차로 → 목표 ${engine.demoTargetLane}차로 · 남은 이동 ${engine.demoLaneChangesRemaining}개"
        demoButtons.forEach { (step,button) ->
            val current=engine.active && step==engine.demoStep
            button.isEnabled=current && !engine.demoObstruction && detailsAllowed()
            button.alpha=if(current) 1f else 0.45f
            button.backgroundTintList=android.content.res.ColorStateList.valueOf(if(current) Color.rgb(36,111,97) else Color.rgb(41,66,79))
        }
        demoObstructionButton?.apply {
            visibility=if(engine.active && engine.demoStep in setOf(DemoDriveStep.MIRROR,DemoDriveStep.SURROUNDINGS)) View.VISIBLE else View.GONE
            text=if(engine.demoObstruction) "다시 확인한 상황 가정 · 보류 해제" else "접근 차량·보행자 있는 상황 가정"
            isEnabled=detailsAllowed()
        }
        scene?.apply { phase=engine.phase;indicator=engine.indicator;demoStep=engine.demoStep;demoObstruction=engine.demoObstruction
            laneCount=engine.demoLaneCount;currentLane=engine.demoCurrentLane;refreshPose();invalidate() }
        updateMapCaption()
        vehicleView?.text=vehicleState.summary()+"\n"+vehicleState.cue(detailsAllowed())
        if(demo && referenceRoute!=null) {
            val completed=(engine.demoCurrentLane-engine.demoStartLane).toDouble()
            val laneChanges=(engine.demoTargetLane-engine.demoStartLane).coerceAtLeast(1)
            val progress=when(engine.demoStep) {
                DemoDriveStep.SIGNAL->0.0;DemoDriveStep.MIRROR->0.10+0.4*completed/laneChanges
                DemoDriveStep.LANE_CHANGE->0.10+0.4*(completed+0.3)/laneChanges
                DemoDriveStep.STOP->0.61;DemoDriveStep.SURROUNDINGS->0.65;DemoDriveStep.TURN->0.65
                DemoDriveStep.EXIT->0.87;DemoDriveStep.FINISHED->1.0
            }
            if(progress!=lastMapProgress) {mapView?.moveAlongRoute(progress,lastMapProgress>=0);lastMapProgress=progress}
        }
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

    private fun updateMapCaption() {
        mapCaption?.text=if(demo) "$mapStatus · 판교 연습용 참고 경로\n위치는 버튼 입력에 따른 시뮬레이션 · 실제 주행 아님 · $referenceStatus"
            else "$mapStatus · 위치: Pleos 응답\n${if(referenceRoute==null) "목적지 ${destinationName.ifBlank {"응답 대기"}} · 경로 불러오기를 눌러 주세요" else "OSRM 참고 경로 · 차량 내비 경로와 다를 수 있어요"} · $referenceStatus"
        laneView?.text=if(demo) "체험 ${engine.demoCurrentLane}→${engine.demoTargetLane}차로 · 오른쪽 ${engine.demoLaneChangesRemaining}개 남음"
            else lanePlan?.instruction(activeRouteId)?.let {"직접 지정 · $it"} ?: "현재 차로 미확인 · 정차 중 차로 계획을 지정해 주세요"
    }
    private fun clearMapRoute() {
        routeClient?.cancel();referenceRoute=null;lanePlan=null;lastMapProgress=-1.0
        mapView?.setRoute(null);referenceStatus="";updateMapCaption()
    }
    private fun loadReferenceRoute() {
        val start=if(demo)GeoPoint(37.4015,127.1058) else livePosition
        val end=if(demo)GeoPoint(37.4030,127.1059) else destination
        if(start==null || end==null) {toast("Pleos 위치와 목적지 응답이 필요해요. 차량 내비에서 목적지를 설정해 주세요.");return}
        referenceStatus="경로를 불러오는 중";updateMapCaption()
        if(routeClient==null)routeClient=ReferenceRouteClient(this)
        val mode=demo;val routeId=activeRouteId
        routeClient?.request(start,end,{route->
            if(!active || demo!=mode || (!demo && routeId!=activeRouteId))return@request
            referenceRoute=route;mapView?.setRoute(route);lastMapProgress=-1.0
            referenceStatus="${route.distanceMeters.toInt()}m · ${route.roadNames.joinToString(" → ")}"
            updateDriving(false)
        },{message->referenceStatus=message;updateMapCaption()})
    }
    private fun showLanePlan() {
        if(!detailsAllowed()) {blocked();return}
        if(demo) {toast("아래 ‘체험 차로’ 버튼으로 현재·목표 차로를 선택해 주세요");return}
        if(activeRouteId.isBlank()) {toast("차량 내비에서 경로 안내를 먼저 시작해 주세요");return}
        val routeId=activeRouteId
        val fields=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(12),dp(24),dp(12))}
        fields.addView(label("왼쪽부터 1차로예요. 실제 차로를 직접 확인해 입력해 주세요. 경로가 바뀌면 초기화해요.",16f,Color.DKGRAY))
        fun input(title: String,value: Int)=EditText(this).apply {hint=title;inputType=android.text.InputType.TYPE_CLASS_NUMBER;setText(value.toString());fields.addView(this)}
        fields.addView(label("전체 차로 수",15f,Color.DKGRAY));val total=input("전체",3)
        fields.addView(label("현재 차로",15f,Color.DKGRAY));val current=input("현재",1)
        fields.addView(label("목표 차로",15f,Color.DKGRAY));val target=input("목표",3)
        dialog=AlertDialog.Builder(this).setTitle("이 경로의 차로 계획").setView(fields).setNegativeButton("취소",null)
            .setPositiveButton("지정") {_,_->
                if(!detailsAllowed() || activeRouteId!=routeId)return@setPositiveButton
                val plan=DriverLanePlan(routeId,current.text.toString().toIntOrNull() ?: 0,target.text.toString().toIntOrNull() ?: 0,total.text.toString().toIntOrNull() ?: 0)
                if(plan.valid) {lanePlan=plan;updateDriving(false)} else toast("전체 2~8개와 범위 안의 현재·목표 차로를 입력해 주세요")
            }.create().also {it.show()}
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
        repository.questPort=questBridge ?: CoachHostBindings.questPort;repository.feedbackPort=CoachHostBindings.feedbackPort
        engine.preferences=repository.preferences()
        if(!engine.preferences.voiceEnabled) voice?.stop()
        lastSpokenKey=null;buildPage()
    } } }

    /** Host/test diagnostics. Call on the main thread; values are service status, never demo substitutions. */
    fun connectionStatus(): Map<String,String> = statuses.toMap()
    fun runtimeObservations(): List<ApiObservation> = runtimeEvidence.snapshot()
    fun isDemoModeForDiagnostics(): Boolean = demo
    /** Debug emulator probe only; no path for synthetic turn or speed input into the coach. */
    fun speakConnectionCheckForTest(): Boolean {
        val debug=applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if(!debug || demo || !active || android.os.Build.HARDWARE !in setOf("ranchu","goldfish")) return false
        voice?.speak("주행 코치 음성 연결을 확인합니다.")
        return voice!=null
    }

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
                isEnabled=questBridge==null
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
        panel.addView(TextView(this).apply {text="안내 수준은 퀘스트 설정에서 바꿔요. 이 화면에서는 음성과 자동 방향지시등 체험 옵션을 설정해요.";textSize=13f})
        dialog=AlertDialog.Builder(this).setTitle("나에게 맞는 안내").setView(ScrollView(this).apply {addView(panel)})
            .setNeutralButton("퀘스트 설정") {_,_->openQuests(1)}
            .setNegativeButton("취소",null).setPositiveButton("적용") {_,_->
                if(detailsAllowed()) {
                    if(repository.questPort===UnconnectedQuestPort || questBridge!=null) { repository.savePreferences(value);engine.preferences=repository.preferences() }
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

    private fun openQuests(tab: Int=0) {
        if(!detailsAllowed()) {blocked();return}
        startActivity(Intent(this,com.hmgchallenge.pleosstarter.quests.QuestActivity::class.java)
            .putExtra("quest_demo",demo).putExtra("quest_tab",tab))
    }

    private fun buildFeedback() {
        if(demo) getSharedPreferences("driver_simulator",MODE_PRIVATE).getString("last_report",null)?.let {report->
            val simulatorPanel=card(content)
            simulatorPanel.addView(label("최근 운전석 체험",18f,mint));simulatorPanel.addView(label(report,15f,muted))
        }
        val events=repository.eventsAfter().filter {it.tripId==engine.tripId && (it.source==EvidenceSource.DEMO)==demo}
        val panel=card(content)
        panel.addView(label("주행 돌아보기 · ${if(demo) "버튼 체험" else "실제 연결 기록"}",23f,mint))
        panel.addView(label("이 기기에 저장된 기록 · 휴대전화 동기화 연결 없음",13f,muted))
        val phases=events.filter {it.eventType==CoachEventType.PHASE_RECORDED}
        panel.addView(label(if(demo) "체험에서 차로 변경·우회전·교차로 이탈 ${phases.size}/3개 장면을 기록했어요." else
            "차량 관측과 사용자 메모를 돌아봐요. 관측값만으로 운전 수행을 평가하지 않아요.",18f))
        val notes=events.filter {it.eventType==CoachEventType.DIFFICULTY_NOTED}
        panel.addView(label("내가 기억한 순간 · ${notes.size}개",18f,mint))
        if(notes.isEmpty()) panel.addView(label("‘이 상황 기억’을 누르면 당시 상황과 함께 표시돼요.",15f,muted))
        notes.forEach {panel.addView(label("${it.feedbackNote()?.category ?: it.situationType.name} · ${it.detail}",16f))}
        panel.addView(label("상황 타임라인",18f,mint))
        events.filter {it.eventType in setOf(CoachEventType.STARTED,CoachEventType.PHASE_RECORDED,CoachEventType.UNVERIFIED,
            CoachEventType.CANCELLED,CoachEventType.DEFERRED,CoachEventType.CHECK_CONFIRMED)}.takeLast(24).forEach {event->
            val time=java.text.SimpleDateFormat("HH:mm:ss",Locale.KOREA).format(java.util.Date(event.timestamp))
            val title=when(event.eventType) {
                CoachEventType.PHASE_RECORDED->if(demo) "장면 체험 완료" else "공급자 수행 확인"
                CoachEventType.STARTED->"상황 시작"
                CoachEventType.CANCELLED->"상황 중단"
                CoachEventType.UNVERIFIED->"수행 미확인"
                CoachEventType.DEFERRED->"기다리기"
                else->"확인 입력"
            }
            val detail=when(event.eventType) {
                CoachEventType.CHECK_CONFIRMED->if(event.detail.startsWith("행동 버튼 · ")) event.detail.removePrefix("행동 버튼 · ").substringBefore(" · ") else "주변을 다시 확인한 상황"
                CoachEventType.PHASE_RECORDED->when(event.situationType) {
                    SituationType.LANE_CHANGE_RIGHT->"목표 차로까지 이동"
                    SituationType.RIGHT_TURN->"우회전"
                    else->"같은 교차로 빠져나오기"
                }
                CoachEventType.DEFERRED->"접근 차량·보행자를 가정하고 기다림"
                CoachEventType.STARTED->"이번 교차로의 차로 준비"
                else->event.detail
            }
            panel.addView(label("$time · $title · $detail",14f,muted))
        }
        if(events.isEmpty()) panel.addView(label("이번 주행에 기록된 상황이 아직 없어요.",16f))
        val actions=row(content)
        button(actions,"이 기록 돌아봤어요","review_confirm") {
            if(!detailsAllowed()) {blocked();return@button}
            runCatching {questBridge?.review(events)}.onSuccess {toast("같은 주행의 돌아보기를 퀘스트에 연결했어요")}
                .onFailure {toast("기록을 연결하지 못했어요. 기존 기록은 보존했어요")}
        }
        button(actions,"퀘스트에서 확인","review_quests") {openQuests(2)}
        button(actions,"주행 화면으로","review_back") {page="driving";buildPage()}
    }

    private fun detailsAllowed()=gate.allows(demo && !packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
    private fun openSimulator() {
        // Pleos labels an embedded app task by its root Activity. The debug cockpit must
        // have its own root, instead of inheriting the parked-only coach task's label.
        startActivity(Intent(this,DrivingSimulatorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
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
        routeClient?.cancel()
    }
    override fun onSaveInstanceState(outState: Bundle) {outState.putBoolean("demo",demo);super.onSaveInstanceState(outState)}
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig);buildPage()
    }
    override fun onDestroy() {routeClient?.close();questBridge?.close();questBridge=null;super.onDestroy()}
    companion object {private const val SPEED_PERMISSION="android.car.permission.CAR_SPEED"}
}
