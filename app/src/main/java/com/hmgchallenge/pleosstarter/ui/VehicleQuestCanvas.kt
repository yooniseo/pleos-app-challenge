package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import com.hmgchallenge.pleosstarter.feature.quests.*
import com.hmgchallenge.pleosstarter.session.VehicleQuestController
import com.hmgchallenge.pleosstarter.session.VehicleQuestInput
import java.util.Locale

class VehicleQuestCanvas(context: Context,assets: ProductIllustrations,private val controller: VehicleQuestController): SceneCanvas(context,assets) {
    var input: VehicleQuestInput?=null
    override fun drawScene(c: Canvas) {
        c.drawColor(Color.rgb(197,216,255));assets.hills(c)
        text(c,"실제 차량 신호 퀘스트",72f,70f,59f,Palette.ink,true)
        text(c,if(controller.source=="VEHICLE")"Pleos 입력 전용 · 자동 시연 누적은 ‘종합 기록’에서 확인해요" else "TEST · 가상 입력 검증 기록",76f,174f,27f,Palette.ink)
        QuestCatalog.all.forEachIndexed {i,quest->
            val x=65f+i*825f;val p=controller.board.progressOf(quest.questId)
            box(c,x,330f,760f,340f,Color.WHITE,40f)
            assets.questBadge(c,x+25,345f,if(i==0)2 else 0,true)
            text(c,quest.title,x+305,365f,41f,Palette.ink,true)
            text(c,"Lv.${p.level}  ·  ${p.steps} / 30",x+305,430f,34f,Palette.purple,true)
            box(c,x+300,495f,405f,18f,Color.rgb(220,225,241),9f)
            box(c,x+300,495f,405f*p.fraction,18f,Palette.purple,9f)
            text(c,"성공 ${p.successCount} · 재시도 ${p.failCount} · 보류 ${p.unverifiedCount}",x+300,555f,25f,Palette.ink)
            text(c,if(i==0)"깜빡이 선행 · 조향 후 복귀" else "오른쪽 깜빡이 · 진입 전 브레이크",x+300,607f,23f)
        }
        box(c,65f,710f,1585f,325f,Color.WHITE,30f)
        text(c,controller.error ?: controller.status,98f,740f,29f,if(controller.error==null)Palette.purple else Color.rgb(175,55,44),true)
        val now=SystemClock.elapsedRealtime()
        fun fresh(at: Long)=now-at in 0..3000 && at>0
        val signal=input?.takeIf {fresh(it.signalAt)}?.signal
        val angle=input?.takeIf {fresh(it.angleAt)}?.angle
        val brake=input?.takeIf {fresh(it.brakeAt)}?.brake
        text(c,"깜빡이 ${when(signal){Signal.RIGHT->"오른쪽";Signal.LEFT->"왼쪽";Signal.OFF->"OFF";else->"미확인"}}  ·  조향각(SDK) ${angle?.let {String.format(Locale.KOREA,"%.1f°",it)} ?: "미수신"}  ·  브레이크 ${when(brake){true->"밟음";false->"안 밟음";null->"미수신"}}",98f,794f,25f)
        val recent=controller.board.attempts.takeLast(2).reversed()
        text(c,recent.joinToString("\n") {attempt->
            "${QuestCatalog.find(attempt.questId)?.title} · ${if(attempt.success)"신호 조건 충족" else attempt.result.message}"
        }.ifEmpty {"주행 중 수신한 조향·깜빡이 조건이 여기에 기록돼요"},98f,858f,26f)
        text(c,"임시 각도 기준 적용 · 실제 차로 위치·보행자 확인은 운전자가 직접 해요",98f,982f,22f)
    }
}
