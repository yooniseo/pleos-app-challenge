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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Reproduce six completed replays, then verify all six product areas on an isolated disk database. */
fun Instrumentation.runQuestDesignValidation() {
    val previous=SharedCoachRepository.testOverride
    val name="quest-design-validation.db"
    val output=File(targetContext.filesDir,"quest-design-validation").apply {mkdirs()}
    val result=Bundle();val proof=JSONObject()
    targetContext.deleteDatabase(name)
    fun open()=SharedCoachRepository(Room.databaseBuilder(targetContext,TripDatabase::class.java,name).addMigrations(TripDatabase.MIGRATION_1_2).build())
    var repository=open();SharedCoachRepository.testOverride=repository
    var activity: ChageunActivity?=null
    fun onMain(action: ()->Unit) {
        var failure: Throwable?=null
        runOnMainSync {try {action()}catch(error: Throwable){failure=error}}
        failure?.let {throw it}
    }
    fun launch(): ChageunActivity=startActivitySync(Intent(targetContext,ChageunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as ChageunActivity
    fun capture(app: Activity,label: String) {
        onMain {
            val view=app.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap));File(output,"$label.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    try {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        check(QuestStore(targetContext,true).resetDemoProgress())
        repository.saveSnapshot("settings:product","{\"voice\":false,\"celebration\":false}")
        activity=launch();var app=requireNotNull(activity);waitForIdleSync()
        listOf("arrival_search" to "짧은 자동 시연","route_steps" to "시연 경로 안내","settings" to "나에게 맞는 코치").forEach {(tag,title)->
            onMain {app.window.decorView.findViewWithTag<Button>(tag).performClick()};waitForIdleSync()
            check(uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(title).isNotEmpty()) {"Missing dialog: $tag"}
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);waitForIdleSync()
        }
        proof.put("nativeControls","PASS: search opens practice selection, maneuver card opens route steps, settings opens coach preferences")
        val epoch=System.currentTimeMillis()
        repeat(6) {i->onMain {
            app.select(RoadLesson.LANE);check(app.session.start(epoch+i*200_000))
            var ticks=0
            while(app.session.completedAt==null && ticks<2000) {
                ticks++;app.session.tick(100,SystemClock.elapsedRealtime()+ticks*100,null,false,epoch+i*200_000+ticks*100)
            }
            check(app.session.completedAt!=null)
        }}
        onMain {
            app.show(AppScreen.QUESTS)
            check(app.questDiagnostics()?.size==6)
            check(app.questDiagnostics()?.get("LANE_CHANGE")==6)
            check(app.window.decorView.findViewWithTag<Button>("quests_vehicle")==null)
            check(app.vehicleQuests.board.progressOf(com.hmgchallenge.pleosstarter.feature.quests.QuestIds.LANE_CHANGE).steps==0)
            app.show(AppScreen.MAP);app.show(AppScreen.QUESTS)
            check(app.questDiagnostics()?.get("LANE_CHANGE")==6)
        }
        waitForIdleSync();capture(app,"01-six-lane-replays")
        onMain {app.finish()};waitForIdleSync();activity=null
        repository.close();repository=open();SharedCoachRepository.testOverride=repository
        activity=launch();app=requireNotNull(activity);waitForIdleSync()
        onMain {app.show(AppScreen.QUESTS);check(app.questDiagnostics()?.get("LANE_CHANGE")==6)}
        check(repository.database.openHelper.readableDatabase.query("SELECT progress FROM Quest WHERE questId='demo:practice:LANE_CHANGE'").use {it.moveToFirst() && it.getInt(0)==6})
        proof.put("sixReplays","PASS: 6/30 in UI and Room; no vehicle-signal selector; tab switch and DB reopen preserve progress; VEHICLE remains zero")
        var id="";var combinedAt=0L;var sawApproach=false;var sawTurn=false
        onMain {
            app.select(RoadLesson.COMBINED)
            app.startPrepared(setOf(0,1,2,3));id=app.session.tripId!!;combinedAt=repository.trip(id)!!.startedAt
            check(app.session.quest.practiceCount(CoachSkill.PREPARATION)==1)
            check(!app.session.confirmChecklist(CoachSkill.FINISH,setOf(0,1,2)))
        }
        waitForIdleSync();capture(app,"07-navigation-start")
        onMain {
            val trip=app.session.tripId;val distance=app.session.travelledMetres
            app.window.decorView.findViewWithTag<Button>("map_view").performClick()
            check(app.navigationDiagnostics()?.get("mode")=="2D")
            check(app.session.tripId==trip && app.session.travelledMetres==distance)
        }
        waitForIdleSync();capture(app,"08-map-flat")
        onMain {app.toggleMapView();app.toggleQuestBannerStyle()}
        waitForIdleSync();capture(app,"09-compact-quest")
        onMain {app.toggleQuestBannerStyle()}
        proof.put("navigationControls","PASS: 2D/3D changes projection without restarting the trip; compact quest variant renders")
        var ticks=0
        while(app.session.completedAt==null && ticks<2400) {
            var captureApproach=false;var captureTurn=false
            onMain {
                repeat(10) {
                    ticks++;app.session.tick(100,SystemClock.elapsedRealtime()+ticks*100,null,false,combinedAt+ticks*100)
                }
                val scenario=app.session.scenario
                if(scenario.lesson==RoadLesson.COMBINED && !sawApproach && scenario.frame().turnRemaining in 40.0..100.0) {
                    sawApproach=true;captureApproach=true;app.show(AppScreen.MAP)
                }else if(scenario.turning && !sawTurn) {sawTurn=true;captureTurn=true;app.show(AppScreen.MAP)}
            }
            waitForIdleSync()
            if(captureApproach)capture(app,"02-mascot-approach")
            if(captureTurn)capture(app,"03-caution-turn")
        }
        check(app.session.completedAt!=null && sawApproach && sawTurn)
        check(repository.completedSkills(id)==setOf("PREPARATION","LANE_CHANGE","INTERSECTION","SPEED","PARKING"))
        onMain {
            check(app.session.confirmChecklist(CoachSkill.FINISH,setOf(0,1,2)))
            check(app.session.confirmChecklist(CoachSkill.FINISH,setOf(0,1,2)))
            check(app.session.quest.practiceCount(CoachSkill.FINISH)==1)
            app.showFeedback(id)
        }
        check(repository.completedSkills(id).size==6 && repository.tripPoints(id)==600)
        check(repository.report(id)?.goodPoints?.let(::JSONArray)?.length()==6)
        capture(app,"04-six-areas-completed")
        onMain {app.show(AppScreen.MAP);app.window.decorView.findViewWithTag<Button>("coach_toggle").performClick()}
        waitForIdleSync();capture(app,"05-coach-off")
        onMain {
            app.window.decorView.findViewWithTag<Button>("coach_toggle").performClick()
            app.window.decorView.findViewWithTag<Button>("map_quest_banner").performClick()
            check(app.screen==AppScreen.QUESTS && app.questDiagnostics()?.get("LANE_CHANGE")==8)
            check(CoachSkill.values().all {app.questDiagnostics()?.get(it.name)!!>0})
        }
        capture(app,"06-six-area-quest-board")
        onMain {
            app.select(RoadLesson.LANE);app.startPrepared()
            val cancelled=app.session.tripId!!
            app.session.tick(1000,SystemClock.elapsedRealtime()+1000,null,false)
            app.window.decorView.findViewWithTag<Button>("route_cancel").performClick()
            check(app.session.tripId==null && !app.session.scenario.started)
            check(repository.trip(cancelled)?.endedAt!=null)
            check(JSONObject(repository.snapshot("trip:$cancelled")!!).getString("status")=="INTERRUPTED")
            check(repository.tripPoints(cancelled)==0)
            check(repository.completedSkills(id).size==6 && repository.tripPoints(id)==600)
        }
        proof.put("routeCancel","PASS: partial trip ends as INTERRUPTED, no unearned reward, previous records preserved")
        onMain {app.finish()};waitForIdleSync();activity=null
        repository.close();repository=open();SharedCoachRepository.testOverride=repository
        check(repository.completedSkills(id).size==6 && repository.report(id)!=null)
        check(QuestStore(targetContext,true).load().practiceCount(CoachSkill.FINISH)==1)
        proof.put("fullDemo","PASS: preparation checklist, two lane changes, turn, approach deceleration, parking, departure checklist; 6 areas / 7 evidence, one trip, 600P")
            .put("noFalseCredit","Departure blocked before parking; duplicate checklist does not increase progress; no headway or physical sensor success inferred")
            .put("source","SIMULATION only; actual user trips.db untouched")
        File(output,"proof.json").writeText(proof.toString(2))
        result.putString("result","PASS_SIX_QUESTS_PROGRESS_ROOM_AND_FIGMA_COMPONENTS")
        finish(Activity.RESULT_OK,result)
    }catch(error: Throwable) {
        File(output,"failure.txt").writeText(error.stackTraceToString());result.putString("error",error.stackTraceToString());finish(Activity.RESULT_CANCELED,result)
    }finally {
        activity?.let {app->onMain {app.finish()};waitForIdleSync()}
        SharedCoachRepository.testOverride=previous;repository.close()
    }
}
