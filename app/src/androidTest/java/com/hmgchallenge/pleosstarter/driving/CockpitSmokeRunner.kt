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
    override fun onCreate(arguments: Bundle?) {super.onCreate(arguments);automaticProbe=arguments?.getString("auto_probe")=="1";navigationProbe=arguments?.getString("navigation_probe")=="1"}
    override fun runOnMainSync(runner: Runnable) {
        // Keep test assertion failures on the instrumentation thread so finally restores user data.
        var failure: Throwable?=null
        super.runOnMainSync {try {runner.run()}catch(error: Throwable){failure=error}}
        failure?.let {throw it}
    }
    override fun onStart() {
        val result=Bundle();var a: DrivingSimulatorActivity?=null;var launcher: Activity?=null;var code=Activity.RESULT_OK
        val demo=QuestStore(targetContext,true);val before=demo.load();val personal=QuestStore(targetContext,false).load()
        val simulatorPrefs=targetContext.getSharedPreferences("driver_simulator",0)
        val priorReport=simulatorPrefs.getString("last_report",null)
        val journal=File(targetContext.filesDir,"driving-coach-events.jsonl")
        val priorJournal=journal.takeIf {it.exists()}?.readBytes()
        try {
            check(EmulatorCockpitBridge.isAllowed(targetContext)) {"Debug emulator required"}
            runCatching {uiAutomation.grantRuntimePermission(targetContext.packageName,"android.car.permission.CAR_SPEED")}
            val monitor=addMonitor(DrivingSimulatorActivity::class.java.name,null,false)
            launcher=startActivitySync(Intent(targetContext,DrivingCoachActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("open_cockpit",true))
            a=checkNotNull(waitForMonitorWithTimeout(monitor,10_000)) as DrivingSimulatorActivity
            removeMonitor(monitor)
            waitForIdleSync();Thread.sleep(1_000)
            var taskRoot=""
            runOnMainSync {
                val tasks=(a.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).appTasks
                taskRoot=tasks.first {it.taskInfo.taskId==a.taskId}.taskInfo.baseActivity?.className.orEmpty()
            }
            check(taskRoot==DrivingSimulatorActivity::class.java.name) {"Cockpit inherited a parked-only task root: $taskRoot"}
            if(!navigationProbe)click(a,"nav_controls")
            runOnMainSync {
                if(navigationProbe)return@runOnMainSync
                val coach=a.window.decorView.findViewWithTag<android.view.View>("training_coach_panel")
                val controls=a.window.decorView.findViewWithTag<android.view.View>("training_controls_panel")
                val left=android.graphics.Rect();val right=android.graphics.Rect()
                check(coach.getGlobalVisibleRect(left) && controls.getGlobalVisibleRect(right)) {"Coach and controls must both stay visible"}
                if(a.resources.configuration.screenWidthDp>=760)check(left.right<=right.left) {"Coach and controls overlap"}
            }
            capture("cockpit-ready.png")
            if(automaticProbe || navigationProbe) {
                val proof=if(navigationProbe)runNavigationPlayback(a) else runAutomaticPlayback(a)
                runOnMainSync {a.finish()};Thread.sleep(2_500)
                val after=consoleState()
                check(after.getJSONObject("controls").getInt("speed_kph")==0)
                check(after.getJSONObject("gear").getJSONObject("actual").getString("value")=="Park")
                check(QuestStore(targetContext,false).load()==personal)
                val status=if(navigationProbe)"PASS_FIGMA_NAVIGATION" else "PASS_AUTO_PLAYBACK"
                proof.put("after",after);proof.put("result",status)
                File(targetContext.filesDir,if(navigationProbe)"figma-navigation-proof.json" else "auto-playback-proof.json").writeText(proof.toString(2))
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
            check(simulatorPrefs.getString("last_report","")!!.contains("기억한 순간")) {"Coach note was not linked to feedback"}
            val proof=JSONObject().apply {
                put("result","PASS_COCKPIT_CONTROLS");put("launchPath","COACH_ENTRY");put("taskRoot",taskRoot)
                put("coachAndControlsVisible",true);put("coachInteractionKeptSimulationRunning",true)
                put("reverseModelMeters",reverseDistance);put("during",during);put("forward",forward);put("after",after);put("sdk",JSONObject(sdk))
            }
            File(targetContext.filesDir,"cockpit-proof.json").writeText(proof.toString(2))
            result.putString("result","PASS_COCKPIT_CONTROLS")
            }
        } catch(error: Throwable) {code=Activity.RESULT_CANCELED;result.putString("result","FAIL: ${error.stackTraceToString()}")}
        finally {
            a?.let {runOnMainSync {it.finish()}};launcher?.let {runOnMainSync {it.finish()}}
            Thread.sleep(3_000);demo.save(before)
            if(priorJournal==null)journal.delete() else journal.writeBytes(priorJournal)
            simulatorPrefs.edit().apply {if(priorReport==null)remove("last_report")else putString("last_report",priorReport)}.commit()
        }
        finish(code,result)
    }
    private fun runNavigationPlayback(a: DrivingSimulatorActivity): JSONObject {
        val deadline=android.os.SystemClock.elapsedRealtime()+180_000
        var ready=false
        while(!ready && android.os.SystemClock.elapsedRealtime()<deadline) {
            runOnMainSync {ready=(a.navigationForDiagnostics()["visibleTiles"]?.toIntOrNull() ?: 0)>8}
            if(!ready)Thread.sleep(300)
        }
        check(ready) {"Navigation tiles did not load"};capture("figma-navigation-ready.png")
        click(a,"nav_auto")
        val states=mutableListOf<JSONObject>();val seen=mutableSetOf<SimStage>();val lanes=mutableSetOf<Int>()
        var complete=false;var firstMarker="";var endMarker="";var pausedMarker="";var maxSpeed=0f
        do {
            var stage=SimStage.PREPARE;var data=emptyMap<String,String>()
            runOnMainSync {
                val m=a.simulatorForDiagnostics();stage=m.stage;data=a.navigationForDiagnostics()
                lanes.add(m.currentLane);maxSpeed=maxOf(maxSpeed,a.sdkForDiagnostics()["speedMps"]?.toFloatOrNull() ?: 0f)
                check(a.window.decorView.findViewWithTag<android.view.View>("navigation_panel").isShown)
                check(!a.pausedForDiagnostics())
            }
            if(firstMarker.isBlank())firstMarker=data.getValue("marker")
            endMarker=data.getValue("marker")
            if(seen.add(stage)) {
                states.add(JSONObject(data))
                if(stage in setOf(SimStage.LANE_OUT,SimStage.TURN_CHECK,SimStage.TURN,SimStage.COMPLETE))capture("figma-navigation-${stage.name.lowercase()}.png")
                if(stage==SimStage.LANE_RETURN) {
                    click(a,"nav_pause")
                    runOnMainSync {check(a.pausedForDiagnostics());pausedMarker=a.navigationForDiagnostics().getValue("marker")}
                    Thread.sleep(400)
                    runOnMainSync {check(a.navigationForDiagnostics().getValue("marker")==pausedMarker)}
                    click(a,"nav_pause")
                }
            }
            complete=stage==SimStage.COMPLETE
            if(!complete)Thread.sleep(250)
        }while(!complete && android.os.SystemClock.elapsedRealtime()<deadline)
        check(complete && lanes==setOf(1,2,3));check(firstMarker!=endMarker);check(maxSpeed>0)
        click(a,"nav_controls")
        runOnMainSync {check(a.window.decorView.findViewWithTag<android.view.View>("training_controls_panel").isShown)}
        click(a,"sim_navigation")
        runOnMainSync {check(a.window.decorView.findViewWithTag<android.view.View>("navigation_panel").isShown)}
        return JSONObject().apply {
            put("complete",complete);put("lanes",org.json.JSONArray(lanes.sorted()));put("states",org.json.JSONArray(states))
            put("firstMarker",firstMarker);put("endMarker",endMarker);put("pauseFreezesMap",true);put("controlsRoundTrip",true);put("maxActualSdkSpeedMps",maxSpeed)
        }
    }
    private fun runAutomaticPlayback(a: DrivingSimulatorActivity): JSONObject {
        click(a,"sim_auto")
        val deadline=android.os.SystemClock.elapsedRealtime()+180_000
        val stages=linkedSetOf<SimStage>();var complete=false;var maxSdkSpeed=0f
        var physicalTurnLane=0;var stopLineClearance=0.0;var turnMidCaptured=false;val crosswalks=mutableSetOf<Int>()
        do {
            var stage=SimStage.PREPARE
            runOnMainSync {
                stage=a.simulatorForDiagnostics().stage
                check(!a.pausedForDiagnostics()) {"Automatic playback paused at $stage"}
                check(a.automaticForDiagnostics() || stage==SimStage.COMPLETE) {"Automatic playback stopped at $stage"}
                maxSdkSpeed=maxOf(maxSdkSpeed,a.sdkForDiagnostics()["speedMps"]?.toFloatOrNull() ?: 0f)
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
        check(maxSdkSpeed>0) {"No actual emulator speed observed"}
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
            put("stages",org.json.JSONArray(stages.map {it.name}));put("maxActualSdkSpeedMps",maxSdkSpeed)
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
