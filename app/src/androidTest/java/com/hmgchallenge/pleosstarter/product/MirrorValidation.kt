package com.hmgchallenge.pleosstarter.product

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.room.Room
import com.hmgchallenge.pleosstarter.app.AppScreen
import com.hmgchallenge.pleosstarter.app.ChageunActivity
import com.hmgchallenge.pleosstarter.core.data.SharedCoachRepository
import com.hmgchallenge.pleosstarter.core.data.TripDatabase
import com.hmgchallenge.pleosstarter.driving.RoadLesson

/** The app publishes the authored lane replay; independent SDK readback must follow on the map. */
fun Instrumentation.runMirrorValidation(combined: Boolean) {
    val name = "mirror-validation.db"
    targetContext.deleteDatabase(name)
    val repository = SharedCoachRepository(Room.databaseBuilder(targetContext,TripDatabase::class.java,name).build())
    val previous = SharedCoachRepository.testOverride
    SharedCoachRepository.testOverride = repository
    var app: ChageunActivity? = null
    val result = Bundle()
    var code = Activity.RESULT_CANCELED
    try {
        val launch = Intent(targetContext,ChageunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("verify_console",true)
        val activity = startActivitySync(launch) as ChageunActivity
        app = activity
        runOnMainSync { activity.select(if(combined) RoadLesson.COMBINED else RoadLesson.LANE); activity.startPrepared() }
        var moving=false
        var right=false
        var ended=false
        var stopped=false
        var reversed=false
        val until = SystemClock.elapsedRealtime()+if(combined)240_000 else 95_000
        while(SystemClock.elapsedRealtime()<until && !(ended && stopped)) {
            runOnMainSync {
                val state = activity.liveDrivingDiagnostics()
                moving = moving || (state.getValue("speedMps").toDoubleOrNull() ?: 0.0)>1.0
                right = right || state["sdkIndicator"]=="RIGHT"
                reversed = reversed || state["gear"]=="R"
                ended = activity.session.completedAt!=null
                stopped = ended && state["motion"]=="STOPPED" && state["sdkIndicator"]=="OFF" && state["gear"]=="P"
            }
            Thread.sleep(250)
        }
        check(moving && right && ended && stopped) {"SDK moving=$moving right=$right complete=$ended parked=$stopped"}
        check(!combined || reversed) {"Reverse gear never observed during parking"}
        check(repository.trips().none {it.source=="VEHICLE"}) {"Mirrored replay leaked into personal driving records"}
        var quest: String?=null
        runOnMainSync {
            activity.window.decorView.findViewWithTag<android.widget.Button>("tab_QUESTS").performClick()
            quest=activity.recommendationDiagnostics()?.recommendations?.firstOrNull()?.weakness
            activity.show(AppScreen.MAP)
        }
        check(quest==(if(combined)"PARKING" else "TURN_SIGNAL")) {"Fresh replay recommendation: $quest"}
        result.putString("result","PASS SDK moving, RIGHT, OFF, stopped; REPLAY-only Room trip and next recommendation=$quest; reverse=$reversed")
        code=Activity.RESULT_OK
    } catch(e: Throwable) {
        result.putString("error",e.stackTraceToString())
    } finally {
        app?.let { runOnMainSync {it.finish()}; waitForIdleSync() }
        SharedCoachRepository.testOverride=previous
        repository.close()
        finish(code,result)
    }
}
