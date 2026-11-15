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

/** A small, touch-through cue for an authored emulator replay. Pleos renders the video. */
internal class ParkingCameraCoach(private val activity: Activity) {
    private val windows = activity.windowManager
    private var cueView: CueView? = null
    private var attempted = false

    fun update(scenario: SimpleRoadScenario) {
        if (!attempted) {
            attempted = true
            if (!Settings.canDrawOverlays(activity)) return
            val width = (activity.window.decorView.width * .43f).toInt().coerceAtLeast(1)
            val params = WindowManager.LayoutParams(width, width / 4,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.RIGHT
                x = width / 18
                y = width / 11
                alpha = if (Build.VERSION.SDK_INT >= 31) {
                    minOf(.8f, activity.getSystemService(android.hardware.input.InputManager::class.java).maximumObscuringOpacityForTouch)
                } else .8f
                title = "차근차근 주차 코치"
            }
            val view = CueView()
            try {
                windows.addView(view, params)
                cueView = view
            } catch (_: SecurityException) {
                return
            } catch (_: WindowManager.BadTokenException) {
                return
            }
        }
        cueView?.apply { parking = scenario; invalidate() }
    }

    fun hide() {
        cueView?.let { windows.removeViewImmediate(it) }
        cueView = null
        attempted = false
    }

    private inner class CueView : View(activity) {
        var parking: SimpleRoadScenario? = null
        private val wheel = SteeringWheelRenderer()
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = resources.getFont(R.font.asta_sans_bold) }

        override fun onDraw(canvas: Canvas) {
            val s = parking ?: return
            val cue = ScenarioCoachingCues.at(s)
            canvas.save()
            canvas.scale(width / 560f, height / 140f)
            brush.color = Color.rgb(20, 25, 31)
            canvas.drawRoundRect(0f, 0f, 560f, 140f, 24f, 24f, brush)
            wheel.draw(canvas, 67f, 70f, 133f, s.steeringTarget, dark = true)
            brush.color = Palette.lime
            brush.textSize = 22f
            canvas.drawText("후진 주차  ·  시연 코치", 136f, 49f, brush)
            brush.color = Color.WHITE
            brush.textSize = 30f
            brush.textSize *= minOf(1f, 399f / brush.measureText(cue.action))
            canvas.drawText(cue.action, 136f, 101f, brush)
            canvas.restore()
        }
    }
}
