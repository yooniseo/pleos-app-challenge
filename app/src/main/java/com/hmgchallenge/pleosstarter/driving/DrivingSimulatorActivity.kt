package com.hmgchallenge.pleosstarter.driving

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import com.hmgchallenge.pleosstarter.quests.CoachSkill
import com.hmgchallenge.pleosstarter.quests.PracticeEvidence
import com.hmgchallenge.pleosstarter.quests.QuestEngine
import com.hmgchallenge.pleosstarter.quests.QuestIntegration
import com.hmgchallenge.pleosstarter.quests.QuestStore
import java.util.UUID
import com.hmgchallenge.pleosstarter.core.data.SharedCoachRepository
import org.json.JSONObject
import org.json.JSONArray
import com.hmgchallenge.pleosstarter.SpeedSource
import kotlin.math.*

/** Driver-operated training cockpit. Real-car actuator APIs are never called here. */
class DrivingSimulatorActivity : Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private var model=DrivingSimulator()
    private var bridge: EmulatorCockpitBridge?=null
    private var voice: PleosCoachVoice?=null
    private var active=false
    private var screenVisible=false
    private var pendingCelebration=false
    private var paused=false
    private var pauseReason=""
    private var automatic=false
    private var autoDriver=TrainingAutoDriver()
    private var tripId=UUID.randomUUID().toString()
    private val recorded=mutableSetOf<String>()
    private val mint=Color.rgb(101,233,197)
    private val muted=Color.rgb(180,199,209)
    private lateinit var navigation: SimulatorNavigationView
    private lateinit var root: LinearLayout
    private lateinit var stateView: TextView
    private lateinit var controlsStateView: TextView
    private lateinit var coachVoiceView: TextView
    private lateinit var coachVoiceButton: Button
    private lateinit var movementHintView: TextView
    private lateinit var resumeRow: LinearLayout
    private lateinit var cueView: TextView
    private lateinit var stageView: TextView
    private lateinit var feedbackView: TextView
    private lateinit var targetView: TextView
    private lateinit var linkView: TextView
    private lateinit var wheel: SimulatorWheelView
    private lateinit var road: SimulatorRoadView
    private lateinit var steeringSlider: SeekBar
    private lateinit var beltButton: Button
    private lateinit var parkingButton: Button
    private lateinit var greenButton: Button
    private lateinit var pedestrianButton: Button
    private lateinit var pauseButton: Button
    private lateinit var brakeButton: Button
    private lateinit var acceleratorButton: Button
    private lateinit var autoButton: Button
    private var voiceEnabled=false
    private var lastCue=""
    private var lastStage: SimStage?=null
    private var lastFeedback=""
    private var sdkSource: PleosCoachSource?=null
    private var sdkSpeedSource: SpeedSource?=null
    private var sdkSpeed: Float?=null
    private var sdkSpeedAt=0L
    private var speedReaderReady=true
    private var lastTick=0L
    private var simple: SimpleRoadScenario?=null
    private var simpleEnabled=true
    private var simpleQuests: SimpleScenarioQuests?=null
    private var simpleQuestKey=""
    private var questSaved=true
    private var simpleLastCue=""
    private var beginnerStyle=BeginnerStyle.ADAPTIVE
    private var celebrationEnabled=true
    private var celebration: ScenarioCelebration?=null
    private var celebrationVoiceAfter=0L
    private var simpleLastSpokenAt=0L
    private var steeringFollow: SteeringFollowPractice?=null
    private var pausedBeforeFollow=false
    private val database by lazy {SharedCoachRepository.get(this)}
    private var recordingId: String?=null
    private var lastRecordingId: String?=null
    private var lastSampleAt=0L
    private var lastPlanKey=""
    private var memoDialog: DrivingMemoDialog?=null
    private var questState=com.hmgchallenge.pleosstarter.quests.QuestState()
    private var questObserver: java.io.Closeable?=null
    private var sdkSignal=Indicator.UNKNOWN
    private var bridgeStatus="에뮬레이터 연결 확인 중"
    private var voiceStatus="Pleos 음성 연결 중"
    private val coachNotes=mutableListOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(packageManager.hasSystemFeature("android.hardware.type.automotive") && !EmulatorCockpitBridge.isAllowed(this)) {
            toast("운전석 조작 체험은 개발용 에뮬레이터에서 실행해 주세요");finish();return
        }
        window.statusBarColor=Color.rgb(13,25,34);window.navigationBarColor=Color.rgb(13,25,34)
        voiceEnabled=CoachRepository(this).preferences().voiceEnabled
        val questStore=QuestStore(this,true)
        questState=questStore.load()
        questObserver=questStore.observe({questState=it;render()},{questSaved=false})
        val preferences=getSharedPreferences("scenario_coach",MODE_PRIVATE)
        val settings=JSONObject(database.snapshot("settings:scenario") {JSONObject().put("style",preferences.getString("style","ADAPTIVE")).put("celebration",preferences.getBoolean("celebration",true)).toString()}!!)
        beginnerStyle=runCatching {BeginnerStyle.valueOf(settings.optString("style","ADAPTIVE"))}.getOrDefault(BeginnerStyle.ADAPTIVE)
        celebrationEnabled=settings.optBoolean("celebration",true)
        build()
    }
    override fun onStart() {
        super.onStart();AppNavigation.own(this);resumeInternalSession()
    }
    fun resumeInternalSession() {
        if(active || isFinishing || isDestroyed || !::root.isInitialized)return
        active=true
        if(simple?.source!=ScenarioSpeed.VEHICLE)startBridge() else speedReaderReady=true
        lastTick=android.os.SystemClock.elapsedRealtime()
        celebration=ScenarioCelebration(this,{active && sdkSpeed?.let {it.isFinite() && abs(it)<.1f}==true && android.os.SystemClock.elapsedRealtime()-sdkSpeedAt<=5000},
            {status->navigation.celebrationStatus=status;render()}).also {it.start()}
        if(packageManager.hasSystemFeature("android.hardware.type.automotive")) {
            voice=PleosCoachVoice(this,{message->
                voiceStatus=message
                if(active && screenVisible && !paused && voiceEnabled && (simple==null || learningDecision().automaticSpeech) && message=="Pleos ON_DEVICE 음성 준비됨")voice?.speak(simple?.cue() ?: model.instruction())
            }).also {it.start()}
            sdkSource=PleosCoachSource(this,object : PleosCoachSource.Listener {
                override fun onNavigation(right: Boolean,meters: Double?,description: String) {}
                override fun onIndicator(value: Indicator) {if(active){sdkSignal=value;render()}}
                override fun onRouteInvalidated(reason: String) {}
                override fun onArrival() {}
                override fun onStatus(channel: String,message: String) {}
            }).also {it.start()}
            if(checkSelfPermission("android.car.permission.CAR_SPEED")==android.content.pm.PackageManager.PERMISSION_GRANTED)
                sdkSpeedSource=SpeedSource(this) {value,_->handler.post {if(active){sdkSpeed=value;sdkSpeedAt=android.os.SystemClock.elapsedRealtime();celebration?.tryLight();render()}}}.also {it.start()}
        }
        handler.post(loop)
    }
    private val loop=object : Runnable {
        override fun run() {
            if(!active)return
            val now=android.os.SystemClock.elapsedRealtime()
            val elapsed=now-lastTick;lastTick=now
            val scenario=simple
            if(scenario!=null) {
                if(steeringFollow?.check(now)==true) {
                    celebration?.ding(celebrationEnabled);navigation.celebrationMessage="띵동! 핸들 따라하기 완료"
                    navigation.celebrationUntil=now+1800
                    saveMemo("핸들 따라하기 · 직접 조작 일치","STEERING",false)
                }
                if(!paused && steeringFollow==null)scenario.advance(elapsed,sdkSpeed?.toDouble(),speedReaderReady && now-sdkSpeedAt<=5_000)
                recordSample(now);syncSimpleQuests()
                if(scenario.complete)finishRecording(true)
                render()
                bridge?.update(EmulatorCockpitBridge.Controls(SimGear.P,SimSignal.OFF,0,0,0,0,0,0,true))
                handler.postDelayed(this,50);return
            }
            if(!paused) {
                if(automatic)autoDriver.update(model,0.05)
                model.tick(0.05)
                if(automatic && model.stage==SimStage.COMPLETE) {automatic=false;model.freeze()}
            }
            if(model.stage!=SimStage.PREPARE && recordingId==null && model.stage!=SimStage.COMPLETE)startRecording(tripId,if(model.turnRight)"LEGACY_RIGHT" else "LEGACY_LEFT","REPLAY")
            recordSample(now);saveNewPractice()
            if(model.stage==SimStage.COMPLETE)finishRecording(true)
            render()
            bridge?.update(EmulatorCockpitBridge.Controls(model.gear,model.signal,model.steering.roundToInt(),
                model.accelerator.roundToInt(),model.brake.roundToInt(),(model.speed*3.6).roundToInt(),model.windowOpen,model.wiper,
                automatic || navigation.visibility==View.VISIBLE))
            handler.postDelayed(this,50)
        }
    }
    private fun build() {
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(10),dp(18),dp(12));setBackgroundColor(Color.rgb(13,25,34))
        }
        val layers=FrameLayout(this)
        layers.addView(root,FrameLayout.LayoutParams(-1,-1))
        navigation=SimulatorNavigationView(this)
        if(simpleEnabled && simple==null)simple=SimpleRoadScenario(navigation.course)
        layers.addView(navigation,FrameLayout.LayoutParams(-1,-1))
        root.visibility=View.GONE
        AppNavigation.content(this,layers,"drive")
        navigation.onAutomatic={
            if(steeringFollow!=null)toggleSteeringFollow()
            if(simple!=null) {
                if(simple!!.complete)newSimple(simple!!.lesson,simple!!.source)
                simple!!.start();paused=false;lastTick=android.os.SystemClock.elapsedRealtime()
                val at=System.currentTimeMillis()
                val id=UUID.randomUUID().toString()
                startRecording(id,simple!!.lesson.name,simple!!.source.name)
                simpleQuests=SimpleScenarioQuests(id,at)
                synchronized(QuestIntegration.lock) {
                    val store=QuestStore(this,true);questSaved=store.save(simpleQuests!!.begin(store.load(),at))
                }
                simpleQuestKey="";syncSimpleQuests();render()
            } else if(automatic) {automatic=false;paused=true;model.freeze()}
            else {reset(true,true);model.startRoadPractice();autoDriver=TrainingAutoDriver();automatic=true;paused=false}
            render()
        }
        navigation.onPausePlayback={if(steeringFollow!=null)toggleSteeringFollow()else pauseButton.performClick()}
        navigation.onControls={steeringFollow=null;finishRecording(false);closeSimpleQuest();lastRecordingId=null;coachNotes.clear();simple=null;simpleEnabled=false;model=DrivingSimulator();tripId=UUID.randomUUID().toString();recorded.clear();startBridge();navigation.visibility=View.GONE;root.visibility=View.VISIBLE;render()}
        navigation.onVoice={coachVoiceButton.performClick()}
        navigation.onNote={
            val s=simple
            if(s!=null)showMemoOptions()
            else root.findViewWithTag<Button>("sim_coach_note").performClick()
        }
        navigation.onFeedback={showFeedback()}
        navigation.onRestart={if(simple!=null)newSimple(simple!!.lesson,simple!!.source)else {reset(true,true);model.startRoadPractice();render()}}
        navigation.onQuests={AppNavigation.quests(this)}
        navigation.onScenario={chooseSimpleScenario()}
        navigation.onSpeedMode={chooseSpeedSource()}
        navigation.onProfile={chooseBeginnerProfile()}
        navigation.onSteeringPractice={toggleSteeringFollow()}
        navigation.onSteeringInput={angle->steeringFollow?.input(angle,android.os.SystemClock.elapsedRealtime());render()}
        navigation.onHelp={showMoreHelp()}
        text(root,"주행 코치와 함께 운전해요",23f,mint)
        text(root,"시뮬레이션 · 왼쪽 안내를 보며 오른쪽에서 조작해요. 핸들 각도는 가상 차량·도로 기준입니다.",12f,muted)
        val header=row(root)
        button(header,"← 지도로 돌아가기","sim_navigation") {
            if(!model.turnRight){reset(true,true);model.startRoadPractice()}
            simpleEnabled=true;newSimple(RoadLesson.COMBINED,ScenarioSpeed.REPLAY)
            root.visibility=View.GONE;navigation.visibility=View.VISIBLE;render()
        }
        button(header,"체험 끝내기","sim_close") {finish()}
        button(header,"시나리오 선택","sim_scenario") {chooseScenario()}
        button(header,"처음부터","sim_restart") {reset(model.turnRight,model.laneRight)}
        autoButton=button(header,"자동 시연 시작","sim_auto") {
            if(automatic) {
                automatic=false;paused=true;pauseReason="자동 시연을 멈췄어요. ‘계속하기’로 수동 운전을 이어갈 수 있어요.";model.freeze();voice?.stop();render()
            } else {
                if(model.practices.isNotEmpty() || coachNotes.isNotEmpty())database.saveSnapshot("last-report",reportText())
                val rainy=model.rain
                reset(model.turnRight,model.laneRight);model.rain=rainy;autoDriver=TrainingAutoDriver();automatic=true;render()
                if(voiceEnabled)voice?.speak("가상 시나리오를 처음부터 자동으로 시연할게요. 직접 조작하면 수동으로 바뀌어요.")
            }
        }
        pauseButton=button(header,"일시정지","sim_pause") {
            if(paused)resumeTraining() else {
                paused=true;pauseReason="일시정지 버튼으로 체험을 멈췄어요.";voice?.stop();model.freeze();render()
            }
        }
        button(header,"피드백","sim_feedback") {showFeedback()}
        val wide=resources.configuration.screenWidthDp>=760
        val panels=LinearLayout(this).apply {orientation=if(wide)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL}
        root.addView(panels,LinearLayout.LayoutParams(-1,0,1f))
        fun pane(tagName: String,weight: Float): LinearLayout {
            val scroll=ScrollView(this).apply {
                tag=tagName;isFillViewport=true
                background=GradientDrawable().apply {setColor(Color.rgb(25,43,54));cornerRadius=dp(16).toFloat()}
            }
            val params=if(wide)LinearLayout.LayoutParams(0,-1,weight) else LinearLayout.LayoutParams(-1,0,weight)
            params.setMargins(0,dp(8),if(wide)dp(10) else 0,0);panels.addView(scroll,params)
            return LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(16));scroll.addView(this)}
        }
        val coach=pane("training_coach_panel",1f)
        text(coach,"주행 코치  ·  시뮬레이션 입력 연결",18f,mint)
        stageView=text(coach,"",16f,muted)
        cueView=text(coach,"",23f,Color.WHITE).apply {tag="sim_cue";setTypeface(null,Typeface.BOLD)}
        resumeRow=row(coach)
        button(resumeRow,"▶ 계속하기","sim_resume_inline") {resumeTraining()}.apply {
            textSize=19f;minHeight=dp(60);backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(36,111,97))
        }
        movementHintView=text(coach,"",16f,Color.rgb(255,204,133)).apply {tag="sim_movement_hint"}
        stateView=text(coach,"",16f,muted).apply {tag="sim_state"}
        road=SimulatorRoadView(this,model).apply {tag="sim_road"}
        coach.addView(road,LinearLayout.LayoutParams(-1,dp(if(wide)440 else 300)).apply {topMargin=dp(8)})
        feedbackView=text(coach,"",17f,mint)
        val coachActions=row(coach)
        button(coachActions,"다시 듣기","sim_speak") {voice?.speak(model.instruction()) ?: toast("화면 안내를 확인해 주세요")}
        coachVoiceButton=button(coachActions,"음성 켜기","sim_coach_voice") {
            voiceEnabled=!voiceEnabled
            val repository=CoachRepository(this);repository.savePreferences(repository.preferences().copy(voiceEnabled=voiceEnabled))
            if(voiceEnabled)voice?.speak(simple?.cue() ?: model.instruction()) else voice?.stop()
            render()
        }
        button(coachActions,"이 순간 기억","sim_coach_note") {
            val stamp=java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.KOREA).format(java.util.Date())
            saveMemo("$stamp · ${model.stage.title} · ${model.instruction()}","OTHER",false);render()
        }
        coachVoiceView=text(coach,"",12f,muted)
        text(coach,"자동 시연은 처음부터 안내대로 가상 조작을 재생해요. 신호·주변 확인도 가상 입력이며 개인 실주행 기록과 구분해요.",12f,muted)
        val controls=pane("training_controls_panel",1.05f)
        text(controls,"운전석 조작판",18f,mint)
        controlsStateView=text(controls,"",13f,muted).apply {tag="sim_controls_state"}
        text(controls,"핸들을 드래그하거나 슬라이더로 돌려요",13f,muted)
        wheel=SimulatorWheelView(this).apply {tag="sim_wheel";onSteer={automatic=false;model.steer(it);render()}}
        controls.addView(wheel,LinearLayout.LayoutParams(-1,dp(155)))
        targetView=text(controls,"",16f,mint).apply {tag="sim_target"}
        steeringSlider=SeekBar(this).apply {
            tag="sim_steering";max=900;progress=450
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(v: SeekBar?,p: Int,fromUser: Boolean) {if(fromUser){automatic=false;model.steer((p-450).toDouble());render()}}
                override fun onStartTrackingTouch(v: SeekBar?) {}
                override fun onStopTrackingTouch(v: SeekBar?) {}
            })
        };controls.addView(steeringSlider)
        val trim=row(controls)
        button(trim,"−15°","sim_steer_left") {model.steer(model.steering-15);render()}
        button(trim,"0°","sim_steer_zero") {model.steer(0.0);render()}
        button(trim,"+15°","sim_steer_right") {model.steer(model.steering+15);render()}
        val pedals=row(controls)
        brakeButton=pedal(pedals,"브레이크 켜기","sim_brake",true)
        acceleratorButton=pedal(pedals,"액셀 켜기","sim_accelerator",false)
        button(pedals,"페달 모두 떼기","sim_pedals_release") {model.pedals(0.0,0.0);render()}
        text(controls,"페달은 클릭 한 번으로 유지, 다시 클릭하면 해제돼요.",12f,muted)
        text(controls,"자동 시연 중 오른쪽을 직접 조작하면 수동 운전으로 바뀌어요.",12f,muted)
        val gears=row(controls)
        SimGear.values().forEach {g->button(gears,g.name,"sim_gear_$g") {if(!model.selectGear(g))toast(model.feedback);render()}}
        val preparations=row(controls)
        beltButton=button(preparations,"","sim_belt") {if(model.stopped){model.belt=!model.belt;render()}else toast("정차 중 조작해 주세요")}
        parkingButton=button(preparations,"","sim_parking_brake") {if(!model.setParkingBrake(!model.parkingBrake))toast(model.feedback);render()}
        val signals=row(controls)
        button(signals,"← 왼쪽 깜빡이","sim_signal_left") {model.setSignal(SimSignal.LEFT);render()}
        button(signals,"깜빡이 끄기","sim_signal_off") {model.setSignal(SimSignal.OFF);render()}
        button(signals,"오른쪽 깜빡이 →","sim_signal_right") {model.setSignal(SimSignal.RIGHT);render()}
        val surroundings=row(controls)
        button(surroundings,"미러·주변 확인 입력","sim_check") {
            model.checkMirrors()
            if(paused)toast("주변 확인은 기록했어요. ‘계속하기’를 눌러 체험을 이어가요.")
            render()
        }
        button(surroundings,"와이퍼 단계 변경","sim_wiper") {model.wiper=(model.wiper+1)%4;render()}
        val body=row(controls)
        button(body,"창문 열기 50%","sim_window_open") {model.windowOpen=50;render()}
        button(body,"창문 닫기","sim_window_close") {model.windowOpen=0;render()}
        val situations=row(controls)
        greenButton=button(situations,"","sim_green") {model.green=!model.green;render()}
        pedestrianButton=button(situations,"","sim_pedestrian") {model.pedestrian=!model.pedestrian;render()}
        button(situations,"비 오는 상황 전환","sim_rain") {model.rain=!model.rain;render()}
        text(controls,"시스템 카메라가 열리면 뒤로 돌아와 ‘계속하기’를 눌러요. 코치와 조작판 사이를 누를 때는 계속 진행돼요.",12f,muted)
        linkView=text(controls,"에뮬레이터 연결 확인 중",12f,muted)
        text(controls,"R단 이동·벨트·사이드브레이크는 앱 모형입니다. 종료 시 에뮬레이터는 P·0km/h·깜빡이 OFF로 복귀해요.",12f,muted)
        render()
    }
    private fun pedal(parent: LinearLayout,title: String,tag: String,braking: Boolean)=button(parent,title,tag) {
        val wasOn=if(braking)model.brake>0 else model.accelerator>0
        if(wasOn)model.pedals(0.0,0.0)
        else model.pedals(if(braking)0.0 else 45.0,if(braking)80.0 else 0.0)
        render()
    }.apply {minHeight=dp(72)}
    private fun render() {
        if(!::wheel.isInitialized)return
        simple?.let {s->
            val decision=learningDecision()
            navigation.coachStyle=decision.style
            navigation.profileLabel=if(beginnerStyle==BeginnerStyle.ADAPTIVE)"맞춤 · ${decision.label}" else beginnerStyle.title
            navigation.learningLabel=decision.reason
            val planKey="${currentSkill()}:${decision.support}:${decision.reason}"
            recordingId?.let {id->if(planKey!=lastPlanKey) {
                lastPlanKey=planKey;database.recordEvent("$id:plan:$planKey",id,"COACHING_PLAN","SIMULATION",JSONObject().put("skill",currentSkill()).put("support",decision.support.name).put("reason",decision.reason).toString(),System.currentTimeMillis())
            }}
            navigation.steeringPractice=steeringFollow
            val received=sdkSpeed?.toDouble()?.takeIf {it.isFinite() && it>=0 && speedReaderReady && android.os.SystemClock.elapsedRealtime()-sdkSpeedAt<=5000}
            navigation.renderSimple(s,paused,voiceEnabled,questSaved,received)
            val guide=BeginnerScenarioCoach.guidance(s,decision.style)
            val cue=(guide.headline+if(decision.style==BeginnerStyle.STEP_BY_STEP)"。${guide.detail}" else "")
                .replace("1/4 바퀴","4분의 1바퀴").replace("3/4 바퀴","4분의 3바퀴")
            val now=android.os.SystemClock.elapsedRealtime()
            if(active && screenVisible && !paused && cue!=simpleLastCue && now>=celebrationVoiceAfter && (s.complete || now-simpleLastSpokenAt>=3500)) {
                simpleLastCue=cue
                if(voiceEnabled && (decision.automaticSpeech || s.complete || s.waitingForSpeed)){voice?.speak(cue);simpleLastSpokenAt=now}
            }
            return
        }
        stageView.text=(if(paused)"일시정지 · " else if(automatic)"자동 시연 · " else "수동 운전 · ")+model.stage.title.replace("왼쪽",model.laneDirection)
        stageView.setTextColor(if(paused)Color.rgb(255,204,133) else muted)
        navigation.render(model,paused,automatic,voiceEnabled)
        val cue=model.instruction()
        cueView.text=if(paused)"일시정지 중이에요. ‘계속하기’를 눌러 체험을 이어가요." else cue
        resumeRow.visibility=if(paused)View.VISIBLE else View.GONE
        movementHintView.text=when {
            paused->pauseReason.ifBlank {"안내와 조작 입력은 보이지만 차량 이동은 잠시 멈춘 상태예요."}
            automatic->"자동 시연 중 · 벨트·기어·깜빡이·핸들·페달과 가상 주변 상황을 순서대로 조작해요."
            else->model.movementHint().orEmpty()
        }
        movementHintView.visibility=if(movementHintView.text.isBlank())View.GONE else View.VISIBLE
        stateView.text="${model.gear}단 · ${"%.1f".format(java.util.Locale.US,model.speed*3.6)}km/h · 핸들 ${model.steering.roundToInt()}° · 깜빡이 ${when(model.signal){SimSignal.OFF->"꺼짐";SimSignal.LEFT->"왼쪽";SimSignal.RIGHT->"오른쪽"}}\n"+
            model.laneStatus
        controlsStateView.text="액셀 ${model.accelerator.roundToInt()}% · 브레이크 ${model.brake.roundToInt()}% · 창문 ${model.windowOpen}% · 와이퍼 ${listOf("OFF","AUTO","LOW","HIGH")[model.wiper]}"
        wheel.angle=model.steering;wheel.target=model.targetSteering;wheel.invalidate()
        if(!steeringSlider.isPressed)steeringSlider.progress=model.steering.roundToInt()+450
        val target=model.targetSteering
        targetView.text="목표 ${if(target<0)"왼쪽" else if(target>0)"오른쪽" else "가운데"} ${abs(target).roundToInt()}° · 현재 ${model.steering.roundToInt()}°"
        road.model=model;road.invalidate();feedbackView.text=model.feedback
        beltButton.text="벨트 ${if(model.belt)"착용" else "미착용"}"
        parkingButton.text="사이드브레이크 ${if(model.parkingBrake)"체결" else "해제"}"
        greenButton.text="체험 신호 ${if(model.green)"초록" else "빨강"}"
        pedestrianButton.text="보행자 접근 ${if(model.pedestrian)"있음" else "없음"}"
        pauseButton.text=if(paused)"계속하기" else "일시정지"
        autoButton.text=if(automatic)"자동 시연 멈추기" else "자동 시연 시작"
        autoButton.backgroundTintList=android.content.res.ColorStateList.valueOf(if(automatic)Color.rgb(147,96,40) else Color.rgb(36,111,97))
        pauseButton.backgroundTintList=android.content.res.ColorStateList.valueOf(if(paused)Color.rgb(36,111,97) else Color.rgb(41,66,79))
        brakeButton.text=if(model.brake>0)"브레이크 유지 중\n클릭하면 해제" else "브레이크 켜기"
        acceleratorButton.text=if(model.accelerator>0) {
            if(paused)"액셀 입력 대기\n계속하기 후 적용" else "액셀 유지 중\n클릭하면 해제"
        } else "액셀 켜기"
        brakeButton.backgroundTintList=android.content.res.ColorStateList.valueOf(if(model.brake>0)Color.rgb(147,69,54) else Color.rgb(41,66,79))
        acceleratorButton.backgroundTintList=android.content.res.ColorStateList.valueOf(if(model.accelerator>0)Color.rgb(36,111,97) else Color.rgb(41,66,79))
        coachVoiceButton.text=if(voiceEnabled)"음성 끄기" else "음성 켜기"
        coachVoiceView.text="코치 음성 ${if(voiceEnabled)"ON" else "OFF"} · $voiceStatus\n기억한 순간 ${coachNotes.size}개 · 피드백에서 돌아볼 수 있어요"
        linkView.text="$bridgeStatus\nSDK 관측 · 속도 ${sdkSpeed?.let {"%.1f km/h".format(java.util.Locale.US,it*3.6)} ?: "응답 대기"} · 깜빡이 ${sdkSignal.name}"
        if(model.feedback!=lastFeedback && model.feedback.startsWith("좋아요")) {lastFeedback=model.feedback;if(screenVisible && voiceEnabled && !paused)voice?.speak(model.feedback)}
        if(model.stage!=lastStage || (cue!=lastCue && (cue.contains("깜빡이") || model.stage==SimStage.PREPARE || model.stage==SimStage.TURN_CHECK))) {
            lastStage=model.stage;lastCue=cue;if(active && screenVisible && voiceEnabled && !paused)voice?.speak(cue)
        }
    }
    private fun resumeTraining() {
        paused=false;pauseReason="";render()
        lastTick=android.os.SystemClock.elapsedRealtime()
        if(voiceEnabled)voice?.speak(simple?.cue() ?: model.instruction())
    }
    private fun startBridge() {
        if(bridge==null)bridge=EmulatorCockpitBridge(this) {bridgeStatus=it;render()}.also {it.start()}
    }
    private fun closeSimpleQuest() {
        val link=simpleQuests ?: return
        synchronized(QuestIntegration.lock) {
            val store=QuestStore(this,true);var state=store.load()
            state.session?.takeIf {it.tripId==link.tripId && it.finishedAt==null}?.let {session->
                val at=maxOf(System.currentTimeMillis(),session.events.lastOrNull()?.at ?: session.startedAt)
                session.current?.let {state=QuestEngine.acceptEvent(state,com.hmgchallenge.pleosstarter.quests.SituationEvent(
                    "${link.tripId}:cancel",it.context,com.hmgchallenge.pleosstarter.quests.SituationEventType.CANCELLED,at))}
                state=QuestEngine.finishTrip(state,link.tripId,at);store.save(state)
            }
        }
        simpleQuests=null
    }
    private fun newSimple(lesson: RoadLesson,source: ScenarioSpeed) {
        finishRecording(simple?.complete==true)
        lastRecordingId=null
        steeringFollow=null
        closeSimpleQuest();simpleEnabled=true
        simple=SimpleRoadScenario(navigation.course,lesson,if(lesson==RoadLesson.PARKING)ScenarioSpeed.REPLAY else source)
        if(lesson==RoadLesson.PARKING)startBridge()
        navigation.celebrationUntil=0;navigation.celebrationMessage="";navigation.celebrationStatus=""
        simpleQuestKey="";questSaved=true;simpleLastCue="";simpleLastSpokenAt=0;paused=false;automatic=false;model.freeze();coachNotes.clear()
        lastTick=android.os.SystemClock.elapsedRealtime();render()
    }
    private fun toggleSteeringFollow() {
        if(steeringFollow!=null) {
            steeringFollow=null;paused=pausedBeforeFollow;lastTick=android.os.SystemClock.elapsedRealtime();render();return
        }
        val s=simple ?: return
        pausedBeforeFollow=paused;paused=true;voice?.stop()
        val target=s.steeringTarget.takeIf {abs(it)>=10} ?: if(s.lesson==RoadLesson.PARKING)360.0 else 90.0
        steeringFollow=SteeringFollowPractice(target);render()
        if(voiceEnabled)voice?.speak(steeringFollow!!.instruction.replace("1/4 바퀴","4분의 1바퀴").replace("3/4 바퀴","4분의 3바퀴"))
    }
    private fun syncSimpleQuests() {
        val s=simple ?: return;val link=simpleQuests ?: return
        val key="${s.started}:${s.laneDone}:${s.complete}"
        if(key==simpleQuestKey)return
        var newlyCompleted=false
        synchronized(QuestIntegration.lock) {
            val store=QuestStore(this,true);val before=store.load();val next=link.sync(before,s,System.currentTimeMillis()) {skill->
                when(learningDecision(skill.name).support) {
                    CoachingSupport.DETAILED->com.hmgchallenge.pleosstarter.quests.AssistanceUsed.DETAILED
                    CoachingSupport.HINT->com.hmgchallenge.pleosstarter.quests.AssistanceUsed.BRIEF
                    CoachingSupport.ON_REQUEST->com.hmgchallenge.pleosstarter.quests.AssistanceUsed.NONE
                }
            }
            questSaved=next==before || store.save(next)
            newlyCompleted=questSaved && next.evidence.size>before.evidence.size
            if(questSaved)simpleQuestKey=key
        }
        if(newlyCompleted) {
            val message=if(s.lesson==RoadLesson.PARKING)"좋아요! 후진 주차 완료" else if(s.complete)"좋아요! 주행 퀘스트 완료" else "좋아요! 한 차로 이동 완료"
            navigation.celebrationMessage=message;navigation.celebrationUntil=android.os.SystemClock.elapsedRealtime()+2500
            celebrationVoiceAfter=android.os.SystemClock.elapsedRealtime()+600
            // Large quest celebration waits for the end of the virtual drive as well as real P/0.
            if(s.complete){if(screenVisible){celebration?.celebrate(celebrationEnabled);toast(message)}else pendingCelebration=true}
        }
    }
    private fun chooseSimpleScenario() {
        paused=true;voice?.stop();render()
        AlertDialog.Builder(this).setTitle("짧은 주행 연습")
            .setItems(RoadLesson.values().map {it.title}.toTypedArray()) {_,which->
                newSimple(RoadLesson.values()[which],simple?.source ?: ScenarioSpeed.REPLAY)
            }.setNegativeButton("닫기",null).show()
    }
    private fun chooseSpeedSource() {
        if(simple?.lesson==RoadLesson.PARKING){toast("주차 연습은 가상 연습장에서 자동 재생해요");return}
        paused=true;voice?.stop();render()
        AlertDialog.Builder(this).setTitle("지도 진행에 사용할 속도")
            .setItems(arrayOf("자동 재생 · 직진 20 / 회전 12km/h","차량 SDK · 받은 속도만큼 이동")) {_,which->
                val source=if(which==0)ScenarioSpeed.REPLAY else ScenarioSpeed.VEHICLE
                newSimple(simple?.lesson ?: RoadLesson.COMBINED,source)
                if(source==ScenarioSpeed.VEHICLE) {
                    speedReaderReady=false;sdkSpeed=null;sdkSpeedAt=0
                    val prior=bridge;bridge=null
                    if(prior!=null)prior.stop {
                        if(active && simple?.source==ScenarioSpeed.VEHICLE){speedReaderReady=true;sdkSpeed=null;sdkSpeedAt=0;render()}
                    } else speedReaderReady=true
                } else {speedReaderReady=true;startBridge()}
                render()
            }.setNegativeButton("닫기",null).show()
    }
    private fun chooseBeginnerProfile() {
        val wasPaused=paused;paused=true;voice?.stop();render()
        AlertDialog.Builder(this).setTitle("나에게 맞는 코치")
            .setItems(arrayOf("맞춤 · 연습 기록에 맞춰 도움 줄이기","차근차근 · 확인 순서와 이유까지","핸들 중심 · 방향·회전량·복원 시점","짧게 · 지금 필요한 행동만",if(celebrationEnabled)"축하음·조명 끄기" else "축하음·조명 켜기")) {_,which->
                if(which<4)beginnerStyle=listOf(BeginnerStyle.ADAPTIVE,BeginnerStyle.STEP_BY_STEP,BeginnerStyle.VISUAL,BeginnerStyle.BRIEF)[which] else celebrationEnabled=!celebrationEnabled
                database.saveSnapshot("settings:scenario",JSONObject().put("style",beginnerStyle.name).put("celebration",celebrationEnabled).toString())
                simpleLastCue="";paused=wasPaused;render()
            }.setNegativeButton("닫기") {_,_->paused=wasPaused;render()}.setOnCancelListener {paused=wasPaused;render()}.show()
    }
    private fun saveNewPractice() {
        val skillById=mapOf("prepare" to CoachSkill.PREPARATION,"lane1" to CoachSkill.LANE_CHANGE,"lane2" to CoachSkill.LANE_CHANGE,"lane3" to CoachSkill.LANE_CHANGE,
            "intersection" to CoachSkill.INTERSECTION)
        model.practices.forEach {practice->
            val skill=skillById[practice.id] ?: return@forEach
            val evidenceId="$tripId:${skill.name}"
            if(recorded.add(evidenceId)) synchronized(QuestIntegration.lock) {
                val store=QuestStore(this,true);val next=QuestEngine.recordPractice(store.load(),PracticeEvidence(evidenceId,skill,
                    com.hmgchallenge.pleosstarter.quests.EvidenceSource.SIMULATION,System.currentTimeMillis(),tripId))
                if(!store.save(next)) {recorded.remove(evidenceId);toast("체험 기록 저장 실패")}
            }
        }
    }
    private fun currentSkill(): String=when(simple?.lesson) {
        RoadLesson.PARKING->"PARKING"
        RoadLesson.TURN->"INTERSECTION"
        RoadLesson.LANE->"LANE_CHANGE"
        else->if(simple?.laneDone==true)"INTERSECTION" else "LANE_CHANGE"
    }
    private fun learningDecision(skill: String=currentSkill()): LearningDecision {
        val records=database.completions("demo",skill)
        val category=CoachSkill.valueOf(skill)
        val difficultAt=database.difficultyAt("demo",skill)
        return CoachLearningPolicy.forQuest(questState,category,records.count {it.at>difficultAt && !it.difficult},difficultAt>0,beginnerStyle)
    }
    private fun startRecording(id: String,lesson: String,source: String) {
        if(recordingId==id)return
        if(recordingId!=null)finishRecording(false)
        if(database.beginTrip(id,lesson,source,System.currentTimeMillis())) {
            recordingId=id;lastRecordingId=id;lastSampleAt=0;lastPlanKey=""
        } else {questSaved=false;toast("주행 DB를 시작하지 못했어요")}
    }
    private fun recordSample(now: Long) {
        val id=recordingId ?: return
        if(lastSampleAt!=0L && now-lastSampleAt<1000)return
        lastSampleAt=now
        val s=simple
        val speed=if(s?.source==ScenarioSpeed.VEHICLE) sdkSpeed?.takeIf {speedReaderReady && now-sdkSpeedAt<=5000 && it.isFinite()}?.let {abs(it)*3.6f}
            else if(paused)0f else ((s?.speedMps ?: model.speed)*3.6).toFloat()
        val metres=s?.let {it.distance-it.startDistance} ?: model.distance
        database.sample(id,System.currentTimeMillis(),speed,(metres/1000).toFloat())
    }
    private fun finishRecording(complete: Boolean) {
        val id=recordingId ?: return
        val metres=simple?.let {it.distance-it.startDistance} ?: model.distance
        if(database.endTrip(id,System.currentTimeMillis(),(metres/1000).toFloat(),complete)) {
            lastRecordingId=id;recordingId=null
        } else questSaved=false
    }
    private fun categoryForSkill()=when(currentSkill()) {"LANE_CHANGE"->"LANE_CHANGE";"INTERSECTION"->"RIGHT_TURN";"PARKING"->"PARKING";else->"OTHER"}
    private fun saveMemo(text: String,category: String,difficult: Boolean): Boolean {
        if(recordingId==null && lastRecordingId==null)startRecording(UUID.randomUUID().toString(),simple?.lesson?.name ?: "LEGACY","REPLAY")
        val id=recordingId ?: lastRecordingId ?: return false
        val skill=when(category) {"LANE_CHANGE"->"LANE_CHANGE";"RIGHT_TURN","LEFT_TURN"->"INTERSECTION";"PARKING"->"PARKING";"SPEED_CONTROL"->"SPEED";else->currentSkill()}
        val value=if(difficult)"어려웠어요 · $text" else text
        val context=JSONObject().put("skill",skill).put("category",category).put("simulated",true)
        simple?.let {s->
            val frame=s.frame()
            context.put("lesson",s.lesson.name).put("metres",s.distance-s.startDistance)
                .put("latitude",frame.position.latitude).put("longitude",frame.position.longitude)
                .put("cue",BeginnerScenarioCoach.guidance(s,learningDecision().style).headline)
        } ?: context.put("stage",model.stage.name).put("metres",model.distance).put("cue",model.instruction())
        val success=database.addNote(id,value,category,skill,difficult,contextJson=context.toString())
        if(success){
            if(difficult) {
                val integration=QuestIntegration(this,true)
                integration.snapshot().session?.current?.context?.takeIf {it.tripId==id && it.kind.skill.name==skill}?.let {
                    integration.recordDifficulty(UUID.randomUUID().toString(),it,System.currentTimeMillis(),text)
                }
            }
            if(coachNotes.size<100)coachNotes.add(value);lastPlanKey="";toast(if(difficult)"다음에도 이 상황은 차근차근 도와드릴게요" else "같은 주행 기록에 저장했어요")
        }
        return success
    }
    private fun showMemoOptions() {
        val wasPaused=paused;paused=true;voice?.stop();render()
        val skillLabel=when(currentSkill()) {"LANE_CHANGE"->"차로 변경";"INTERSECTION"->"우회전";"PARKING"->"주차";else->"이번 상황"}
        AlertDialog.Builder(this).setTitle("기억할 순간")
            .setItems(arrayOf("이 상황이 어려웠어요","좋았던 순간 기록","음성·글로 메모하기","지난 주행 기록 보기")) {_,which->
                when(which) {
                    0->{saveMemo(skillLabel,categoryForSkill(),true);paused=wasPaused;render()}
                    1->{saveMemo("좋았어요 · $skillLabel",categoryForSkill(),false);paused=wasPaused;render()}
                    2->{memoDialog=DrivingMemoDialog(this,{text,category,difficult->saveMemo(text,category,difficult)}, {memoDialog=null;paused=wasPaused;render()}).also {it.show(categoryForSkill())}}
                    else->showHistory()
                }
            }.setNegativeButton("닫기") {_,_->paused=wasPaused;render()}.setOnCancelListener {paused=wasPaused;render()}.show()
    }
    private fun showMoreHelp() {
        val s=simple ?: return;val wasPaused=paused;paused=true;voice?.stop();render()
        val guide=BeginnerScenarioCoach.guidance(s,BeginnerStyle.STEP_BY_STEP)
        recordingId?.let {database.recordEvent(UUID.randomUUID().toString(),it,"HELP_REQUESTED","USER",JSONObject().put("skill",currentSkill()).put("detail",guide.detail).toString(),System.currentTimeMillis())}
        val integration=QuestIntegration(this,true)
        integration.snapshot().session?.current?.context?.let {ctx->
            integration.reportGuidance(UUID.randomUUID().toString(),ctx,System.currentTimeMillis(),com.hmgchallenge.pleosstarter.quests.AssistanceUsed.ON_REQUEST)
        }
        AlertDialog.Builder(this).setTitle("차근차근 다시 확인해요")
            .setMessage("${guide.headline}\n\n${guide.detail}\n\n${guide.checklist}\n\n추가 설명은 언제든 다시 볼 수 있어요.")
            .setPositiveButton("이어서 연습") {_,_->paused=wasPaused;render()}
            .setNeutralButton("어려웠어요") {_,_->saveMemo(guide.checklist,categoryForSkill(),true);paused=wasPaused;render()}
            .setOnCancelListener {paused=wasPaused;render()}.show()
    }
    private fun databaseReport(id: String): String {
        val report=database.report(id) ?: return "속도·메모·완료 기록을 모으고 있어요"
        fun lines(value: String): String {val a=JSONArray(value);return (0 until a.length()).joinToString("\n") {"• "+a.getString(it)}}
        val speedSamples=database.samples(id);val notes=database.notes(id)
        return "${report.headline}\n\n잘한 점\n${lines(report.goodPoints)}\n\n다음에 연습할 점\n${lines(report.practicePoints)}\n\n속도 기록 ${speedSamples.size}개 · 기억한 순간 ${notes.size}개\n체험 기록은 실제 운전 실력 평가에 합산하지 않아요"
    }
    private fun showHistory() {
        paused=true;voice?.stop();val trips=database.trips()
        if(trips.isEmpty()){toast("아직 저장된 주행 기록이 없어요");return}
        val titles=trips.map {t->
            val date=java.text.SimpleDateFormat("MM/dd HH:mm",java.util.Locale.KOREA).format(java.util.Date(t.startedAt))
            "$date · ${if(t.source=="VEHICLE")"SDK 속도" else "체험"} · ${if(t.endedAt==null)"진행 중" else "저장됨"}"
        }
        AlertDialog.Builder(this).setTitle("지난 연습 돌아보기").setItems(titles.toTypedArray()) {_,which->
            val id=trips[which].tripId;database.previewReport(id)
            AlertDialog.Builder(this).setTitle("차근차근 주행 피드백").setMessage(databaseReport(id))
                .setPositiveButton("닫기",null).setNeutralButton("퀘스트 보기") {_,_->navigation.onQuests()}.show()
        }.setNegativeButton("이어서 연습") {_,_->paused=false;render()}.show()
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        memoDialog?.onPermissionResult(requestCode,grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    private fun chooseScenario() {
        model.freeze();paused=true;pauseReason="시나리오를 선택하는 동안 잠시 멈췄어요."
        AlertDialog.Builder(this).setTitle("도로 표시를 따르는 회전 연습")
            .setItems(arrayOf("우회전: 1 → 2 → 3차로 → 우회전","좌회전: 3 → 2 → 1차로 → 좌회전")) {_,which->reset(which==0,which==0)}
            .setNegativeButton("계속하기") {_,_->paused=false}.setOnCancelListener {paused=false}.show()
    }
    private fun reset(right: Boolean,laneRight: Boolean) {
        finishRecording(model.stage==SimStage.COMPLETE)
        lastRecordingId=null
        automatic=false
        voice?.stop()
        model.freeze();model=DrivingSimulator(right,laneRight);tripId=UUID.randomUUID().toString();recorded.clear();coachNotes.clear();paused=false;lastStage=null;lastCue="";lastFeedback="";render()
    }
    private fun showFeedback() {
        voice?.stop();model.freeze();paused=true;pauseReason="주행 피드백을 확인하는 동안 잠시 멈췄어요."
        (recordingId ?: lastRecordingId)?.let {database.previewReport(it)}
        val report=reportText()
        database.saveSnapshot("last-report",report)
        AlertDialog.Builder(this).setTitle("운전석 체험 피드백").setMessage(report)
            .setPositiveButton("계속하기") {_,_->paused=false}
            .setNeutralButton("지난 기록") {_,_->showHistory()}
            .setNegativeButton("퀘스트 보기") {_,_->AppNavigation.quests(this)}
            .setOnCancelListener {paused=false}.show()
    }
    private fun reportText(): String {
        val stored=(recordingId ?: lastRecordingId)?.let {"\n\n이번 연습 돌아보기\n"+databaseReport(it)}.orEmpty()
        simple?.let {s->return "시나리오 체험 피드백\n\n${s.lesson.title}\n"+
            (if(s.lesson==RoadLesson.PARKING)"• 후진 주차·정렬: ${if(s.complete)"완료" else "진행 중"}\n" else "")+
            (if(s.lesson in setOf(RoadLesson.COMBINED,RoadLesson.LANE))"• 오른쪽 한 차로 변경: ${if(s.laneDone)"완료" else "진행 전"}\n" else "")+
            (if(s.lesson in setOf(RoadLesson.COMBINED,RoadLesson.TURN))"• 우회전: ${if(s.complete)"완료" else "진행 전"}\n" else "")+
            "이동 ${(s.distance-s.startDistance).roundToInt()}m · ${if(s.source==ScenarioSpeed.VEHICLE)"차량 SDK 속도" else "자동 재생 속도"}\n"+
            "위치·차로는 시나리오이며 체험 퀘스트에만 반영합니다.\n"+
            (if(coachNotes.isEmpty())"" else "\n기억한 순간\n"+coachNotes.joinToString("\n"))+stored}
        return "체험 결과 · 실제 주행 성공 판정 아님\n\n"+model.practices.joinToString("\n") {"• ${it.message}"}+
        "\n\n현재 단계: ${model.stage.title}\n조작 거리 ${model.distance.roundToInt()}m · 남은 차로 ${model.lanesRemaining}개\n퀘스트의 체험 기록에 연결했어요."+
        (if(coachNotes.isEmpty())"" else "\n\n기억한 순간\n"+coachNotes.joinToString("\n"))+stored
    }
    fun simpleForDiagnostics()=simple
    fun celebrationForDiagnostics()=celebration?.diagnostics().orEmpty()
    fun celebrationSoundStarts()=celebration?.soundStarts ?: 0
    fun steeringFollowForDiagnostics()=steeringFollow
    fun learningForDiagnostics()=learningDecision()
    fun recordingForDiagnostics()=recordingId ?: lastRecordingId
    fun runtimeActiveForDiagnostics()=active
    override fun onDestroy() {
        finishRecording(simple?.complete==true || model.stage==SimStage.COMPLETE);closeSimpleQuest()
        suspendInternalSession();AppNavigation.forget(this);questObserver?.close();questObserver=null;super.onDestroy()
    }
    fun openInternalView(controls: Boolean) {
        if(!::navigation.isInitialized)return
        if(controls && navigation.visibility==View.VISIBLE)navigation.onControls()
        else if(!controls && root.visibility==View.VISIBLE)root.findViewWithTag<Button>("sim_navigation").performClick()
    }
    override fun onNewIntent(value: Intent) {super.onNewIntent(value);intent=value;consumeInternalView()}
    private fun consumeInternalView() {
        intent.getStringExtra("app_view")?.let {intent.removeExtra("app_view");openInternalView(it=="controls")}
    }
    override fun onResume() {
        super.onResume();screenVisible=true;consumeInternalView()
        if(pendingCelebration){pendingCelebration=false;celebration?.celebrate(celebrationEnabled);toast("좋아요! 연습 기록을 저장했어요")}
    }
    fun navigationForDiagnostics()=navigation.diagnostics()
    fun simulatorForDiagnostics()=model
    fun pausedForDiagnostics()=paused
    fun automaticForDiagnostics()=automatic
    fun sdkForDiagnostics()=mapOf("speedMps" to sdkSpeed.toString(),"indicator" to sdkSignal.name,"bridge" to bridgeStatus)
    private fun toast(value: String)=Toast.makeText(this,value,Toast.LENGTH_SHORT).show()
    private fun dp(v: Int)=(v*resources.displayMetrics.density).toInt()
    private fun text(parent: LinearLayout,value: String,size: Float,color: Int)=TextView(this).apply {text=value;textSize=size;setTextColor(color);setPadding(0,dp(5),0,dp(5));parent.addView(this)}
    private fun row(parent: LinearLayout)=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;parent.addView(this,LinearLayout.LayoutParams(-1,-2))}
    private fun card(parent: LinearLayout)=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(10));background=GradientDrawable().apply {setColor(Color.rgb(25,43,54));cornerRadius=dp(16).toFloat()}
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8);bottomMargin=dp(8)})
    }
    private fun button(parent: LinearLayout,value: String,key: String,action: ()->Unit)=Button(this).apply {
        text=value;tag=key;textSize=14f;isAllCaps=false;minHeight=dp(48);setTextColor(Color.WHITE)
        backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(41,66,79));setOnClickListener {
            if(key.startsWith("sim_gear_") || key.startsWith("sim_steer_") || key.startsWith("sim_signal_") ||
                key in setOf("sim_brake","sim_accelerator","sim_pedals_release","sim_parking_brake","sim_belt","sim_check","sim_wiper","sim_window_open","sim_window_close","sim_green","sim_pedestrian","sim_rain"))automatic=false
            action()
        }
        parent.addView(this,LinearLayout.LayoutParams(0,-2,1f).apply {rightMargin=dp(5)})
    }
    override fun onPause() {
        screenVisible=false;voice?.stop()
        if(active && !AppNavigation.internalTransition) {model.freeze();paused=true;pauseReason="앱 밖의 화면으로 전환되어 체험을 멈췄어요.";render()}
        super.onPause()
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {super.onConfigurationChanged(newConfig);build()}
    override fun onStop() {
        if(isFinishing){finishRecording(simple?.complete==true || model.stage==SimStage.COMPLETE);closeSimpleQuest();suspendInternalSession()}
        super.onStop()
    }
    fun suspendInternalSession() {
        if(!active)return
        if(simple?.started==true || model.practices.isNotEmpty() || coachNotes.isNotEmpty())database.saveSnapshot("last-report",reportText())
        memoDialog?.dismiss();memoDialog=null
        active=false;model.freeze();paused=true;handler.removeCallbacksAndMessages(null);bridge?.stop();bridge=null;voice?.release();voice=null
        celebration?.stop();celebration=null
        automatic=false
        sdkSource?.stop();sdkSource=null;sdkSpeedSource?.stop();sdkSpeedSource=null;sdkSpeed=null;sdkSpeedAt=0
    }
}
