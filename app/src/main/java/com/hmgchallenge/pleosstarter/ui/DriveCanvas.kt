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
        text(c,if(parking)"가상 연습장 · 천천히 정렬" else if(s.lesson==RoadLesson.LANE)"2차로 → 3차로" else "창룡대로 · 우회전",185f,151f,27f)
        box(c,58f,225f,300f,68f,Color.WHITE,16f)
        text(c,if(parking)"${s.parkingGear}단 · 후진·정렬" else if(s.laneDone || s.lesson==RoadLesson.TURN)"3차로 유지" else "오른쪽 1개 차로 이동",82f,237f,26f)

        text(c,"깜빡이 입력 · "+(if(session.indicatorOverride==null)"SDK" else "시연")+" · "+when(s.indicator){Indicator.RIGHT->"오른쪽 ON";Indicator.LEFT->"왼쪽 ON";Indicator.OFF->"OFF";else->"미확인"},58f,595f,24f,Palette.ink,true)
        if(coachEnabled) {
            if(!parking && s.started && !s.complete && (s.turning || s.distance>=s.course.turnDistance-30)) {
                box(c,510f,310f,775f,165f,Color.rgb(255,249,232),32f)
                text(c,"!",540f,323f,94f,Palette.ink,true)
                assets.mascot(c,RectF(608f,328f,728f,447f))
                text(c,"우회전하기 전에!",755f,331f,31f,Palette.ink,true)
                text(c,"보행자를 확인하세요",755f,386f,35f,Palette.ink,true)
            }
            box(c,1120f,575f,535f,450f,Color.argb(244,255,255,255),32f)
            text(c,session.learning().label,1152f,600f,27f,Palette.purple,true)
            text(c,"시연 핸들 · 실제 조향량 아님",1152f,642f,21f,Color.rgb(107,112,135))
            wheel.draw(c,1378f,774f,280f,g.steering.toDouble())
            text(c,BeginnerScenarioCoach.steeringPosition(s.steeringTarget),1152f,883f,22f,Palette.purple,true)
            wrappedText(c,g.headline,1152f,910f,475f,29f,Palette.ink,true)
        }
        box(c,1430f,460f,225f,85f,Color.WHITE,42f)
        assets.mascot(c,RectF(1440f,469f,1510f,538f))
        box(c,45f,760f,525f,262f,Color.WHITE,30f)
        text(c,"${(s.endDistance-s.distance).coerceAtLeast(0.0).roundToInt()}m 남음",76f,790f,38f,Palette.ink,true)
        text(c,if(s.source==ScenarioSpeed.VEHICLE)"실제 SDK 속도 · 가상 경로" else "자동 시연 · ${(s.speedMps*3.6).roundToInt()}km/h",76f,852f,26f)
        text(c,if(parking)"주차칸에 나란하게 맞추기" else "경기대 후문 → 창룡대로",76f,900f,25f)
        if(!s.started)text(c,"출발을 누르면 자동으로 움직여요",610f,675f,29f,Palette.ink,true)
        box(c,610f,905f,465f,120f,Palette.purple,24f)
        val earned=session.quest.evidence.count {it.tripId==session.tripId}
        val target=if(s.lesson==RoadLesson.COMBINED)2 else 1
        text(c,"QUEST  $earned / $target",640f,919f,25f,Color.WHITE,true)
        text(c,if(parking)"후진 주차 마무리" else if(s.laneDone || s.lesson==RoadLesson.TURN)"조심히 우회전하기" else "한 차로씩 이동하기",640f,964f,30f,Color.WHITE,true)
        if(android.os.SystemClock.elapsedRealtime()<messageUntil) {
            box(c,575f,500f,1020f,120f,Palette.lime,35f);wrappedText(c,message,610f,522f,945f,29f,Palette.ink,true)
        }
        text(c,"DEMO · 경로·차로·핸들은 시연 값 | © OpenStreetMap contributors · OSRM",45f,1040f,22f,Color.rgb(68,74,95))
        session.error?.let {box(c,400f,500f,900f,135f,Color.rgb(255,231,217));text(c,"저장 실패 · 재개를 눌러 다시 시도해 주세요",430f,535f,31f)}
    }
}
