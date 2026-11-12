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
    private var strict=false
    private var seconds=90
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments);live=arguments?.getString("live_probe")=="1"
        strict=arguments?.getString("strict_live")=="1"
        seconds=arguments?.getString("seconds")?.toIntOrNull()?.coerceIn(15,180) ?: 90
        start()
    }
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
            if(live) runCatching {uiAutomation.grantRuntimePermission(targetContext.packageName,"android.car.permission.CAR_SPEED")}
            activity=startActivitySync(intent);waitForIdleSync()
            val current=activity
            if(live && strict) {
                val verification=verifyActualCallbacks(current as DrivingCoachActivity,seconds)
                result.putString("verification",verification.toString())
                result.putString("evidence_file",File(targetContext.filesDir,"pleos-runtime-proof.json").absolutePath)
                result.putString("result",if(verification.allVerified) "PASS_ACTUAL_CALLBACKS" else "INCOMPLETE_ACTUAL_CALLBACKS")
                resultCode=if(verification.allVerified) Activity.RESULT_OK else Activity.RESULT_CANCELED
            } else if(live) {
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
            if(!(live && strict)) result.putString("result",if(live) "OBSERVATION_FINISHED_NOT_VERIFIED" else "PASS")
        } catch (error: Throwable) {
            result.putString("result","FAIL: ${error.stackTraceToString()}");resultCode=Activity.RESULT_CANCELED
        } finally {
            activity?.let {a->runOnMainSync {a.finish()}}
            if(original==null) journal.delete() else journal.writeBytes(original)
        }
        finish(resultCode,result)
    }
    private fun verifyActualCallbacks(a: DrivingCoachActivity,seconds: Int): RuntimeVerification {
        runOnMainSync {check(!a.isDemoModeForDiagnostics()) {"Actual SDK probe cannot run in demo mode"}}
        val dir=File(targetContext.getExternalFilesDir(null),"coach-smoke").apply {mkdirs()}
        val report=File(dir,"pleos-runtime-proof.json")
        val internalReport=File(targetContext.filesDir,"pleos-runtime-proof.json")
        val deadline=android.os.SystemClock.elapsedRealtime()+seconds*1_000L
        var spoken=false
        var samples=emptyList<ApiObservation>()
        var statuses=emptyMap<String,String>()
        do {
            runOnMainSync {
                samples=a.runtimeObservations();statuses=a.connectionStatus()
                if(!spoken && samples.any {it.api=="TTS" && it.kind=="CONNECTED"}) spoken=a.speakConnectionCheckForTest()
            }
            val v=RuntimeVerification.from(samples)
            val json=org.json.JSONObject().apply {
                put("source","PLEOS_EMULATOR_SDK_CALLBACKS");put("appDemoMode",false)
                put("allVerified",v.allVerified);put("signalValueReceived",v.signalValueReceived)
                put("signalSubscriptionReceived",v.signalSubscriptionReceived);put("speedValueReceived",v.speedValueReceived)
                put("tbtValueReceived",v.tbtValueReceived);put("speechPlaybackCompleted",v.speechPlaybackCompleted)
                put("statuses",org.json.JSONObject(statuses))
                put("observations",org.json.JSONArray().apply {samples.forEach {s->put(org.json.JSONObject().apply {
                    put("api",s.api);put("kind",s.kind);put("timestamp",s.timestamp);put("values",org.json.JSONObject(s.values))
                })}})
            }
            report.writeText(json.toString(2))
            internalReport.writeText(json.toString(2))
            if(v.allVerified) break
            Thread.sleep(2_000)
        } while(android.os.SystemClock.elapsedRealtime()<deadline)
        return RuntimeVerification.from(samples)
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
