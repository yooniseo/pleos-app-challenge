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
import com.hmgchallenge.pleosstarter.feature.quests.*
import com.hmgchallenge.pleosstarter.session.*
import com.hmgchallenge.pleosstarter.ui.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Native integration validation. Synthetic observations use SIMULATION and a separate on-disk DB. */
open class VehicleQuestSmokeRunner: Instrumentation() {
    fun runVehicleQuestValidation() {
        val result=Bundle()
        val previous=SharedCoachRepository.testOverride
        val name="vehicle-quest-validation.db"
        val output=File(targetContext.filesDir,"vehicle-quest-validation").apply {mkdirs()}
        val proof=JSONObject()
        var app: ChageunActivity?=null
        targetContext.deleteDatabase(name)
        fun open()=SharedCoachRepository(Room.databaseBuilder(targetContext,TripDatabase::class.java,name).addMigrations(TripDatabase.MIGRATION_1_2).build())
        var repository=open()
        SharedCoachRepository.testOverride=repository
        try {
            check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
            app=startActivitySync(Intent(targetContext,ChageunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as ChageunActivity
            val activity=app
            waitForIdleSync()
            runOnMainSync {
                activity.show(AppScreen.QUESTS)
                activity.window.decorView.findViewWithTag<Button>("quests_vehicle").performClick()
            }
            val deadline=SystemClock.elapsedRealtime()+15_000
            var observations=emptyList<com.hmgchallenge.pleosstarter.driving.ApiObservation>()
            do {
                runOnMainSync {observations=activity.vehicleDiagnostics()}
                if(listOf("QUEST_SIGNAL","QUEST_STEERING","QUEST_BRAKE").all {channel->observations.any {it.api==channel && it.kind=="VALUE"}})break
                Thread.sleep(200)
            }while(SystemClock.elapsedRealtime()<deadline)
            proof.put("sdk",JSONArray(observations.map {JSONObject().put("api",it.api).put("kind",it.kind).put("values",JSONObject(it.values))}))
            check(listOf("QUEST_SIGNAL","QUEST_STEERING","QUEST_BRAKE").all {channel->observations.any {it.api==channel && it.kind=="VALUE"}}) {"Missing SDK input: $observations"}
            capture(activity,File(output,"01-pleos-inputs.png"))
            runOnMainSync {
                activity.show(AppScreen.MAP);activity.show(AppScreen.QUESTS)
                check(activity.window.decorView.findViewWithTag<Button>("quests_vehicle")!=null)
            }
            val controller=VehicleQuestController(targetContext,"SIMULATION")
            check(controller.finish() && !controller.enabled && controller.tripId==null)
            controller.resume()
            val initialQuestRows=repository.questRows()
            var now=10_000L
            val epoch=System.currentTimeMillis()
            val cues=mutableListOf<String>()
            controller.onCue={cues.add(it)}
            fun input(signal: Signal?,angle: Float?,brake: Boolean?,at: Long=now)=VehicleQuestInput(signal,at,angle,at,brake,at,8.0,at)
            fun tick(signal: Signal?,angle: Float?,brake: Boolean?,advance: Long=500) {
                now+=advance;controller.update(input(signal,angle,brake),now,epoch+now)
            }
            fun attempt(signal: Signal,angle: Float,brake: Boolean?,lead: Long=3000) {
                tick(Signal.OFF,0f,false,10_000)
                tick(signal,0f,brake)
                // Refresh steady observations while accumulating signal lead time.
                repeat((lead/500).toInt()){tick(signal,0f,brake)}
                tick(signal,angle,brake,0)
                tick(signal,0f,brake)
            }
            attempt(Signal.RIGHT,6f,false)
            attempt(Signal.RIGHT,25f,true)
            val id=controller.tripId!!
            check(controller.board.progressOf(QuestIds.LANE_CHANGE).steps==1)
            check(controller.board.progressOf(QuestIds.INTERSECTION_RIGHT).steps==1)
            check(cues.size==2 && cues.all {it.contains("기록했어요")})
            proof.put("coach",JSONArray(cues))
            runOnMainSync {activity.setContentView(VehicleQuestCanvas(activity,ProductIllustrations(),controller))}
            waitForIdleSync();capture(activity,File(output,"02-test-one-thirtieth.png"))
            attempt(Signal.OFF,6f,false)
            attempt(Signal.LEFT,6f,false)
            attempt(Signal.RIGHT,6f,false,500)
            attempt(Signal.RIGHT,25f,null)
            check(controller.board.progressOf(QuestIds.LANE_CHANGE).steps==1)
            check(controller.board.progressOf(QuestIds.LANE_CHANGE).failCount==3)
            check(controller.board.progressOf(QuestIds.INTERSECTION_RIGHT).unverifiedCount==1)
            val before=repository.events(id).size
            tick(Signal.RIGHT,0f,false,10_000);repeat(6){tick(Signal.RIGHT,0f,false)}
            tick(Signal.RIGHT,6f,false)
            now+=4000
            controller.update(input(Signal.RIGHT,0f,false,now-4000),now,epoch+now)
            tick(Signal.RIGHT,0f,false)
            check(repository.events(id).size==before) {"Stale data created an attempt"}
            val stored=repository.events(id).first {it.type=="VEHICLE_QUEST_ATTEMPT"}
            check(repository.recordVehicleQuestAttempt(stored.id,id,"SIMULATION",QuestBoardCodec.decodeAttempt(stored.payload)))
            check(controller.finish(at=epoch+now))
            check(repository.events(id).size==before)
            check(repository.report(id)!=null && repository.samples(id).isNotEmpty())
            check(repository.tripPoints(id)==0 && repository.vehicleQuestBoard("VEHICLE").attempts.isEmpty())
            check(repository.questRows()==initialQuestRows+2)
            proof.put("pipeline","PASS: lane + turn, failures, missing brake, stale input, idempotent event, coach callback, same Room report; no demo rewards")
            runOnMainSync {activity.showFeedback(id)}
            check(repository.addNote(id,"우회전이 어려웠어요","RIGHT_TURN","INTERSECTION",true,source="SIMULATION"))
            check(repository.notes(id).single().category=="RIGHT_TURN")
            runOnMainSync {check(activity.window.decorView.findViewWithTag<Button>("feedback_memo")!=null)}
            waitForIdleSync();capture(activity,File(output,"03-test-feedback.png"))
            runOnMainSync {activity.finish()};waitForIdleSync();app=null
            repository.close();repository=open();SharedCoachRepository.testOverride=repository
            check(repository.vehicleQuestBoard("SIMULATION").progressOf(QuestIds.LANE_CHANGE).steps==1)
            check(repository.vehicleQuestBoard("SIMULATION").progressOf(QuestIds.INTERSECTION_RIGHT).steps==1)
            check(repository.events(id).count {it.type=="VEHICLE_QUEST_ATTEMPT"}==before && repository.report(id)!=null)
            // Level rollover is persisted, with distinct event IDs, without replaying the SDK.
            val second="level-rollover"
            check(repository.beginVehicleQuestTrip(second,epoch,"SIMULATION"))
            repeat(29) {i->check(repository.recordVehicleQuestAttempt("rollover:$i",second,"SIMULATION",Attempt(QuestIds.LANE_CHANGE,epoch+i,AttemptResult.SUCCESS,Direction.RIGHT,6f,3000,null)))}
            check(repository.vehicleQuestBoard("SIMULATION").progressOf(QuestIds.LANE_CHANGE).level==2)
            check(repository.vehicleQuestBoard("SIMULATION").progressOf(QuestIds.LANE_CHANGE).steps==0)
            check(repository.endTrip(second,epoch+50,0f,true))
            proof.put("reopen","PASS: board, attempts, report survive DB close/reopen; 30 successes -> level 2; VEHICLE stays separate")
            File(output,"proof.json").writeText(proof.toString(2))
            result.putString("result","PASS_VEHICLE_QUEST_ROOM_COACH_AND_SDK")
            finish(Activity.RESULT_OK,result)
        }catch(error: Throwable) {
            File(output,"failure.txt").writeText(error.stackTraceToString())
            result.putString("error",error.stackTraceToString());finish(Activity.RESULT_CANCELED,result)
        }finally {
            app?.let {activity->runOnMainSync {activity.finish()};waitForIdleSync()}
            SharedCoachRepository.testOverride=previous;repository.close()
        }
    }
    private fun capture(activity: Activity,file: File) {
        runOnMainSync {
            val view=activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap));file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
}
