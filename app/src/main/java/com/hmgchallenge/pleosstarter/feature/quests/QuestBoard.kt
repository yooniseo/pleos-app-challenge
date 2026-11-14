package com.hmgchallenge.pleosstarter.feature.quests

/**
 * 퀘스트 진행도와 시도 기록을 모아 둔다. 진행은 항상 자동이다: 감지된 시도를 record()에 넣기만 하면 된다.
 * 성공은 막대를 1/30 올리고, 실패는 기록만 남긴다. 레벨·진행도는 내려가지 않는다.
 */
class QuestBoard(initial: List<QuestProgress> = emptyList(),history: List<Attempt> = emptyList()) {
    private val progress = QuestCatalog.all.associate { quest ->
        quest.questId to (initial.firstOrNull {it.questId==quest.questId} ?: QuestProgress(quest.questId))
    }.toMutableMap()
    private val log = history.toMutableList()

    /** 지금까지 감지된 모든 시도 (성공·실패 포함, 오래된 것부터). */
    val attempts: List<Attempt> get() = log

    fun progressOf(questId: String) = progress[questId] ?: QuestProgress(questId)

    /** 시도를 기록하고 바뀐 진행 상태를 돌려준다. */
    fun record(attempt: Attempt): QuestProgress {
        log += attempt
        val before = progressOf(attempt.questId)
        val after = if(attempt.result==AttemptResult.UNVERIFIED)before.copy(unverifiedCount=before.unverifiedCount+1)
        else if (!attempt.success) before.copy(failCount = before.failCount + 1) else {
            val steps = before.steps + 1
            val levelUp = steps >= QuestProgress.STEPS_PER_LEVEL
            before.copy(
                level = if (levelUp) before.level + 1 else before.level,
                steps = if (levelUp) 0 else steps,
                successCount = before.successCount + 1
            )
        }
        progress[attempt.questId] = after
        return after
    }
}
