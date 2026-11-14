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
class ProductSmokeRunner: Instrumentation() {
    private lateinit var output: File
    override fun onCreate(arguments: Bundle?) {super.onCreate(arguments);start()}
    override fun onStart() {
        val result=Bundle();var activity: ChageunActivity?=null
        val previous=SharedCoachRepository.testOverride
        val name="product-validation.db"
        targetContext.deleteDatabase(name)
        var repository=SharedCoachRepository(Room.databaseBuilder(targetContext,TripDatabase::class.java,name).addMigrations(TripDatabase.MIGRATION_1_2).build())
        SharedCoachRepository.testOverride=repository
        output=File(targetContext.filesDir,"product-validation").apply {mkdirs()}
        val proof=JSONObject().put("clock","four fixtures use explicit virtual elapsed time; one lane run uses real time")
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
            val runs=JSONArray()
            ReplayCatalog.all.forEachIndexed {i,definition->
                var id=""
                runOnMainSync {
                    app.select(definition.lesson)
                    val session=app.session
                    val epoch=System.currentTimeMillis()+i*200_000L
                    val monotonic=SystemClock.elapsedRealtime()
                    check(session.start(epoch))
                    id=session.tripId!!
                    var tick=0
                    while(session.completedAt==null && tick<1800) {
                        tick++;session.tick(100,monotonic+tick*100,null,false,epoch+tick*100)
                    }
                    check(session.scenario.complete && session.completedAt!=null && session.error==null) {"Incomplete ${definition.id}: ${session.error}"}
                    // Finalization must be idempotent even when a callback is replayed.
                    repeat(10){session.tick(100,monotonic+200_000,null,false,epoch+200_000)}
                }
                waitForIdleSync();Thread.sleep(200)
                val events=repository.events(id)
                val earned=events.filter {it.type=="PRACTICE_REWARD"}
                val actual=earned.map {JSONObject(it.payload).getString("skill")}.toSet()
                check(actual==definition.expectedSkills) {"Wrong evidence: $actual vs ${definition.expectedSkills}"}
                check(earned.size==definition.expectedSkills.size)
                check(repository.tripPoints(id)==definition.expectedSkills.sumOf {PracticeRewards.points(it)})
                check(repository.trip(id)?.distanceKm!!>0 && repository.trip(id)?.endedAt!=null)
                check(repository.samples(id).size>=5 && repository.report(id)!=null)
                capture(app,"complete-${definition.id}")
                runs.put(JSONObject().put("fixture",definition.id).put("tripId",id).put("metres",repository.trip(id)!!.distanceKm!!*1000)
                    .put("samples",repository.samples(id).size).put("skills",JSONArray(actual.toList())).put("points",repository.tripPoints(id)))
            }
            check(repository.trips().size==4 && repository.points("demo")==700)
            proof.put("fixtures",runs)
            // Real-time playback continues through internal tab switches and stops while paused.
            runOnMainSync {app.select(RoadLesson.LANE);app.window.decorView.findViewWithTag<Button>("replay_start").performClick()}
            val id=app.session.tripId!!
            Thread.sleep(1100)
            var before=0.0
            runOnMainSync {before=app.session.scenario.distance;app.show(AppScreen.QUESTS)}
            Thread.sleep(1100)
            runOnMainSync {check(app.session.tripId==id && app.session.scenario.distance>before);app.session.pause();before=app.session.scenario.distance}
            Thread.sleep(450)
            runOnMainSync {check(app.session.scenario.distance==before);app.show(AppScreen.MAP);app.window.decorView.findViewWithTag<Button>("replay_start").performClick()}
            val deadline=SystemClock.elapsedRealtime()+25_000
            while(app.session.completedAt==null && SystemClock.elapsedRealtime()<deadline)Thread.sleep(200)
            check(app.session.completedAt!=null)
            check(repository.points("demo")==900)
            var speech=emptyList<ApiObservation>()
            val speechDeadline=SystemClock.elapsedRealtime()+6000
            do {
                runOnMainSync {speech=app.voiceDiagnostics()}
                if(speech.any {it.api=="TTS" && it.kind=="PLAYBACK_DONE"})break
                Thread.sleep(100)
            } while(SystemClock.elapsedRealtime()<speechDeadline)
            val playback=speech.filter {it.api=="TTS" && it.kind=="PLAYBACK_STARTED"}
            check(playback.size in 1..2) {"Unexpected automatic speech count: ${playback.size}; $speech"}
            check(speech.any {it.api=="TTS" && it.kind=="PLAYBACK_DONE"}) {"No completed Pleos speech playback"}
            proof.put("pleosSpeech",JSONObject().put("starts",playback.size).put("completed",speech.count {it.api=="TTS" && it.kind=="PLAYBACK_DONE"}))
            proof.put("realTimeLane",JSONObject().put("tripId",id).put("metres",repository.trip(id)!!.distanceKm!!*1000).put("samples",repository.samples(id).size))
            capture(app,"real-time-completion")
            runOnMainSync {app.finish()};waitForIdleSync();Thread.sleep(250);activity=null
            repository.close()
            repository=SharedCoachRepository(Room.databaseBuilder(targetContext,TripDatabase::class.java,name).addMigrations(TripDatabase.MIGRATION_1_2).build())
            SharedCoachRepository.testOverride=repository
            check(repository.trips().size==5 && repository.points("demo")==900)
            val reopened=ReplaySessionController(targetContext,NavigationSimulationRoute(listOf(GeoPoint(37.0,127.0),GeoPoint(37.001,127.0))))
            val personal=QuestStore(targetContext,false).load()
            check(reopened.quest.evidence.size==6)
            check(reopened.resetProgress())
            check(repository.points("demo")==0 && reopened.quest.evidence.isEmpty())
            check(repository.trips().size==5 && repository.report(id)!=null && repository.tripPoints(id)==200)
            check(QuestStore(targetContext,false).load()==personal)
            proof.put("reopen","PASS: 5 trips, 6 evidence, 900 points").put("reset","PASS: current 0 points; previous reports/rewards and personal state preserved")
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
