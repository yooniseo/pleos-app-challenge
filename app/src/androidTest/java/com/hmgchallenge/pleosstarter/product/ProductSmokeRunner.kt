package com.hmgchallenge.pleosstarter.product

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import androidx.room.Room
import com.hmgchallenge.pleosstarter.app.*
import com.hmgchallenge.pleosstarter.core.data.*
import com.hmgchallenge.pleosstarter.driving.*
import com.hmgchallenge.pleosstarter.quests.*
import com.hmgchallenge.pleosstarter.session.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Tests own a separate on-disk database. No user progress, prefs, or Virtual Console mutation. */
class ProductSmokeRunner: VehicleQuestSmokeRunner() {
    private lateinit var output: File
    private var realTime=false
    private var focusedReal=false
    private var mirrorTest: String?=null
    private var vehicleQuests=false
    private var questDesign=false
    private var analysisMerge=false
    private var liveConsole: String?=null
    override fun onCreate(arguments: Bundle?) {mirrorTest=arguments?.getString("mirror");liveConsole=arguments?.getString("live_console");analysisMerge=arguments?.getString("analysis_merge")=="true";questDesign=arguments?.getString("quest_design")=="true";vehicleQuests=arguments?.getString("vehicle_quests")=="true";realTime=arguments?.getString("real_time")=="true";focusedReal=arguments?.getString("focused_real")=="true";super.onCreate(arguments);start()}
    override fun onStart() {
        if(mirrorTest!=null){runMirrorValidation(mirrorTest=="combined");return}
        if(liveConsole!=null){runLiveConsoleValidation(liveConsole!!);return}
        if(analysisMerge){runAnalysisMergeValidation();return}
        if(questDesign){runQuestDesignValidation();return}
        if(vehicleQuests){runVehicleQuestValidation();return}
        val result=Bundle();var activity: ChageunActivity?=null
        val previous=SharedCoachRepository.testOverride
        val name="product-validation.db"
        targetContext.deleteDatabase(name)
        var repository=SharedCoachRepository(Room.databaseBuilder(targetContext,TripDatabase::class.java,name).addMigrations(TripDatabase.MIGRATION_1_2).build())
        SharedCoachRepository.testOverride=repository
        output=File(targetContext.filesDir,"product-validation").apply {mkdirs()}
        val proof=JSONObject().put("clock",if(focusedReal)"combined road-to-parking session and tab-switch lane use real elapsed time; standalone fixtures use virtual elapsed time" else if(realTime)"all four fixtures and the tab-switch lane run use real elapsed time" else "four fixtures use virtual elapsed time; one lane run uses real time")
        try {
            check(android.os.Build.HARDWARE in setOf("ranchu","goldfish")) {"Emulator-only probe"}
            QuestStore(targetContext,true).resetDemoProgress()
            QuestStore(targetContext,false).save(QuestState(demo=false))
            repository.saveSnapshot("settings:product","{\"voice\":true,\"celebration\":false}")
            val emptyIntent=Intent(targetContext,ChageunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("empty_preview",true)
            activity=startActivitySync(emptyIntent) as ChageunActivity;waitForIdleSync()
            val empty=activity
            AppScreen.values().forEach {screen->
                runOnMainSync {empty.show(screen)};waitForIdleSync();capture(empty,"empty-${screen.name}")
                check(repository.trips().isEmpty() && repository.points("demo")==0)
            }
            runOnMainSync {empty.finish()};waitForIdleSync()
            val normal=Intent(targetContext,ChageunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity=startActivitySync(normal) as ChageunActivity;waitForIdleSync()
            val app=activity
            runOnMainSync {
                app.openSimulationTools()
                app.window.decorView.findViewWithTag<Button>("indicator_off").performClick()
                check(app.session.scenario.indicator==Indicator.OFF)
                check(ScenarioCoachingCues.preparation(CoachingStep.TURN_PREPARE,app.session.scenario.indicator).full.contains("깜빡이를 켜고"))
            }
            capture(app,"indicator-off")
            runOnMainSync {
                app.window.decorView.findViewWithTag<Button>("indicator_right").performClick()
                check(app.session.scenario.indicator==Indicator.RIGHT)
                check(!ScenarioCoachingCues.preparation(CoachingStep.TURN_PREPARE,app.session.scenario.indicator).full.contains("깜빡이"))
            }
            capture(app,"indicator-right")
            runOnMainSync {app.window.decorView.findViewWithTag<Button>("indicator_live").performClick();check(app.session.indicatorOverride==null)}
            runOnMainSync {app.openSimulationTools(false)}
            proof.put("indicatorButtons","PASS: OFF reminder, RIGHT suppresses reminder, SDK input selectable")
            val runs=JSONArray()
            ReplayCatalog.all.forEachIndexed {i,definition->
                val playReal=realTime || (focusedReal && definition.lesson==RoadLesson.COMBINED)
                var id=""
                var speechFrom=0L
                runOnMainSync {
                    app.select(definition.lesson)
                    val session=app.session
                    val epoch=System.currentTimeMillis()+(if(playReal)0L else i*200_000L)
                    val monotonic=SystemClock.elapsedRealtime()
                    check(session.start(epoch))
                    speechFrom=System.currentTimeMillis()
                    id=session.tripId!!
                    var tick=0
                    while(!playReal && session.completedAt==null && tick<1800) {
                        tick++;session.tick(100,monotonic+tick*100,null,false,epoch+tick*100)
                    }
                    if(!playReal)check(session.scenario.complete && session.completedAt!=null && session.error==null) {"Incomplete ${definition.id}: ${session.error}"}
                    // Finalization must be idempotent even when a callback is replayed.
                    if(!playReal)repeat(10){session.tick(100,monotonic+200_000,null,false,epoch+200_000)}
                }
                if(playReal) {
                    val phases=JSONArray();val seen=mutableSetOf<String>()
                    val deadline=SystemClock.elapsedRealtime()+180_000
                    while(app.session.completedAt==null && SystemClock.elapsedRealtime()<deadline) {
                        check(app.session.tripId==id) {"Selected practice changed during validation"}
                        var stage: CoachingStep?=null
                        runOnMainSync {
                            if(app.session.paused && !app.session.scenario.complete)check(app.session.start())
                            val s=app.session.scenario;val cue=ScenarioCoachingCues.at(s)
                            if(definition.lesson==RoadLesson.COMBINED && s.lesson==RoadLesson.PARKING && !s.complete) {
                                check(app.session.completedAt==null)
                                check(repository.trip(id)?.endedAt==null)
                                check(app.screen!=AppScreen.FEEDBACK)
                                proof.put("parkingTransition","PASS: same trip continues into parking; no clear or trip end before parking completion")
                            }
                            if(seen.add(cue.identity)) {
                                stage=cue.step
                                phases.put(JSONObject().put("step",cue.step.name).put("metres",s.distance).put("wheel",s.steeringTarget).put("lesson",s.lesson.name).put("lane",s.currentLane).put("bodyHeading",s.frame().bearing).put("text",cue.full))
                            }
                        }
                        stage?.let {capture(app,"phase-${definition.id}-${phases.length()}-${it.name}")}
                        Thread.sleep(100)
                    }
                    check(app.session.scenario.complete) {"Real time incomplete ${definition.id}"}
                    proof.put("phases-${definition.id}",phases)
                    var voiceEvents=emptyList<ApiObservation>()
                    runOnMainSync {voiceEvents=app.voiceDiagnostics().filter {it.timestamp>=speechFrom}}
                    proof.put("voice-${definition.id}",JSONArray(voiceEvents.map {JSONObject().put("api",it.api).put("kind",it.kind).put("at",it.timestamp).put("values",JSONObject(it.values))}))
                }
                waitForIdleSync();Thread.sleep(200)
                val events=repository.events(id)
                val earned=events.filter {it.type=="PRACTICE_REWARD"}
                val actual=earned.map {JSONObject(it.payload).getString("skill")}.toSet()
                check(actual==definition.expectedSkills) {"Wrong evidence: $actual vs ${definition.expectedSkills}"}
                check(earned.size==definition.rewardSkills.size)
                check(repository.tripPoints(id)==definition.rewardSkills.sumOf {PracticeRewards.points(it)})
                check(repository.trip(id)?.distanceKm!!>0 && repository.trip(id)?.endedAt!=null)
                check(repository.samples(id).size>=5 && repository.report(id)!=null)
                capture(app,"complete-${definition.id}")
                runs.put(JSONObject().put("fixture",definition.id).put("tripId",id).put("metres",repository.trip(id)!!.distanceKm!!*1000)
                    .put("samples",repository.samples(id).size).put("skills",JSONArray(actual.toList())).put("points",repository.tripPoints(id)))
            }
            check(repository.trips().size==4 && repository.points("demo")==800)
            proof.put("fixtures",runs)
            var speechBaseline=0
            runOnMainSync {speechBaseline=app.voiceDiagnostics().count {it.api=="TTS" && it.kind=="PLAYBACK_STARTED"}}
            // Real-time playback continues through internal tab switches and stops while paused.
            runOnMainSync {app.select(RoadLesson.LANE);app.session.manualStyle=BeginnerStyle.STEP_BY_STEP;app.startPrepared()}
            val id=app.session.tripId!!
            Thread.sleep(1100)
            var before=0.0
            runOnMainSync {before=app.session.scenario.distance;app.show(AppScreen.QUESTS)}
            Thread.sleep(1100)
            runOnMainSync {check(app.session.tripId==id && app.session.scenario.distance>before);app.session.pause();before=app.session.scenario.distance}
            Thread.sleep(450)
            runOnMainSync {check(app.session.scenario.distance==before);app.show(AppScreen.MAP);app.window.decorView.findViewWithTag<Button>("replay_start").performClick()}
            val deadline=SystemClock.elapsedRealtime()+40_000
            while(app.session.completedAt==null && SystemClock.elapsedRealtime()<deadline)Thread.sleep(200)
            check(app.session.completedAt!=null)
            check(repository.points("demo")==1000)
            var speech=emptyList<ApiObservation>()
            val speechDeadline=SystemClock.elapsedRealtime()+6000
            do {
                runOnMainSync {speech=app.voiceDiagnostics()}
                if(speech.any {it.api=="TTS" && it.kind=="PLAYBACK_DONE"})break
                Thread.sleep(100)
            } while(SystemClock.elapsedRealtime()<speechDeadline)
            val playback=speech.filter {it.api=="TTS" && it.kind=="PLAYBACK_STARTED"}
            check(playback.size-speechBaseline in 1..10) {"Unexpected automatic speech count: ${playback.size}; $speech"}
            check(speech.any {it.api=="TTS" && it.kind=="PLAYBACK_DONE"}) {"No completed Pleos speech playback"}
            proof.put("pleosSpeech",JSONObject().put("starts",playback.size).put("completed",speech.count {it.api=="TTS" && it.kind=="PLAYBACK_DONE"}))
            proof.put("realTimeLane",JSONObject().put("tripId",id).put("metres",repository.trip(id)!!.distanceKm!!*1000).put("samples",repository.samples(id).size))
            capture(app,"real-time-completion")
            runOnMainSync {app.finish()};waitForIdleSync();Thread.sleep(250);activity=null
            repository.close()
            repository=SharedCoachRepository(Room.databaseBuilder(targetContext,TripDatabase::class.java,name).addMigrations(TripDatabase.MIGRATION_1_2).build())
            SharedCoachRepository.testOverride=repository
            check(repository.trips().size==5 && repository.points("demo")==1000)
            val reopened=ReplaySessionController(targetContext,NavigationSimulationRoute(listOf(GeoPoint(37.0,127.0),GeoPoint(37.001,127.0))))
            val personal=QuestStore(targetContext,false).load()
            check(reopened.quest.evidence.size==8)
            check(reopened.resetProgress())
            check(repository.points("demo")==0 && reopened.quest.evidence.isEmpty())
            check(repository.trips().size==5 && repository.report(id)!=null && repository.tripPoints(id)==200)
            check(QuestStore(targetContext,false).load()==personal)
            proof.put("reopen","PASS: 5 trips, 8 evidence including signaled lane change and approach deceleration, 1000 points").put("reset","PASS: current 0 points; previous reports/rewards and personal state preserved")
            // Simulate process loss without calling close(): recovery cannot award a success.
            check(reopened.start())
            val orphanId=reopened.tripId!!
            reopened.tick(100,SystemClock.elapsedRealtime(),null,false)
            val recovered=ReplaySessionController(targetContext,reopened.course)
            check(repository.snapshot("trip:$orphanId")?.let(::JSONObject)?.getString("status")=="INTERRUPTED")
            check(repository.trip(orphanId)?.endedAt!=null && repository.tripPoints(orphanId)==0)
            check(recovered.quest.session?.finishedAt!=null && recovered.quest.evidence.isEmpty())
            proof.put("processRecovery","PASS: interrupted trip closed, no completion or reward fabricated")
            result.putString("result","PASS_PRODUCT_STRUCTURE_AND_FOUR_REPLAYS")
            File(output,"proof.json").writeText(proof.toString(2))
            finish(Activity.RESULT_OK,result)
        } catch(error: Throwable) {
            result.putString("error",error.stackTraceToString());File(output,"failure.txt").writeText(error.stackTraceToString());finish(Activity.RESULT_CANCELED,result)
        } finally {
            activity?.let {runOnMainSync {it.finish()};waitForIdleSync()}
            SharedCoachRepository.testOverride=previous;repository.close()
        }
    }
    private fun capture(activity: Activity,name: String) {
        runOnMainSync {
            val view=activity.window.decorView
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image));File(output,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        }
    }
}
