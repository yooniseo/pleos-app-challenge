package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.hmgchallenge.pleosstarter.quests.*

class QuestCanvas(context: Context,assets: ProductIllustrations): QuestPageCanvas(context,assets) {
    var state=QuestState()
    var points=0
    fun diagnostics()=ProductQuestCatalog.all.associate {it.skill.name to state.practiceProgress(it.skill).steps}
    override fun drawScene(c: Canvas) {
        page(c,"차근차근, 나의 운전 경험","연습할 때마다 1칸씩 채워져요! 30칸을 다 채우면 다음 레벨로 갈 수 있어요")
        box(c,1395f,77f,261f,94f,blue,47f)
        image(c,"quest_star",1430f,109f,30f,29f)
        text(c,"$points P",1480f,97f,36f,Color.WHITE,true)
        ProductQuestCatalog.all.forEachIndexed {i,quest->
            val x=62f+(i%3)*541;val y=322f+(i/3)*299
            val progress=state.practiceProgress(quest.skill)
            val icon=when(quest.skill) {
                CoachSkill.PREPARATION,CoachSkill.FINISH->"badge_prepare"
                CoachSkill.LANE_CHANGE->"badge_lane"
                CoachSkill.INTERSECTION->"badge_intersection"
                CoachSkill.SPEED->"badge_speed"
                CoachSkill.PARKING->"badge_parking"
            }
            box(c,x,y,512f,270f,Color.WHITE,35f)
            image(c,icon,x+35,y+32,130f,130f)
            text(c,if(quest.skill==CoachSkill.PARKING)"후면 주차" else quest.title,x+195,y+27,34f,ink,true)
            text(c,"Lv.${progress.level}   ${progress.steps} / ${PracticeProgress.TARGET}",x+195,y+83,28f,blue,true)
            text(c,"누적 ${progress.completed}회",x+195,y+127,24f,muted)
            box(c,x+189,y+168,288f,14f,0xffe1e9f4.toInt(),7f)
            if(progress.steps>0)box(c,x+189,y+168,288f*progress.fraction,14f,blue,7f)
            text(c,quest.detail,x+35,y+204,22f,ink)
        }
        text(c,"시연 기록 · 누적 연습 ${state.evidence.size}회",68f,953f,26f,ink)
    }
}
