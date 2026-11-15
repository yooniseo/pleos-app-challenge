package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.hmgchallenge.pleosstarter.driving.BeginnerScenarioCoach
import com.hmgchallenge.pleosstarter.driving.CoachingStep
import com.hmgchallenge.pleosstarter.driving.RoadLesson
import com.hmgchallenge.pleosstarter.driving.ScenarioCoachingCues
import com.hmgchallenge.pleosstarter.driving.SimpleRoadScenario
import com.hmgchallenge.pleosstarter.session.ReplaySessionController

class ParkingCanvas(
    context: Context,
    assets: ProductIllustrations,
    private val session: ReplaySessionController
) : SceneCanvas(context, assets) {
    private val preview = SimpleRoadScenario(session.course, RoadLesson.PARKING)
    private val wheel = SteeringWheelRenderer()
    private val fade = LinearGradient(0f, 661f, 0f, 1184f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
    private val panel = Color.argb(230, 20, 25, 31)
    private val muted = Color.rgb(174, 186, 203)
    private val gears = listOf("R", "N", "D", "P")
    private val steps = listOf("주차 공간 확인", "핸들 조향", "천천히 후진", "핸들 정렬")
    private val hints = listOf(
        "주차칸 옆에 멈추고\n뒤쪽과 양옆을 살펴요",
        "핸들 그림을 보며\n후진 방향을 맞춰요",
        "뒤쪽과 양옆을 보며\n천천히 후진해요",
        "차가 주차선과\n나란한지 확인해요"
    )

    override fun drawScene(c: Canvas) {
        val s = session.scenario.takeIf { it.lesson == RoadLesson.PARKING } ?: preview
        val cue = ScenarioCoachingCues.at(s)
        val step = when (cue.step) {
            CoachingStep.PARK_REVERSE -> 2
            CoachingStep.PARK_COUNTERSTEER -> 3
            CoachingStep.PARK_ALIGN, CoachingStep.COMPLETE -> 4
            else -> 1
        }
        paint.color = Color.WHITE
        paint.shader = fade
        c.drawRect(0f, 661f, 1700f, 1184f, paint)
        paint.shader = null
        wheel.draw(c, 850f, 729f, 340f, s.steeringTarget, dark = true)

        centered(c, "가이드에 맞춰 천천히 움직여 주세요", 850f, 72f, 28f, Color.WHITE)
        for (x in listOf(613f, 1078f)) {
            assets.figma?.draw(c, "parking_dot", RectF(x, 89f, x + 9f, 98f))
        }
        drawSteps(c, step)
        box(c, 446f, 879f, 808f, 156f, Color.argb(215, 15, 18, 23), 24f)
        val action = if (cue.step == CoachingStep.READY) "후진 주차를 연습해요" else cue.action
        centered(c, action, 850f, 899f, 46f, Color.WHITE, bold = true, maxWidth = 750f)
        centered(c, BeginnerScenarioCoach.steeringPosition(s.steeringTarget), 850f, 972f, 29f, muted)
        box(c, 511f, 1044f, 697f, 58f, panel, 24f)
        gears.forEachIndexed { index, gear ->
            val color = when {
                gear != s.parkingGear -> muted
                gear == "R" -> Color.rgb(255, 96, 78)
                else -> Palette.lime
            }
            centered(c, gear, 687f + index * 115f, 1046f, 40f, color, bold = true)
        }
        text(c, "DEMO · 가상 주차 연습", 58f, 1048f, 23f, Color.WHITE)
    }

    private fun drawSteps(c: Canvas, current: Int) {
        box(c, 56f, 147f, 299f, 635f, panel, 28f)
        text(c, "주차 도움 가이드", 87f, 179f, 29f, Color.WHITE, true)
        var y = 249f
        steps.forEachIndexed { index, label ->
            val active = index + 1 == current
            val color = if (active) Color.BLACK else muted
            box(c, 83f, y, if (index == 1) 191f else 243f, 59f,
                if (active) Palette.lime else Color.rgb(52, 63, 77), 28f)
            val badge = if (active) "parking_step_active" else "parking_step_idle"
            assets.figma?.draw(c, badge, RectF(113f, y + 12f, 148f, y + 47f))
            centered(c, (index + 1).toString(), 130.5f, y + 14f, 22f, color, active)
            text(c, label, 162f, y + 13f, 22f, color, active)
            if (active) {
                text(c, "STEP. ${index + 1}", 97f, y + 73f, 26f, Palette.lime, true)
                text(c, hints[index], 97f, y + 117f, 24f, Color.WHITE, true)
                y += 190f
            }
            y += 82f
        }
    }

    private fun centered(c: Canvas, label: String, x: Float, y: Float, size: Float, color: Int,
                         bold: Boolean = false, maxWidth: Float = Float.MAX_VALUE) {
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        paint.typeface = resources.getFont(if (bold) com.hmgchallenge.pleosstarter.R.font.asta_sans_bold
                                          else com.hmgchallenge.pleosstarter.R.font.asta_sans_medium)
        val fitted = size * minOf(1f, maxWidth / paint.measureText(label))
        text(c, label, x, y, fitted, color, bold)
        paint.textAlign = Paint.Align.LEFT
    }
}
