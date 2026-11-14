package com.hmgchallenge.pleosstarter.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.*
import android.widget.*
import com.hmgchallenge.pleosstarter.core.data.SharedCoachRepository
import com.hmgchallenge.pleosstarter.driving.*
import com.hmgchallenge.pleosstarter.session.*
import com.hmgchallenge.pleosstarter.ui.*
import org.json.JSONArray
import org.json.JSONObject

enum class AppScreen { MAP, QUESTS, PARKING, FEEDBACK }

/** Single navigation host: changing tabs does not create another drive or SDK connection. */
class ChageunActivity: Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private val repository by lazy {SharedCoachRepository.get(this)}
    lateinit var session: ReplaySessionController; private set
    lateinit var vehicleQuests: VehicleQuestController;private set
    private var vehicleQuestView=false
    private var pendingVehicleSpeech: String?=null
    private lateinit var art: ProductIllustrations
    private lateinit var telemetry: VehicleTelemetry
    private lateinit var scene: ProductScene
    private var canvas: SceneCanvas?=null
    private var map: ScenarioRoadMapView?=null
    var screen=AppScreen.MAP; private set
    private var voice: PleosCoachVoice?=null
    private var celebration: ScenarioCelebration?=null
    private var active=false
    private var lastTick=0L
    private val speechGate=ScenarioSpeechGate()
    private val speechEvidence=RuntimeEvidence()
    private var completionShown: String?=null
    private var points=0
    private var selectedTrip: String?=null
    private var coach=true
    private var voiceEnabled=true
    private var celebrationEnabled=true
    private var voiceStatus="연결 대기"
    private var celebrationStatus="P단 정차 시 조명 응원"
    private var milestoneText=""
    private var milestoneUntil=0L
    private var startButton: Button?=null
    private var pauseButton: Button?=null
    private var memo: DrivingMemoDialog?=null
    private val emptyPreview get()=intent.getBooleanExtra("empty_preview",false)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // The debug-only distraction-optimized entry is deliberately restricted to the emulator.
        if((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0 && packageManager.hasSystemFeature("android.hardware.type.automotive") && !isEmulator()) {finish();return}
        window.statusBarColor=Color.WHITE;window.navigationBarColor=Color.WHITE
        art=ProductIllustrations();telemetry=VehicleTelemetry(this)
        val route=ReferenceRouteClient.parse(applicationContext.assets.open("road_reference/kgu-route.json").bufferedReader().use {it.readText()})
        session=ReplaySessionController(this,NavigationSimulationRoute.reference(route))
        vehicleQuests=VehicleQuestController(this).also {controller->controller.onCue={pendingVehicleSpeech=it}}
        val settings=JSONObject(repository.snapshot("settings:product") ?: "{}")
        coach=settings.optBoolean("coach",true);voiceEnabled=settings.optBoolean("voice",true);celebrationEnabled=settings.optBoolean("celebration",true)
        session.coachingEnabled=coach
        session.manualStyle=runCatching {BeginnerStyle.valueOf(settings.optString("style","ADAPTIVE"))}.getOrDefault(BeginnerStyle.ADAPTIVE)
        session.onMilestone={done->
            points=repository.points("demo")
            milestoneText=if(done && session.scenario.lesson==RoadLesson.PARKING)"잘했어요! 주차까지 마쳤어요" else if(session.scenario.complete)"주행을 마쳤어요 · 주차 가이드로 이어가요" else "띵동! ${session.scenario.currentLane}차로 이동 완료"
            milestoneUntil=SystemClock.elapsedRealtime()+2300
            if(done)celebration?.celebrate(celebrationEnabled) else celebration?.ding(celebrationEnabled)
        }
        session.onParkingStarted={voice?.stop();show(AppScreen.PARKING)}
        points=repository.points("demo");show(AppScreen.MAP)
    }
    override fun onStart() {
        super.onStart();if(!::session.isInitialized)return
        active=true;lastTick=SystemClock.elapsedRealtime();telemetry.start()
        celebration=ScenarioCelebration(this,{active && telemetry.stationary},{celebrationStatus=it}).also {it.start()}
        if(packageManager.hasSystemFeature("android.hardware.type.automotive"))voice=PleosCoachVoice(this,{voiceStatus=it},{speechEvidence.record(it)}).also {it.start()}
        handler.post(loop)
    }
    private val loop=object: Runnable {
        override fun run() {
            if(!active)return
            val now=SystemClock.elapsedRealtime();session.tick(now-lastTick,now,telemetry.speedMps,telemetry.fresh);lastTick=now
            session.updateIndicator(telemetry.indicator)
            vehicleQuests.update(telemetry.questInput(),now)
            val s=session.scenario
            if(screen!=AppScreen.PARKING || s.lesson==RoadLesson.PARKING)map?.setFrame(s.course,s.frame(),s.lesson==RoadLesson.PARKING)
            (canvas as? DriveCanvas)?.apply {
                coachEnabled=coach
                val live=vehicleQuests.coachMessageUntil>now
                message=if(live)vehicleQuests.coachMessage else milestoneText
                messageUntil=if(live)vehicleQuests.coachMessageUntil else milestoneUntil
            }
            (canvas as? QuestCanvas)?.apply {state=session.quest;points=this@ChageunActivity.points}
            (canvas as? VehicleQuestCanvas)?.input=telemetry.questInput()
            canvas?.invalidate()
            startButton?.text=when {s.complete->"다시 시연";s.started && !session.paused->"진행 중";s.started->"재개";else->"자동 시연 시작"}
            startButton?.isEnabled=!emptyPreview && (!s.started || session.paused || s.complete)
            pauseButton?.isEnabled=s.started && !s.complete && !session.paused
            if(session.completedAt!=null && completionShown!=session.tripId) {
                completionShown=session.tripId;selectedTrip=session.tripId
                if(s.lesson==RoadLesson.PARKING)show(AppScreen.FEEDBACK)
                else {milestoneText="주행 연습 완료 · 주차 가이드에서 이어가세요";milestoneUntil=now+8000}
            }
            if(pendingVehicleSpeech!=null && now>vehicleQuests.coachMessageUntil)pendingVehicleSpeech=null
            if(pendingVehicleSpeech!=null && coach && voiceEnabled && voice?.isReady==true && voice?.isBusy==false) {
                voice?.speak(pendingVehicleSpeech!!);pendingVehicleSpeech=null
            }
            speechGate.next(session.tripId,ScenarioCoachingCues.at(s),now,
                coach && voiceEnabled && vehicleQuests.tripId==null && !session.paused && session.learning().automaticSpeech,
                voice?.isReady==true,voice?.isBusy==false)?.let {voice?.speak(it)}
            handler.postDelayed(this,50)
        }
    }
    fun show(destination: AppScreen) {
        screen=destination;startButton=null;pauseButton=null;map=null
        val drawing: android.view.View=when(destination) {
            AppScreen.MAP->{
                val layers=FrameLayout(this)
                map=ScenarioRoadMapView(this,art).apply {setFrame(session.scenario.course,session.scenario.frame(),session.scenario.lesson==RoadLesson.PARKING)}
                canvas=DriveCanvas(this,art,session).apply {coachEnabled=coach}
                layers.addView(map,FrameLayout.LayoutParams(-1,-1));layers.addView(canvas,FrameLayout.LayoutParams(-1,-1));layers
            }
            AppScreen.QUESTS->if(vehicleQuestView)VehicleQuestCanvas(this,art,vehicleQuests).also {canvas=it;it.input=telemetry.questInput()}
                else QuestCanvas(this,art).also {canvas=it;it.state=session.quest;it.points=points}
            AppScreen.PARKING->{
                val layers=FrameLayout(this)
                val s=session.scenario.takeIf {it.lesson==RoadLesson.PARKING} ?: SimpleRoadScenario(session.course,RoadLesson.PARKING)
                map=ScenarioRoadMapView(this,art).apply {setFrame(s.course,s.frame(),true)}
                canvas=ParkingCanvas(this,art,session)
                layers.addView(map,FrameLayout.LayoutParams(-1,-1));layers.addView(canvas,FrameLayout.LayoutParams(-1,-1));layers
            }
            AppScreen.FEEDBACK->{
                val layers=FrameLayout(this)
                val bg=ScenarioRoadMapView(this,art).apply {setFrame(session.scenario.course,session.scenario.frame())}
                val id=selectedTrip ?: session.tripId ?: repository.trips().firstOrNull {it.endedAt!=null}?.tripId
                val feedback=CompletionCanvas(this,art).apply {
                    trip=id?.let(repository::trip);report=id?.let(repository::report);state=session.quest
                    complete=id?.let {repository.snapshot("trip:$it")}?.let(::JSONObject)?.optString("status")=="COMPLETE" && repository.events(id).any {it.type=="PRACTICE_REWARD" && JSONObject(it.payload).optString("skill")=="PARKING"}
                    earned=id?.let(repository::tripPoints) ?: 0
                    completed=id?.let {repository.events(it).count {e->e.type=="PRACTICE_REWARD"}} ?: 0
                    val metadata=id?.let {repository.snapshot("trip:$it")}?.let(::JSONObject)
                    target=metadata?.optInt("targetCount",if(metadata.optString("lesson")=="COMBINED")2 else 1) ?: 0
                    if(metadata?.optString("lesson")=="VEHICLE_QUEST") {
                        vehicleRecord=true
                        val attempts=repository.events(id!!).filter {it.type=="VEHICLE_QUEST_ATTEMPT"}.map {com.hmgchallenge.pleosstarter.feature.quests.QuestBoardCodec.decodeAttempt(it.payload)}
                        completed=attempts.count {it.success};target=attempts.size
                        recordSource=if(trip?.source=="VEHICLE")"Pleos 차량 신호 기록" else "TEST · 가상 입력 기록"
                    }
                }
                canvas=feedback;layers.addView(bg,FrameLayout.LayoutParams(-1,-1));layers.addView(feedback,FrameLayout.LayoutParams(-1,-1));layers
            }
        }
        scene=ProductScene(this,drawing)
        when(destination) {
            AppScreen.MAP->{
                scene.button("설정", "settings",1500f,160f,155f,78f){settings()}
                scene.button(if(coach)"ON" else "OFF","coach_toggle",1510f,460f,145f,85f){coach=!coach;session.coachingEnabled=coach;saveSettings();if(!coach)voice?.stop();show(screen)}.contentDescription="주행 코치 ${if(coach)"켜짐" else "꺼짐"} 전환"
                startButton=scene.button("자동 시연 시작","replay_start",75f,945f,285f,65f,true){start()}
                pauseButton=scene.button("일시정지","replay_pause",380f,945f,160f,65f){session.pause();voice?.stop()}
                scene.button("연습 선택","lesson_select",45f,330f,260f,80f){chooseLesson()}
                scene.button("깜빡이 OFF","indicator_off",45f,430f,245f,65f){setIndicator(Indicator.OFF)}
                scene.button("오른쪽 ON","indicator_right",305f,430f,245f,65f){setIndicator(Indicator.RIGHT)}
                scene.button("SDK 입력","indicator_live",45f,510f,245f,65f){setIndicator(null)}
                scene.button("비교 안내 듣기","indicator_preview",305f,510f,245f,65f){previewIndicator()}
                scene.button("설명 더 보기","coach_help",1320f,1040f,335f,65f){help()}
            }
            AppScreen.QUESTS->{
                scene.button("시연 퀘스트","quests_demo",70f,228f,270f,52f,!vehicleQuestView){vehicleQuestView=false;show(AppScreen.QUESTS)}
                scene.button("차량 신호 퀘스트","quests_vehicle",355f,228f,350f,52f,vehicleQuestView){vehicleQuestView=true;show(AppScreen.QUESTS)}
                if(vehicleQuestView) {
                    scene.button(if(vehicleQuests.enabled)"주행 기록 마치기" else "차량 연결 시작","vehicle_finish",1240f,940f,360f,68f,true){
                        if(!vehicleQuests.enabled){vehicleQuests.resume();show(screen)}else {
                            val id=vehicleQuests.tripId
                            if(vehicleQuests.finish()) {
                                if(id!=null){selectedTrip=id;show(AppScreen.FEEDBACK)}else show(screen)
                            }else toast(vehicleQuests.error ?: "저장 실패")
                        }
                    }
                }else {
                scene.button("★ 퀘스트 계속하기","quest_continue",555f,885f,600f,105f,true){show(if(session.scenario.lesson==RoadLesson.PARKING)AppScreen.PARKING else AppScreen.MAP);if(!session.scenario.started || session.paused)start()}
                scene.button("초기화","quest_reset",1435f,178f,195f,70f){resetQuests()}
                val skills=listOf("교차로 통과","출발 전 점검","차선 변경","안전 거리 유지","주차 마무리")
                skills.forEachIndexed {i,title->
                    val b=scene.button("", "quest_card_$i",54f+321*i,280f,290f,550f){when(i){0->select(RoadLesson.TURN);2->select(RoadLesson.LANE);4->select(RoadLesson.PARKING);else->toast("실제 센서 연동 후 제공할 퀘스트예요")}}
                    b.background=null;b.elevation=0f;b.contentDescription="$title 연습 선택"
                }
                }
            }
            AppScreen.PARKING->{
                if(session.scenario.lesson!=RoadLesson.PARKING)scene.button("후진 주차 시연 선택","parking_select",1165f,900f,460f,105f,true){select(RoadLesson.PARKING)}
                else {
                    startButton=scene.button("주차 자동 시연","replay_start",1210f,890f,390f,90f,true){start()}
                    pauseButton=scene.button("일시정지","replay_pause",1290f,990f,260f,75f){session.pause();voice?.stop()}
                }
            }
            AppScreen.FEEDBACK->{
                scene.button("지난 기록","records_history",790f,190f,270f,90f){history()}
                scene.button("어려웠어요","feedback_difficult",1110f,190f,270f,90f){
                    val id=vehicleFeedbackId()
                    if(id!=null) {
                        if(repository.addNote(id,"어려웠어요 · 차량 신호 조건 연습","OTHER",null,true,source=repository.trip(id)!!.source)) {
                            toast("오늘의 어려움을 기록했어요");show(screen)
                        }else toast(repository.lastError ?: "저장 실패")
                    }else if((selectedTrip==null || selectedTrip==session.tripId) && session.note(true))toast("다음 연습은 차근차근 도와드릴게요")else toast("현재 연습 기록에서 남길 수 있어요")
                }
                scene.button("메모 남기기","feedback_memo",1410f,190f,230f,90f){
                    val id=vehicleFeedbackId()
                    if(id!=null) {
                        memo=DrivingMemoDialog(this,{text,category,difficult->
                            val skill=when(category){"LANE_CHANGE"->"LANE_CHANGE";"RIGHT_TURN"->"INTERSECTION";"PARKING"->"PARKING";else->null}
                            repository.addNote(id,text,category,skill,difficult,source=repository.trip(id)!!.source)
                        },{memo=null;show(screen)}).also {it.show("OTHER")}
                    }else if(session.tripId==null || (selectedTrip!=null && selectedTrip!=session.tripId))toast("현재 연습을 시작한 뒤 메모할 수 있어요")
                    else memo=DrivingMemoDialog(this,{text,category,difficult->session.memo(text,category,difficult)},{memo=null;show(screen)}).also {it.show("OTHER")}
                }
                scene.button("다음 연습","feedback_next",1320f,970f,280f,90f,true){chooseLesson()}
            }
        }
        // These destinations are always available; no separate navy/white application tasks.
        val names=listOf("지도 · 주행 코치","퀘스트","주차 가이드","주행 피드백")
        AppScreen.values().forEachIndexed {i,dest->scene.button(names[i],"tab_${dest.name}",25f+420*i,1100f,390f,70f,dest==screen){show(dest)}}
        setContentView(scene)
    }
    fun setIndicator(value: Indicator?) {
        session.indicatorOverride=value
        session.updateIndicator(telemetry.indicator)
        voice?.stop()
        canvas?.invalidate()
    }
    fun previewIndicator() {
        session.pause()
        session.updateIndicator(telemetry.indicator)
        val cue=ScenarioCoachingCues.preparation(CoachingStep.TURN_PREPARE,session.scenario.indicator)
        milestoneText=cue.full;milestoneUntil=SystemClock.elapsedRealtime()+8000
        if(voiceEnabled)voice?.speak(cue.full)
    }
    private fun start() {
        if(emptyPreview){toast("연습 시나리오를 준비 중이에요");return}
        if(!session.start())toast(session.error ?: "저장 실패")
        lastTick=SystemClock.elapsedRealtime()
    }
    fun select(lesson: RoadLesson,source: ScenarioSpeed=ScenarioSpeed.REPLAY) {
        voice?.stop()
        if(!session.select(lesson,source)){toast(session.error ?: "기록 저장 실패");return}
        selectedTrip=null;show(if(lesson==RoadLesson.PARKING)AppScreen.PARKING else AppScreen.MAP)
    }
    private fun chooseLesson() {
        AlertDialog.Builder(this).setTitle("짧은 자동 시연")
            .setItems(ReplayCatalog.all.map {it.title}.toTypedArray()){_,i->select(ReplayCatalog.all[i].lesson)}.setNegativeButton("닫기",null).show()
    }
    private fun help() {
        val g=BeginnerScenarioCoach.guidance(session.scenario,BeginnerStyle.STEP_BY_STEP)
        if(voiceEnabled)voice?.speak(g.headline+". "+g.detail)
        AlertDialog.Builder(this).setTitle("차근차근, 함께 확인해요").setMessage("${g.headline}\n\n${g.detail}\n\n${g.checklist}\n\n핸들 그림은 시연 경로를 설명하는 예시예요. 실제 조향량은 차량과 상황에 따라 달라요.")
            .setPositiveButton("확인",null).setNeutralButton("어려웠어요"){_,_->session.note(true)}.show()
    }
    private fun settings() {
        AlertDialog.Builder(this).setTitle("나에게 맞는 코치")
            .setItems(arrayOf("음성 ${if(voiceEnabled)"ON" else "OFF"}","축하음·조명 ${if(celebrationEnabled)"ON" else "OFF"}","설명 방식: ${session.manualStyle.title}","실제 차량 데이터 확인","지도 진행: ${session.scenario.source}")){_,i->when(i){
                0->{voiceEnabled=!voiceEnabled;saveSettings();if(!voiceEnabled)voice?.stop()else voice?.speak("주행 코치 음성 안내를 켰어요")}
                1->{celebrationEnabled=!celebrationEnabled;saveSettings()}
                2->AlertDialog.Builder(this).setTitle("설명 방식").setItems(BeginnerStyle.values().map {it.title}.toTypedArray()){_,n->session.manualStyle=BeginnerStyle.values()[n];saveSettings()}.show()
                3->AlertDialog.Builder(this).setTitle("Pleos 연결 상태").setMessage("속도: ${if(telemetry.fresh)"${telemetry.speedMps} m/s" else "미수신/오래된 값"}\n방향지시등: ${telemetry.indicator}\n조향각(SDK): ${telemetry.steeringAngle}\n브레이크: ${telemetry.brakePressed}\n내비: ${telemetry.navigationStatus}\n음성: $voiceStatus\n축하: $celebrationStatus\n\n지도·차로·핸들은 시나리오 값입니다. 차량 신호는 신호 조건 퀘스트·피드백에 사용합니다. 실제 차로 위치 검증은 별도입니다.").setPositiveButton("확인",null).show()
                4->AlertDialog.Builder(this).setTitle("지도 진행 속도").setItems(arrayOf("자동 재생 · 직진 40 / 접근 20 / 회전 10km/h","차량 SDK 속도 · 경로는 가상")){_,n->select(session.scenario.lesson,if(n==0)ScenarioSpeed.REPLAY else ScenarioSpeed.VEHICLE)}.show()
            }}.setNegativeButton("닫기",null).show()
    }
    private fun saveSettings(){if(!repository.saveSnapshot("settings:product",JSONObject().put("coach",coach).put("voice",voiceEnabled).put("celebration",celebrationEnabled).put("style",session.manualStyle.name).toString()))toast("설정을 저장하지 못했어요")}
    private fun resetQuests() {
        if(session.scenario.started && !session.scenario.complete){toast("진행 중인 연습을 먼저 마쳐 주세요");return}
        AlertDialog.Builder(this).setTitle("시연 퀘스트를 처음부터?").setMessage("현재 시연 성장·포인트를 0으로 시작해요. 지난 주행 기록과 개인 기록은 유지돼요.")
            .setPositiveButton("초기화"){_,_->if(session.resetProgress()){points=0;show(AppScreen.QUESTS)}else toast(session.error ?: "초기화 실패")}.setNegativeButton("취소",null).show()
    }
    private fun history() {
        val trips=repository.trips().filter {it.endedAt!=null}
        if(trips.isEmpty()){toast("아직 저장된 연습이 없어요");return}
        val format=java.text.SimpleDateFormat("MM/dd HH:mm",java.util.Locale.KOREA)
        AlertDialog.Builder(this).setTitle("지난 연습 기록").setItems(trips.map {"${format.format(java.util.Date(it.startedAt))} · ${((it.distanceKm ?: 0f)*1000).toInt()}m"}.toTypedArray()){_,i->selectedTrip=trips[i].tripId;show(AppScreen.FEEDBACK)}.setNegativeButton("닫기",null).show()
    }
    override fun onStop() {
        memo?.dismiss();memo=null
        active=false;handler.removeCallbacks(loop)
        if(::session.isInitialized)session.pause()
        if(::vehicleQuests.isInitialized) {
            vehicleQuests.suspendInputs()
            if(vehicleQuests.tripId!=null && vehicleQuests.finish(false))vehicleQuests.resume()
        }
        voice?.release();voice=null;celebration?.stop();celebration=null
        if(::telemetry.isInitialized)telemetry.stop()
        super.onStop()
    }
    override fun onDestroy(){if(::session.isInitialized)session.close();super.onDestroy()}
    fun voiceDiagnostics()=speechEvidence.snapshot()
    fun vehicleDiagnostics()=telemetry.diagnostics()
    fun vehicleInput()=telemetry.questInput()
    fun showFeedback(tripId: String) {
        require(repository.trip(tripId)!=null)
        selectedTrip=tripId;show(AppScreen.FEEDBACK)
    }
    private fun vehicleFeedbackId(): String? {
        val id=selectedTrip ?: vehicleQuests.tripId ?: return null
        return id.takeIf {repository.snapshot("trip:$it")?.let(::JSONObject)?.optString("lesson")=="VEHICLE_QUEST"}
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        memo?.onPermissionResult(requestCode,grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    override fun onBackPressed(){if(screen!=AppScreen.MAP)show(AppScreen.MAP)else super.onBackPressed()}
    private fun toast(message: String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    private fun isEmulator()=Build.HARDWARE in setOf("ranchu","goldfish") || Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("emulator",true)
}
