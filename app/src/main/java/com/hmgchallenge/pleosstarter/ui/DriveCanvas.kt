package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.*
import com.hmgchallenge.pleosstarter.driving.*
import com.hmgchallenge.pleosstarter.session.ReplaySessionController
import kotlin.math.*

/** Only the application window is rendered here. OEM speed/status/climate remain system owned. */
class DriveCanvas(context: Context,assets: ProductIllustrations,private val session: ReplaySessionController): SceneCanvas(context,assets) {
    private val wheel=SteeringWheelRenderer()
    var coachEnabled=true
    var compactQuestBanner=false
    var tilted=true
    var toolsExpanded=false
    var message=""
    var messageUntil=0L
    override fun drawScene(c: Canvas) {
        val s=session.scenario
        val parking=s.lesson==RoadLesson.PARKING
        val g=BeginnerScenarioCoach.guidance(s,session.learning().style)
        val hud=NavigationHudModel.from(s,session.totalTripMetres,session.travelledMetres,session.continuingToParking)
        navigation(c,hud)
        card(c,1591f,59f,68f,68f,Color.WHITE,20f)
        text(c,if(tilted)"2D" else "3D",1608f,65f,20f,Color.BLACK,true)
        assets.figma?.draw(c,"map_pointer",RectF(1612f,97f,1638f,119f))
        card(c,1591f,157f,68f,68f,Color.WHITE,20f)
        assets.figma?.draw(c,"map_settings",RectF(1610f,176f,1640f,206f))
        if(toolsExpanded) {
            box(c,53f,385f,543f,260f,Color.WHITE)
            text(c,"깜빡이 비교 · 시연 도구",78f,400f,26f,Palette.ink,true)
            text(c,"입력: ${if(session.indicatorOverride==null)"SDK" else "시연"} · ${s.indicator}",78f,594f,22f)
        }
        if(coachEnabled) {
            if(!parking && s.started && !s.complete && (s.turning || s.distance>=s.course.turnDistance-130)) {
                val imminent=s.frame().turnRemaining<=30
                val left=if(imminent)980f else 955f
                val width=if(imminent)675f else 700f
                val scale=width/if(imminent)675f else 804f
                c.save();c.translate(left,705f);c.scale(scale,scale)
                box(c,0f,0f,if(imminent)675f else 804f,189f,Color.WHITE,30f)
                if(imminent)assets.figma?.draw(c,"caution",RectF(69f,22f,139f,167f))
                else assets.mascot(c,RectF(45f,20f,195f,170f))
                val tx=if(imminent)169f else 255f
                text(c,"우회전 하기 전!",tx,36f,40f,Color.BLACK)
                text(c,"보행자를 확인하세요",tx,84f,55f,Color.BLACK,true)
                c.restore()
            }
            box(c,1120f,270f,535f,420f,Color.argb(244,255,255,255),32f)
            text(c,session.learning().label,1152f,286f,27f,Palette.purple,true)
            wrappedText(c,g.headline,1152f,327f,475f,25f,Palette.ink,true)
            text(c,"시연 핸들 · 실제 조향량 아님",1152f,400f,19f,Color.rgb(107,112,135))
            wheel.draw(c,1378f,502f,200f,g.steering.toDouble())
            text(c,BeginnerScenarioCoach.steeringPosition(s.steeringTarget),1152f,596f,20f,Palette.purple,true)
        }
        if(coachEnabled)assets.figma?.draw(c,"coach_on",RectF(58f,650f,242f,743f))
        else {
            box(c,58f,660f,184f,75f,Color.rgb(217,217,217),38f)
            text(c,"OFF",73f,676f,34f,Color.BLACK)
            c.save();c.clipPath(Path().apply {addOval(RectF(162f,665f,228f,731f),Path.Direction.CW)})
            assets.figma?.draw(c,"coach_off_avatar",RectF(162f,665f,228f,731f));c.restore()
        }
        arrival(c,hud)
        val earned=session.quest.evidence.filter {it.tripId==session.tripId}.map {it.skill}.distinct().size
        val target=if(session.selectedLesson==RoadLesson.COMBINED)6 else 1
        if(compactQuestBanner) {
            box(c,1129f,928f,526f,147f,Color.rgb(217,217,217),80f)
            assets.figma?.draw(c,"quest_flag_outline",RectF(1167f,961f,1211f,1024f))
            assets.figma?.draw(c,"quest_chevron_dark",RectF(1615f,980f,1635f,1022f))
            text(c,"QUEST $earned/$target",1230f,944f,20f,Color.BLACK)
            text(c,if(s.complete)"이번 연습 완료" else if(parking)"주차 마무리하기" else if(s.laneDone || s.lesson==RoadLesson.TURN)"조심히 우회전하기" else "한 차로씩 이동하기",1230f,979f,31f,Color.BLACK,true)
        }else {
            box(c,1095f,905f,560f,170f,Color.rgb(75,143,248),30f)
            assets.figma?.draw(c,"quest_flag",RectF(1122f,925f,1252f,1055f))
            assets.figma?.draw(c,"chevron",RectF(1602f,973f,1622f,1008f))
            text(c,"Quest  $earned/$target",1292f,923f,20f,Color.WHITE)
            text(c,if(s.complete)"이번 연습\n잘 마쳤어요" else if(parking)"주차선에\n나란히 정렬하기" else if(s.laneDone || s.lesson==RoadLesson.TURN)"경로따라\n우회전하기" else "${s.nextLaneChange?.to ?: 3}차로로\n차선변경하기",1292f,955f,42f,Color.WHITE,true)
        }
        if(android.os.SystemClock.elapsedRealtime()<messageUntil) {
            box(c,600f,180f,480f,140f,Palette.lime,30f);wrappedText(c,message,625f,200f,430f,28f,Palette.ink,true)
        }
        text(c,"DEMO · 경로·차로·핸들은 시연 값\n© OpenStreetMap contributors · OSRM",635f,1030f,19f,Color.rgb(68,74,95))
        session.error?.let {box(c,400f,500f,900f,135f,Color.rgb(255,231,217));text(c,"저장 실패 · 재개를 눌러 다시 시도해 주세요",430f,535f,31f)}
    }
    private fun card(c: Canvas,x: Float,y: Float,w: Float,h: Float,color: Int,r: Float) {
        paint.setShadowLayer(10f,0f,3f,Color.argb(25,0,0,0))
        box(c,x,y,w,h,color,r);paint.clearShadowLayer()
    }
    private fun navigation(c: Canvas,hud: NavigationHudModel) {
        val cue=hud.cues.first()
        card(c,53f,59f,543f,186f,Color.WHITE,30f)
        maneuver(c,cue,RectF(96f,85f,170f,205f),false)
        text(c,cue.metres?.let {"${it.coerceAtLeast(0.0).roundToInt()}m"} ?: "다음",228f,79f,65f,Color.BLACK,true)
        text(c,cue.label,228f,169f,27f,Color.rgb(147,147,147))
        assets.figma?.draw(c,"nav_expand",RectF(531f,130f,565f,164f))
        hud.cues.getOrNull(1)?.let {next->
            card(c,53f,245f,354f,105f,Color.WHITE,27f)
            maneuver(c,next,RectF(119f,258f,170f,332f),true)
            text(c,next.metres?.let {"${it.roundToInt()}m"} ?: "주차",211f,264f,46f,Color.BLACK,true)
        }
    }
    private fun maneuver(c: Canvas,cue: HudCue,b: RectF,small: Boolean) {
        when(cue.maneuver) {
            HudManeuver.TURN->assets.figma?.draw(c,if(small)"nav_turn_next" else "nav_turn",b)
            HudManeuver.LANE->assets.figma?.draw(c,"quest_flag_outline",b)
            else->text(c,when(cue.maneuver){HudManeuver.PARKING->"P";HudManeuver.FINISH->"✓";else->"↑"},b.left,b.top,if(small)54f else 83f,if(small)Color.BLACK else Palette.purple,true)
        }
    }
    private fun arrival(c: Canvas,hud: NavigationHudModel) {
        card(c,53f,770f,543f,240f,Color.WHITE,30f)
        assets.figma?.draw(c,"arrival_handle",RectF(265f,785f,385f,790f))
        val eta=hud.remainingSeconds
        val time=when {session.scenario.complete->"연습 완료";!session.scenario.started->"출발 전";session.paused->"일시정지";eta==null->"속도 대기";else->java.text.SimpleDateFormat("h:mm a",java.util.Locale.KOREA).format(java.util.Date(System.currentTimeMillis()+eta*1000L))}
        text(c,time,78f,811f,28f,Color.BLACK,true)
        text(c,"시연 종료 예상 · 경기대 방향",78f,850f,17f,Color.GRAY)
        val distance=if(hud.remainingMetres>=1000)"%.1fkm".format(java.util.Locale.KOREA,hud.remainingMetres/1000) else "${hud.remainingMetres.roundToInt()}m"
        text(c,"${eta?.let {"약 ${ceil(it/60.0).toInt()}분"} ?: "대기"} · $distance",365f,814f,23f,Color.BLACK,true)
        text(c,"시연 진행 ${(hud.progress*100).toInt()}%",420f,853f,18f,Color.GRAY)
        box(c,83f,896f,482f,6f,Color.rgb(220,225,237),3f)
        if(hud.progress>0)box(c,83f,896f,482f*hud.progress,6f,Color.rgb(75,143,248),3f)
        assets.figma?.draw(c,"progress_start",RectF(73f,889f,93f,909f))
        val x=83+482*hud.progress
        assets.figma?.draw(c,"progress_current",RectF(x-10,889f,x+10,909f))
        assets.figma?.draw(c,"arrival_search",RectF(72f,931f,184.5f,991f))
        box(c,220f,931f,356f,60f,Color.rgb(243,243,244),15f)
        text(c,"경로 취소",337f,948f,24f,Color.BLACK,true)
    }
}
