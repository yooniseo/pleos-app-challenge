package com.hmgchallenge.pleosstarter.driving

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import java.io.File

/** Dependency-free emulator checks; live_probe=1 runs read-only SDK observation only. */
class CoachSmokeRunner : Instrumentation() {
    private var live=false
    override fun onCreate(arguments: Bundle?) {super.onCreate(arguments);live=arguments?.getString("live_probe")=="1";start()}
    override fun onStart() {
        val result=Bundle()
        val journal=File(targetContext.filesDir,"driving-coach-events.jsonl")
        val original=journal.takeIf {it.exists()}?.readBytes()
        var activity: Activity?=null
        var resultCode=Activity.RESULT_OK
        try {
            check(android.os.Build.HARDWARE in setOf("ranchu","goldfish") || android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.MODEL.contains("sdk") || android.os.Build.MODEL.contains("emulator",true)) {"Emulator-only test"}
            val intent=Intent(targetContext,DrivingCoachActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("live_mode",live)
            activity=startActivitySync(intent);waitForIdleSync()
            val current=activity
            if(live) {
                Thread.sleep(12_000);waitForIdleSync();capture("live-coach.png")
                runOnMainSync {
                    result.putString("observed_screen",allText(current.window.decorView).joinToString(" | "))
                    result.putString("sdk_status",(current as DrivingCoachActivity).connectionStatus().entries.joinToString(" | ") {"${it.key}=${it.value}"})
                }
            } else {
                check(targetContext.packageManager.hasSystemFeature("android.hardware.type.automotive").not()) {"Interactive demo smoke uses ordinary emulator; live probe is read-only on Automotive"}
                capture("driving-lane.png")
                click(current,"demo_next");assertText(current,"표시된 교차로")
                capture("driving-turn.png")
                click(current,"note")
                click(current,"demo_next");assertText(current,"빠져나오")
                click(current,"demo_next");assertText(current,"체험을 마쳤")
                val repository=CoachRepository(targetContext)
                val events=repository.eventsAfter()
                val completed=events.filter {it.eventType==CoachEventType.PHASE_RECORDED}.takeLast(3)
                check(completed.size==3 && completed.map {it.situationInstanceId}.distinct().size==1)
                check(completed.all {it.source==EvidenceSource.DEMO})
                check(events.any {it.feedbackNote()?.category=="RIGHT_TURN"})
                val last=events.last();check(CoachRepository.decode(CoachRepository.encode(last))==last)
                check(repository.eventsAfter(last.eventId).isEmpty())
                click(current,"tab_parking");capture("parking-lesson.png")
                repeat(4){click(current,"parking_next")}
                click(current,"parking_next");waitForIdleSync();capture("parking-checklist.png")
                result.putInt("scenario_steps",completed.size)
            }
            result.putString("result","PASS")
        } catch (error: Throwable) {
            result.putString("result","FAIL: ${error.stackTraceToString()}");resultCode=Activity.RESULT_CANCELED
        } finally {
            activity?.let {a->runOnMainSync {a.finish()}}
            if(original==null) journal.delete() else journal.writeBytes(original)
        }
        finish(resultCode,result)
    }
    private fun click(a: Activity,tag: String) {
        runOnMainSync {check(a.window.decorView.findViewWithTag<Button>(tag)?.performClick()==true){"Missing action $tag"}}
        waitForIdleSync()
    }
    private fun assertText(a: Activity,text: String) {
        runOnMainSync {check(allText(a.window.decorView).any {it.contains(text)}) {"Missing text $text"}}
    }
    private fun allText(v: View): List<String> = when(v) {
        is ViewGroup -> (0 until v.childCount).flatMap {allText(v.getChildAt(it))}
        is TextView -> listOf(v.text.toString())
        else -> emptyList()
    }
    private fun capture(name: String) {
        val deadline=android.os.SystemClock.elapsedRealtime()+30_000
        var root=uiAutomation.rootInActiveWindow
        while(root?.packageName!=targetContext.packageName && android.os.SystemClock.elapsedRealtime()<deadline) {
            // Cold boot on this low-memory host can ANR System UI. Never dismiss an app ANR.
            if(root?.packageName=="android" && root.findAccessibilityNodeInfosByText("System UI isn't responding").isNotEmpty()) {
                root.findAccessibilityNodeInfosByViewId("android:id/aerr_wait").firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            Thread.sleep(500);root=uiAutomation.rootInActiveWindow
        }
        check(root?.packageName==targetContext.packageName) {"Unexpected overlay: ${root?.packageName}"}
        val screenshot=checkNotNull(uiAutomation.takeScreenshot())
        val dir=File(targetContext.getExternalFilesDir(null),"coach-smoke").apply {mkdirs()}
        File(dir,name).outputStream().use {check(screenshot.compress(Bitmap.CompressFormat.PNG,100,it))};screenshot.recycle()
    }
}
