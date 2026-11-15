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
    private val mirrorAvailable by lazy {
        ReplayConsole.supported(this) && (SharedCoachRepository.testOverride==null || intent.getBooleanExtra("verify_console",false))
    }
    private var vehicleMovedInGear=false
    private var mirroredFrame: ReplayControls?=null
    private val cameraCoach by lazy { ParkingCameraCoach(this) }
    private var recommendView=true
    private var recommendationSource="VEHICLE"
    private var recommendationObserver: java.io.Closeable?=null
    private var vehicleFinishButton: Button?=null
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
    private var mapTilted=true
    private var compactQuestBanner=false
    private var toolsExpanded=false
    private var cancelButton: Button?=null
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
        art=ProductIllustrations(this);telemetry=VehicleTelemetry(this)
        val route=ReferenceRouteClient.parse(applicationContext.assets.open("road_reference/kgu-route.json").bufferedReader().use {it.readText()})
        val streets=JSONObject(assets.open("road_reference/kgu-streets.json").bufferedReader().use {it.readText()}).getJSONArray("roads")
        val receivingWay=(0 until streets.length()).map {streets.getJSONObject(it)}.first {it.getString("id")=="667234603"}
        val nodes=receivingWay.getJSONArray("points")
        val receivingRoad=(0 until nodes.length()).map {nodes.getJSONArray(it).let {p->GeoPoint(p.getDouble(0),p.getDouble(1))}}
        session=ReplaySessionController(this,NavigationSimulationRoute.reference(route,receivingRoad))
        vehicleQuests=VehicleQuestController(this).also {controller->controller.onCue={pendingVehicleSpeech=it}}
        val settings=JSONObject(repository.snapshot("settings:product") ?: "{}")
        coach=settings.optBoolean("coach",true);voiceEnabled=settings.optBoolean("voice",true);celebrationEnabled=settings.optBoolean("celebration",true)
        mapTilted=settings.optBoolean("mapTilted",true);compactQuestBanner=settings.optBoolean("compactQuestBanner",false)
        session.coachingEnabled=coach
        session.manualStyle=runCatching {BeginnerStyle.valueOf(settings.optString("style","ADAPTIVE"))}.getOrDefault(BeginnerStyle.ADAPTIVE)
        session.onMilestone={done->
            points=repository.points("demo")
            milestoneText=if(done && session.scenario.lesson==RoadLesson.PARKING)"잘했어요! 주차까지 마쳤어요" else if(session.scenario.complete)"주행을 마쳤어요 · 주차 가이드로 이어가요" else "띵동! ${session.scenario.currentLane}차로 이동 완료"
            milestoneUntil=SystemClock.elapsedRealtime()+2300
            if(done)celebration?.celebrate(celebrationEnabled) else celebration?.ding(celebrationEnabled)
        }
        session.onLaneResult={index,signal->
            milestoneText=if(signal==Indicator.RIGHT)"${index+1}번째 변경 · 깜빡이 ON · 퀘스트 +1" else "${index+1}번째 변경 · 깜빡이 미충족 · +0"
            milestoneUntil=SystemClock.elapsedRealtime()+5500
        }
        session.onParkingStarted={voice?.stop();show(AppScreen.PARKING)}
        recommendationSource=repository.trips().firstOrNull {it.endedAt!=null && it.source in setOf("REPLAY","VEHICLE")}?.source ?: "VEHICLE"
        points=repository.points("demo");openRequestedScreen(intent)
    }
    private fun openRequestedScreen(request: android.content.Intent) {
        if(request.getStringExtra("open_screen")=="QUESTS")showRecommendations(recommendationSource)
        else if(request.getStringExtra("open_screen")=="PARKING")show(AppScreen.PARKING)
        else show(AppScreen.MAP)
    }
    override fun onNewIntent(request: android.content.Intent) {
        super.onNewIntent(request)
        if(::session.isInitialized && request.hasExtra("open_screen"))openRequestedScreen(request)
    }
    override fun onStart() {
        super.onStart();if(!::session.isInitialized)return
        handler.removeCallbacks(finishCameraReplay)
        active=true;lastTick=SystemClock.elapsedRealtime();telemetry.start()
        recommendationObserver=repository.observe {key->
            if(key in setOf("note","trip","report","vehicle-quests","quest:demo")) {
                handler.removeCallbacks(refreshRecommendations);handler.postDelayed(refreshRecommendations,200)
            }
        }
        if(packageManager.hasSystemFeature("android.hardware.type.automotive") && checkSelfPermission("android.car.permission.CAR_SPEED")!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val permission=runCatching {packageManager.getPermissionInfo("android.car.permission.CAR_SPEED",0)}.getOrNull()
            if(permission?.protection==android.content.pm.PermissionInfo.PROTECTION_DANGEROUS)requestPermissions(arrayOf("android.car.permission.CAR_SPEED"),834)
        }
        celebration=ScenarioCelebration(this,{active && telemetry.stationary},{celebrationStatus=it}).also {it.start()}
        if(packageManager.hasSystemFeature("android.hardware.type.automotive"))voice=PleosCoachVoice(this,{voiceStatus=it},{speechEvidence.record(it)}).also {it.start()}
        handler.post(loop)
    }
    private val refreshRecommendations=Runnable {if(active && screen==AppScreen.QUESTS && recommendView)show(AppScreen.QUESTS)}
    private fun vehicleHeadline()=when(telemetry.motion) {
        MotionState.MOVING->"차량 주행 중 · ${((telemetry.speedMps ?: 0.0)*3.6).toInt()}km/h"
        MotionState.STOPPED->"차량 정차 중 · 0km/h"
        MotionState.UNKNOWN->"차량 속도 확인 중"
    }
    private fun vehicleSignalLabel()="SDK 깜빡이 · "+when(telemetry.indicator) {
        Indicator.RIGHT->"오른쪽 ON"
        Indicator.LEFT->"왼쪽 ON"
        Indicator.OFF->"OFF"
        Indicator.UNKNOWN->"미수신"
    }+if(vehicleQuests.tripId!=null)" · 기록 중" else ""
    private val loop=object: Runnable {
        override fun run() {
            if(!active)return
            val now=SystemClock.elapsedRealtime();session.tick(now-lastTick,now,telemetry.speedMps,telemetry.fresh);lastTick=now
            val s=session.scenario
            val replay=s.started && !session.paused && !s.complete && session.usesReplaySpeed
            val frame=if(replay) ReplayControls.from(s,session.indicatorOverride) else null
            session.updateIndicator(frame?.signal ?: telemetry.indicator)
            mirror(frame)
            if(!mirrorAvailable || !ReplayConsole.filtersVehicleInput(telemetry.speedAt)) {
                val previousTrip=vehicleQuests.tripId
                vehicleQuests.update(telemetry.questInput(),now)
                if(vehicleQuests.tripId!=null && celebration?.gear in setOf("D","R"))vehicleMovedInGear=true
                if(vehicleMovedInGear && vehicleQuests.tripId!=null && celebration?.gear=="P" && telemetry.motion==MotionState.STOPPED) {
                    val finished=vehicleQuests.tripId
                    if(vehicleQuests.finish()) {
                        vehicleMovedInGear=false
                        if(screen==AppScreen.FEEDBACK && finished!=null)showFeedback(finished)
                    }
                }
                if(previousTrip==null && vehicleQuests.tripId!=null) {
                    recommendationSource="VEHICLE"
                    if(screen==AppScreen.FEEDBACK || screen==AppScreen.QUESTS && recommendView)show(screen)
                }
            }
            if(screen!=AppScreen.PARKING || s.lesson==RoadLesson.PARKING)map?.setFrame(s.course,s.frame(),s.lesson==RoadLesson.PARKING)
            (canvas as? DriveCanvas)?.apply {
                coachEnabled=coach
                vehicleLine=if(frame!=null)"시연 주행 · ${frame.speed}km/h" else vehicleHeadline()
                vehicleDetail=if(frame!=null)"${if(frame.signal==Indicator.RIGHT)"오른쪽 ON" else if(frame.signal==Indicator.LEFT)"왼쪽 ON" else "깜빡이 OFF"} · ${if(mirrorAvailable)ReplayConsole.status else "시연"}" else vehicleSignalLabel()
                val live=vehicleQuests.coachMessageUntil>now
                message=if(live)vehicleQuests.coachMessage else milestoneText
                messageUntil=if(live)vehicleQuests.coachMessageUntil else milestoneUntil
            }
            (canvas as? QuestCanvas)?.apply {state=session.quest;points=this@ChageunActivity.points}
            canvas?.invalidate()
            startButton?.text=when {s.complete->"다시 시연";s.started && !session.paused->"진행 중";s.started->"재개";else->"자동 시연 시작"}
            startButton?.isEnabled=!emptyPreview && (!s.started || session.paused || s.complete)
            pauseButton?.isEnabled=s.started && !s.complete && !session.paused
            cancelButton?.isEnabled=s.started && !s.complete
            vehicleFinishButton?.isEnabled=vehicleQuests.tripId!=null && telemetry.motion==MotionState.STOPPED
            if(session.completedAt!=null && completionShown!=session.tripId) {
                completionShown=session.tripId;selectedTrip=session.tripId
                recommendationSource=repository.trip(session.tripId!!)?.source ?: "REPLAY"
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
        if(destination==AppScreen.QUESTS)session.refresh()
        screen=destination;startButton=null;pauseButton=null;cancelButton=null;vehicleFinishButton=null;map=null
        val drawing: android.view.View=when(destination) {
            AppScreen.MAP->{
                val layers=FrameLayout(this)
                map=ScenarioRoadMapView(this,art).apply {tilted=mapTilted;showCautions=true;setFrame(session.scenario.course,session.scenario.frame(),session.scenario.lesson==RoadLesson.PARKING)}
                canvas=DriveCanvas(this,art,session).apply {coachEnabled=coach;compactQuestBanner=this@ChageunActivity.compactQuestBanner;tilted=mapTilted;toolsExpanded=this@ChageunActivity.toolsExpanded;vehicleLine=vehicleHeadline();vehicleDetail=vehicleSignalLabel()}
                layers.addView(map,FrameLayout.LayoutParams(-1,-1));layers.addView(canvas,FrameLayout.LayoutParams(-1,-1));layers
            }
            AppScreen.QUESTS->if(recommendView)RecommendationCanvas(this,art).also {
                canvas=it;it.result=repository.recommendations(recommendationSource)
                it.sourceLabel=when(recommendationSource){"REPLAY"->"DEMO · 자동 시연 기록";"SIMULATION"->"TEST · 가상 신호 기록 기준";else->"Pleos 차량 주행 기록 기준"}
            }else QuestCanvas(this,art).also {canvas=it;it.state=session.quest;it.points=points}
            AppScreen.PARKING->{
                val parking = session.scenario.takeIf { it.lesson == RoadLesson.PARKING } ?: SimpleRoadScenario(session.course,RoadLesson.PARKING)
                val layers = FrameLayout(this)
                map = ScenarioRoadMapView(this,art).apply { setFrame(parking.course, parking.frame(), true) }
                canvas = ParkingCanvas(this,art,session)
                layers.addView(map,FrameLayout.LayoutParams(-1,-1))
                layers.addView(canvas,FrameLayout.LayoutParams(-1,-1))
                layers
            }
            AppScreen.FEEDBACK->{
                val layers=FrameLayout(this)
                val bg=ScenarioRoadMapView(this,art).apply {setFrame(session.scenario.course,session.scenario.frame())}
                val id=selectedTrip ?: session.tripId ?: repository.trips().firstOrNull {it.endedAt!=null}?.tripId
                val feedback=CompletionCanvas(this,art).apply {
                    trip=id?.let(repository::trip);report=id?.let(repository::report);state=session.quest
                    scores=id?.let(repository::scores)
                    complete=id?.let {repository.snapshot("trip:$it")}?.let(::JSONObject)?.optString("status")=="COMPLETE" && repository.events(id).any {it.type=="PRACTICE_REWARD" && JSONObject(it.payload).optString("skill")=="PARKING"}
                    earned=id?.let(repository::tripPoints) ?: 0
                    completed=id?.let {repository.completedSkills(it).size} ?: 0
                    val metadata=id?.let {repository.snapshot("trip:$it")}?.let(::JSONObject)
                    target=if(metadata?.optString("lesson")=="COMBINED")6 else metadata?.optInt("targetCount",1) ?: 0
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
                scene.button("", "settings",1591f,157f,68f,68f){settings()}.apply {background=null;elevation=0f;contentDescription="코치 및 지도 설정"}
                scene.button("","map_view",1591f,59f,68f,68f){toggleMapView()}.apply {background=null;elevation=0f;contentDescription="지도 ${if(mapTilted)"2D" else "3D"} 전환"}
                scene.button("","route_steps",53f,59f,543f,186f){routeSteps()}.apply {background=null;elevation=0f;contentDescription="다음 안내 단계 보기"}
                scene.button("","arrival_search",72f,931f,113f,60f){chooseLesson()}.apply {background=null;elevation=0f;contentDescription="시연 경로 찾기"}
                cancelButton=scene.button("","route_cancel",220f,931f,356f,60f){cancelRoute()}.apply {background=null;elevation=0f;contentDescription="시연 경로 취소"}
                scene.button("","coach_toggle",58f,650f,184f,93f){coach=!coach;session.coachingEnabled=coach;saveSettings();if(!coach)voice?.stop();show(screen)}.apply {
                    background=null;elevation=0f;contentDescription="주행 코치 ${if(coach)"켜짐" else "꺼짐"} 전환"
                }
                startButton=scene.button("자동 시연 시작","replay_start",53f,1025f,225f,60f,true){start()}
                pauseButton=scene.button("일시정지","replay_pause",290f,1025f,140f,60f){session.pause();voice?.stop()}
                scene.button("연습 선택","lesson_select",445f,1025f,150f,60f){chooseLesson()}
                if(toolsExpanded) {
                    scene.button("깜빡이 OFF","indicator_off",73f,447f,235f,60f){setIndicator(Indicator.OFF)}
                    scene.button("오른쪽 ON","indicator_right",323f,447f,235f,60f){setIndicator(Indicator.RIGHT)}
                    scene.button("SDK 입력","indicator_live",73f,521f,235f,60f){setIndicator(null)}
                    scene.button("비교 안내 듣기","indicator_preview",323f,521f,235f,60f){previewIndicator()}
                    scene.button("닫기","tools_close",463f,391f,110f,48f){openSimulationTools(false)}
                }
                if(coach)scene.button("설명 더 보기","coach_help",1195f,620f,390f,60f){help()}
                scene.button("","map_quest_banner",1095f,905f,560f,170f){showQuestProgress()}.apply {
                    background=null;elevation=0f;contentDescription="이번 시연 퀘스트 진행도 보기"
                }
            }
            AppScreen.QUESTS->{
                scene.button("맞춤 퀘스트","quests_recommend",70f,231f,292f,59f,recommendView){showRecommendations(recommendationSource)}
                scene.button("종합 기록","quests_demo",402f,231f,292f,59f,!recommendView){showQuestProgress()}
                if(recommendView) {
                    scene.button("${when(recommendationSource){"REPLAY"->"시연 기록";"SIMULATION"->"가상 신호 TEST";else->"차량 주행"}}","recommend_source",734f,231f,292f,59f){chooseRecommendationSource()}
                    val recommendations=(canvas as RecommendationCanvas).result?.recommendations.orEmpty()
                    recommendations.forEachIndexed {i,q->
                        scene.button("","recommend_card_$i",667f,RecommendationCanvas.cardTop(i,recommendations.size),963f,RecommendationCanvas.cardHeight(recommendations.size)){recommendationDetails(q)}.apply {
                            background=null;elevation=0f;contentDescription="${q.title} · ${q.reason} · 자세히 보기"
                        }
                    }
                }else {
                    scene.button("★ 퀘스트 계속하기","quest_continue",1211f,944f,445f,66f,true){show(if(session.scenario.lesson==RoadLesson.PARKING)AppScreen.PARKING else AppScreen.MAP);if(!session.scenario.started || session.paused)start()}
                    scene.button("초기화","quest_reset",1461f,231f,195f,53f){resetQuests()}
                    com.hmgchallenge.pleosstarter.quests.ProductQuestCatalog.all.forEachIndexed {i,quest->
                        scene.button("", "quest_card_$i",62f+(i%3)*541,322f+(i/3)*299,512f,270f){
                            when(quest.skill) {
                                com.hmgchallenge.pleosstarter.quests.CoachSkill.PREPARATION->if(session.tripId==null || session.scenario.complete)start()else checklist(quest.skill)
                                com.hmgchallenge.pleosstarter.quests.CoachSkill.FINISH->checklist(quest.skill)
                                else->select(RoadLesson.valueOf(quest.lesson!!))
                            }
                        }.apply {background=null;elevation=0f;contentDescription="${quest.title} · ${session.quest.practiceCount(quest.skill)}회"}
                    }
                }
            }
            AppScreen.PARKING->{
                if(session.scenario.lesson!=RoadLesson.PARKING)scene.button("후진 주차 시연 선택","parking_select",1315f,55f,330f,76f,true){select(RoadLesson.PARKING)}
                else {
                    startButton=scene.button("주차 자동 시연","replay_start",1195f,55f,245f,76f,true){start()}
                    pauseButton=scene.button("일시정지","replay_pause",1455f,55f,190f,76f){session.pause();voice?.stop()}
                }
            }
            AppScreen.FEEDBACK->{
                if(vehicleQuests.tripId!=null)vehicleFinishButton=scene.button("차량 주행 기록 마치기","vehicle_record_finish",790f,305f,490f,68f){
                    if(telemetry.motion!=MotionState.STOPPED)toast("정차 후 주행 기록을 마칠 수 있어요")
                    else {
                        val id=vehicleQuests.tripId
                        if(vehicleQuests.finish()){vehicleMovedInGear=false;if(id!=null)showFeedback(id)}else toast(vehicleQuests.error ?: "저장 실패")
                    }
                }
                scene.button("지난 기록","records_history",790f,190f,270f,90f){history()}
                scene.button("어려웠어요","feedback_difficult",1110f,190f,270f,90f){chooseDifficultArea()}
                scene.button("메모 남기기","feedback_memo",1410f,190f,230f,90f){
                    if(feedbackTripId()==null)toast("주행 기록을 남긴 뒤 메모할 수 있어요")
                    else memo=DrivingMemoDialog(this,{text,category,difficult->saveFeedbackNote(text,category,difficult)},{memo=null;show(screen)}).also {it.show("OTHER")}
                }
                scene.button("맞춤 퀘스트","feedback_recommend",1010f,970f,290f,90f){
                    val id=selectedTrip ?: session.tripId ?: repository.trips().firstOrNull {it.endedAt!=null}?.tripId
                    showRecommendations(id?.let(repository::trip)?.source ?: "VEHICLE")
                }
                scene.button("다음 연습","feedback_next",1320f,970f,280f,90f,true){chooseLesson()}
                if(vehicleFeedbackId()==null && session.completedAt!=null && session.scenario.lesson==RoadLesson.PARKING && (selectedTrip==null || selectedTrip==session.tripId)) {
                    scene.button("하차 준비 확인","departure_checklist",790f,970f,200f,90f){checklist(com.hmgchallenge.pleosstarter.quests.CoachSkill.FINISH)}
                }
            }
        }
        // These destinations are always available; no separate navy/white application tasks.
        val names=listOf("지도 · 주행 코치","퀘스트","주차 가이드","주행 피드백")
        AppScreen.values().forEachIndexed {i,dest->
            val questPage=destination==AppScreen.QUESTS
            val x=if(questPage)listOf(65f,515f,915f,1315f)[i] else 25f+420*i
            val width=if(questPage && i==0)350f else if(questPage)300f else 390f
            scene.button(names[i],"tab_${dest.name}",x,if(questPage)1075f else 1100f,width,70f,dest==screen) {
                if(dest==AppScreen.QUESTS)showRecommendations(recommendationSource) else show(dest)
            }
        }
        setContentView(scene)
    }
    private fun feedbackTripId()=selectedTrip ?: session.tripId ?: repository.trips().firstOrNull {it.endedAt!=null}?.tripId
    fun saveFeedbackNote(text: String,category: String,difficult: Boolean): Boolean {
        val id=feedbackTripId() ?: return false
        val skill=when(category){"LANE_CHANGE"->"LANE_CHANGE";"RIGHT_TURN","LEFT_TURN"->"INTERSECTION";"PARKING"->"PARKING";"SPEED_CONTROL"->"SPEED";else->null}
        val scenario=repository.snapshot("trip:$id")?.let(::JSONObject)?.optBoolean("scenario")==true
        val source=if(repository.trip(id)?.source=="VEHICLE" && !scenario)"VEHICLE" else "SIMULATION"
        val value=if(difficult && !text.startsWith("어려웠어요"))"어려웠어요 · $text" else text
        val saved=repository.addNote(id,value,category,skill,difficult,source=source)
        if(saved)session.refresh()
        return saved
    }
    private fun chooseDifficultArea() {
        if(feedbackTripId()==null){toast("주행 기록을 남긴 뒤 선택해 주세요");return}
        val areas=listOf("차선 변경" to "LANE_CHANGE","우회전" to "RIGHT_TURN","후진 주차" to "PARKING","속도 조절" to "SPEED_CONTROL")
        AlertDialog.Builder(this).setTitle("어떤 상황이 어려웠나요?").setItems(areas.map {it.first}.toTypedArray()){_,i->
            if(saveFeedbackNote(areas[i].first,areas[i].second,true)){toast("다음 맞춤 퀘스트에 반영할게요");show(screen)}else toast(repository.lastError ?: "저장 실패")
        }.setNegativeButton("닫기",null).show()
    }
    fun showQuestProgress() {recommendView=false;show(AppScreen.QUESTS)}
    fun showRecommendations(source: String="VEHICLE") {
        require(source in setOf("VEHICLE","REPLAY","SIMULATION"))
        recommendationSource=source;recommendView=true;show(AppScreen.QUESTS)
    }
    fun recommendationDiagnostics()=(canvas as? RecommendationCanvas)?.result
    private fun chooseRecommendationSource() {
        AlertDialog.Builder(this).setTitle("맞춤 퀘스트 기준")
            .setItems(arrayOf("Pleos 차량 주행 기록","자동 시연 · 최근 연습 점수")){_,i->showRecommendations(if(i==0)"VEHICLE" else "REPLAY")}
            .setNegativeButton("닫기",null).show()
    }
    private fun recommendationDetails(q: com.hmgchallenge.pleosstarter.analysis.Recommendation) {
        val lesson=when(q.weakness) {
            "TURN_SIGNAL"->RoadLesson.LANE
            "TURN_SLOWDOWN","LEFT_TURN","U_TURN"->if(q.weakness=="TURN_SLOWDOWN")RoadLesson.TURN else null
            "PARKING"->RoadLesson.PARKING
            "SMOOTH_ACCEL","BRAKING"->RoadLesson.COMBINED
            else->null
        }
        val message="${q.reason}\n\n${q.goal}\n\n"+if(recommendationSource=="REPLAY")"시연 기록과 메모로 고른 연습입니다. 자동 시연 완료는 실제 운전 숙련도나 차량 퀘스트 달성으로 기록하지 않습니다." else "주행 신호로 확인 가능한 조건만 기록합니다. 차로 위치·차간거리·주변 안전을 자동으로 확인한 결과는 아닙니다."
        val dialog=AlertDialog.Builder(this).setTitle(q.title).setMessage(message).setNegativeButton("닫기",null)
        if(lesson!=null)dialog.setPositiveButton("관련 자동 시연 보기"){_,_->select(lesson)}
        else dialog.setPositiveButton("확인",null)
        dialog.show()
    }
    fun openSimulationTools(open: Boolean=true) {toolsExpanded=open;if(!open)setIndicator(null);show(AppScreen.MAP)}
    fun toggleMapView() {mapTilted=!mapTilted;saveSettings();show(AppScreen.MAP)}
    fun toggleQuestBannerStyle() {compactQuestBanner=!compactQuestBanner;saveSettings();show(AppScreen.MAP)}
    fun navigationDiagnostics()=map?.diagnostics()
    fun cancelRoute() {
        voice?.stop()
        if(session.cancel()) {selectedTrip=null;show(AppScreen.MAP);toast("경로를 취소했어요 · 지난 기록은 유지돼요")}
        else toast(session.error ?: "기록 저장 실패")
    }
    private fun routeSteps() {
        val hud=NavigationHudModel.from(session.scenario,session.totalTripMetres,session.travelledMetres,session.continuingToParking)
        AlertDialog.Builder(this).setTitle("시연 경로 안내").setMessage(hud.cues.joinToString("\n\n") {cue->"${cue.label} · ${cue.metres?.let {"${it.toInt()}m"} ?: "다음 단계"}"}+"\n\n차로·횡단보도·핸들은 시연 경로를 설명하는 예시입니다.")
            .setPositiveButton("확인",null).show()
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
        if(session.tripId==null || session.scenario.complete) {
            val definition=com.hmgchallenge.pleosstarter.quests.QuestCatalog.get("prepare")
            val checked=mutableSetOf<Int>()
            val dialog=AlertDialog.Builder(this).setTitle("시연 출발 점검 · 체크하면 가상 센서에 반영")
                .setMultiChoiceItems(definition.checks.toTypedArray(),BooleanArray(definition.checks.size)){d,i,on->
                    if(on)checked.add(i)else checked.remove(i)
                    (d as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=checked.size==definition.checks.size
                }.setPositiveButton("확인하고 시연 시작"){_,_->startPrepared(checked)}
                .setNeutralButton("점검 기록 없이 시연"){_,_->startPrepared()}.setNegativeButton("닫기",null).create()
            dialog.setOnShowListener {dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false};dialog.show()
            return
        }
        startPrepared()
    }
    fun startPrepared(checked: Set<Int>?=null) {
        if(emptyPreview)return
        if(session.usesReplaySpeed && mirrorAvailable && vehicleQuests.tripId!=null) {
            if(telemetry.motion!=MotionState.STOPPED){toast("차량 주행 중이에요 · 정차 후 자동 시연을 시작해 주세요");return}
            if(!vehicleQuests.finish()){toast("이전 주행 기록을 저장하지 못했어요");return}
        }
        if(!session.start())toast(session.error ?: "저장 실패")
        else if(checked!=null && !confirmChecklist(com.hmgchallenge.pleosstarter.quests.CoachSkill.PREPARATION,checked))toast(session.error ?: "점검 기록 실패")
        lastTick=SystemClock.elapsedRealtime()
    }
    private fun confirmChecklist(skill: com.hmgchallenge.pleosstarter.quests.CoachSkill,checked: Set<Int>): Boolean {
        if(!session.confirmChecklist(skill,checked))return false
        if(mirrorAvailable && session.usesReplaySpeed) {
            ReplayConsole.publishChecklist(skill==com.hmgchallenge.pleosstarter.quests.CoachSkill.PREPARATION) {ok->
                if(!ok)runOnUiThread {if(!isDestroyed)toast("점검 기록 저장됨 · 에뮬레이터 신호 전송 실패")}
            }
        }
        return true
    }
    private fun checklist(skill: com.hmgchallenge.pleosstarter.quests.CoachSkill) {
        if(session.tripId==null){toast("시연을 시작한 뒤 확인할 수 있어요");return}
        if(skill==com.hmgchallenge.pleosstarter.quests.CoachSkill.FINISH && (session.completedAt==null || session.scenario.lesson!=RoadLesson.PARKING)) {
            toast("주차를 마친 뒤 하차 준비를 확인해요");return
        }
        val definition=com.hmgchallenge.pleosstarter.quests.QuestCatalog.get(if(skill==com.hmgchallenge.pleosstarter.quests.CoachSkill.FINISH)"finish" else "prepare")
        val checked=mutableSetOf<Int>()
        val dialog=AlertDialog.Builder(this).setTitle("시연 센서 입력 · ${definition.title}")
            .setMultiChoiceItems(definition.checks.toTypedArray(),BooleanArray(definition.checks.size)){d,i,on->
                if(on)checked.add(i)else checked.remove(i)
                (d as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=checked.size==definition.checks.size
            }.setPositiveButton("확인한 내용 기록"){_,_->
                if(confirmChecklist(skill,checked)){show(screen);toast("퀘스트에 반영했어요")}else toast(session.error ?: "확인 조건을 충족하지 않았어요")
            }.setNegativeButton("닫기",null).create()
        dialog.setOnShowListener {dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false};dialog.show()
    }
    fun questDiagnostics()=(canvas as? QuestCanvas)?.diagnostics()
    fun select(lesson: RoadLesson,source: ScenarioSpeed=ScenarioSpeed.REPLAY) {
        voice?.stop()
        if(!session.select(lesson,source)){toast(session.error ?: "기록 저장 실패");return}
        if(source==ScenarioSpeed.VEHICLE)session.indicatorOverride=null
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
            .setItems(arrayOf("음성 ${if(voiceEnabled)"ON" else "OFF"}","축하음·조명 ${if(celebrationEnabled)"ON" else "OFF"}","설명 방식: ${session.manualStyle.title}","실제 차량 데이터 확인","지도 진행: ${session.scenario.source}","깜빡이 비교 · 시연 도구","퀘스트 배너: ${if(compactQuestBanner)"간단히" else "카드형"}")){_,i->when(i){
                0->{voiceEnabled=!voiceEnabled;saveSettings();if(!voiceEnabled)voice?.stop()else voice?.speak("주행 코치 음성 안내를 켰어요")}
                1->{celebrationEnabled=!celebrationEnabled;saveSettings()}
                2->AlertDialog.Builder(this).setTitle("설명 방식").setItems(BeginnerStyle.values().map {it.title}.toTypedArray()){_,n->session.manualStyle=BeginnerStyle.values()[n];saveSettings()}.show()
                3->AlertDialog.Builder(this).setTitle("Pleos 연결 상태").setMessage("속도: ${if(telemetry.fresh)"${telemetry.speedMps} m/s" else "미수신/오래된 값"}\n방향지시등: ${telemetry.indicator}\n조향각(SDK): ${telemetry.steeringAngle}\n브레이크: ${telemetry.brakePressed}\n내비: ${telemetry.navigationStatus}\n음성: $voiceStatus\n축하: $celebrationStatus\n\n지도·차로·핸들은 시나리오 값입니다. 차량 신호는 신호 조건 퀘스트·피드백에 사용합니다. 실제 차로 위치 검증은 별도입니다.").setPositiveButton("확인",null).show()
                4->AlertDialog.Builder(this).setTitle("지도 진행 속도").setItems(arrayOf("자동 재생 · 직진 40 / 접근 40→30 / 회전 중 10km/h","차량 SDK 속도 · 경로는 가상")){_,n->select(session.scenario.lesson,if(n==0)ScenarioSpeed.REPLAY else ScenarioSpeed.VEHICLE)}.show()
                5->openSimulationTools()
                6->toggleQuestBannerStyle()
            }}.setNegativeButton("닫기",null).show()
    }
    private fun saveSettings(){if(!repository.saveSnapshot("settings:product",JSONObject().put("coach",coach).put("voice",voiceEnabled).put("celebration",celebrationEnabled).put("style",session.manualStyle.name).put("mapTilted",mapTilted).put("compactQuestBanner",compactQuestBanner).toString()))toast("설정을 저장하지 못했어요")}
    private fun resetQuests() {
        if(session.scenario.started && !session.scenario.complete){toast("진행 중인 연습을 먼저 마쳐 주세요");return}
        AlertDialog.Builder(this).setTitle("시연 퀘스트를 처음부터?").setMessage("종합 시연 진행도와 포인트, 맞춤 퀘스트의 최근 평균·추천 진행도를 초기화해요. 지난 주행 리포트는 보관해요.")
            .setPositiveButton("초기화"){_,_->if(session.resetProgress()){points=0;recommendationSource="REPLAY";showQuestProgress()}else toast(session.error ?: "초기화 실패")}.setNegativeButton("취소",null).show()
    }
    private fun history() {
        val trips=repository.trips().filter {it.endedAt!=null}
        if(trips.isEmpty()){toast("아직 저장된 연습이 없어요");return}
        val format=java.text.SimpleDateFormat("MM/dd HH:mm",java.util.Locale.KOREA)
        AlertDialog.Builder(this).setTitle("지난 연습 기록").setItems(trips.map {"${format.format(java.util.Date(it.startedAt))} · ${((it.distanceKm ?: 0f)*1000).toInt()}m"}.toTypedArray()){_,i->selectedTrip=trips[i].tripId;show(AppScreen.FEEDBACK)}.setNegativeButton("닫기",null).show()
    }
    private fun mirror(frame: ReplayControls?) {
        if (mirrorAvailable && frame?.gear == "Reverse" && session.scenario.lesson == RoadLesson.PARKING && coach) {
            cameraCoach.update(session.scenario)
        } else if (mirroredFrame?.gear == "Reverse") {
            cameraCoach.hide()
        }
        if (mirrorAvailable && frame != mirroredFrame) {
            mirroredFrame = frame
            ReplayConsole.update(frame)
        }
    }

    // Pleos owns the foreground during reverse. Finish only the active emulator parking
    // manoeuvre so the console returns to Park and the same trip can show its feedback.
    private val finishCameraReplay = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            session.tick(now - lastTick, now, null, false)
            lastTick = now
            val parking = session.scenario
            if (session.paused || parking.complete || parking.parkingGear != "R") {
                mirror(null)
                return
            }
            mirror(ReplayControls.from(parking, session.indicatorOverride))
            handler.postDelayed(this, 100)
        }
    }

    override fun onStop() {
        memo?.dismiss();memo=null
        active=false;handler.removeCallbacks(loop);handler.removeCallbacks(refreshRecommendations);recommendationObserver?.close();recommendationObserver=null
        if (!isFinishing && mirroredFrame?.gear == "Reverse" && !session.paused) {
            handler.post(finishCameraReplay)
        } else {
            mirror(null)
            if (::session.isInitialized) session.pause()
        }
        if(::vehicleQuests.isInitialized) {
            vehicleMovedInGear=false
            vehicleQuests.suspendInputs()
            if(vehicleQuests.tripId!=null)vehicleQuests.finish(false)
        }
        voice?.release();voice=null;celebration?.stop();celebration=null
        if(::telemetry.isInitialized)telemetry.stop()
        super.onStop()
    }
    override fun onDestroy() {
        handler.removeCallbacks(finishCameraReplay)
        if (mirroredFrame?.gear == "Reverse") cameraCoach.hide()
        if(mirroredFrame!=null){mirroredFrame=null;ReplayConsole.update(null)}
        if(::session.isInitialized)session.close()
        super.onDestroy()
    }
    fun voiceDiagnostics()=speechEvidence.snapshot()
    fun vehicleDiagnostics()=telemetry.diagnostics()
    fun vehicleInput()=telemetry.questInput()
    fun liveDrivingDiagnostics()=mapOf("gear" to celebration?.gear.orEmpty(),"motion" to telemetry.motion.name,"speedMps" to telemetry.speedMps.toString(),"speedStatus" to telemetry.speedStatus,"sdkIndicator" to telemetry.indicator.name,"coachIndicator" to session.scenario.indicator.name,"indicatorInput" to if(session.indicatorOverride!=null || session.usesReplaySpeed && session.scenario.started && !session.paused)"SIMULATION" else "SDK","tripId" to vehicleQuests.tripId.orEmpty(),"recordingStatus" to vehicleQuests.status)
    fun showFeedback(tripId: String) {
        require(repository.trip(tripId)!=null)
        if(tripId==session.tripId && session.completedAt!=null)completionShown=tripId
        selectedTrip=tripId;show(AppScreen.FEEDBACK)
    }
    private fun vehicleFeedbackId(): String? {
        val id=selectedTrip ?: vehicleQuests.tripId ?: return null
        return id.takeIf {repository.snapshot("trip:$it")?.let(::JSONObject)?.optString("lesson")=="VEHICLE_QUEST"}
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==834 && grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)telemetry.startSpeed()
        memo?.onPermissionResult(requestCode,grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    override fun onBackPressed(){if(screen!=AppScreen.MAP)show(AppScreen.MAP)else super.onBackPressed()}
    private fun toast(message: String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    private fun isEmulator()=Build.HARDWARE in setOf("ranchu","goldfish") || Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("emulator",true)
}
