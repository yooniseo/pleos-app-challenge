package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.*
import com.hmgchallenge.pleosstarter.quests.*

class QuestCanvas(context: Context,assets: ProductIllustrations): SceneCanvas(context,assets) {
    var state=QuestState()
    var points=0
    override fun drawScene(c: Canvas) {
        c.drawColor(Color.rgb(197,216,255))
        repeat(12){i->assets.cloud(c,90f+137*i,96f+52*(i%3),72f,23f,i%2==1)}
        assets.hills(c)
        text(c,"TUTORIAL  /  DRIVE QUEST",72f,40f,29f,Palette.ink,true)
        text(c,"오늘의 운전 퀘스트",72f,103f,61f,Palette.ink,true)
        text(c,"차근차근 미션을 깨고, 새로운 운전 실력을 모아보세요!",76f,191f,27f)
        box(c,1370f,45f,260f,94f,Palette.purple,47f);text(c,"★  $points P",1403f,66f,36f,Color.WHITE,true)
        val skills=listOf(CoachSkill.INTERSECTION,CoachSkill.PREPARATION,CoachSkill.LANE_CHANGE,CoachSkill.SPEED,CoachSkill.PARKING)
        val titles=listOf("교차로 통과","출발 전 점검","차선 변경","안전 거리 유지","주차 마무리")
        val rewards=listOf(100,0,200,0,100)
        val active=state.session?.current?.context?.kind?.skill ?: CoachSkill.LANE_CHANGE
        skills.forEachIndexed {i,skill->
            val selected=skill==active;val x=54f+321*i;val y=if(selected)280f else if(i in listOf(1,3))343f else 388f
            val supported=i !in listOf(1,3)
            box(c,x,y,290f,if(selected)495f else 435f,if(selected)Palette.purple else Palette.cream,78f)
            assets.questBadge(c,x+26,y+19,i,selected)
            val count=state.practiceCount(skill)
            text(c,if(supported)"$count 회" else "준비 중",x+83,y+207,35f,Palette.ink,true)
            text(c,titles[i],x+23,y+309,29f,if(selected)Color.WHITE else Palette.ink,true)
            box(c,x+37,y+369,216f,53f,if(selected)Color.rgb(249,213,141) else Color.rgb(226,227,220),26f)
            text(c,if(supported)"★ ${rewards[i]} P" else "센서 연동 예정",x+60,y+376,25f)
            if(selected){box(c,x+33,y+445,224f,11f,Color.rgb(152,157,245),5f);box(c,x+33,y+445,224f*(state.completedStages(skill).size/4f),11f,Color.WHITE,5f)}
        }
        text(c,"DEMO · 시연 기록 ${state.evidence.size}회 · 성장 ${state.growthPercent}% · ${state.levelName}",400f,1023f,27f,Palette.ink,true)
    }
}
