package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import com.hmgchallenge.pleosstarter.R
import kotlin.math.*
import com.caverock.androidsvg.SVG

/** Native adaptation of Figma 73:10511. Static icons are original Figma exports. */
class SimulatorNavigationView(context: Context): FrameLayout(context) {
    private val reference=ReferenceRouteClient.parse(context.assets.open("figma_navigation/kgu-route.json").bufferedReader().use {it.readText()})
    private val course=NavigationSimulationRoute(reference.points)
    private val map=CoachMapView(context,"https://tile.openstreetmap.org/{z}/{x}/{y}.png",true)
    private val hud=NavigationHud(context)
    private val positions=mutableMapOf<View,RectF>()
    private var model: DrivingSimulator?=null
    var onAutomatic: ()->Unit={}
    var onPausePlayback: ()->Unit={}
    var onControls: ()->Unit={}
    var onVoice: ()->Unit={}
    var onNote: ()->Unit={}
    var onFeedback: ()->Unit={}
    var onRestart: ()->Unit={}
    var onQuests: ()->Unit={}
    private val autoButton: Button
    private val pauseButton: Button
    private val voiceButton: Button
    init {
        tag="navigation_panel";setBackgroundColor(Color.WHITE)
        clipToOutline=true;outlineProvider=object: ViewOutlineProvider(){override fun getOutline(v: View,o: Outline){o.setRoundRect(0,0,v.width,v.height,24f)}}
        addView(map,LayoutParams(-1,-1));addView(hud,LayoutParams(-1,-1))
        autoButton=action("자동 시연 시작","nav_auto",40f,967f,355f,76f){onAutomatic()}
        pauseButton=action("일시정지","nav_pause",410f,967f,230f,76f){onPausePlayback()}
        action("운전 조작","nav_controls",655f,967f,235f,76f){onControls()}
        voiceButton=action("음성 켜기","nav_voice",905f,967f,220f,76f){onVoice()}
        action("이 순간 기억","nav_note",1140f,967f,265f,76f){onNote()}
        action("퀘스트","nav_quests",1420f,967f,230f,76f){onQuests()}
        action("","nav_restart",40f,1070f,105f,104f){onRestart()}.apply {contentDescription="주행 연습 처음부터";background=null}
        action("","nav_menu",1530f,1070f,140f,104f){onFeedback()}.apply {contentDescription="주행 피드백 열기";background=null}
        map.onMapStatus={hud.mapStatus=it;hud.invalidate()}
    }
    private fun action(title: String,key: String,x: Float,y: Float,w: Float,h: Float,action: ()->Unit)=Button(context).apply {
        text=title;tag=key;isAllCaps=false;textSize=17f;minHeight=0;minimumHeight=0;setPadding(2,0,2,0)
        typeface=resources.getFont(R.font.asta_sans_bold);setTextColor(Color.WHITE)
        background=GradientDrawable().apply {setColor(Color.rgb(8,113,64));cornerRadius=18f}
        setOnClickListener {action()};addView(this);positions[this]=RectF(x,y,x+w,y+h)
    }
    override fun onMeasure(w: Int,h: Int) {
        super.onMeasure(w,h)
        positions.forEach {(v,r)->v.measure(MeasureSpec.makeMeasureSpec((r.width()*measuredWidth/1700).roundToInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((r.height()*measuredHeight/1184).roundToInt(),MeasureSpec.EXACTLY))}
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
        map.layout(0,0,width,height);hud.layout(0,0,width,height)
        positions.forEach {(v,p)->v.layout((p.left*width/1700).roundToInt(),(p.top*height/1184).roundToInt(),(p.right*width/1700).roundToInt(),(p.bottom*height/1184).roundToInt())}
    }
    fun render(m: DrivingSimulator,paused: Boolean,automatic: Boolean,voice: Boolean) {
        model=m
        val frame=course.frame(m)
        map.setNavigationFrame(reference,frame)
        hud.model=m;hud.frame=frame;hud.paused=paused;hud.automatic=automatic
        hud.contentDescription="주행 코치. ${m.instruction()}. 현재 ${m.currentLane}차로, 목표 3차로. ${m.lanesRemaining}개 남음."
        hud.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES;hud.invalidate()
        autoButton.text=if(automatic)"자동 시연 멈추기" else "자동 시연 시작"
        pauseButton.text=if(paused)"계속하기" else "일시정지"
        voiceButton.text=if(voice)"음성 끄기" else "음성 켜기"
    }
    fun diagnostics(): Map<String,String> {
        val m=model ?: return emptyMap()
        val frame=course.frame(m)
        return map.diagnostics()+mapOf("routeDistance" to frame.distance.toString(),"turnRemaining" to frame.turnRemaining.toString(),"currentLane" to m.currentLane.toString(),"lanesRemaining" to m.lanesRemaining.toString(),"heading" to frame.bearing.toString(),"cue" to m.instruction())
    }
    private class NavigationHud(context: Context): View(context) {
        var model: DrivingSimulator?=null
        var frame: NavigationSimulationFrame?=null
        var paused=false
        var automatic=false
        var mapStatus="지도 불러오는 중"
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        private val bold=resources.getFont(R.font.asta_sans_bold)
        private val medium=resources.getFont(R.font.asta_sans_medium)
        private val icons=mapOf("right" to "0b65a.svg","left" to "a9356.svg","straight" to "f63dd.svg","straight-active" to "straight-active.svg","lane-right" to "82a06.svg","menu" to "4513b.svg","restart" to "87735.svg","place" to "99f3b.svg").mapValues {(_,file)->
            SVG.getFromAsset(context.assets,"figma_navigation/$file").renderToPicture()
        }
        private fun icon(c: Canvas,name: String,x: Float,y: Float,w: Float,h: Float,alpha: Int=255) {
            val art=icons.getValue(name)
            c.saveLayerAlpha(RectF(x,y,x+w,y+h),alpha)
            c.drawPicture(art,RectF(x,y,x+w,y+h));c.restore()
        }
        private fun text(c: Canvas,value: String,x: Float,y: Float,size: Float,color: Int=Color.WHITE,strong: Boolean=true) {
            p.color=color;p.textSize=size;p.typeface=if(strong)bold else medium;p.style=Paint.Style.FILL
            c.drawText(value,x,y,p)
        }
        private fun round(c: Canvas,x: Float,y: Float,w: Float,h: Float,color: Int,r: Float=30f) {
            p.color=color;p.style=Paint.Style.FILL;c.drawRoundRect(x,y,x+w,y+h,r,r,p)
        }
        override fun onDraw(c: Canvas) {
            val m=model ?: return;val f=frame ?: return
            c.save();c.scale(width/1700f,height/1184f)
            val green=Color.rgb(8,113,64);val ink=Color.rgb(27,34,42)
            val turning=m.stage in setOf(SimStage.TURN,SimStage.TURN_ALIGN)
            val finished=m.stage==SimStage.COMPLETE
            val passed=m.stage.ordinal>SimStage.TURN_ALIGN.ordinal
            // Figma's two stacked maneuver cards, with values supplied by the simulation.
            round(c,40f,248f,350f,170f,Color.rgb(8,87,67))
            icon(c,"straight-active",82f,330f,36f,55f)
            text(c,if(passed)"연습 완료" else "이후 직진",144f,375f,45f)
            round(c,40f,40f,520f,265f,green,35f)
            icon(c,if(passed)"straight-active" else "right",80f,70f,130f,130f)
            text(c,if(passed)"도착" else if(turning)"지금" else "${f.turnRemaining.roundToInt()}m",250f,160f,88f)
            text(c,if(passed)"연습 마무리" else "우회전",250f,218f,40f)
            text(c,"대학로",80f,280f,55f)
            // Contextual coach bubble leaves the middle of the map visible.
            round(c,610f,40f,680f,200f,Color.argb(239,255,255,255))
            val headline=when {
                m.stage==SimStage.PREPARE->"우회전 주행 연습"
                paused->"일시정지 · 계속하기를 눌러요"
                finished->"좋아요. 우회전 연습 완료!"
                m.stage==SimStage.TURN_CHECK->if(!m.green)"정지선 앞에서 신호 대기" else if(m.pedestrian)"횡단보도 보행자 확인" else "주변 확인 후 우회전"
                turning->"3차로에서 천천히 우회전"
                m.lanesRemaining>0->"오른쪽 ${m.lanesRemaining}개 차로 이동"
                else->"3차로 유지 · 우회전 준비"
            }
            text(c,headline,638f,104f,38f,ink)
            val detail=when {
                m.stage==SimStage.PREPARE->"자동 시연 또는 운전 조작을 눌러 시작해요."
                paused->"차량과 경로 이동을 잠시 멈췄어요."
                finished->"주행 피드백과 퀘스트에서 돌아봐요."
                m.stage==SimStage.LANE_PREPARE && m.signal!=m.expectedSignal->"오른쪽 깜빡이를 켜고 주변을 확인하세요."
                m.stage==SimStage.LANE_PREPARE->"미러·사각지대를 확인하고 한 차로씩 이동해요."
                m.stage in setOf(SimStage.LANE_OUT,SimStage.LANE_RETURN)->"${m.currentLane} → ${m.currentLane+1}차로 · 경로를 따라 정렬해요."
                m.stage==SimStage.TURN_CHECK->if(m.pedestrian)"횡단보도가 비워질 때까지 기다려요." else "방향지시등·신호·주변을 확인해요."
                turning->"회전 후 차로 중심에 맞춰 핸들을 풀어요."
                else->"${m.road.turnLane}차로에서 정지선 앞 정차를 준비해요."
            }
            text(c,detail,638f,162f,29f,Color.rgb(65,78,84),false)
            text(c,if(automatic)"자동 시연 중 · 지도 모형 이동 / 차량 입력 P·0 유지" else "시뮬레이션 · 가상 위치 / 가상 3차로 연습",638f,208f,24f,green,false)
            // Target speed and actual model speed occupy the design's speed widget.
            p.style=Paint.Style.FILL;p.color=Color.WHITE;c.drawCircle(1449f,140f,92f,p)
            p.style=Paint.Style.STROKE;p.color=Color.rgb(216,67,52);p.strokeWidth=13f;c.drawCircle(1449f,140f,86f,p);p.style=Paint.Style.FILL
            text(c,"10",1391f,172f,85f,Color.BLACK)
            round(c,1349f,210f,200f,70f,Color.rgb(216,67,52),20f);text(c,"연습 속도",1371f,258f,35f)
            round(c,1349f,279f,200f,70f,Color.rgb(252,192,41),20f);text(c,"저속 주행",1380f,326f,32f,ink)
            text(c,(m.speed*3.6).roundToInt().toString(),1380f,440f,90f,Color.BLACK)
            text(c,"km/h",1400f,482f,29f,ink,false)
            // Right-side rail represents lesson progress; it is not live traffic data.
            round(c,1627f,81f,22f,850f,Color.WHITE,12f)
            round(c,1632f,86f,12f,840f,Color.rgb(217,217,217),6f)
            val progress=(m.stage.ordinal.toFloat()/SimStage.COMPLETE.ordinal).coerceIn(0f,1f)
            round(c,1632f,86f,12f,840f*progress,Color.rgb(67,102,245),6f)
            text(c,"진행",1585f,955f,24f,ink,false)
            // Dynamic lane count: icons match the lesson, never claim a real-world lane reading.
            round(c,668f,846f,393f,76f,Color.argb(220,28,28,28),20f)
            for(lane in 1..3) {
                val x=700f+(lane-1)*115
                val active=lane==m.currentLane
                if(active)round(c,x-9,850f,75f,67f,Color.rgb(67,102,245),12f)
                icon(c,if(lane==3)"lane-right" else if(lane==1)"left" else if(active)"straight-active" else "straight",x,860f,if(lane==2)32f else 45f,49f,if(active || lane==m.targetLane)255 else 90)
            }
            val laneLine=if(passed)"좋아요. 회전 후 차로 정렬 완료" else "현재 ${m.currentLane}차로 → 목표 3차로 · ${m.lanesRemaining}개 남음"
            round(c,620f,924f,600f,34f,Color.argb(225,255,255,255),10f)
            text(c,laneLine,640f,951f,28f,ink)
            // Bottom destination strip from Figma, with honest simulation distance/time.
            round(c,27f,1060f,1645f,125f,Color.rgb(247,248,250),30f)
            icon(c,"restart",67f,1093f,60f,60f)
            val remain=(192.2-f.distance).coerceAtLeast(0.0).roundToInt()
            text(c,if(finished)"완료" else "약 2분",187f,1141f,60f,Color.BLACK)
            text(c,"${remain}m",460f,1141f,55f,Color.BLACK)
            icon(c,"place",1030f,1098f,50f,50f)
            text(c,"경기대학교 수원캠퍼스 후문",1090f,1138f,30f,Color.BLACK,false)
            icon(c,"menu",1570f,1093f,60f,60f)
            c.restore()
        }
    }
}
