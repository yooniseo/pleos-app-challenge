package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.*
import com.hmgchallenge.pleosstarter.analysis.Recommendation
import com.hmgchallenge.pleosstarter.core.data.PracticeRewards
import com.hmgchallenge.pleosstarter.quests.CoachSkill
import com.hmgchallenge.pleosstarter.quests.QuestState

/** Eunji’s quest board, Figma 166:14543. The cards use the same evidence as the list. */
class MissionCanvas(
    context: Context, assets: ProductIllustrations, val mission: Recommendation,
    source: String, state: QuestState, alternatives: List<Recommendation>
) : QuestPageCanvas(context, assets) {
    private data class Topic(val skill: CoachSkill, val title: String, val icon: String)
    private data class Card(val title: String, val icon: String, val steps: Int, val target: Int, val points: Int)
    private val topics = listOf(
        Topic(CoachSkill.INTERSECTION, "교차로 통과", "junction"),
        Topic(CoachSkill.PREPARATION, "출발 전 점검", "check"),
        Topic(CoachSkill.LANE_CHANGE, "차선 변경", "lane"),
        Topic(CoachSkill.SPEED, "속도 관리", "distance"),
        Topic(CoachSkill.PARKING, "주차 마무리", "parking")
    )
    private val replay = source == "REPLAY"
    private val cards = mutableMapOf<Int, Card>()
    init {
        val center = focused(mission)
        cards[2] = center
        if (replay) {
            topics.filter { it.icon != center.icon }.take(4).zip(listOf(0,1,3,4)).forEach { (t,slot) ->
                cards[slot] = Card(t.title,t.icon,state.practiceCount(t.skill),1,PracticeRewards.points(t.skill.name))
            }
        } else {
            alternatives.filter { it.id != mission.id }.zip(listOf(1,3)).forEach { (other,slot) -> cards[slot] = focused(other) }
        }
    }
    private fun focused(q: Recommendation): Card {
        val topic = when (q.weakness) {
            "TURN_SIGNAL" -> topics[2]
            "TURN_SLOWDOWN" -> topics[0].copy(title="우회전")
            "PARKING" -> topics[4]
            "LEFT_TURN", "U_TURN" -> topics[0].copy(title=if(q.weakness=="U_TURN") "유턴" else "좌회전")
            "SMOOTH_ACCEL" -> topics[1].copy(title="부드러운 출발")
            else -> topics[3].copy(title="여유 있는 감속")
        }
        return Card(topic.title,topic.icon,q.progress,q.target,if(replay)PracticeRewards.points(topic.skill.name) else 0)
    }
    var actionLabel = "퀘스트 계속하기"
    override fun drawScene(c: Canvas) {
        image(c,"mission_sky",0f,0f,1700f,1184f,true)
        text(c,"오늘의 운전 퀘스트",72f,72f,72f,Color.WHITE,true)
        text(c,mission.goal,76f,190f,32f,Color.WHITE)
        val tracked = cards.values.filter { it.target > 0 }
        box(c,1370f,107f,260f,94f,0xff5768ec.toInt(),47f)
        centered(c,if(tracked.isEmpty()) "직접 연습" else "${tracked.count { it.steps >= it.target }} / ${tracked.size}",
            1500f,122f,42f,Color.WHITE)
        for ((i,card) in cards) drawCard(c,i,card)
        val caption = if (replay && mission.lifetimeCount != null)
            "시연 · 깜빡이 먼저 켜기 누적 ${mission.lifetimeCount}회 · 3회씩 연습"
            else "${mission.title} · ${mission.reason}"
        centered(c,caption,850f,851f,24f,Palette.ink)
        box(c,410f,907f,890f,153f,0xff5768ec.toInt(),77f)
        image(c,"mission_continue",624f,956f,60f,57f)
        centered(c,actionLabel,909f,944f,50f,Color.WHITE)
    }
    private fun drawCard(c: Canvas, slot: Int, card: Card) {
        val x = 57f + 321f * slot
        val y = when(slot) { 0,4 -> 397f; 1,3 -> 326f; else -> 294f }
        val enabled = card.steps > 0 || slot == 2
        val accent = if(enabled) 0xffffc224.toInt() else 0xffd5dbe6.toInt()
        val bottom = when(slot) { 2 -> 0xffd4ffcc.toInt(); 3 -> 0xffd9ccff.toInt(); else -> 0xffffedcc.toInt() }
        paint.color = Color.WHITE
        paint.shader = if(enabled) LinearGradient(x,y,x,y+767f,0xff558bed.toInt(),bottom,Shader.TileMode.CLAMP) else null
        val silhouette = Path().apply {
            addRoundRect(RectF(x,y,x+302,y+451),floatArrayOf(151f,151f,151f,151f,72f,72f,72f,72f),Path.Direction.CW)
        }
        c.drawPath(silhouette,paint)
        paint.shader = null
        image(c,if(enabled)"mission_disc" else "mission_disc_idle",x+27,y+35,248f,240f,true)
        image(c,"mission_${card.icon}",x+104,y+89,94f,90f)
        val count = if(card.target>0) "${card.steps.coerceAtMost(card.target)} / ${card.target}" else "직접 연습"
        centered(c,count,x+151,y+211,25f,if(enabled)Color.BLACK else Color.DKGRAY)
        centered(c,card.title,x+151,y+299,33f,if(enabled)Color.WHITE else 0xff667080.toInt())
        val border = if(enabled) 0xffffbd21.toInt() else 0xffaea592.toInt()
        box(c,x+85,y+19,132f,40f,border,22f)
        box(c,x+87,y+21,128f,36f,Color.WHITE,21f)
        if(card.points>0) {
            image(c,if(enabled)"mission_reward" else "mission_reward_idle",x+104,y+27,24f,23f)
            text(c,"${card.points}P",x+135,y+24,20f,0xff936300.toInt(),true)
        } else centered(c,if(slot==2)"맞춤 미션" else "연습",x+151,y+23,20f,Palette.ink)
        box(c,x+41,y+389,220f,15f,0xffd5dbe6.toInt(),8f)
        if(card.target>0 && card.steps>0)
            box(c,x+41,y+389,220f*card.steps.coerceAtMost(card.target)/card.target,15f,accent,8f)
    }
    private fun centered(c: Canvas,label: String,x: Float,y: Float,size: Float,color: Int) {
        paint.textAlign=Paint.Align.CENTER
        text(c,label,x,y,size,color,true)
        paint.textAlign=Paint.Align.LEFT
    }
}

