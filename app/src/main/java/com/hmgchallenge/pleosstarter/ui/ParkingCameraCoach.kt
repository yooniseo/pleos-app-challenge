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
internal class ParkingCameraCoach(private val activity: Activity, private val showDiagram: () -> Unit) {
    private val windows = activity.windowManager
    private var cueView: CueView? = null
    private var parking: SimpleRoadScenario? = null
    private var attempted = false

    fun update(scenario: SimpleRoadScenario) {
        parking = scenario
        if (!attempted) {
            attempted = true
            if (!Settings.canDrawOverlays(activity)) return
            val appWidth = activity.window.decorView.width
            val appHeight = activity.window.decorView.height
            val width = (appWidth * .33f).toInt().coerceAtLeast(1)
            val params = WindowManager.LayoutParams(width, width / 5,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.RIGHT
                x = (appWidth * .035f).toInt()
                y = (appHeight * .035f).toInt()
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
            } catch (_: SecurityException) {
                return
            } catch (_: WindowManager.BadTokenException) {
                return
            }
        }
        cueView?.invalidate()
    }

    fun hide() {
        cueView?.let { windows.removeViewImmediate(it) }
        cueView = null
        parking = null
        attempted = false
    }

    private inner class CueView : View(activity) {
        private val wheel = SteeringWheelRenderer()
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = resources.getFont(R.font.asta_sans_bold) }

        override fun onDraw(canvas: Canvas) {
            val s = parking ?: return
            val cue = ScenarioCoachingCues.at(s)
            canvas.save()
            canvas.scale(width / 400f, height / 80f)
            brush.color = Color.rgb(20, 25, 31)
            canvas.drawRoundRect(0f, 0f, 400f, 80f, 20f, 20f, brush)
            wheel.draw(canvas, 41f, 40f, 76f, s.steeringTarget, dark = true)
            val instruction = cue.spoken ?: cue.action
            brush.color = Color.WHITE
            brush.textSize = 29f
            brush.textSize *= minOf(1f, 304f / brush.measureText(instruction))
            canvas.drawText(instruction, 78f, 37f, brush)
            brush.color = Color.LTGRAY
            brush.textSize = 21f
            canvas.drawText("도식 보기 ›", 78f, 66f, brush)
            canvas.restore()
        }
    }
}
