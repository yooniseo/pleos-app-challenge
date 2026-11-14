package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.*
import com.hmgchallenge.pleosstarter.quests.*

class QuestCanvas(context: Context,assets: ProductIllustrations): SceneCanvas(context,assets) {
    var state=QuestState()
    var points=0
    fun diagnostics()=ProductQuestCatalog.all.associate {it.skill.name to state.practiceProgress(it.skill).steps}
    override fun drawScene(c: Canvas) {
        c.drawColor(Color.rgb(197,216,255));assets.hills(c)
        text(c,"TUTORIAL  /  DRIVE QUEST",72f,35f,27f,Palette.ink,true)
        text(c,"차근차근, 여섯 가지 운전 경험",72f,93f,54f,Palette.ink,true)
        text(c,"자동 시연 기록 · 저장된 연습마다 1칸씩, 30칸을 채우면 다음 레벨",76f,170f,26f)
        box(c,1370f,45f,260f,94f,Palette.purple,47f);text(c,"★  $points P",1403f,66f,36f,Color.WHITE,true)
        ProductQuestCatalog.all.forEachIndexed {i,quest->
            val x=65f+(i%3)*540;val y=315f+(i/3)*300
            val progress=state.practiceProgress(quest.skill)
            val selected=state.session?.current?.context?.kind?.skill==quest.skill
            box(c,x,y,510f,268f,if(selected)Palette.purple else Color.WHITE,35f)
            val color=if(selected)Color.WHITE else Palette.ink
            c.save();c.translate(x+15,y+23);c.scale(.55f,.55f)
            assets.questBadge(c,0f,0f,listOf(1,2,0,3,4,1)[i],selected);c.restore()
            text(c,quest.title,x+170,y+26,31f,color,true)
            text(c,progress.label,x+170,y+79,27f,if(selected)Color.WHITE else Palette.purple,true)
            text(c,"누적 ${progress.completed}회",x+170,y+124,24f,color)
            box(c,x+170,y+167,300f,13f,Color.rgb(218,225,247),6f)
            box(c,x+170,y+167,300f*progress.fraction,13f,if(selected)Color.WHITE else Palette.purple,6f)
            wrappedText(c,quest.detail,x+26,y+208,460f,22f,color,false)
        }
        text(c,"출발·하차는 직접 확인 · 속도 기록만으로 실제 차간거리를 판정하지 않아요",110f,920f,25f,Palette.ink)
        text(c,"DEMO · 누적 시연 연습 ${state.evidence.size}회",560f,1060f,24f,Palette.ink,true)
    }
}
