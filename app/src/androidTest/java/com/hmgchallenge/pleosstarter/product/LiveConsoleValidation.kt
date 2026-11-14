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
import com.hmgchallenge.pleosstarter.analysis.DrivingSkill
import com.hmgchallenge.pleosstarter.app.*
import com.hmgchallenge.pleosstarter.core.data.*
import com.hmgchallenge.pleosstarter.feature.quests.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Watches the original branch's external Virtual Console scripts through the real SDK. */
fun Instrumentation.runLiveConsoleValidation(mode: String) {
    val previous=SharedCoachRepository.testOverride
    val name="live-console-$mode.db"
    val output=File(targetContext.filesDir,"live-console-$mode").apply {mkdirs()}
    val result=Bundle();val proof=JSONObject().put("environment","Pleos emulator SDK; separate test database, not physical vehicle validation")
    var activity: ChageunActivity?=null
    targetContext.deleteDatabase(name)
    val repository=SharedCoachRepository(Room.databaseBuilder(targetContext,TripDatabase::class.java,name).addMigrations(TripDatabase.MIGRATION_1_2).build())
    SharedCoachRepository.testOverride=repository
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
        File(output,"ready.json").writeText("{\"ready\":true}")
        sendStatus(1,Bundle().apply {putString("phase","READY_FOR_ORIGINAL_CONSOLE_SCRIPT_$mode")})
        val deadline=SystemClock.elapsedRealtime()+240_000
        var moving=false;var right=false;var off=false;var captured=false;var stillSince=0L;var id: String?=null;var divergence=0
        val states=JSONArray();var lastState=""
        while(SystemClock.elapsedRealtime()<deadline) {
            var speed=0.0;var indicator="";var motion=""
            onMain {
                val d=app.liveDrivingDiagnostics();motion=d.getValue("motion");indicator=d.getValue("sdkIndicator")
                speed=d.getValue("speedMps").toDoubleOrNull() ?: 0.0
                id=app.vehicleQuests.tripId ?: id
                check(d.getValue("indicatorInput")=="SDK")
                divergence=if(d.getValue("coachIndicator")!=indicator)divergence+1 else 0
                check(divergence<4) {"SDK and coach signal diverged for a second: $d"}
                val state="$motion / $indicator"
                if(state!=lastState){states.put(JSONObject(d));lastState=state}
            }
            if(motion=="MOVING" && speed>1.0){moving=true;stillSince=0L}
            if(indicator=="RIGHT")right=true
            if(indicator=="OFF")off=true
            if(moving && (right || mode=="harsh") && !captured){capture(app,"01-live-moving-signal");captured=true}
            File(output,"watch.json").writeText(JSONObject().put("moving",moving).put("right",right).put("speedMps",speed).put("tripId",id).toString())
            if(moving && motion=="STOPPED") {
                if(stillSince==0L)stillSince=SystemClock.elapsedRealtime()
                if(SystemClock.elapsedRealtime()-stillSince>=4000)break
            }
            Thread.sleep(250)
        }
        check(moving && id!=null) {"No live movement from Virtual Console"}
        // The external script parks after the standstill; wait for the app to finalize without a button.
        val parkedBy=SystemClock.elapsedRealtime()+20_000
        var finalized=false
        while(!finalized && SystemClock.elapsedRealtime()<parkedBy) {
            onMain {finalized=app.vehicleQuests.tripId==null}
            if(!finalized)Thread.sleep(250)
        }
        check(finalized && repository.trip(id!!)?.endedAt!=null) {"Park did not finalize the trip automatically"}
        onMain {app.showFeedback(id!!)}
        val scores=repository.scores(id!!)!!
        val attempts=repository.events(id!!).filter {it.type=="VEHICLE_QUEST_ATTEMPT"}.map {QuestBoardCodec.decodeAttempt(it.payload)}
        proof.put("states",states).put("scores",JSONObject(scores.scores.mapKeys {it.key.name})).put("samples",repository.samples(id!!).size)
        proof.put("attempts",JSONArray(attempts.map {JSONObject().put("questId",it.questId).put("result",it.result.name)}))
        check(repository.samples(id!!).size>=30)
        if(mode=="signal") {
            check(right && off)
            check(attempts.any {it.success} && attempts.any {it.result==AttemptResult.NO_SIGNAL} && attempts.any {it.result==AttemptResult.NO_BRAKE})
            check(scores.scores[DrivingSkill.TURN_SIGNAL]!!<70 && scores.scores[DrivingSkill.TURN_SLOWDOWN]!!<70)
        }else check(scores.scores[DrivingSkill.BRAKING]!!<80 && scores.scores[DrivingSkill.SMOOTH_ACCEL]!!<80)
        capture(app,"02-live-feedback")
        onMain {
            app.window.decorView.findViewWithTag<Button>("feedback_recommend").performClick()
            check(app.recommendationDiagnostics()?.recommendations?.isNotEmpty()==true)
        }
        waitForIdleSync();capture(app,"03-live-recommendations")
        proof.put("recommendations",JSONArray(repository.recommendations().recommendations.map {it.title}))
        File(output,"proof.json").writeText(proof.toString(2))
        result.putString("result","PASS_LIVE_CONSOLE_${mode.uppercase()}_SDK_TO_RECOMMENDATIONS")
        finish(Activity.RESULT_OK,result)
    }catch(e: Throwable) {
        File(output,"failure.txt").writeText(e.stackTraceToString());File(output,"partial.json").writeText(proof.toString(2))
        result.putString("error",e.stackTraceToString());finish(Activity.RESULT_CANCELED,result)
    }finally {
        activity?.let {onMain {it.finish()};waitForIdleSync()}
        SharedCoachRepository.testOverride=previous;repository.close()
    }
}
