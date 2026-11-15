package com.hmgchallenge.pleosstarter.ui

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.hmgchallenge.pleosstarter.R
import com.hmgchallenge.pleosstarter.driving.ScenarioCoachingCues
import com.hmgchallenge.pleosstarter.driving.SimpleRoadScenario

/** Only the coach card handles taps. Pleos owns the camera and its controls. */
internal class ParkingCameraCoach(private val activity: Activity, private val art: ProductIllustrations, private val showDiagram: () -> Unit) {
    private val windows = activity.windowManager
    private var cueView: CueView? = null
    private var stepsView: StepsView? = null
    private var parking: SimpleRoadScenario? = null
    private var attempted = false

    fun update(scenario: SimpleRoadScenario) {
        parking = scenario
        if (!attempted) {
            attempted = true
            if (!Settings.canDrawOverlays(activity)) return
            val appWidth = activity.window.decorView.width
            val appHeight = activity.window.decorView.height
            val width = (appWidth * .48f).toInt().coerceAtLeast(1)
            val params = WindowManager.LayoutParams(width, width / 3,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.RIGHT
                x = (appWidth * .16f).toInt()
                y = (appHeight * .49f).toInt()
                alpha = if (Build.VERSION.SDK_INT >= 31) {
                    minOf(.8f, activity.getSystemService(android.hardware.input.InputManager::class.java).maximumObscuringOpacityForTouch)
                } else .8f
                title = "차근차근 주차 코치"
            }
            val view = CueView().apply {
                contentDescription = "백미러 화면 끄고 주차 가이드 보기"
                setOnClickListener { showDiagram() }
            }
            try {
                windows.addView(view, params)
                cueView = view
                val side = WindowManager.LayoutParams().apply {
                    copyFrom(params)
                    this.width = (appWidth * .17f).toInt()
                    height = this.width * 635 / 299
                    x = (appWidth * .715f).toInt()
                    y = (appHeight * .09f).toInt()
                    flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    title = "주차 도움 가이드"
                }
                val steps = StepsView()
                windows.addView(steps, side)
                stepsView = steps
            } catch (_: SecurityException) {
                hideViews()
                return
            } catch (_: WindowManager.BadTokenException) {
                hideViews()
                return
            }
        }
        cueView?.invalidate()
        stepsView?.invalidate()
    }

    fun hide() {
        hideViews()
        parking = null
        attempted = false
    }

    private fun hideViews() {
        cueView?.let { windows.removeViewImmediate(it) }
        stepsView?.let { windows.removeViewImmediate(it) }
        cueView = null
        stepsView = null
    }

    private inner class StepsView : ParkingStepsCanvas(activity, art) {
        override fun drawScene(c: Canvas) {
            val s = parking ?: return
            c.scale(1700f / 299f, 1184f / 635f)
            c.translate(-56f, -147f)
            drawSteps(c, parkingGuideStep(ScenarioCoachingCues.at(s).step))
        }
    }

    private inner class CueView : View(activity) {
        private val wheel = SteeringWheelRenderer()
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = resources.getFont(R.font.asta_sans_bold) }

        override fun onDraw(canvas: Canvas) {
            val s = parking ?: return
            val cue = ScenarioCoachingCues.at(s)
            canvas.save()
            canvas.scale(width / 560f, height / 186f)
            brush.color = Color.rgb(20, 25, 31)
            canvas.drawRoundRect(0f, 0f, 560f, 130f, 24f, 24f, brush)
            wheel.draw(canvas, 67f, 70f, 133f, s.steeringTarget, dark = true)
            brush.color = Palette.lime
            brush.textSize = 22f
            canvas.drawText("후진 주차  ·  ${parkingGuideStep(cue.step)} / 4", 136f, 49f, brush)
            brush.textSize = 20f
            brush.color = Color.WHITE
            canvas.drawText("도식 보기 ›", 437f, 49f, brush)
            brush.color = Color.WHITE
            brush.textSize = 30f
            brush.textSize *= minOf(1f, 399f / brush.measureText(cue.action))
            canvas.drawText(cue.action, 136f, 101f, brush)
            brush.color = Color.rgb(20, 25, 31)
            canvas.drawRoundRect(97f, 138f, 463f, 186f, 18f, 18f, brush)
            for ((index, gear) in "RNDP".withIndex()) {
                brush.color = if (gear.toString() == s.parkingGear) Color.rgb(255, 96, 78) else Color.rgb(174, 186, 203)
                brush.textSize = 29f
                canvas.drawText(gear.toString(), 147f + 82f * index, 172f, brush)
            }
            canvas.restore()
        }
    }
}
