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
import com.hmgchallenge.pleosstarter.analysis.*
import com.hmgchallenge.pleosstarter.app.*
import com.hmgchallenge.pleosstarter.core.data.*
import com.hmgchallenge.pleosstarter.driving.RoadLesson
import com.hmgchallenge.pleosstarter.session.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Synthetic driving remains SIMULATION in an isolated on-disk DB; live SDK checks only read. */
fun Instrumentation.runAnalysisMergeValidation() {
    val previous=SharedCoachRepository.testOverride
    val name="analysis-merge-validation.db"
    val output=File(targetContext.filesDir,"analysis-merge-validation").apply {mkdirs()}
    val result=Bundle();val proof=JSONObject()
    var activity: ChageunActivity?=null
    targetContext.deleteDatabase(name)
    fun open()=SharedCoachRepository(Room.databaseBuilder(targetContext,TripDatabase::class.java,name).addMigrations(TripDatabase.MIGRATION_1_2).build())
    var repository=open();SharedCoachRepository.testOverride=repository
    fun onMain(action: ()->Unit) {
        var failure: Throwable?=null
        runOnMainSync {try {action()}catch(e: Throwable){failure=e}}
        failure?.let {throw it}
    }
    fun capture(app: Activity,label: String) {onMain {
        val view=app.window.decorView;val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image));File(output,"$label.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }}
    try {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        check(com.hmgchallenge.pleosstarter.quests.QuestStore(targetContext,true).resetDemoProgress())
        repository.saveSnapshot("settings:product","{\"voice\":false,\"celebration\":false}")
        activity=startActivitySync(Intent(targetContext,ChageunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as ChageunActivity
        val app=activity;waitForIdleSync()
        onMain {
            check(app.session.indicatorOverride==null)
            for(signal in listOf(com.hmgchallenge.pleosstarter.driving.Indicator.RIGHT,com.hmgchallenge.pleosstarter.driving.Indicator.LEFT,com.hmgchallenge.pleosstarter.driving.Indicator.OFF)) {
                app.session.updateIndicator(signal);check(app.session.scenario.indicator==signal)
            }
            app.openSimulationTools();app.setIndicator(com.hmgchallenge.pleosstarter.driving.Indicator.OFF)
            app.openSimulationTools(false);check(app.session.indicatorOverride==null)
        }
        proof.put("indicatorRouting","PASS: default SDK input follows RIGHT/LEFT/OFF; closing simulation tools restores SDK instead of leaving hidden OFF override")
        onMain {app.showRecommendations();check(app.recommendationDiagnostics()?.recommendations?.isEmpty()==true)}
        val controller=VehicleQuestController(targetContext,"SIMULATION")
        var now=10_000L;val epoch=System.currentTimeMillis();var id=""
        fun speed(value: Double?,timestamp: Long=now) {
            controller.update(VehicleQuestInput(null,0,null,0,null,0,value,timestamp),now,epoch+now)
        }
        repeat(3) {
            now+=10_000;speed(0.0)
            // Multiple UI ticks reuse a sample; the zero sample must retain the sensor's receipt time.
            val zeroAt=now
            repeat(10){now+=50;speed(0.0,zeroAt)}
            now=zeroAt+1000;speed(15.0/3.6)
            id=controller.tripId!!
            repeat(180) {i->
                now+=1000
                val kmh=if(i<12)15.0+i*3 else if(i in setOf(45,90,135))30.0 else 50.0
                speed(kmh/3.6)
            }
            check(controller.finish(at=epoch+now))
            val samples=repository.samples(id)
            check(samples.first().speedKmh==0f && samples[1].t-samples[0].t==1000L)
            check(repository.scores(id)?.scores?.get(DrivingSkill.BRAKING)!!<60)
            check(repository.events(id).none {it.type=="VEHICLE_QUEST_ATTEMPT"})
        }
        check(repository.recommendations("SIMULATION").recommendations.any {it.weakness=="BRAKING"})
        check(repository.recommendations("VEHICLE").tripCount==0)
        proof.put("speedOnlyRecording","PASS: moving speed starts recording without signal/steering/brake; initial zero keeps sensor time; samples and scores persist; no maneuver credit")
        onMain {
            app.showFeedback(id)
            repeat(2){check(app.saveFeedbackNote("주차가 편했어요","PARKING",false))}
            check(repository.recommendations("SIMULATION").recommendations.none {it.weakness=="PARKING"})
            repeat(2){check(app.saveFeedbackNote("핸들 복원이 어려웠어요","PARKING",true))}
            app.showFeedback(id)
        }
        waitForIdleSync();capture(app,"01-feedback-scores")
        onMain {
            app.window.decorView.findViewWithTag<Button>("feedback_recommend").performClick()
            check(app.recommendationDiagnostics()?.recommendations?.any {it.weakness=="PARKING"}==true)
            check(app.window.decorView.findViewWithTag<Button>("quests_vehicle")==null)
        }
        waitForIdleSync();capture(app,"02-personalized-quests")
        onMain {
            app.window.decorView.findViewWithTag<Button>("quests_demo").performClick()
            check(app.questDiagnostics()?.size==6)
            app.window.decorView.findViewWithTag<Button>("tab_QUESTS").performClick()
            check(app.recommendationDiagnostics()!=null)
        }
        // Replay notes are usable, but authored speed/steering never become physical performance scores.
        onMain {
            app.select(RoadLesson.PARKING);app.startPrepared()
            var tick=0
            while(app.session.completedAt==null && tick<2000){tick++;app.session.tick(100,SystemClock.elapsedRealtime()+tick*100,null,false,epoch+tick*100)}
            check(app.session.completedAt!=null)
            app.showFeedback(app.session.tripId!!)
            check(repository.events(app.session.tripId!!).count {it.type=="NEXT_PRACTICE"}==1)
            check(repository.recommendations("REPLAY").recommendations.single().weakness=="PARKING")
            repeat(2){check(app.saveFeedbackNote("후진 주차","PARKING",true))}
            app.window.decorView.findViewWithTag<Button>("feedback_recommend").performClick()
            check(app.recommendationDiagnostics()?.averages?.values?.all {it==null}==true)
            check(app.recommendationDiagnostics()?.recommendations?.single()?.weakness=="PARKING")
        }
        waitForIdleSync();capture(app,"03-replay-note-recommendation")
        onMain {app.showQuestProgress()};waitForIdleSync();capture(app,"04-six-area-records")
        proof.put("personalization","PASS: positive notes do not create weakness; difficult notes create parking recommendation; feedback button uses matching source; old/new UI reachable; replay has no skill scores")
        onMain {app.show(AppScreen.MAP)}
        val deadline=SystemClock.elapsedRealtime()+10_000
        while(SystemClock.elapsedRealtime()<deadline) {
            var count=0
            onMain {count=app.vehicleDiagnostics().count {it.api=="VEHICLE_SPEED" && it.kind=="VALUE"}}
            if(count>=5)break
            Thread.sleep(250)
        }
        onMain {
            val speeds=app.vehicleDiagnostics().filter {it.api=="VEHICLE_SPEED" && it.kind=="VALUE"}
            check(speeds.size>=5) {"Live speed not refreshed: ${app.liveDrivingDiagnostics()}"}
            proof.put("liveSpeed",JSONArray(speeds.map {JSONObject().put("at",it.timestamp).put("values",JSONObject(it.values))}))
            proof.put("liveState",JSONObject(app.liveDrivingDiagnostics()))
        }
        capture(app,"05-live-speed-status")
        onMain {
            app.showRecommendations()
            check(app.window.decorView.findViewWithTag<Button>("recommend_to_vehicle")==null)
            check(app.recommendationDiagnostics()!=null)
        }
        proof.put("automaticConnection","SDK inputs arrive on the map. Three successive trips are recorded without a start or reconnect action.")
        onMain {app.finish()};waitForIdleSync();activity=null
        repository.close();repository=open();SharedCoachRepository.testOverride=repository
        check(repository.recommendations("SIMULATION").recommendations.any {it.weakness=="PARKING"})
        check(repository.recommendations("REPLAY").recommendations.single().weakness=="PARKING")
        check(repository.scores(id)?.scores?.get(DrivingSkill.BRAKING)!!<60)
        proof.put("reopen","PASS: reports, notes, recommendations survive DB reopen; test records remain SIMULATION, user trips.db untouched")
        File(output,"proof.json").writeText(proof.toString(2))
        result.putString("result","PASS_ANALYSIS_PERSONALIZATION_AND_LIVE_SPEED")
        finish(Activity.RESULT_OK,result)
    }catch(e: Throwable) {
        File(output,"failure.txt").writeText(e.stackTraceToString());result.putString("error",e.stackTraceToString());finish(Activity.RESULT_CANCELED,result)
    }finally {
        activity?.let {onMain {it.finish()};waitForIdleSync()}
        SharedCoachRepository.testOverride=previous;repository.close()
    }
}
