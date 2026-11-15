package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.*
import com.hmgchallenge.pleosstarter.analysis.DrivingSkill
import com.hmgchallenge.pleosstarter.analysis.TripScores
import com.hmgchallenge.pleosstarter.core.data.entity.Trip
import com.hmgchallenge.pleosstarter.core.data.entity.TripReport
import com.hmgchallenge.pleosstarter.quests.QuestState
import org.json.JSONArray
import java.util.Locale

/** Uses persisted trip/report data, including archived trips, rather than Figma's example numbers. */
class CompletionCanvas(context: Context,assets: ProductIllustrations): SceneCanvas(context,assets) {
    var trip: Trip?=null
    var report: TripReport?=null
    var state=QuestState()
    var earned=0
    var completed=0
    var target=0
    var complete=false
    var vehicleRecord=false
    var recordSource="DEMO  |  시연용 가상 기록"
    var scores: TripScores?=null
    private fun short(skill: DrivingSkill)=when(skill){DrivingSkill.SMOOTH_ACCEL->"출발";DrivingSkill.BRAKING->"감속";DrivingSkill.TURN_SIGNAL->"깜빡이";DrivingSkill.TURN_SLOWDOWN->"회전감속"}
    private fun fit(value: String,max: Int)=if(value.length<=max)value else value.take(max-1)+"…"
    override fun drawScene(c: Canvas) {
        if(trip==null) {
            c.drawColor(Color.rgb(239,242,250))
            assets.mascot(c,RectF(720f,190f,980f,448f))
            text(c,"아직 주행 기록이 없어요",495f,500f,58f,Palette.ink,true)
            text(c,"지도에서 연습을 마치면 퀘스트와 피드백이 여기에 모여요",360f,615f,32f)
            text(c,"자동으로 채운 점수나 완료 기록은 없어요",540f,720f,27f)
            return
        }
        assets.completionPanel(c)
        text(c,"DRIVE",65f,46f,112f,Color.WHITE,true)
        text(c,if(complete)"CLEAR!" else "RECORD",63f,149f,if(complete)113f else 90f,Color.rgb(249,217,77),true)
        text(c,"한 번 더 운전할 용기가 생겼어요!",69f,289f,30f,Palette.cream)
        text(c,"지금 등급은",69f,390f,20f,Color.WHITE)
        text(c,if(vehicleRecord)"신호 조건 연습" else state.levelName,67f,430f,if(vehicleRecord)30f else 43f,Color.WHITE,true)
        text(c,"···›",320f,415f,40f,Color.WHITE)
        text(c,"다음 등급은",420f,390f,20f,Color.WHITE)
        text(c,if(vehicleRecord)"차근차근" else if(state.levelName=="STARTER")"DRIVER" else "MASTER",420f,441f,28f,Color.WHITE,true)
        val t=trip
        metric(c,520.67f,"DISTANCE","주행 거리",String.format(Locale.KOREA,"%.3f",t?.distanceKm ?: 0f),"KM")
        metric(c,670.67f,"PLAY TIME","주행 시간",(((t?.endedAt ?: t?.startedAt ?: 0L)-(t?.startedAt ?: 0L))/1000).toString(),"SEC")
        box(c,67f,820.34f,619f,145f,Color.rgb(249,217,77),7f)
        text(c,"QUEST",108f,842f,28f,Color.rgb(125,29,32),true)
        text(c,if(vehicleRecord)"조건 충족 / 시도" else "완료 퀘스트",108f,892f,25f,Color.rgb(25,29,36))
        text(c,"$completed / $target",345f,850f,66f,Color.rgb(25,29,36),true)
        text(c,recordSource,69f,1003f,26f,Palette.cream)
        text(c,"© OpenStreetMap contributors · OSRM",69f,1054f,18f,Palette.cream)
        box(c,790f,400f,810f,540f,Color.argb(243,255,255,255),36f)
        text(c,"차근차근, 오늘의 성장",835f,438f,44f,Palette.ink,true)
        val good=report?.goodPoints?.let(::JSONArray)
        val nextAll=report?.practicePoints?.let(::JSONArray)
        val s=scores
        if(vehicleRecord && s!=null) {
            // 차량 주행 기록: 분석 요약 · 기술별 점수 · 잘한 점 2개 · 연습할 점 2개
            wrappedText(c,report?.headline.orEmpty(),835f,505f,740f,29f,Palette.purple,true)
            text(c,DrivingSkill.values().joinToString("  ·  ") {"${short(it)} ${s.scores[it]?.toString() ?: "—"}"}+if(s.lowConfidence)"  (짧은 주행·참고용)" else "",835f,592f,23f,Palette.ink,true)
            text(c,if(good==null)"" else (0 until minOf(2,good.length())).joinToString("\n") {"✓ "+fit(good.getString(it),30)},835f,640f,26f)
            text(c,if(nextAll==null)"" else (0 until minOf(2,nextAll.length())).joinToString("\n") {"• "+fit(nextAll.getString(it),31)},835f,735f,25f,Color.rgb(125,29,32))
        }else {
            val signal=s?.scores?.get(DrivingSkill.TURN_SIGNAL)
            text(c,if(signal!=null)"시연 깜빡이 ${signal}점 · +${earned} P" else if(vehicleRecord)"신호 조건 충족 $completed / $target" else "이번 연습 +${earned} P",835f,520f,34f,Palette.purple,true)
            val changes=s?.laneSignals.orEmpty()
            if(changes.isNotEmpty()) {
                val results=changes.mapIndexed {index,value->
                    val outcome=when(value) {"RIGHT"->"오른쪽 ON · +1회";"LEFT"->"왼쪽 ON · +0회";else->"OFF · +0회"}
                    "${index+1}차 변경 $outcome"
                }
                text(c,results.joinToString("   /   "),835f,574f,24f,Palette.ink,true)
            }
            text(c,if(good==null)"아직 완료한 연습이 없어요" else (0 until minOf(6,good.length())).joinToString("\n") {"✓ "+good.getString(it)},835f,if(changes.isEmpty())580f else 620f,23f)
            text(c,nextAll?.optString(0).orEmpty().take(35),835f,805f,27f)
        }

        text(c,if(vehicleRecord)"신호 조건 기록이며 실제 차로 이동 검증은 별도예요" else "시연 기록은 실제 운전 실력 평가와 구분해요",835f,865f,24f,Color.rgb(101,106,128))
    }
    private fun metric(c: Canvas,y: Float,en: String,ko: String,value: String,unit: String) {
        box(c,67f,y,467f,104f,Color.rgb(255,246,222),5f)
        text(c,en,97f,y+16,20f,Color.rgb(125,29,32),true);text(c,ko,97f,y+52,20f,Color.rgb(25,29,36))
        text(c,value,265f,y+18,46f,Color.rgb(25,29,36),true);text(c,unit,469f,y+57,20f,Color.rgb(25,29,36))
    }
}

