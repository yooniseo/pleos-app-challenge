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
class CoachSmokeRunner : CockpitSmokeRunner() {
    private var cockpitProbe=false
    private var live=false
    private var strict=false
    private var seconds=90
    private var actionDemo=false
    private var mapProbe=false
    private var minimumObservationSeconds=0
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments);live=arguments?.getString("live_probe")=="1"
        cockpitProbe=arguments?.getString("cockpit_probe")=="1" || arguments?.getString("auto_probe")=="1" || arguments?.getString("navigation_probe")=="1"
        strict=arguments?.getString("strict_live")=="1"
        seconds=arguments?.getString("seconds")?.toIntOrNull()?.coerceIn(15,180) ?: 90
        actionDemo=arguments?.getString("action_demo")=="1"
        mapProbe=arguments?.getString("map_probe")=="1"
        minimumObservationSeconds=arguments?.getString("minimum_seconds")?.toIntOrNull()?.coerceIn(0,seconds) ?: 0
        start()
    }
    override fun onStart() {
        if(cockpitProbe) {super.onStart();return}
        val result=Bundle()
        val journal=File(targetContext.filesDir,"driving-coach-events.jsonl")
        val original=journal.takeIf {it.exists()}?.readBytes()
        var activity: Activity?=null
        val questStore=if(actionDemo || mapProbe) com.hmgchallenge.pleosstarter.quests.QuestStore(targetContext,true) else null
        val questBefore=questStore?.load()
        val personalBefore=if(actionDemo || mapProbe) com.hmgchallenge.pleosstarter.quests.QuestStore(targetContext,false).load() else null
        var resultCode=Activity.RESULT_OK
        try {
            check(!live || (!actionDemo && !mapProbe)) {"Choose actual SDK verification or demo action testing"}
            check(android.os.Build.HARDWARE in setOf("ranchu","goldfish") || android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.MODEL.contains("sdk") || android.os.Build.MODEL.contains("emulator",true)) {"Emulator-only test"}
            val intent=Intent(targetContext,DrivingCoachActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("live_mode",live)
            if(live || actionDemo || mapProbe) runCatching {uiAutomation.grantRuntimePermission(targetContext.packageName,"android.car.permission.CAR_SPEED")}
            activity=startActivitySync(intent);waitForIdleSync()
            val current=activity
            if(mapProbe) {
                val a=current as DrivingCoachActivity
                val deadline=android.os.SystemClock.elapsedRealtime()+45_000
                var before=emptyMap<String,String>()
                var ready=false
                do {
                    runOnMainSync {
                        before=a.window.decorView.findViewWithTag<CoachMapView>("coach_map").diagnostics()
                        ready=before["visibleTiles"]!!.toInt()>0 && before["routePoints"]!!.toInt()>1 &&
                            a.window.decorView.findViewWithTag<Button>("demo_action_SIGNAL").isEnabled
                    }
                    if(!ready)Thread.sleep(300)
                } while(!ready && android.os.SystemClock.elapsedRealtime()<deadline)
                check(ready) {"Map tiles, route or stationary controls unavailable: $before"}
                capture("map-ready.png")
                click(a,"demo_action_SIGNAL");click(a,"demo_action_MIRROR");click(a,"demo_action_LANE_CHANGE")
                assertText(a,"오른쪽 1개 남음");Thread.sleep(1_700);capture("map-one-lane.png")
                click(a,"demo_action_MIRROR");click(a,"demo_action_LANE_CHANGE")
                click(a,"demo_action_STOP");click(a,"demo_action_SURROUNDINGS");click(a,"demo_action_TURN");click(a,"demo_action_EXIT")
                Thread.sleep(1_700);waitForIdleSync()
                var after=emptyMap<String,String>()
                runOnMainSync {after=a.window.decorView.findViewWithTag<CoachMapView>("coach_map").diagnostics()}
                check(after["progress"]=="1.0");check(before["marker"]!=after["marker"])
                capture("map-complete.png")
                click(a,"vehicle_light");Thread.sleep(3_000)
                click(a,"vehicle_light");Thread.sleep(200)
                runOnMainSync {a.finish()};Thread.sleep(2_000)
                var observed=emptyList<ApiObservation>()
                runOnMainSync {observed=a.runtimeObservations()}
                check(observed.any {it.api=="GEAR" && it.kind=="VALUE"})
                check(observed.any {it.api=="SEAT_BELT" && it.kind=="VALUE"})
                check(observed.any {it.api=="PARKING_BRAKE" && it.kind=="VALUE"})
                val light=observed.filter {it.api=="AMBIENT"}
                check(light.none {it.kind=="PULSE_SET"} || light.any {it.kind=="RESTORED"}) {"Ambient original color restoration not confirmed"}
                if(light.any {it.kind=="PULSE_SET"}) {
                    check(light.count {it.kind=="PULSE_SET"}==light.count {it.kind=="RESTORED"}) {"Ambient pulse left active on exit"}
                    val original=light.first {it.kind=="ORIGINAL_COLOR"}.values["argb"]
                    check(light.lastOrNull {it.kind=="RESTORED_VALUE"}?.values?.get("argb")==original) {"Original color readback did not match"}
                }
                check(com.hmgchallenge.pleosstarter.quests.QuestStore(targetContext,false).load()==personalBefore)
                val proof=org.json.JSONObject().apply {
                    put("result","PASS_MAP_VEHICLE");put("before",org.json.JSONObject(before));put("after",org.json.JSONObject(after))
                    put("ambientColorConfirmed",light.any {it.kind=="PULSE_VALUE" && it.values["argb"]?.toIntOrNull()?.and(0x00FFFFFF)==0x65E9C5})
                    put("observations",org.json.JSONArray().apply {observed.filter {it.api in setOf("GEAR","SEAT_BELT","PARKING_BRAKE","AMBIENT","AMBIENT_CAPABILITY")}.forEach {s->put(org.json.JSONObject().apply {
                        put("api",s.api);put("kind",s.kind);put("values",org.json.JSONObject(s.values))
                    })}})
                }
                File(targetContext.filesDir,"map-vehicle-proof.json").writeText(proof.toString(2))
                result.putString("result","PASS_MAP_VEHICLE");result.putString("ambient",if(light.any {it.kind=="COLOR_UNCONFIRMED"})"REQUEST_ACCEPTED_COLOR_UNCONFIRMED" else light.joinToString {it.kind})
            } else if(actionDemo) {
                val deadline=android.os.SystemClock.elapsedRealtime()+20_000
                var ready=false
                do {
                    runOnMainSync {ready=current.window.decorView.findViewWithTag<Button>("demo_action_SIGNAL")?.isEnabled==true}
                    if(!ready) Thread.sleep(250)
                } while(!ready && android.os.SystemClock.elapsedRealtime()<deadline)
                check(ready) {"Stationary demo controls unavailable"}
                capture("actions-signal.png")
                click(current,"demo_action_SIGNAL")
                click(current,"demo_obstruction")
                runOnMainSync {check(!current.window.decorView.findViewWithTag<Button>("demo_action_MIRROR").isEnabled)}
                capture("actions-wait-for-gap.png")
                click(current,"demo_obstruction")
                repeat(2) {click(current,"demo_action_MIRROR");click(current,"demo_action_LANE_CHANGE")}
                capture("actions-stop.png")
                click(current,"demo_action_STOP");click(current,"demo_action_SURROUNDINGS")
                click(current,"demo_action_TURN");click(current,"demo_action_EXIT")
                assertText(current,"체험을 마쳤어요")
                capture("actions-complete.png")
                val events=CoachRepository(targetContext).eventsAfter().filter {it.situationInstanceId==CoachRepository(targetContext).eventsAfter().last().situationInstanceId}
                check(events.all {it.source==EvidenceSource.DEMO}) {"Demo actions leaked into actual evidence"}
                check(events.count {it.eventType==CoachEventType.PHASE_RECORDED}==3)
                val questState=checkNotNull(questStore).load()
                check(questState.session?.situations?.size==3)
                check(questState.practiceCount(com.hmgchallenge.pleosstarter.quests.CoachSkill.INTERSECTION)>0)
                check(com.hmgchallenge.pleosstarter.quests.QuestStore(targetContext,false).load()==personalBefore)
                click(current,"feedback");capture("linked-feedback.png")
                click(current,"review_confirm")
                check(questStore.load().session?.reviewed==true)
                val monitor=addMonitor("com.hmgchallenge.pleosstarter.quests.QuestActivity",null,false)
                click(current,"review_quests")
                val questActivity=checkNotNull(waitForMonitorWithTimeout(monitor,10_000))
                waitForIdleSync();capture("linked-quests.png")
                runOnMainSync {questActivity.finish()};removeMonitor(monitor)
                result.putString("result","PASS_DEMO_ACTIONS");result.putString("source","DEMO")
            } else if(live && strict) {
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
            if(!actionDemo && !mapProbe && !(live && strict)) result.putString("result",if(live) "OBSERVATION_FINISHED_NOT_VERIFIED" else "PASS")
        } catch (error: Throwable) {
            result.putString("result","FAIL: ${error.stackTraceToString()}");resultCode=Activity.RESULT_CANCELED
        } finally {
            activity?.let {a->runOnMainSync {a.finish()}}
            if(questBefore!=null) questStore?.save(questBefore)
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
        val minimumDeadline=android.os.SystemClock.elapsedRealtime()+minimumObservationSeconds*1_000L
        var spoken=false
        var samples=emptyList<ApiObservation>()
        var statuses=emptyMap<String,String>()
        do {
            runOnMainSync {
                check(!a.isDemoModeForDiagnostics()) {"Actual SDK verification interrupted by demo mode"}
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
            if(v.allVerified && android.os.SystemClock.elapsedRealtime()>=minimumDeadline) break
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
        val internal=File(targetContext.filesDir,"coach-smoke").apply {mkdirs()}
        File(internal,name).outputStream().use {check(screenshot.compress(Bitmap.CompressFormat.PNG,100,it))}
        File(internal,name).copyTo(File(dir,name),overwrite=true)
        screenshot.recycle()
    }
}
