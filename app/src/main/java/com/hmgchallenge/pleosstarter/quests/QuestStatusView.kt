package com.hmgchallenge.pleosstarter.quests

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.hmgchallenge.pleosstarter.driving.GuideUiModel

/** Read-only compact widget for part 1. No list, pop-up, completion animation, or touch action. */
class QuestStatusView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val title = TextView(context).apply { textSize = 17f; setTextColor(Color.rgb(97, 230, 198)) }
    private val progress = TextView(context).apply { textSize = 14f; setTextColor(Color.LTGRAY) }
    init { orientation = VERTICAL; addView(title); addView(progress) }
    fun render(model: GuideUiModel) {
        title.text = model.activeQuest?.title ?: "상황에 맞는 퀘스트 대기"
        progress.text = "완료 ${model.completedStageCount}/4단계 · " + if (model.demo) "시연 기록" else "개인 기록"
        contentDescription = "${title.text}. ${progress.text}"
    }
}
