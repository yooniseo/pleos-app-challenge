package com.hmgchallenge.pleosstarter.feature.quests

import org.json.JSONArray
import org.json.JSONObject

/** Versioned Room snapshot of the team's 30-step board. Full attempts also live in CoachingEvent. */
object QuestBoardCodec {
    fun encodeAttempt(attempt: Attempt): String=JSONObject()
        .put("questId",attempt.questId).put("t",attempt.t).put("result",attempt.result.name)
        .put("direction",attempt.direction.name).put("peakAngleDeg",attempt.peakAngleDeg)
        .put("signalLeadMs",attempt.signalLeadMs).put("braked",attempt.braked).toString()

    fun decodeAttempt(raw: String): Attempt {
        val value=JSONObject(raw)
        return Attempt(value.getString("questId"),value.getLong("t"),AttemptResult.valueOf(value.getString("result")),
            Direction.valueOf(value.getString("direction")),value.getDouble("peakAngleDeg").toFloat(),
            if(value.has("signalLeadMs") && !value.isNull("signalLeadMs"))value.getLong("signalLeadMs") else null,
            if(value.has("braked") && !value.isNull("braked"))value.getBoolean("braked") else null)
    }
    fun encode(board: QuestBoard): String {
        val progress=QuestCatalog.all.map {definition->
            val p=board.progressOf(definition.questId)
            JSONObject().put("questId",p.questId).put("level",p.level).put("steps",p.steps)
                .put("successCount",p.successCount).put("failCount",p.failCount).put("unverifiedCount",p.unverifiedCount)
        }
        return JSONObject().put("version",1).put("progress",JSONArray(progress))
            .put("history",JSONArray(board.attempts.takeLast(20).map {JSONObject(encodeAttempt(it))})).toString()
    }
    fun decode(raw: String?): QuestBoard {
        if(raw==null)return QuestBoard()
        val root=JSONObject(raw)
        require(root.getInt("version")==1)
        val array=root.getJSONArray("progress")
        val progress=(0 until array.length()).map {i->
            val p=array.getJSONObject(i)
            QuestProgress(p.getString("questId"),p.getInt("level"),p.getInt("steps"),p.getInt("successCount"),p.getInt("failCount"),p.optInt("unverifiedCount",0)).also {
                require(QuestCatalog.find(it.questId)!=null && it.level>=1 && it.steps in 0 until QuestProgress.STEPS_PER_LEVEL && it.successCount>=0 && it.failCount>=0 && it.unverifiedCount>=0)
            }
        }
        val history=root.getJSONArray("history")
        return QuestBoard(progress,(0 until history.length()).map {decodeAttempt(history.getJSONObject(it).toString())})
    }
}
