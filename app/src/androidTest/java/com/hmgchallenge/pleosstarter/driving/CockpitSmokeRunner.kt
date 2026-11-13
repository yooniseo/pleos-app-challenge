package com.hmgchallenge.pleosstarter.driving

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import com.hmgchallenge.pleosstarter.quests.QuestStore
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Touch controls -> Virtual Console state -> actual speed/blinker SDK callbacks. Emulator only. */
open class CockpitSmokeRunner : Instrumentation() {
    private var automaticProbe=false
    private var navigationProbe=false
    private var interactionProbe=false
    private var fastNavigationProbe=false
    override fun onCreate(arguments: Bundle?) {super.onCreate(arguments);automaticProbe=arguments?.getString("auto_probe")=="1";navigationProbe=arguments?.getString("navigation_probe")=="1";interactionProbe=arguments?.getString("interaction_probe")=="1";fastNavigationProbe=arguments?.getString("fast_navigation")=="1"}
    override fun runOnMainSync(runner: Runnable) {
        // Keep test assertion failures on the instrumentation thread so finally restores user data.
        var failure: Throwable?=null
        super.runOnMainSync {try {runner.run()}catch(error: Throwable){failure=error}}
        failure?.let {throw it}
    }
    override fun onStart() {
        val result=Bundle();var a: DrivingSimulatorActivity?=null;var launcher: Activity?=null;var code=Activity.RESULT_OK
        val watching=java.util.concurrent.atomic.AtomicBoolean(true)
        Thread {
            val main=android.os.Handler(android.os.Looper.getMainLooper())
            while(watching.get()) {
                val beat=java.util.concurrent.CountDownLatch(1);main.post {beat.countDown()}
                if(!beat.await(2,java.util.concurrent.TimeUnit.SECONDS))File(targetContext.filesDir,"cockpit-ui-stall.txt")
                    .writeText(android.os.Looper.getMainLooper().thread.stackTrace.joinToString("\n"))
                Thread.sleep(350)
            }
        }.apply {isDaemon=true;name="cockpit-test-watchdog";start()}
        val isolated=IsolatedCoachDatabase(targetContext)
        val demo=QuestStore(targetContext,true);val before=demo.load();val personal=QuestStore(targetContext,false).load()
        val simulatorPrefs=targetContext.getSharedPreferences("driver_simulator",0)
        val priorReport=simulatorPrefs.getString("last_report",null)
        val profilePrefs=targetContext.getSharedPreferences("scenario_coach",0)
        val priorStyle=profilePrefs.getString("style",null)
        val hadCelebration=profilePrefs.contains("celebration");val priorCelebration=profilePrefs.getBoolean("celebration",true)
        val journal=File(targetContext.filesDir,"driving-coach-events.jsonl")
        val priorJournal=journal.takeIf {it.exists()}?.readBytes()
        try {
            check(EmulatorCockpitBridge.isAllowed(targetContext)) {"Debug emulator required"}
            if(navigationProbe || interactionProbe)profilePrefs.edit().putString("style","STEP_BY_STEP").putBoolean("celebration",true).commit()
            runCatching {uiAutomation.grantRuntimePermission(targetContext.packageName,"android.car.permission.CAR_SPEED")}
            val monitor=addMonitor(DrivingSimulatorActivity::class.java.name,null,false)
            val entry=if(interactionProbe)checkNotNull(targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)) else Intent(targetContext,DrivingCoachActivity::class.java).putExtra("open_cockpit",true)
            launcher=startActivitySync(entry.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            a=checkNotNull(waitForMonitorWithTimeout(monitor,10_000)) as DrivingSimulatorActivity
            removeMonitor(monitor)
            waitForIdleSync();Thread.sleep(1_000)
            var taskRoot=""
            runOnMainSync {
                val tasks=(a.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).appTasks
                taskRoot=tasks.first {it.taskInfo.taskId==a.taskId}.taskInfo.baseActivity?.className.orEmpty()
            }
            check(taskRoot==DrivingSimulatorActivity::class.java.name) {"Cockpit inherited a parked-only task root: $taskRoot"}
            if(!navigationProbe && !interactionProbe)click(a,"nav_controls")
            runOnMainSync {
                if(navigationProbe || interactionProbe)return@runOnMainSync
                val coach=a.window.decorView.findViewWithTag<android.view.View>("training_coach_panel")
                val controls=a.window.decorView.findViewWithTag<android.view.View>("training_controls_panel")
                val left=android.graphics.Rect();val right=android.graphics.Rect()
                check(coach.getGlobalVisibleRect(left) && controls.getGlobalVisibleRect(right)) {"Coach and controls must both stay visible"}
                if(a.resources.configuration.screenWidthDp>=760)check(left.right<=right.left) {"Coach and controls overlap"}
            }
            capture("cockpit-ready.png")
            if(automaticProbe || navigationProbe || interactionProbe) {
                val proof=if(interactionProbe)runCoachInteraction(a)else if(navigationProbe)runNavigationPlayback(a) else runAutomaticPlayback(a)
                runOnMainSync {a.finish()};Thread.sleep(2_500)
                val after=consoleState()
                check(after.getJSONObject("controls").getInt("speed_kph")==0)
                check(after.getJSONObject("gear").getJSONObject("actual").getString("value")=="Park")
                check(QuestStore(targetContext,false).load()==personal)
                val status=if(interactionProbe)"PASS_COACH_INTERACTION" else if(navigationProbe)"PASS_FIGMA_NAVIGATION" else "PASS_AUTO_PLAYBACK"
                proof.put("after",after);proof.put("result",status)
                File(targetContext.filesDir,if(interactionProbe)"coach-interaction-proof.json" else if(navigationProbe)"figma-navigation-proof.json" else "auto-playback-proof.json").writeText(proof.toString(2))
                result.putString("result",status)
            } else {
            click(a,"sim_brake");Thread.sleep(250)
            runOnMainSync {check(a.simulatorForDiagnostics().brake==80.0)}
            click(a,"sim_brake")
            runOnMainSync {check(a.simulatorForDiagnostics().brake==0.0)}
            click(a,"sim_accelerator");click(a,"sim_brake")
            runOnMainSync {check(a.simulatorForDiagnostics().accelerator==0.0 && a.simulatorForDiagnostics().brake==80.0)}
            click(a,"sim_pedals_release")
            runOnMainSync {check(a.simulatorForDiagnostics().accelerator==0.0 && a.simulatorForDiagnostics().brake==0.0)}
            click(a,"sim_belt");touch(a,"sim_brake",true)
            click(a,"sim_parking_brake");click(a,"sim_gear_R");Thread.sleep(150)
            touch(a,"sim_brake",false)
            click(a,"sim_window_open");click(a,"sim_wiper");click(a,"sim_wiper")
            click(a,"sim_signal_left")
            var paused=false
            val cameraDeadline=android.os.SystemClock.elapsedRealtime()+5_000
            do {
                runOnMainSync {paused=a.pausedForDiagnostics()}
                if(!paused)Thread.sleep(200)
            } while(!paused && android.os.SystemClock.elapsedRealtime()<cameraDeadline)
            if(paused) {
                saveImage("cockpit-oem-camera.png")
                sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);Thread.sleep(800)
            }
            runOnMainSync {paused=a.pausedForDiagnostics()}
            if(paused)click(a,"sim_pause")
            repeat(3){click(a,"sim_steer_right")}
            touch(a,"sim_accelerator",true);Thread.sleep(4_500)
            val during=consoleState()
            val body=during.getJSONObject("body")
            check(during.getJSONObject("gear").getJSONObject("actual").getString("value")=="Park")
            var reverseDistance=0.0
            runOnMainSync {reverseDistance=a.simulatorForDiagnostics().distance}
            var debug=""
            runOnMainSync {val m=a.simulatorForDiagnostics();debug="stage=${m.stage} gear=${m.gear} belt=${m.belt} parking=${m.parkingBrake} pedal=${m.accelerator}/${m.brake} paused=${a.pausedForDiagnostics()}"}
            check(reverseDistance>0) {"Reverse model did not move: $debug root=${uiAutomation.rootInActiveWindow?.packageName}"}
            check(body.getJSONObject("windows").getInt("front_left")==50)
            check(body.getJSONObject("wipers").getJSONObject("front").getString("mode")=="Low")
            check(during.getJSONObject("lights").getString("turn_signal_mode")=="BlinkLeft")
            var sdk=emptyMap<String,String>()
            runOnMainSync {sdk=a.sdkForDiagnostics()}
            check(sdk["indicator"]=="LEFT") {"Actual blinker callback not received: $sdk"}
            capture("cockpit-controls.png")
            touch(a,"sim_accelerator",false);touch(a,"sim_brake",true);Thread.sleep(1_000);touch(a,"sim_brake",false)
            touch(a,"sim_brake",true);click(a,"sim_gear_D");touch(a,"sim_brake",false)
            touch(a,"sim_accelerator",true);Thread.sleep(3_500)
            val forward=consoleState()
            check(forward.getJSONObject("gear").getJSONObject("actual").getString("value")=="Drive")
            check(forward.getJSONObject("controls").getInt("accelerator_pct")==45)
            check(forward.getJSONObject("controls").getInt("steering_deg")==45)
            check(forward.getJSONObject("controls").getInt("speed_kph")>0)
            runOnMainSync {sdk=a.sdkForDiagnostics()}
            check((sdk["speedMps"]?.toFloatOrNull() ?: 0f)>0f) {"Actual speed callback not received: $sdk"}
            var beforeCoachClick=0.0
            runOnMainSync {beforeCoachClick=a.simulatorForDiagnostics().distance}
            click(a,"sim_coach_note");Thread.sleep(500)
            runOnMainSync {
                check(!a.pausedForDiagnostics()) {"Clicking coach paused the simulation"}
                check(a.simulatorForDiagnostics().distance>beforeCoachClick) {"Simulation stopped while using coach"}
                val cue=a.window.decorView.findViewWithTag<android.widget.TextView>("sim_cue").text.toString()
                check(cue==a.simulatorForDiagnostics().instruction()) {"Coach is not observing the current simulation"}
            }
            capture("cockpit-forward.png")
            touch(a,"sim_accelerator",false);touch(a,"sim_brake",true);Thread.sleep(1_000);touch(a,"sim_brake",false)
            runOnMainSync {check(a.simulatorForDiagnostics().stopped);a.finish()}
            Thread.sleep(2_500)
            val after=consoleState()
            check(after.getJSONObject("controls").getInt("speed_kph")==0)
            check(after.getJSONObject("gear").getJSONObject("actual").getString("value")=="Park")
            check(after.getJSONObject("lights").getString("turn_signal_mode")=="Off")
            check(QuestStore(targetContext,false).load()==personal)
            check(isolated.repository.snapshot("last-report").orEmpty().contains("기억한 순간")) {"Coach note was not linked to feedback"}
            val proof=JSONObject().apply {
                put("result","PASS_COCKPIT_CONTROLS");put("launchPath","COACH_ENTRY");put("taskRoot",taskRoot)
                put("coachAndControlsVisible",true);put("coachInteractionKeptSimulationRunning",true)
                put("reverseModelMeters",reverseDistance);put("during",during);put("forward",forward);put("after",after);put("sdk",JSONObject(sdk))
            }
            File(targetContext.filesDir,"cockpit-proof.json").writeText(proof.toString(2))
            result.putString("result","PASS_COCKPIT_CONTROLS")
            }
        } catch(error: Throwable) {code=Activity.RESULT_CANCELED;File(targetContext.filesDir,"cockpit-last-failure.txt").writeText(error.stackTraceToString());result.putString("result","FAIL: ${error.stackTraceToString()}")}
        finally {
            a?.let {runOnMainSync {it.finish()}};launcher?.let {runOnMainSync {it.finish()}}
            Thread.sleep(3_000);demo.save(before)
            if(priorJournal==null)journal.delete() else journal.writeBytes(priorJournal)
            simulatorPrefs.edit().apply {if(priorReport==null)remove("last_report")else putString("last_report",priorReport)}.commit()
            profilePrefs.edit().apply {if(priorStyle==null)remove("style")else putString("style",priorStyle)
                if(hadCelebration)putBoolean("celebration",priorCelebration)else remove("celebration")}.commit()
            isolated.close()
            watching.set(false)
        }
        finish(code,result)
    }
    private fun runCoachInteraction(a: DrivingSimulatorActivity): JSONObject {
        val entries=targetContext.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(targetContext.packageName),0)
        check(entries.size==1 && entries.single().loadLabel(targetContext.packageManager)=="차근차근")
        val before=QuestStore(targetContext,true).load().evidence.size
        capture("chageun-ready.png");click(a,"nav_steering_practice")
        fun drag(clockwise: Boolean) {runOnMainSync {
            val wheel=a.window.decorView.findViewWithTag<SimulatorWheelView>("coach_steering_guide")
            val cx=wheel.width/2f;val cy=wheel.height/2f;val radius=wheel.width*.3f;val now=android.os.SystemClock.uptimeMillis()
            fun event(action: Int,x: Float,y: Float) {val e=android.view.MotionEvent.obtain(now,now+16,action,x,y,0);wheel.dispatchTouchEvent(e);e.recycle()}
            event(android.view.MotionEvent.ACTION_DOWN,cx+radius,cy)
            event(android.view.MotionEvent.ACTION_MOVE,cx,cy+if(clockwise)radius else -radius)
            event(android.view.MotionEvent.ACTION_UP,cx,cy+if(clockwise)radius else -radius)
        }}
        drag(false);Thread.sleep(450)
        runOnMainSync {check(!a.steeringFollowForDiagnostics()!!.rewarded && a.celebrationSoundStarts()==0)}
        drag(true);drag(true);Thread.sleep(600)
        runOnMainSync {check(a.steeringFollowForDiagnostics()!!.rewarded && a.celebrationSoundStarts()==1);check(a.simpleForDiagnostics()!!.distance==0.0)}
        Thread.sleep(500);runOnMainSync {check(a.celebrationSoundStarts()==1)}
        check(QuestStore(targetContext,true).load().evidence.size==before)
        capture("chageun-steering-ding.png");click(a,"nav_steering_practice")
        click(a,"nav_profile")
        check(uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("핸들 중심 · 방향·회전량·복원 시점").first().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        click(a,"nav_scenario")
        check(uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(RoadLesson.PARKING.title).first().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        click(a,"nav_auto")
        runOnMainSync {repeat(60){a.simpleForDiagnostics()!!.advance(1000,null,false)}}
        Thread.sleep(2000)
        runOnMainSync {check(a.simpleForDiagnostics()!!.complete);check(a.navigationForDiagnostics()["coachStyle"]=="VISUAL")}
        val state=QuestStore(targetContext,true).load();check(state.evidence.size==before+1)
        check(state.session!!.situations.single().context.kind==com.hmgchallenge.pleosstarter.quests.SituationKind.PARKING)
        capture("chageun-parking-complete.png")
        return JSONObject().apply {put("launcherEntries",1);put("launcherLabel","차근차근");put("directCoachEntry",true)
            put("wrongSteeringRejected",true);put("correctSteeringDingOnce",true);put("noQuestCreditForWheelAlone",true)
            put("parkingComplete",true);put("parkingTestUsesVirtualTime",true);put("parkingEvidenceAdded",1)}
    }
    private fun runNavigationPlayback(a: DrivingSimulatorActivity): JSONObject {
        capture("simple-navigation-ready.png")
        val before=QuestStore(targetContext,true).load().evidence.size
        click(a,"nav_auto")
        val deadline=android.os.SystemClock.elapsedRealtime()+85_000
        var done=false;var laneSeen=false;var turnSeen=false;var maxSdk=0f;var maxSteering=0.0
        val states=mutableListOf<JSONObject>()
        do {
            var d=0.0;var laneDone=false;var turning=false;var data=emptyMap<String,String>()
            runOnMainSync {
                if(fastNavigationProbe && !a.pausedForDiagnostics())a.simpleForDiagnostics()!!.advance(1000,null,false)
                val s=checkNotNull(a.simpleForDiagnostics());d=s.distance;done=s.complete;laneDone=s.laneDone;turning=s.turning
                data=a.navigationForDiagnostics()
                maxSteering=maxOf(maxSteering,kotlin.math.abs(s.steeringTarget))
                check(a.window.decorView.findViewWithTag<android.view.View>("coach_steering_guide").isShown)
                check(!a.pausedForDiagnostics())
                maxSdk=maxOf(maxSdk,a.sdkForDiagnostics()["speedMps"]?.toFloatOrNull() ?: 0f)
                check(data["questSaved"]=="true")
            }
            if(d>16 && !laneSeen) {
                capture("simple-navigation-lane.png");click(a,"nav_pause")
                var marker="";runOnMainSync {marker=a.navigationForDiagnostics().getValue("marker")}
                Thread.sleep(600)
                runOnMainSync {check(marker==a.navigationForDiagnostics()["marker"])}
                click(a,"nav_pause");laneSeen=true
            }
            if(turning && !turnSeen) {capture("simple-navigation-turn.png");states.add(JSONObject(data));turnSeen=true}
            if(laneDone && states.isEmpty())states.add(JSONObject(data))
            if(!done)Thread.sleep(200)
        }while(!done && android.os.SystemClock.elapsedRealtime()<deadline)
        check(done && laneSeen && turnSeen);check(maxSdk<.1f)
        check(maxSteering>10) {"Steering guide did not change"}
        val after=QuestStore(targetContext,true).load()
        check(after.evidence.size==before+2)
        check(after.session?.finishedAt!=null)
        check(after.session!!.situations.count {it.outcome==com.hmgchallenge.pleosstarter.quests.SituationEventType.PRACTICED}==3)
        capture("simple-navigation-complete.png")
        Thread.sleep(1800)
        var ambient=emptyList<ApiObservation>();var sounds=0
        runOnMainSync {ambient=a.celebrationForDiagnostics();sounds=a.celebrationSoundStarts()}
        // Quest celebration now waits until the virtual drive ends; no large lane-change reward.
        check(sounds==1) {"Expected one end-of-drive celebration melody: $sounds"}
        check(ambient.any {it.kind=="PULSE_VALUE" && it.values["argb"]?.toIntOrNull()?.and(0x00FFFFFF)==0x65E9C5}) {"Ambient response not confirmed: $ambient"}
        check(ambient.count {it.kind=="PULSE_SET"}==ambient.count {it.kind=="RESTORED"}) {"Ambient colour not restored"}
        var completeData=emptyMap<String,String>();runOnMainSync {completeData=a.navigationForDiagnostics()}
        // Switch through the same visible source picker as the user. The bridge must release ownership.
        click(a,"nav_speed_mode")
        val choice=uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("차량 SDK · 받은 속도만큼 이동").first()
        check(choice.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(3_000)
        var sdkDistance=0.0;var sdkElapsed=0L;var actual=0f
        fun console(method: String,path: String) {
            val c=URL("http://127.0.0.1:8088$path").openConnection() as HttpURLConnection
            try {c.requestMethod=method;c.connectTimeout=2000;c.readTimeout=2000;check(c.responseCode in 200..299) {"Console $method $path: ${c.responseCode}"};c.inputStream.close()}finally{c.disconnect()}
        }
        try {
            console("POST","/browser/session?client=scenario-speed-probe")
            console("PUT","/vehicle/gear?gear=Drive")
            console("PUT","/vehicle/controls?speed=20&accelerator=0&brake=0&steering=0")
            val speedDeadline=android.os.SystemClock.elapsedRealtime()+10_000
            do {
                runOnMainSync {actual=a.sdkForDiagnostics()["speedMps"]?.toFloatOrNull() ?: 0f}
                if(actual<5)Thread.sleep(150)
            }while(actual<5 && android.os.SystemClock.elapsedRealtime()<speedDeadline)
            check(kotlin.math.abs(actual-20f/3.6f)<.1f) {"Actual SDK speed not received: $actual"}
            var start=0L;var initialDistance=0.0
            runOnMainSync {
                initialDistance=checkNotNull(a.simpleForDiagnostics()).distance
                start=android.os.SystemClock.elapsedRealtime()
                check(a.window.decorView.findViewWithTag<Button>("nav_auto").performClick())
            }
            waitForIdleSync()
            repeat(8){console("POST","/browser/session?client=scenario-speed-probe");Thread.sleep(500)}
            runOnMainSync {
                sdkElapsed=android.os.SystemClock.elapsedRealtime()-start
                check(a.window.decorView.findViewWithTag<Button>("nav_pause").performClick())
                sdkDistance=checkNotNull(a.simpleForDiagnostics()).distance-initialDistance
            }
            waitForIdleSync()
            check(kotlin.math.abs(sdkDistance-actual*sdkElapsed/1000)<1.5) {"Speed/distance mismatch: $sdkDistance m / $sdkElapsed ms / $actual mps"}
            check(consoleState().getJSONObject("controls").getInt("speed_kph")==20) {"App overwrote external console speed"}
            capture("simple-navigation-sdk-speed.png")
        }finally {
            console("POST","/browser/session?client=scenario-speed-probe")
            console("PUT","/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0")
            console("PUT","/vehicle/gear?gear=Park")
            console("POST","/browser/session?client=scenario-speed-probe&closing=1")
        }
        // The parking course is explicitly virtual and always uses replay, with its own quest.
        click(a,"nav_profile")
        val profile=uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("핸들 중심 · 방향·회전량·복원 시점").first()
        check(profile.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        click(a,"nav_scenario")
        val parkingChoice=uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(RoadLesson.PARKING.title).first()
        check(parkingChoice.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(1000)
        capture("parking-ready.png");click(a,"nav_auto")
        val parkingDeadline=android.os.SystemClock.elapsedRealtime()+60_000
        var parked=false;var reverseCaptured=false
        do {
            var reversing=false
            runOnMainSync {
                if(fastNavigationProbe)a.simpleForDiagnostics()!!.advance(1000,null,false)
                val s=checkNotNull(a.simpleForDiagnostics());parked=s.complete;reversing=s.parkingGear=="R" && s.speedMps>0
                check(s.source==ScenarioSpeed.REPLAY && s.lesson==RoadLesson.PARKING)
                check(a.navigationForDiagnostics()["coachStyle"]=="VISUAL")
            }
            if(reversing && !reverseCaptured){capture("parking-reverse.png");reverseCaptured=true}
            if(!parked)Thread.sleep(250)
        }while(!parked && android.os.SystemClock.elapsedRealtime()<parkingDeadline)
        check(parked && reverseCaptured)
        val finalState=QuestStore(targetContext,true).load()
        check(finalState.evidence.size==before+3)
        check(finalState.session!!.situations.single().context.kind==com.hmgchallenge.pleosstarter.quests.SituationKind.PARKING)
        capture("parking-complete.png");Thread.sleep(1800)
        return JSONObject().apply {
            put("replayUsesVirtualTime",fastNavigationProbe);put("sdkSpeedUsesRealTime",true)
            put("complete",true);put("states",org.json.JSONArray(states));put("completeState",JSONObject(completeData))
            put("questEvidenceAdded",after.evidence.size-before);put("pauseFreezesMap",true);put("replayVehicleSpeedMps",maxSdk)
            put("actualSdkSpeedMps",actual);put("sdkDistanceMetres",sdkDistance);put("sdkElapsedMs",sdkElapsed)
            put("externalConsoleOwnershipPreserved",true)
            put("maxSteeringDegrees",maxSteering);put("celebrationSounds",sounds);put("ambientPulseConfirmedAndRestored",true)
            put("parkingComplete",parked);put("parkingEvidenceAdded",1);put("visualProfileTested",true)
        }
    }
    private fun runAutomaticPlayback(a: DrivingSimulatorActivity): JSONObject {
        click(a,"sim_auto")
        val deadline=android.os.SystemClock.elapsedRealtime()+180_000
        val stages=linkedSetOf<SimStage>();var complete=false;var maxSdkSpeed=0f;var maxModelSpeed=0.0
        var physicalTurnLane=0;var stopLineClearance=0.0;var turnMidCaptured=false;val crosswalks=mutableSetOf<Int>()
        do {
            var stage=SimStage.PREPARE
            runOnMainSync {
                stage=a.simulatorForDiagnostics().stage
                check(!a.pausedForDiagnostics()) {"Automatic playback paused at $stage"}
                check(a.automaticForDiagnostics() || stage==SimStage.COMPLETE) {"Automatic playback stopped at $stage"}
                maxSdkSpeed=maxOf(maxSdkSpeed,a.sdkForDiagnostics()["speedMps"]?.toFloatOrNull() ?: 0f)
                maxModelSpeed=maxOf(maxModelSpeed,a.simulatorForDiagnostics().speed)
                val m=a.simulatorForDiagnostics()
                if(stage==SimStage.TURN_CHECK) {
                    physicalTurnLane=m.road.eastboundLane(m.y) ?: 0
                    stopLineClearance=m.road.junctionX-18-(m.x+m.road.carHalfLength)
                    check(physicalTurnLane==m.targetLane && stopLineClearance>0)
                }
                if(stage==SimStage.TURN || stage==SimStage.TURN_ALIGN) {
                    m.road.mainCrosswalks.forEachIndexed {i,box->if(box.contains(m.x,m.y))crosswalks.add(i)}
                }
            }
            if(stages.add(stage) && stage in setOf(SimStage.EXIT_TURN,SimStage.LANE_OUT,SimStage.TURN))capture("auto-${stage.name.lowercase()}.png")
            if(stage==SimStage.TURN && !turnMidCaptured) {
                var halfway=false
                runOnMainSync {halfway=kotlin.math.abs(a.simulatorForDiagnostics().heading-Math.PI/2)>Math.PI/4}
                if(halfway){capture("auto-turn-mid.png");turnMidCaptured=true}
            }
            complete=stage==SimStage.COMPLETE
            if(!complete)Thread.sleep(300)
        } while(!complete && android.os.SystemClock.elapsedRealtime()<deadline)
        check(complete) {"Automatic playback did not finish: $stages"}
        check(maxModelSpeed>1 && maxSdkSpeed<0.1) {"Playback must move the app model while keeping the vehicle parked"}
        check(turnMidCaptured && crosswalks.containsAll(listOf(0,3))) {"Near/outgoing crosswalks not traversed: $crosswalks"}
        runOnMainSync {
            val m=a.simulatorForDiagnostics()
            check(m.lanesRemaining==0 && m.gear==SimGear.P && m.parkingBrake && m.stopped)
            check(m.practices.count {it.id.startsWith("lane")}==2)
        }
        capture("auto-complete.png")
        click(a,"sim_rain");click(a,"sim_auto");Thread.sleep(1_500)
        runOnMainSync {check(a.simulatorForDiagnostics().rain && a.simulatorForDiagnostics().wiper==2)}
        click(a,"sim_pause")
        runOnMainSync {
            check(a.pausedForDiagnostics())
            check(a.window.decorView.findViewWithTag<Button>("sim_resume_inline").isShown)
            check(a.window.decorView.findViewWithTag<android.widget.TextView>("sim_cue").text.contains("일시정지"))
        }
        click(a,"sim_check")
        runOnMainSync {check(a.pausedForDiagnostics());check(!a.automaticForDiagnostics())}
        click(a,"sim_resume_inline")
        runOnMainSync {check(!a.pausedForDiagnostics())}
        click(a,"sim_auto");Thread.sleep(300);click(a,"sim_steer_right")
        runOnMainSync {check(!a.automaticForDiagnostics());check(a.simulatorForDiagnostics().steering==15.0)}
        return JSONObject().apply {
            put("stages",org.json.JSONArray(stages.map {it.name}));put("maxActualSdkSpeedMps",maxSdkSpeed);put("maxModelSpeedMps",maxModelSpeed)
            put("complete",true);put("pausePromptVisible",true);put("manualOverride",true)
            put("physicalTurnLane",physicalTurnLane);put("stopLineClearanceMetres",stopLineClearance)
            put("crossedMainCrosswalks",org.json.JSONArray(crosswalks.sorted()))
        }
    }
    private fun consoleState(): JSONObject {
        val c=URL("http://127.0.0.1:8088/vehicle/state").openConnection() as HttpURLConnection
        try {c.connectTimeout=2_000;c.readTimeout=2_000;check(c.responseCode==200);return JSONObject(c.inputStream.bufferedReader().use {it.readText()})}
        finally {c.disconnect()}
    }
    private fun click(a: Activity,key: String) {runOnMainSync {check(a.window.decorView.findViewWithTag<Button>(key).performClick())};waitForIdleSync()}
    private fun touch(a: DrivingSimulatorActivity,key: String,down: Boolean) {runOnMainSync {
        val model=a.simulatorForDiagnostics()
        val enabled=if(key=="sim_brake")model.brake>0 else model.accelerator>0
        if(enabled!=down)check(a.window.decorView.findViewWithTag<Button>(key).performClick())
    }}
    private fun capture(name: String) {
        val deadline=android.os.SystemClock.elapsedRealtime()+15_000
        while(uiAutomation.rootInActiveWindow?.packageName!=targetContext.packageName && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(250)
        check(uiAutomation.rootInActiveWindow?.packageName==targetContext.packageName) {"Cockpit is not foreground"}
        saveImage(name)
    }
    private fun saveImage(name: String) {
        val image=checkNotNull(uiAutomation.takeScreenshot());val dir=File(targetContext.filesDir,"coach-smoke").apply {mkdirs()}
        File(dir,name).outputStream().use {check(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
    }
}
