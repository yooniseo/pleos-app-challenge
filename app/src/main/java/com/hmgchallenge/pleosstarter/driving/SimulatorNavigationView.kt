package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import com.hmgchallenge.pleosstarter.R
import com.caverock.androidsvg.SVG
import kotlin.math.*

/** Compact navigation cards inside the OEM application window; OEM chrome stays platform-owned. */
class SimulatorNavigationView(context: Context): FrameLayout(context) {
    private val reference=ReferenceRouteClient.parse(context.assets.open("figma_navigation/kgu-route.json").bufferedReader().use {it.readText()})
    val course=NavigationSimulationRoute(reference.points)
    private val map=ScenarioRoadMapView(context)
    private val hud=NavigationHud(context)
    private val guideWheel=SimulatorWheelView(context).apply {
        tag="coach_steering_guide";interactive=false;showAngle=false;wheelColor=Color.rgb(46,49,68);targetColor=Color.rgb(108,76,247);labelColor=Color.rgb(46,49,68)
        contentDescription="가상 차량의 핸들 회전과 복원 안내."
    }
    private val positions=mutableMapOf<View,RectF>()
    private var currentFrame: NavigationSimulationFrame?=null
    var onAutomatic: ()->Unit={}
    var onPausePlayback: ()->Unit={}
    var onControls: ()->Unit={}
    var onVoice: ()->Unit={}
    var onNote: ()->Unit={}
    var onFeedback: ()->Unit={}
    var onRestart: ()->Unit={}
    var onQuests: ()->Unit={}
    var onScenario: ()->Unit={}
    var onSpeedMode: ()->Unit={}
    var onProfile: ()->Unit={}
    var onSteeringPractice: ()->Unit={}
    var onSteeringInput: (Double)->Unit={}
    var steeringPractice: SteeringFollowPractice?=null
    var coachStyle=BeginnerStyle.STEP_BY_STEP
    var celebrationMessage=""
    var celebrationUntil=0L
    var celebrationStatus=""
    private val startButton: Button
    private val pauseButton: Button
    private val voiceButton: Button
    private val sourceButton: Button
    private val profileButton: Button
    private val steeringButton: Button
    init {
        tag="navigation_panel";setBackgroundColor(Color.rgb(239,240,246))
        addView(map,LayoutParams(-1,-1));addView(hud,LayoutParams(-1,-1))
        addView(guideWheel);positions[guideWheel]=RectF(35f,280f,170f,430f)
        profileButton=action("나의 코치 · 차근차근","nav_profile",20f,235f,308f,40f){onProfile()}
        steeringButton=action("따라하기","nav_steering_practice",184f,391f,124f,32f){onSteeringPractice()}
        guideWheel.onSteer={onSteeringInput(it)}
        action("시나리오 선택","nav_scenario",20f,176f,270f,43f){onScenario()}
        sourceButton=action("속도: 자동 재생","nav_speed_mode",722f,20f,258f,43f){onSpeedMode()}
        action("","nav_quests",700f,84f,280f,188f){onQuests()}.apply {background=null;contentDescription="진행 중인 주행 퀘스트 전체 보기"}
        action("주행 피드백","nav_menu",37f,606f,274f,43f){onFeedback()}
        startButton=action("연습 시작","nav_auto",377f,630f,160f,45f){onAutomatic()}
        pauseButton=action("일시정지","nav_pause",546f,630f,137f,45f){onPausePlayback()}
        action("처음부터","nav_restart",692f,630f,137f,45f){onRestart()}
        voiceButton=action("음성 켜기","nav_voice",838f,630f,142f,45f){onVoice()}
        action("운전 조작","nav_controls",722f,288f,124f,43f){onControls()}
        action("순간 기록","nav_note",856f,288f,124f,43f){onNote()}
    }
    private fun action(title: String,key: String,x: Float,y: Float,w: Float,h: Float,action: ()->Unit)=Button(context).apply {
        text=title;tag=key;isAllCaps=false;textSize=15f;minHeight=0;minimumHeight=0;setPadding(3,0,3,0)
        typeface=resources.getFont(R.font.asta_sans_medium);setTextColor(Color.rgb(61,54,91))
        background=GradientDrawable().apply {setColor(Color.WHITE);cornerRadius=14f;setStroke(1,Color.rgb(224,223,234))}
        elevation=2f;setOnClickListener {action()};addView(this);positions[this]=RectF(x,y,x+w,y+h)
    }
    override fun onMeasure(w: Int,h: Int) {
        super.onMeasure(w,h)
        positions.forEach {(v,r)->
            if(v is Button)v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,18f*measuredWidth/1000)
            v.measure(MeasureSpec.makeMeasureSpec((r.width()*measuredWidth/1000).roundToInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((r.height()*measuredHeight/700).roundToInt(),MeasureSpec.EXACTLY))
        }
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
        map.layout(0,0,width,height);hud.layout(0,0,width,height)
        positions.forEach {(v,p)->v.layout((p.left*width/1000).roundToInt(),(p.top*height/700).roundToInt(),(p.right*width/1000).roundToInt(),(p.bottom*height/700).roundToInt())}
    }
    fun renderSimple(s: SimpleRoadScenario,paused: Boolean,voice: Boolean,questSaved: Boolean,receivedSpeed: Double?=null) {
        currentFrame=s.frame();map.setFrame(s.course,currentFrame!!,s.lesson==RoadLesson.PARKING)
        val follow=steeringPractice
        val guide=if(follow==null)BeginnerScenarioCoach.guidance(s,coachStyle) else BeginnerGuidance(
            if(follow.rewarded)"띵동! 핸들을 알맞게 돌렸어요" else follow.instruction,
            if(follow.rewarded)"좋아요! 연습 마치기로 주행을 이어가요" else "핸들을 드래그해 연한 목표 그림에 맞춰요",
            "핸들 따라하기 · 지도는 잠시 멈췄어요",follow.target.toInt(),
            if(follow.target<0)"왼쪽" else "오른쪽",if(abs(follow.target)<=180)"꺾어주세요" else BeginnerScenarioCoach.turnAmount(follow.target.toInt()))
        guideWheel.interactive=follow!=null;guideWheel.angle=follow?.angle ?: guide.steering.toDouble();guideWheel.target=follow?.target ?: 0.0;guideWheel.invalidate()
        steeringButton.text=if(follow==null)"따라하기" else "연습 마치기"
        guideWheel.contentDescription="핸들을 ${guide.direction} ${guide.amount}. ${guide.detail}"
        profileButton.text="나의 코치 · ${coachStyle.title}"
        hud.apply {
            cue=if(paused && follow==null)"일시정지 중 · 계속하기로 이어가요" else guide.headline
            guidance=guide;this@SimulatorNavigationView.celebrationMessage.let {reward=it}
            rewardUntil=this@SimulatorNavigationView.celebrationUntil;rewardStatus=this@SimulatorNavigationView.celebrationStatus
            distance=s.distance;end=s.endDistance;remaining=(s.course.turnDistance-s.distance).coerceAtLeast(0.0)
            lane=s.currentLane;laneDone=s.laneDone;turnDone=s.complete && s.lesson!=RoadLesson.LANE
            lesson=s.lesson;complete=s.complete;speed=if(s.source==ScenarioSpeed.VEHICLE)(receivedSpeed ?: 0.0)*3.6 else if(paused)0.0 else s.speedMps*3.6
            speedKnown=s.source==ScenarioSpeed.REPLAY || receivedSpeed!=null;parkingGear=s.parkingGear;progress=s.progress.toFloat()
            started=s.started;this.questSaved=questSaved;source=s.source
            contentDescription="주행 퀘스트. $cue. 현재 ${s.currentLane}차로. 시뮬레이션 진행률 ${(s.progress*100).roundToInt()}퍼센트."
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES;invalidate()
        }
        startButton.text=if(s.complete)"다시 연습" else if(s.started)"연습 중" else "연습 시작"
        startButton.isEnabled=!s.started || s.complete
        pauseButton.text=if(paused)"계속하기" else "일시정지";pauseButton.isEnabled=s.started && !s.complete
        voiceButton.text=if(voice)"음성 끄기" else "음성 켜기"
        sourceButton.text=if(s.lesson==RoadLesson.PARKING)"주차 연습 · 자동 재생" else if(s.source==ScenarioSpeed.VEHICLE)"속도: 차량 SDK" else "속도: 자동 재생"
    }
    fun render(m: DrivingSimulator,paused: Boolean,automatic: Boolean,voice: Boolean) {
        currentFrame=course.frame(m);map.setFrame(course,currentFrame!!)
        hud.apply {cue=m.instruction();distance=currentFrame!!.distance;end=course.length;remaining=currentFrame!!.turnRemaining
            lane=m.currentLane;laneDone=m.currentLane==3;turnDone=m.stage==SimStage.COMPLETE;complete=turnDone
            speed=m.speed*3.6;progress=(distance/end).toFloat();started=automatic;invalidate()}
        startButton.isEnabled=true;startButton.text=if(automatic)"시연 멈추기" else "자동 시연"
        pauseButton.isEnabled=true;pauseButton.text=if(paused)"계속하기" else "일시정지"
        voiceButton.text=if(voice)"음성 끄기" else "음성 켜기"
    }
    fun diagnostics()=map.diagnostics()+mapOf("routeDistance" to (currentFrame?.distance ?: 0.0).toString(),
        "turnRemaining" to hud.remaining.toString(),"currentLane" to hud.lane.toString(),"lanesRemaining" to (3-hud.lane).toString(),
        "heading" to (currentFrame?.bearing ?: 0.0).toString(),"cue" to hud.cue,"speedKph" to hud.speed.toString(),
        "questLaneDone" to hud.laneDone.toString(),"questTurnDone" to hud.turnDone.toString(),"questSaved" to hud.questSaved.toString(),"speedSource" to hud.source.name,
        "steeringTarget" to hud.guidance.steering.toString(),"coachStyle" to coachStyle.name,"celebration" to celebrationStatus)
    private class NavigationHud(context: Context): View(context) {
        var cue="";var distance=0.0;var end=192.2;var remaining=87.0;var lane=2
        var laneDone=false;var turnDone=false;var complete=false;var speed=0.0;var progress=0f
        var started=false;var questSaved=true;var source=ScenarioSpeed.REPLAY;var lesson=RoadLesson.COMBINED
        var speedKnown=true;var parkingGear="P"
        var guidance=BeginnerGuidance("","","",0)
        var reward="";var rewardUntil=0L;var rewardStatus=""
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        private val bold=resources.getFont(R.font.asta_sans_bold)
        private val medium=resources.getFont(R.font.asta_sans_medium)
        private val purple=Color.rgb(108,76,247);private val ink=Color.rgb(34,35,45);private val muted=Color.rgb(119,121,137)
        private val right=SVG.getFromAsset(context.assets,"figma_navigation/0b65a.svg").renderToPicture()
        private val laneRight=SVG.getFromAsset(context.assets,"figma_navigation/82a06.svg").renderToPicture()
        private fun text(c: Canvas,value: String,x: Float,y: Float,size: Float,color: Int=ink,strong: Boolean=false) {
            p.color=color;p.textSize=size;p.typeface=if(strong)bold else medium;p.style=Paint.Style.FILL;c.drawText(value,x,y,p)
        }
        private fun card(c: Canvas,x: Float,y: Float,w: Float,h: Float) {
            p.style=Paint.Style.FILL;p.color=Color.WHITE;p.setShadowLayer(7f,0f,3f,Color.argb(25,37,37,64))
            c.drawRoundRect(x,y,x+w,y+h,17f,17f,p);p.clearShadowLayer()
        }
        private fun arrow(c: Canvas,x: Float,y: Float,size: Float,lane: Boolean=false) {
            c.saveLayer(RectF(x,y,x+size,y+size),Paint().apply {colorFilter=PorterDuffColorFilter(purple,PorterDuff.Mode.SRC_IN)})
            c.drawPicture(if(lane)laneRight else right,RectF(x,y,x+size,y+size));c.restore()
        }
        private fun quest(c: Canvas,y: Float,index: Int,title: String,done: Boolean,active: Boolean) {
            p.style=Paint.Style.FILL;p.color=if(done || active)purple else Color.rgb(231,230,240);c.drawCircle(726f,y-6,12f,p)
            if(done) {p.color=Color.WHITE;p.style=Paint.Style.STROKE;p.strokeWidth=2.3f;c.drawLine(720f,y-6,724f,y-2,p);c.drawLine(724f,y-2,732f,y-11,p)}
            else text(c,index.toString(),722f,y-1,13f,if(active)Color.WHITE else muted,true)
            text(c,title,748f,y,18f,if(active || done)ink else muted,active)
        }
        override fun onDraw(c: Canvas) {
            c.save();c.scale(width/1000f,height/700f)
            val parking=lesson==RoadLesson.PARKING
            card(c,20f,20f,302f,112f)
            if(parking)text(c,"P",46f,85f,48f,purple,true)else arrow(c,44f,46f,43f,lesson==RoadLesson.LANE)
            text(c,if(complete)"완료" else if(parking)"후진 주차" else if(lesson==RoadLesson.LANE)"1개 차로" else if(remaining<1)"지금 우회전" else "${remaining.roundToInt()}m",107f,70f,34f,ink,true)
            text(c,if(parking)"가상 $parkingGear 단 · 연습장" else if(lesson==RoadLesson.LANE)"오른쪽 · 2 → 3차로" else "대학로 · 우회전",107f,102f,18f,muted)
            card(c,20f,137f,206f,32f);text(c,if(parking)"주변 확인 → 후진 → 정렬" else if(lesson==RoadLesson.LANE)"이후 3차로 유지" else "이후 직진 · ${(end-courseTurnEnd()).coerceAtLeast(0.0).roundToInt()}m",39f,159f,16f)
            card(c,20f,235f,308f,245f)
            text(c,"핸들 코칭",184f,301f,15f,muted)
            text(c,guidance.direction,184f,334f,25f,ink,true)
            text(c,guidance.amount,184f,373f,if(guidance.amount.length>5)20f else 25f,purple,true)
            val line=guidance.detail
            p.textSize=16f;p.typeface=medium
            val fit=p.breakText(line,true,270f,null)
            val split=if(fit<line.length)line.lastIndexOf(' ',fit).takeIf {it>fit/2} ?: fit else line.length
            text(c,line.take(split),38f,437f,16f)
            val rest=line.drop(split).trim();val restFit=p.breakText(rest,true,270f,null)
            text(c,rest.take(restFit),38f,459f,16f)
            text(c,"가상 차량 연습 · 실제 주변은 직접 확인해요",38f,476f,11f,muted)
            card(c,700f,84f,280f,188f)
            val total=if(lesson==RoadLesson.COMBINED)2 else 1
            val count=if(parking && complete)1 else (if(laneDone && lesson!=RoadLesson.TURN)1 else 0)+(if(turnDone && !parking)1 else 0)
            text(c,"주행 퀘스트",720f,116f,19f,ink,true);text(c,"$count / $total",916f,116f,18f,purple,true)
            if(parking)quest(c,158f,1,"후진 주차·정렬 완료",complete,!complete)
            else {
                if(lesson!=RoadLesson.TURN)quest(c,158f,1,"오른쪽 한 차로 이동",laneDone,!laneDone)
                if(lesson!=RoadLesson.LANE)quest(c,if(lesson==RoadLesson.TURN)158f else 198f,if(lesson==RoadLesson.TURN)1 else 2,"3차로에서 우회전",turnDone,lesson==RoadLesson.TURN || laneDone)
            }
            val celebrating=android.os.SystemClock.elapsedRealtime()<rewardUntil
            text(c,if(!questSaved)"저장 실패 · 다시 시작해 주세요" else if(celebrating)reward else if(complete)"좋아요! 체험 기록에 저장했어요" else "시나리오 완료 시 퀘스트에 반영",720f,228f,14f,if(!questSaved)Color.RED else if(celebrating)purple else muted)
            text(c,rewardStatus.take(28),720f,253f,12f,muted)
            card(c,20f,494f,308f,170f)
            text(c,if(parking)"주차 코치 · 가상 연습장" else "경기대 후문 주변",39f,528f,21f,ink,true)
            text(c,if(complete)"연습 완료" else "남은 거리 ${(end-distance).roundToInt()}m",39f,559f,20f)
            text(c,if(parking)"전진 3 / 후진 2km/h · 가상 차량" else if(source==ScenarioSpeed.VEHICLE)"SDK 속도 · 위치는 시나리오" else "자동 재생 · 직진 20 / 회전 12km/h",39f,586f,13.5f,muted)
            p.style=Paint.Style.FILL;p.color=Color.rgb(231,231,242);c.drawRoundRect(39f,594f,307f,598f,2f,2f,p)
            p.color=purple;c.drawRoundRect(39f,594f,39f+268f*progress.coerceIn(0f,1f),598f,2f,2f,p)
            card(c,377f,549f,603f,66f)
            text(c,cue,397f,577f,if(cue.length>31)17f else 19f,ink,true)
            text(c,if(parking || guidance.checklist.startsWith("핸들 따라하기"))guidance.checklist else "현재 $lane / 3차로 · ${if(laneDone || lesson==RoadLesson.TURN)"목표 차로 유지" else "오른쪽 1개 차로 남음"}",397f,601f,15f,purple)
            card(c,880f,402f,100f,105f);text(c,if(speedKnown)speed.roundToInt().toString() else "—",900f,449f,36f,ink,true);text(c,"km/h",904f,481f,17f,muted)
            if(celebrating) {p.style=Paint.Style.STROKE;p.strokeWidth=5f;p.color=Color.rgb(101,214,176);c.drawRoundRect(5f,5f,995f,695f,20f,20f,p);p.style=Paint.Style.FILL}
            c.restore()
        }
        private fun courseTurnEnd()=87.0
    }
}
