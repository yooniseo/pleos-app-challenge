package com.hmgchallenge.pleosstarter.feature.quests

/**
 * 퀘스트 파트(C)의 모델. Android·SDK에 의존하지 않는 순수 Kotlin이라 PC 단위 테스트가 가능하다.
 * 차량 값은 QuestVehicleSource가 Pleos Vehicle SDK에서 읽어 ManeuverDetector로 넘긴다.
 */

/** 퀘스트 고유 ID. 저장·로그·화면에서 이 값으로 구분한다. */
object QuestIds {
    const val LANE_CHANGE = "Q_LANE_CHANGE"
    const val INTERSECTION_RIGHT = "Q_INTERSECTION_RIGHT"
}

enum class Direction { LEFT, RIGHT }

/** 방향지시등 상태 (Vehicle SDK TurnSignalValue와 1:1). */
enum class Signal { OFF, LEFT, RIGHT }

/** 한 번의 시도를 판정한 결과. SUCCESS만 진행도를 올리고, 나머지는 기록만 한다(감점 없음). */
enum class AttemptResult(val message: String) {
    SUCCESS("잘했어요!"),
    NO_SIGNAL("깜빡이를 먼저 켜고 움직여 봐요."),
    WRONG_SIGNAL("가려는 방향과 같은 쪽 깜빡이를 켜 봐요."),
    SIGNAL_LATE("깜빡이를 조금 더 일찍 켜 봐요."),
    NO_BRAKE("교차로에 들어가기 전에 브레이크로 속도를 줄여 봐요.")
}

/** 감지된 시도 1건과 판정에 쓴 차량 데이터. */
data class Attempt(
    val questId: String,
    val t: Long,                 // 조작이 끝난 시각 (epoch ms)
    val result: AttemptResult,
    val direction: Direction,
    val peakAngleDeg: Float,     // 조작 중 앞바퀴 조향각 최댓값(절댓값)
    val signalLeadMs: Long?,     // 조향 시작 전 깜빡이를 켜 둔 시간. 꺼져 있었으면 null
    val braked: Boolean?         // 교차로 진입 전 브레이크 여부. 브레이크 값을 못 받았으면 null
) {
    val success get() = result == AttemptResult.SUCCESS
}

/** 퀘스트 1개의 누적 진행 상태. 값은 올라가기만 한다. */
data class QuestProgress(
    val questId: String,
    val level: Int = 1,
    val steps: Int = 0,          // 현재 레벨에서 채운 칸 (0 until STEPS_PER_LEVEL)
    val successCount: Int = 0,
    val failCount: Int = 0
) {
    val fraction get() = steps.toFloat() / STEPS_PER_LEVEL

    companion object {
        /** 성공 1번에 막대가 1/30 오른다. 30번 채우면 다음 레벨. */
        const val STEPS_PER_LEVEL = 30
    }
}

/** 화면에 보여 줄 퀘스트 설명. */
data class QuestDef(
    val questId: String,
    val title: String,
    val goal: String,
    val howToSucceed: String
)

object QuestCatalog {
    val all = listOf(
        QuestDef(
            questId = QuestIds.LANE_CHANGE,
            title = "차선 변경",
            goal = "깜빡이를 먼저 켜고 차선 바꾸기",
            howToSucceed = "바꾸려는 방향 깜빡이를 켜고 잠시 기다린 뒤 핸들을 움직이면 성공"
        ),
        QuestDef(
            questId = QuestIds.INTERSECTION_RIGHT,
            title = "교차로 우회전",
            goal = "깜빡이 켜고, 속도 줄이고, 우회전하기",
            howToSucceed = "오른쪽 깜빡이를 미리 켜고 브레이크로 속도를 줄인 뒤 우회전하면 성공"
        )
    )

    fun find(questId: String) = all.firstOrNull { it.questId == questId }
}
