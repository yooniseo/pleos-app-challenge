package com.hmgchallenge.pleosstarter.product

import android.app.Activity
import android.app.Instrumentation
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.room.Room
import com.hmgchallenge.pleosstarter.app.AppScreen
import com.hmgchallenge.pleosstarter.app.ChageunActivity
import com.hmgchallenge.pleosstarter.core.data.SharedCoachRepository
import com.hmgchallenge.pleosstarter.core.data.TripDatabase
import com.hmgchallenge.pleosstarter.driving.RoadLesson
import com.hmgchallenge.pleosstarter.driving.VehicleCoachSource
import java.io.File

/** Exercise both directions of the switch during one reverse manoeuvre. */
fun Instrumentation.runParkingModesValidation() {
    val previous = SharedCoachRepository.testOverride
    val db = SharedCoachRepository(Room.inMemoryDatabaseBuilder(targetContext, TripDatabase::class.java).build())
    SharedCoachRepository.testOverride = db
    var activity: ChageunActivity? = null
    var gear: String? = null
    val sensor = VehicleCoachSource(targetContext, { gear = it.gear }, {})
    val automation = uiAutomation
    val info = automation.serviceInfo
    val originalFlags = info.flags
    info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    automation.serviceInfo = info
    val result = Bundle()
    var code = Activity.RESULT_CANCELED
    fun awaitState(label: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 60000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            Thread.sleep(150)
        }
        error("Timed out: $label (last gear=$gear)")
    }
    fun capture(label: String) {
        val image = requireNotNull(automation.takeScreenshot())
        File(targetContext.filesDir, "parking-mode-$label.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }
    try {
        for (demo in listOf(true, false)) {
            com.hmgchallenge.pleosstarter.quests.QuestStore(targetContext, demo)
                .save(com.hmgchallenge.pleosstarter.quests.QuestState(demo = demo))
        }
        val intent = Intent(targetContext, ChageunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("verify_console", true)
        val app = startActivitySync(intent) as ChageunActivity
        activity = app
        fun mainCheck(check: () -> Boolean): Boolean {
            var satisfied = false
            runOnMainSync { satisfied = check() }
            return satisfied
        }
        fun toggle() = app.window.decorView.findViewWithTag<Button>("parking_camera").performClick()
        runOnMainSync {
            sensor.start()
            app.showRecommendations("REPLAY")
            check(app.window.decorView.findViewWithTag<Button>("recommend_source") == null)
            app.select(RoadLesson.PARKING)
            toggle()
            app.startPrepared()
        }
        val trip = requireNotNull(app.session.tripId)
        awaitState("diagram in reverse, cluster parked") {
            mainCheck { app.session.scenario.parkingGear == "R" && gear == "P" && app.hasWindowFocus() }
        }
        capture("off")
        runOnMainSync { toggle() }
        awaitState("system camera and SDK reverse") {
            mainCheck { gear == "R" && app.session.scenario.speedMps > .4 } &&
                automation.windows.any { it.root?.packageName?.toString() == "ai.umos.car.evs" }
        }
        capture("on")
        val card = automation.windows.mapNotNull { it.root }
            .flatMap { it.findAccessibilityNodeInfosByText("백미러 화면 끄고 주차 가이드 보기") }
            .first { it.isClickable }
        check(card.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        awaitState("camera closed; same reversing trip still running") {
            mainCheck { gear == "P" && app.hasWindowFocus() && app.screen == AppScreen.PARKING &&
                app.session.scenario.parkingGear == "R" && !app.session.paused && app.session.tripId == trip }
        }
        awaitState("parking completed") { mainCheck { app.screen == AppScreen.FEEDBACK && app.session.completedAt != null } }
        check(db.trips().map { it.tripId } == listOf(trip)) { "Unexpected trips: ${db.trips()}" }
        result.putString("result", "PASS diagram → rear camera → diagram; SDK P → R → P; same trip completed")
        code = Activity.RESULT_OK
    } catch (e: Throwable) {
        result.putString("error", e.stackTraceToString())
    } finally {
        runOnMainSync { sensor.stop(); activity?.finish() }
        waitForIdleSync()
        info.flags = originalFlags
        automation.serviceInfo = info
        SharedCoachRepository.testOverride = previous
        db.close()
        finish(code, result)
    }
}
