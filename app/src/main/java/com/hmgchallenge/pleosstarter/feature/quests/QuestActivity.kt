package com.hmgchallenge.pleosstarter.feature.quests

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 연습 퀘스트 화면. 차량 값(깜빡이·조향각·브레이크)을 실시간으로 읽어 시도를 자동 판정하고
 * 퀘스트별 레벨과 진행 막대를 보여 준다. 누르는 버튼은 없다(진행은 항상 자동).
 */
class QuestActivity : Activity() {
    private val accent = Color.rgb(97, 230, 198)
    private val muted = Color.rgb(173, 188, 202)
    private val warn = Color.rgb(255, 192, 109)
    private val board get() = QuestSession.board
    private val cards = mutableMapOf<String, Card>()
    private var source: QuestVehicleSource? = null
    private var detector: ManeuverDetector? = null
    private var signal: Signal? = null
    private var angle: Float? = null
    private var brake: Boolean? = null
    private lateinit var live: TextView
    private lateinit var status: TextView
    private lateinit var history: TextView

    private class Card(val level: TextView, val bar: ProgressBar, val count: TextView, val last: TextView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 22, 31)
        window.navigationBarColor = Color.rgb(13, 22, 31)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        content.addView(label("PLEOS  /  PRACTICE QUESTS", 13f, accent))
        content.addView(label("연습 퀘스트", 30f).apply { setTypeface(null, Typeface.BOLD) })
        content.addView(label("운전하면 자동으로 기록돼요. 실패해도 점수는 내려가지 않아요.", 14f, muted))

        QuestCatalog.all.forEach { quest ->
            val card = card(content)
            val header = LinearLayout(this)
            header.addView(label(quest.title, 21f), LinearLayout.LayoutParams(0, -2, 1f))
            val level = label("", 17f, accent).apply { setTypeface(null, Typeface.BOLD) }
            header.addView(level)
            card.addView(header)
            card.addView(label(quest.goal, 15f))
            card.addView(label(quest.howToSucceed, 13f, muted))
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = QuestProgress.STEPS_PER_LEVEL
                progressTintList = ColorStateList.valueOf(accent)
            }
            card.addView(bar, LinearLayout.LayoutParams(-1, dp(14)).apply { topMargin = dp(8) })
            val count = label("", 13f, muted)
            card.addView(count)
            val last = label("아직 시도가 없어요.", 15f, muted)
            card.addView(last)
            cards[quest.questId] = Card(level, bar, count, last)
        }

        val liveCard = card(content)
        liveCard.addView(label("지금 차량 값", 17f, muted))
        live = label("", 17f)
        liveCard.addView(live)
        status = label("", 13f, warn)
        liveCard.addView(status)

        val historyCard = card(content)
        historyCard.addView(label("최근 기록", 17f, muted))
        history = label("", 14f)
        historyCard.addView(history)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(13, 22, 31)); addView(content)
        })
    }

    override fun onStart() {
        super.onStart()
        signal = null; angle = null; brake = null
        status.text = "차량 연결 중…"
        val detector = ManeuverDetector { attempt -> board.record(attempt); render() }.also { detector = it }
        source = QuestVehicleSource(
            this,
            onSignal = { value -> signal = value; value?.let { detector.onSignal(now(), it) }; connected() },
            onAngle = { value -> angle = value; value?.let { detector.onAngle(now(), it) }; connected() },
            onBrake = { value -> brake = value; value?.let { detector.onBrake(now(), it) }; connected() },
            onError = { message -> status.text = message }
        ).also { it.start() }
        render()
    }

    override fun onStop() {
        source?.stop(); source = null; detector = null
        super.onStop()
    }

    private fun connected() {
        if (status.text == "차량 연결 중…") status.text = ""
        renderLive()
    }

    private fun render() {
        QuestCatalog.all.forEach { quest ->
            val card = cards[quest.questId] ?: return@forEach
            val progress = board.progressOf(quest.questId)
            card.level.text = "Lv.${progress.level}"
            card.bar.progress = progress.steps
            card.count.text = "${progress.steps} / ${QuestProgress.STEPS_PER_LEVEL}  ·  성공 ${progress.successCount}번"
            board.attempts.lastOrNull { it.questId == quest.questId }?.let { attempt ->
                card.last.text = if (attempt.success) "성공! 막대가 한 칸 올랐어요." else "다시 해봐요 · ${attempt.result.message}"
                card.last.setTextColor(if (attempt.success) accent else warn)
            }
        }
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
        history.text = board.attempts.takeLast(6).reversed().joinToString("\n") { attempt ->
            val title = QuestCatalog.find(attempt.questId)?.title ?: attempt.questId
            "${time.format(Date(attempt.t))}  $title  ${if (attempt.success) "성공" else "실패(${attempt.result.name})"}" +
                "  조향 ${String.format(Locale.US, "%.1f", attempt.peakAngleDeg)}°" +
                (attempt.signalLeadMs?.let { "  깜빡이 ${String.format(Locale.US, "%.1f", it / 1000f)}초 전" } ?: "  깜빡이 없음") +
                (attempt.braked?.let { if (it) "  감속함" else "  감속 안 함" } ?: "")
        }.ifEmpty { "차선을 바꾸거나 교차로에서 우회전하면 여기에 기록돼요." }
        renderLive()
    }

    private fun renderLive() {
        val signalText = when (signal) {
            Signal.LEFT -> "왼쪽"; Signal.RIGHT -> "오른쪽"; Signal.OFF -> "꺼짐"; null -> "값 없음"
        }
        val angleText = angle?.let { String.format(Locale.US, "%.1f°", it) } ?: "값 없음"
        val brakeText = when (brake) { true -> "밟음"; false -> "안 밟음"; null -> "값 없음" }
        live.text = "깜빡이 $signalText   ·   앞바퀴 조향각 $angleText   ·   브레이크 $brakeText"
    }

    private fun now() = System.currentTimeMillis()

    private fun label(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(4), 0, dp(4))
    }
    private fun card(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply { setColor(Color.rgb(24, 38, 50)); cornerRadius = dp(18).toFloat() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

/** 앱이 켜져 있는 동안 퀘스트 진행도를 들고 있는다(화면을 나갔다 와도 유지, 앱을 끄면 초기화). */
object QuestSession {
    val board = QuestBoard()
}
