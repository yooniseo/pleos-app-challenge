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
    var message=""
    var messageUntil=0L
    override fun drawScene(c: Canvas) {
        val s=session.scenario
        val parking=s.lesson==RoadLesson.PARKING
        val g=BeginnerScenarioCoach.guidance(s,session.learning().style)
        box(c,45f,55f,540f,170f,Color.WHITE,30f)
        if(parking || s.lesson==RoadLesson.LANE)text(c,if(parking)"P" else "➜",80f,76f,80f,Palette.purple,true)
        else assets.turnArrow(c,80f,86f,80f,111f)
        text(c,if(parking)"후진 주차" else if(s.lesson==RoadLesson.LANE)"오른쪽 한 차로" else "${s.frame().turnRemaining.roundToInt()}m",185f,76f,48f,Palette.ink,true)
        text(c,if(parking)"가상 연습장 · 천천히 정렬" else if(s.lesson==RoadLesson.LANE)"2차로 → 3차로" else if(s.lesson==RoadLesson.COMBINED)"1 → 2 → 3차로 · 우회전 · 주차" else "창룡대로 · 우회전",185f,151f,27f)
        box(c,58f,225f,480f,68f,Color.WHITE,16f)
        text(c,if(parking)"${s.parkingGear}단 · 후진·정렬" else if(s.laneDone || s.lesson==RoadLesson.TURN)"3차로 유지" else "${s.currentLane}차로 → ${s.nextLaneChange?.to ?: 3}차로 · 한 차로씩",82f,237f,26f)

        text(c,"깜빡이 입력 · "+(if(session.indicatorOverride==null)"SDK" else "시연")+" · "+when(s.indicator){Indicator.RIGHT->"오른쪽 ON";Indicator.LEFT->"왼쪽 ON";Indicator.OFF->"OFF";else->"미확인"},58f,595f,24f,Palette.ink,true)
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
        assets.figma?.draw(c,if(coachEnabled)"coach_on" else "coach_off",RectF(58f,650f,242f,743f))
        box(c,45f,760f,525f,262f,Color.WHITE,30f)
        text(c,"${(s.endDistance-s.distance).coerceAtLeast(0.0).roundToInt()}m 남음",76f,790f,38f,Palette.ink,true)
        text(c,if(s.source==ScenarioSpeed.VEHICLE)"실제 SDK 속도 · 가상 경로" else "자동 시연 · ${(s.speedMps*3.6).roundToInt()}km/h",76f,852f,26f)
        text(c,if(parking)"주차칸에 나란하게 맞추기" else "창룡대로 → 대학로 · 경기대 방향",76f,900f,25f)
        if(!s.started)text(c,"출발을 누르면 자동으로 움직여요",610f,675f,29f,Palette.ink,true)
        box(c,1095f,905f,560f,170f,Color.rgb(75,143,248),30f)
        assets.figma?.draw(c,"quest_flag",RectF(1122f,925f,1252f,1055f))
        assets.figma?.draw(c,"chevron",RectF(1602f,973f,1622f,1008f))
        val earned=session.quest.evidence.filter {it.tripId==session.tripId}.map {it.skill}.distinct().size
        val target=if(session.selectedLesson==RoadLesson.COMBINED)6 else 1
        text(c,"Quest  $earned/$target",1292f,923f,20f,Color.WHITE)
        text(c,if(parking)"주차선에\n나란히 정렬하기" else if(s.laneDone || s.lesson==RoadLesson.TURN)"경로따라\n우회전하기" else "${s.nextLaneChange?.to ?: 3}차로로\n차선변경하기",1292f,955f,42f,Color.WHITE,true)
        if(android.os.SystemClock.elapsedRealtime()<messageUntil) {
            box(c,600f,180f,480f,140f,Palette.lime,30f);wrappedText(c,message,625f,200f,430f,28f,Palette.ink,true)
        }
        text(c,"DEMO · 경로·차로·핸들은 시연 값 | © OpenStreetMap contributors · OSRM",45f,1040f,22f,Color.rgb(68,74,95))
        session.error?.let {box(c,400f,500f,900f,135f,Color.rgb(255,231,217));text(c,"저장 실패 · 재개를 눌러 다시 시도해 주세요",430f,535f,31f)}
    }
}
