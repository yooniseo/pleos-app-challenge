package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.hmgchallenge.pleosstarter.analysis.DrivingSkill
import com.hmgchallenge.pleosstarter.analysis.RecommendationKind
import com.hmgchallenge.pleosstarter.analysis.RecommendationResult

/** 최근 주행 점수와 기준에 맞춰 고른 맞춤 퀘스트. 값은 모두 Room의 리포트·메모·퀘스트 진행에서 다시 계산한 것이다. */
class RecommendationCanvas(context: Context,assets: ProductIllustrations): SceneCanvas(context,assets) {
    var result: RecommendationResult?=null
    var sourceLabel="Pleos 차량 주행 기록 기준"
    override fun drawScene(c: Canvas) {
        c.drawColor(Color.rgb(197,216,255));assets.hills(c)
        text(c,"맞춤 추천 퀘스트",72f,70f,59f,Palette.ink,true)
        val r=result
        text(c,"$sourceLabel · 최근 ${r?.tripCount ?: 0}번 기록의 "+if(sourceLabel.startsWith("DEMO"))"연습 내용과 메모로 골랐어요" else "점수와 메모로 골랐어요",76f,174f,27f,Palette.ink)

        // 왼쪽: 기술별 최근 평균 점수
        box(c,65f,300f,520f,560f,Color.WHITE,36f)
        text(c,"최근 평균 점수",100f,325f,36f,Palette.ink,true)
        DrivingSkill.values().forEachIndexed {i,skill->
            val y=410f+i*110f;val score=r?.averages?.get(skill)
            text(c,skill.title,100f,y,28f,Palette.ink)
            text(c,score?.let {"${it}점"} ?: "측정 전",420f,y,28f,if(score!=null && score<70)Color.rgb(175,55,44) else Palette.purple,true)
            box(c,100f,y+50f,450f,14f,Color.rgb(220,225,241),7f)
            if(score!=null)box(c,100f,y+50f,450f*score/100f,14f,if(score<70)Color.rgb(230,120,90) else Palette.purple,7f)
        }

        // 오른쪽: 추천 퀘스트 최대 3개
        val list=r?.recommendations.orEmpty()
        if(list.isEmpty()) {
            box(c,620f,300f,1015f,560f,Color.WHITE,36f)
            assets.mascot(c,android.graphics.RectF(1020f,340f,1230f,548f))
            val message=if((r?.tripCount ?: 0)==0)"주행 기록과 어려웠던 상황을 남기면\n나에게 필요한 연습을 골라 드릴게요"
                else "지금은 반복되는 약점이 없어요!\n이대로 차근차근 달려 봐요"
            text(c,message,700f,600f,34f,Palette.ink,true)
        }
        list.forEachIndexed {i,q->
            val y=300f+i*190f
            box(c,620f,y,1015f,170f,Color.WHITE,32f)
            text(c,q.title+if(q.done)"  ✓" else "",645f,y+17f,32f,Palette.ink,true)
            box(c,645f,y+64f,245f,40f,if(q.kind==RecommendationKind.PRACTICE)Color.rgb(255,239,200) else Color.rgb(228,231,255),20f)
            text(c,q.kind.label,660f,y+70f,21f,Palette.ink,true)
            text(c,q.goal.take(31),910f,y+70f,22f,Palette.ink)
            wrappedText(c,"왜? "+q.reason,645f,y+117f,if(q.target>0)650f else 945f,20f,Color.rgb(101,106,128),false)
            if(q.target>0) {
                text(c,"${q.progress} / ${q.target}",1490f,y+24f,28f,Palette.purple,true)
                box(c,1330f,y+139f,270f,14f,Color.rgb(220,225,241),7f)
                box(c,1330f,y+139f,270f*q.progress.coerceAtMost(q.target)/q.target,14f,Palette.purple,7f)
            }
        }
        text(c,if(sourceLabel.startsWith("DEMO"))"어렵다고 남긴 동작을 우선하고, 메모가 없으면 마친 시연에서 다음 연습을 골라요" else "추천 기준: 평균 70점 미만 · 최근 5번 중 3번 이상 70점 미만 · 같은 어려움 메모 2건 이상",76f,905f,23f,Palette.ink)
        text(c,"점수는 속도·차량 신호로 계산한 연습 참고값이에요. 실제 도로 상황 판단은 운전자가 직접 해요",76f,950f,22f,Color.rgb(101,106,128))
    }
}
