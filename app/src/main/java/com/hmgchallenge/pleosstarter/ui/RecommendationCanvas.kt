package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.hmgchallenge.pleosstarter.analysis.*

class RecommendationCanvas(context: Context,assets: ProductIllustrations): QuestPageCanvas(context,assets) {
    var result: RecommendationResult?=null
    lateinit var actionLabel: (Recommendation) -> String
    var sourceLabel="Pleos 차량 주행 기록 기준"

    override fun drawScene(c: Canvas) {
        val r=result
        page(c,"맞춤 추천 퀘스트","$sourceLabel · 최근 ${r?.tripCount ?: 0}번 기록의 점수와 메모로 골랐어요")
        box(c,65f,338f,523f,560f,Color.WHITE,40f)
        text(c,"최근 평균 점수",100f,360f,35f,Color.DKGRAY,true)
        DrivingSkill.values().forEachIndexed {i,skill->
            val y=450f+i*110f
            val score=r?.averages?.get(skill)
            text(c,skill.title,100f,y-4,30f,ink,true)
            val value=score?.let {"${it}점"} ?: "측정 전"
            paint.textAlign=android.graphics.Paint.Align.RIGHT
            text(c,value,547f,y-2,26f,blue)
            paint.textAlign=android.graphics.Paint.Align.LEFT
            box(c,100f,y+48,453f,14f,0xffdce0f1.toInt(),7f)
            if(score!=null && score>0)box(c,100f,y+48,453f*score/100f,14f,blue,7f)
        }
        val cards=r?.recommendations.orEmpty()
        if(cards.isEmpty()) {
            box(c,623f,338f,1020f,560f,Color.WHITE,40f)
            image(c,"quest_halo",977f,358f,312f,312f)
            image(c,"quest_coach",1025f,376f,215f,340f,true)
            paint.textAlign=android.graphics.Paint.Align.CENTER
            text(c,if((r?.tripCount ?: 0)==0)"주행 중 어렵거나 힘들었던 상황이 있으셨나요?" else "최근 기록에서 반복되는 어려움이 없어요",1133f,726f,25f,muted)
            paint.textAlign=android.graphics.Paint.Align.LEFT
            val greeting="“ 차근님"
            val promise="에게 필요한 연습을 골라 드릴게요! ”"
            paint.typeface=resources.getFont(com.hmgchallenge.pleosstarter.R.font.asta_sans_bold)
            paint.textSize=35f
            val start=1133f-paint.measureText(greeting+promise)/2
            val prefixWidth=paint.measureText(greeting)
            text(c,greeting,start,775f,35f,blue,true)
            text(c,promise,start+prefixWidth,775f,35f,Color.BLACK,true)
            image(c,"quest_underline",853f,819f,560f,10f)
        } else {
            val h=cardHeight(cards.size)
            cards.forEachIndexed {i,q->
                val y=cardTop(i,cards.size)
                box(c,667f,y,963f,h,if(i==0)Color.WHITE else 0xeaffffff.toInt(),40f)
                text(c,q.title+if(q.done)"  ✓" else "",712f,y+19,40f,blue,true)
                val tag=q.kind.label
                box(c,712f,y+83,226f,42f,0xff2059ce.toInt(),21f)
                image(c,"quest_pin",725f,y+90,28f,28f)
                text(c,tag,760f,y+87,19f,Color.WHITE,true)
                wrappedText(c,q.goal,959f,y+80,575f,24f,Color.DKGRAY,false)
                paint.textAlign=android.graphics.Paint.Align.RIGHT
                text(c,actionLabel(q),1553f,y+24,24f,blue,true)
                paint.textAlign=android.graphics.Paint.Align.LEFT
                image(c,"quest_next",1570f,y+h/2-17,20f,35f)
                val reason=if(q.target>0)"${q.progress}/${q.target} · ${q.reason}" else q.reason
                text(c,reason.take(if(q.target>0)40 else 48),712f,y+h-41,19f,muted)
                if(q.target>0) {
                    box(c,1360f,y+h-26,200f,9f,0xffdce0f1.toInt(),5f)
                    if(q.progress>0)box(c,1360f,y+h-26,200f*q.progress.coerceAtMost(q.target)/q.target,9f,blue,5f)
                }
            }
        }
        text(c,"최근 주행의 점수와 어려움 메모를 함께 살펴서 추천해요",65f,934f,23f,ink)
        text(c,"측정 전인 항목은 평균에서 제외해요",65f,977f,22f,muted)
    }
    companion object {
        fun cardHeight(count: Int)=if(count>=3)174f else 200f
        fun cardTop(index: Int,count: Int)=338f+index*(cardHeight(count)+if(count>=3)18f else 30f)
    }
}
