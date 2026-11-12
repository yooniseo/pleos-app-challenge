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
import com.hmgchallenge.pleosstarter.SpeedSource
import kotlin.math.*

/** Driver-operated training cockpit. Real-car actuator APIs are never called here. */
class DrivingSimulatorActivity : Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private var model=DrivingSimulator()
    private var bridge: EmulatorCockpitBridge?=null
    private var voice: PleosCoachVoice?=null
    private var active=false
    private var paused=false
    private var tripId=UUID.randomUUID().toString()
    private val recorded=mutableSetOf<String>()
    private val mint=Color.rgb(101,233,197)
    private val muted=Color.rgb(180,199,209)
    private lateinit var root: LinearLayout
    private lateinit var stateView: TextView
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
    private var voiceEnabled=false
    private var lastCue=""
    private var lastStage: SimStage?=null
    private var lastFeedback=""
    private var sdkSource: PleosCoachSource?=null
    private var sdkSpeedSource: SpeedSource?=null
    private var sdkSpeed: Float?=null
    private var sdkSignal=Indicator.UNKNOWN
    private var bridgeStatus="에뮬레이터 연결 확인 중"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(packageManager.hasSystemFeature("android.hardware.type.automotive") && !EmulatorCockpitBridge.isAllowed(this)) {
            toast("운전석 조작 체험은 개발용 에뮬레이터에서 실행해 주세요");finish();return
        }
        window.statusBarColor=Color.rgb(13,25,34);window.navigationBarColor=Color.rgb(13,25,34)
        voiceEnabled=CoachRepository(this).preferences().voiceEnabled
        build()
    }
    override fun onStart() {
        super.onStart();if(!::root.isInitialized)return;active=true
        bridge=EmulatorCockpitBridge(this) {bridgeStatus=it;render()}.also {it.start()}
        if(packageManager.hasSystemFeature("android.hardware.type.automotive")) {
            voice=PleosCoachVoice(this,{message->
                if(active && !paused && voiceEnabled && message=="Pleos ON_DEVICE 음성 준비됨")voice?.speak(model.instruction())
            }).also {it.start()}
            sdkSource=PleosCoachSource(this,object : PleosCoachSource.Listener {
                override fun onNavigation(right: Boolean,meters: Double?,description: String) {}
                override fun onIndicator(value: Indicator) {if(active){sdkSignal=value;render()}}
                override fun onRouteInvalidated(reason: String) {}
                override fun onArrival() {}
                override fun onStatus(channel: String,message: String) {}
            }).also {it.start()}
            if(checkSelfPermission("android.car.permission.CAR_SPEED")==android.content.pm.PackageManager.PERMISSION_GRANTED)
                sdkSpeedSource=SpeedSource(this) {value,_->handler.post {if(active){sdkSpeed=value;render()}}}.also {it.start()}
        }
        handler.post(loop)
    }
    private val loop=object : Runnable {
        override fun run() {
            if(!active)return
            if(!paused)model.tick(0.05)
            saveNewPractice();render()
            bridge?.update(EmulatorCockpitBridge.Controls(model.gear,model.signal,model.steering.roundToInt(),
                model.accelerator.roundToInt(),model.brake.roundToInt(),(model.speed*3.6).roundToInt(),model.windowOpen,model.wiper))
            handler.postDelayed(this,50)
        }
    }
    private fun build() {
        root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(14),dp(22),dp(24))}
        setContentView(ScrollView(this).apply {setBackgroundColor(Color.rgb(13,25,34));isFillViewport=true;addView(root)})
        text(root,"운전석 체험  /  주차된 차 빼기부터 다시 정차까지",25f,mint)
        text(root,"가상 도로·차량 모형입니다. 핸들 각도는 체험용 계산값이며 실제 도로의 조작 지시로 사용할 수 없어요.",13f,muted)
        val header=row(root)
        button(header,"주행 코치로","sim_close") {finish()}
        button(header,"시나리오 선택","sim_scenario") {chooseScenario()}
        button(header,"처음부터","sim_restart") {reset(model.turnRight,model.laneRight)}
        pauseButton=button(header,"일시정지","sim_pause") {paused=!paused;if(paused)model.freeze();render()}
        button(header,"피드백","sim_feedback") {showFeedback()}
        button(header,"안내 듣기","sim_speak") {voice?.speak(model.instruction()) ?: toast("화면 안내를 확인해 주세요")}
        stageView=text(root,"",19f,mint)
        cueView=text(root,"",23f,Color.WHITE).apply {tag="sim_cue";setTypeface(null,Typeface.BOLD)}
        stateView=text(root,"",16f,muted).apply {tag="sim_state"}
        val panels=row(root)
        val roadCard=card(panels)
        road=SimulatorRoadView(this,model).apply {tag="sim_road"};roadCard.addView(road,LinearLayout.LayoutParams(-1,dp(240)))
        feedbackView=text(roadCard,"",17f,mint)
        val wheelCard=card(panels)
        text(wheelCard,"핸들을 직접 돌리거나 아래 슬라이더를 움직여요",13f,muted)
        wheel=SimulatorWheelView(this).apply {tag="sim_wheel";onSteer={model.steer(it);render()}}
        wheelCard.addView(wheel,LinearLayout.LayoutParams(-1,dp(180)))
        targetView=text(wheelCard,"",16f,mint).apply {tag="sim_target"}
        steeringSlider=SeekBar(this).apply {
            tag="sim_steering";max=900;progress=450
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(v: SeekBar?,p: Int,fromUser: Boolean) {if(fromUser){model.steer((p-450).toDouble());render()}}
                override fun onStartTrackingTouch(v: SeekBar?) {}
                override fun onStopTrackingTouch(v: SeekBar?) {}
            })
        };wheelCard.addView(steeringSlider)
        val trim=row(wheelCard)
        button(trim,"−15°","sim_steer_left") {model.steer(model.steering-15);render()}
        button(trim,"0°","sim_steer_zero") {model.steer(0.0);render()}
        button(trim,"+15°","sim_steer_right") {model.steer(model.steering+15);render()}
        if(resources.configuration.screenWidthDp>=760) {
            roadCard.layoutParams=LinearLayout.LayoutParams(0,-2,1.25f).apply {rightMargin=dp(12)}
            wheelCard.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        } else panels.orientation=LinearLayout.VERTICAL
        val pedals=row(root)
        brakeButton=pedal(pedals,"브레이크 켜기","sim_brake",true)
        acceleratorButton=pedal(pedals,"액셀 켜기","sim_accelerator",false)
        button(pedals,"페달 모두 떼기","sim_pedals_release") {model.pedals(0.0,0.0);render()}
        text(root,"페달은 한 번 클릭하면 유지, 다시 클릭하면 해제돼요. 다른 페달을 누르면 기존 페달은 풀려요.",12f,muted)
        val gears=row(root)
        SimGear.values().forEach {g->button(gears,g.name,"sim_gear_$g") {if(!model.selectGear(g))toast(model.feedback);render()}}
        parkingButton=button(gears,"","sim_parking_brake") {if(!model.setParkingBrake(!model.parkingBrake))toast(model.feedback);render()}
        val signals=row(root)
        button(signals,"← 왼쪽 깜빡이","sim_signal_left") {model.setSignal(SimSignal.LEFT);render()}
        button(signals,"깜빡이 끄기","sim_signal_off") {model.setSignal(SimSignal.OFF);render()}
        button(signals,"오른쪽 깜빡이 →","sim_signal_right") {model.setSignal(SimSignal.RIGHT);render()}
        button(signals,"미러·주변 확인 입력","sim_check") {model.checkMirrors();render()}
        val body=row(root)
        beltButton=button(body,"","sim_belt") {if(model.stopped){model.belt=!model.belt;render()}else toast("정차 중 조작해 주세요")}
        button(body,"창문 열기 50%","sim_window_open") {model.windowOpen=50;render()}
        button(body,"창문 닫기","sim_window_close") {model.windowOpen=0;render()}
        button(body,"와이퍼 단계 변경","sim_wiper") {model.wiper=(model.wiper+1)%4;render()}
        val situations=row(root)
        greenButton=button(situations,"","sim_green") {model.green=!model.green;render()}
        pedestrianButton=button(situations,"","sim_pedestrian") {model.pedestrian=!model.pedestrian;render()}
        button(situations,"비 오는 상황 전환","sim_rain") {model.rain=!model.rain;render()}
        text(root,"신호·보행자·미러 확인은 직접 설정하는 체험 조건입니다. 실제 영상 분석이나 통행 가능 판정이 아닙니다.",12f,muted)
        text(root,"깜빡이를 켜면 Pleos 사각지대 카메라가 열릴 수 있어요. 뒤로 돌아온 뒤 ‘계속하기’를 눌러 체험을 이어가요.",12f,muted)
        text(root,"핸들 계산: 축거 2.75m · 조향비 14.5:1 · 출구 반경 7m / 교차로 반경 9m의 가상 차량",12f,muted)
        linkView=text(root,"에뮬레이터 연결 확인 중",12f,muted)
        text(root,"체험을 닫으면 에뮬레이터는 0km/h·P단·깜빡이 OFF로 돌아갑니다. 체험 기록은 퀘스트의 시뮬레이션 기록에만 연결해요.",12f,muted)
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
        stageView.text=(if(paused)"일시정지 · " else "")+model.stage.title.replace("왼쪽",model.laneDirection)
        val cue=model.instruction();cueView.text=cue
        stateView.text="${model.gear}단 · ${"%.1f".format(java.util.Locale.US,model.speed*3.6)}km/h · 핸들 ${model.steering.roundToInt()}° · 깜빡이 ${when(model.signal){SimSignal.OFF->"꺼짐";SimSignal.LEFT->"왼쪽";SimSignal.RIGHT->"오른쪽"}}\n"+
            "현재 ${model.currentLane}차로 → 목표 ${model.targetLane}차로 · ${model.lanesRemaining}개 남음 · 엑셀 ${model.accelerator.roundToInt()}% / 브레이크 ${model.brake.roundToInt()}% · 창문 ${model.windowOpen}% · 와이퍼 ${listOf("OFF","AUTO","LOW","HIGH")[model.wiper]}"
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
        brakeButton.text=if(model.brake>0)"브레이크 유지 중\n클릭하면 해제" else "브레이크 켜기"
        acceleratorButton.text=if(model.accelerator>0)"액셀 유지 중\n클릭하면 해제" else "액셀 켜기"
        brakeButton.backgroundTintList=android.content.res.ColorStateList.valueOf(if(model.brake>0)Color.rgb(147,69,54) else Color.rgb(41,66,79))
        acceleratorButton.backgroundTintList=android.content.res.ColorStateList.valueOf(if(model.accelerator>0)Color.rgb(36,111,97) else Color.rgb(41,66,79))
        linkView.text="$bridgeStatus\nSDK 관측 · 속도 ${sdkSpeed?.let {"%.1f km/h".format(java.util.Locale.US,it*3.6)} ?: "응답 대기"} · 깜빡이 ${sdkSignal.name}"
        if(model.feedback!=lastFeedback && model.feedback.startsWith("좋아요")) {lastFeedback=model.feedback;if(voiceEnabled)voice?.speak(model.feedback)}
        if(model.stage!=lastStage || (cue!=lastCue && (cue.contains("깜빡이") || model.stage==SimStage.PREPARE || model.stage==SimStage.TURN_CHECK))) {
            lastStage=model.stage;lastCue=cue;if(active && voiceEnabled)voice?.speak(cue)
        }
    }
    private fun saveNewPractice() {
        val skillById=mapOf("prepare" to CoachSkill.PREPARATION,"lane1" to CoachSkill.LANE_CHANGE,"lane2" to CoachSkill.LANE_CHANGE,"lane3" to CoachSkill.LANE_CHANGE,
            "intersection" to CoachSkill.INTERSECTION,"finish" to CoachSkill.FINISH)
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
    private fun chooseScenario() {
        model.freeze();paused=true
        AlertDialog.Builder(this).setTitle("차로 변경 방향 / 교차로 회전 방향")
            .setItems(arrayOf("왼쪽 2개 차로 → 우회전","오른쪽 2개 차로 → 우회전","왼쪽 2개 차로 → 좌회전","오른쪽 2개 차로 → 좌회전")) {_,which->reset(which<2,which%2==1)}
            .setNegativeButton("계속하기") {_,_->paused=false}.setOnCancelListener {paused=false}.show()
    }
    private fun reset(right: Boolean,laneRight: Boolean) {
        voice?.stop()
        model.freeze();model=DrivingSimulator(right,laneRight);tripId=UUID.randomUUID().toString();recorded.clear();paused=false;lastStage=null;lastCue="";lastFeedback="";render()
    }
    private fun showFeedback() {
        voice?.stop();model.freeze();paused=true
        val report=reportText()
        getSharedPreferences("driver_simulator",MODE_PRIVATE).edit().putString("last_report",report).apply()
        AlertDialog.Builder(this).setTitle("운전석 체험 피드백").setMessage(report)
            .setPositiveButton("계속하기") {_,_->paused=false}
            .setNeutralButton("퀘스트 보기") {_,_->startActivity(Intent(this,com.hmgchallenge.pleosstarter.quests.QuestActivity::class.java).putExtra("quest_demo",true))}
            .setOnCancelListener {paused=false}.show()
    }
    private fun reportText()="체험 결과 · 실제 주행 성공 판정 아님\n\n"+model.practices.joinToString("\n") {"• ${it.message}"}+
        "\n\n현재 단계: ${model.stage.title}\n조작 거리 ${model.distance.roundToInt()}m · 남은 차로 ${model.lanesRemaining}개\n퀘스트의 체험 기록에 연결했어요."
    fun simulatorForDiagnostics()=model
    fun pausedForDiagnostics()=paused
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
        backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(41,66,79));setOnClickListener {action()}
        parent.addView(this,LinearLayout.LayoutParams(0,-2,1f).apply {rightMargin=dp(5)})
    }
    override fun onPause() {
        if(active) {voice?.stop();model.freeze();paused=true;render()}
        super.onPause()
    }
    override fun onStop() {
        if(model.practices.isNotEmpty())getSharedPreferences("driver_simulator",MODE_PRIVATE).edit().putString("last_report",reportText()).apply()
        active=false;model.freeze();paused=true;handler.removeCallbacksAndMessages(null);bridge?.stop();bridge=null;voice?.release();voice=null
        sdkSource?.stop();sdkSource=null;sdkSpeedSource?.stop();sdkSpeedSource=null;super.onStop()
    }
}
