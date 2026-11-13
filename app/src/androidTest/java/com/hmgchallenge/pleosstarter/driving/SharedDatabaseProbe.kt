package com.hmgchallenge.pleosstarter.driving

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.room.Room
import com.hmgchallenge.pleosstarter.core.data.SharedCoachRepository
import com.hmgchallenge.pleosstarter.core.data.TripDatabase
import com.hmgchallenge.pleosstarter.quests.*
import org.json.JSONObject
import java.io.Closeable
import java.io.File

/** All test mutations use a private DB, never the user's trips.db or shared preferences. */
class IsolatedCoachDatabase(context: Context): Closeable {
    private val previous=SharedCoachRepository.testOverride
    private val application=context.applicationContext as android.app.Application
    private val activities=java.util.concurrent.CopyOnWriteArraySet<Activity>()
    private val callbacks=object: android.app.Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(a: Activity,s: Bundle?) {activities.add(a)}
        override fun onActivityDestroyed(a: Activity) {activities.remove(a)}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityResumed(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity,s: Bundle) {}
    }
    val repository=SharedCoachRepository(Room.inMemoryDatabaseBuilder(context,TripDatabase::class.java).build())
    init {application.registerActivityLifecycleCallbacks(callbacks);SharedCoachRepository.testOverride=repository}
    override fun close() {
        val closed=java.util.concurrent.CountDownLatch(1)
        android.os.Handler(android.os.Looper.getMainLooper()).post {activities.forEach {if(!it.isFinishing)it.finish()};closed.countDown()}
        check(closed.await(3,java.util.concurrent.TimeUnit.SECONDS))
        val deadline=android.os.SystemClock.elapsedRealtime()+3000
        while(activities.isNotEmpty() && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50)
        check(activities.isEmpty()) {"Test windows did not close before database shutdown"}
        application.unregisterActivityLifecycleCallbacks(callbacks)
        SharedCoachRepository.testOverride=previous;repository.close()
    }
}

/** Native UI -> shared Room DB -> quest -> next coach. Virtual time is explicit in proof. */
class SharedDatabaseProbe(private val i: Instrumentation) {
    private val context get()=i.targetContext
    private fun ui(action: ()->Unit) {i.runOnMainSync(action);i.waitForIdleSync()}
    private fun click(a: Activity,tag: String)=ui {check(checkNotNull(a.window.decorView.findViewWithTag<Button>(tag)) {"Missing button: $tag"}.performClick())}
    private fun textAction(value: String,action: Int=AccessibilityNodeInfo.ACTION_CLICK,args: Bundle?=null) {
        val node=i.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(value).firstOrNull {it.text?.toString()==value}
            ?: error("Missing UI text: $value")
        check(node.performAction(action,args));i.waitForIdleSync();Thread.sleep(200)
    }
    private fun capture(name: String) {
        check(i.uiAutomation.rootInActiveWindow?.packageName==context.packageName)
        val bitmap=checkNotNull(i.uiAutomation.takeScreenshot())
        val dir=File(context.filesDir,"coach-smoke").apply {mkdirs()}
        File(dir,name).outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    fun run() {
        var code=Activity.RESULT_CANCELED;val result=Bundle();var activity: DrivingSimulatorActivity?=null;var entry: Activity?=null
        val isolated=IsolatedCoachDatabase(context);val db=isolated.repository
        try {
            check(EmulatorCockpitBridge.isAllowed(context))
            check(db.saveSnapshot("settings:scenario",JSONObject().put("style","ADAPTIVE").put("celebration",false).toString()))
            check(db.saveSnapshot("settings:voice",JSONObject().put("voice",false).toString()))
            val store=QuestStore(context,true);check(store.save(QuestEngine.learn(QuestEngine.learn(QuestState(),CoachSkill.LANE_CHANGE),CoachSkill.INTERSECTION)))
            val personal=QuestStore(context,false);check(personal.save(QuestState(demo=false)))
            val personalBefore=personal.load()
            val monitor=i.addMonitor(DrivingSimulatorActivity::class.java.name,null,false)
            entry=i.startActivitySync(checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            activity=checkNotNull(i.waitForMonitorWithTimeout(monitor,10_000)) as DrivingSimulatorActivity;i.removeMonitor(monitor)
            val a=activity;i.waitForIdleSync();Thread.sleep(800)
            ui {check(a.learningForDiagnostics().support==CoachingSupport.DETAILED)}
            capture("integrated-first.png")
            val tripIds=mutableListOf<String>()
            repeat(3) {index->
                click(a,"nav_auto");Thread.sleep(1250)
                val id=store.load().session!!.tripId;tripIds.add(id)
                ui {repeat(80){a.simpleForDiagnostics()!!.advance(1000,null,false)}}
                Thread.sleep(900)
                check(store.load().session!!.finishedAt!=null)
                check(store.load().practiceCount(CoachSkill.LANE_CHANGE)==index+1) {"Lane count at run $index: ${store.load().evidence}; scenario=${a.simpleForDiagnostics()?.lesson}"}
                check(store.load().practiceCount(CoachSkill.INTERSECTION)==index+1)
                check(db.trips().single {it.tripId==id}.endedAt!=null)
                check(db.samples(id).size>=2)
                check(db.report(id)!=null && db.events(id).any {it.type.startsWith("SCENARIO_")})
                check(db.completions("demo","LANE_CHANGE").count {it.tripId==id}==1)
                check(db.completions("personal","LANE_CHANGE").isEmpty())
                if(index==0)capture("integrated-hints.png")
            }
            check(tripIds.toSet().size==3);check(db.questRows()==48)
            click(a,"nav_auto");Thread.sleep(300)
            val currentId=store.load().session!!.tripId
            ui {check(a.learningForDiagnostics().support==CoachingSupport.ON_REQUEST)}
            capture("integrated-independent.png")
            click(a,"nav_help");textAction("어려웠어요");Thread.sleep(300)
            ui {check(a.learningForDiagnostics().support==CoachingSupport.DETAILED)}
            check(db.notes(currentId).any {it.rawText.startsWith("어려웠어요")})
            check(db.events(currentId).any {it.type=="HELP_REQUESTED"})
            check(db.events(currentId).any {it.type=="DIFFICULTY"})
            check(store.load().practiceCount(CoachSkill.LANE_CHANGE)==3)
            capture("integrated-help-restored.png")
            click(a,"nav_note");textAction("음성·글로 메모하기")
            val field=i.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByViewId("memo_text").firstOrNull()
                ?: findEditable(i.uiAutomation.rootInActiveWindow)
            checkNotNull(field)
            check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"차로 변경 후 핸들 풀기가 어려웠어요")}))
            textAction("저장")
            check(db.notes(currentId).any {it.rawText=="차로 변경 후 핸들 풀기가 어려웠어요"})
            check(db.events(currentId).filter {it.type=="NOTE_CONTEXT"}.all {
                val p=JSONObject(it.payload);p.getBoolean("simulated") && p.has("latitude") && p.has("cue")
            })
            click(a,"nav_menu");capture("integrated-feedback.png")
            check(store.save(store.load()));check(db.completions("demo","LANE_CHANGE").size==3)
            check(personal.load()==personalBefore)
            textAction("계속하기")
            navigationRoundTrip(a,currentId,db)
            click(a,"nav_restart");click(a,"nav_note");textAction("좋았던 순간 기록")
            var nextId: String?=null;ui {nextId=a.recordingForDiagnostics()}
            check(nextId!=null && nextId!=currentId)
            check(db.notes(nextId!!).size==1 && db.notes(currentId).size==2)
            ui {a.finish()};Thread.sleep(1200)
            check(db.report(currentId)!=null)
            check(db.lastError==null) {db.lastError.orEmpty()}
            migrationAndReopen()
            val proof=JSONObject().put("result","PASS_SHARED_DATABASE").put("virtualScenarioTime",true)
                .put("realOneHzSamples",true).put("sharedTripIds",org.json.JSONArray(tripIds)).put("sameTripMemo",true)
                .put("personalProfileUnchanged",true).put("duplicateCompletionRejected",true).put("questStagesControlCoach",true)
                .put("difficultyRestoresHelp",true).put("v1MigrationPreservedSixTables",true).put("fileDbReopen",true)
                .put("sttRecognitionVerified",false)
                .put("memoHasSimulationLocationAndCue",true).put("restartDoesNotAttachNotesToPreviousTrip",true)
                .put("commonTabsRoundTrip",true).put("recordingContinuesOnQuestScreen",true).put("externalBackgroundPauses",true)
            File(context.filesDir,"shared-database-proof.json").writeText(proof.toString(2))
            result.putString("result",proof.toString());code=Activity.RESULT_OK
        } catch(error: Throwable) {result.putString("result","FAIL: ${error.stackTraceToString()}")}
        finally {activity?.let {ui {it.finish()}};entry?.let {ui {it.finish()}};Thread.sleep(1200);isolated.close()}
        i.finish(code,result)
    }
    private fun navigationRoundTrip(a: DrivingSimulatorActivity,id: String,db: SharedCoachRepository) {
        var before=0.0;ui {before=a.simpleForDiagnostics()!!.distance}
        val questMonitor=i.addMonitor(QuestActivity::class.java.name,null,false)
        click(a,"app_tab_quests")
        val quests=checkNotNull(i.waitForMonitorWithTimeout(questMonitor,5000));i.removeMonitor(questMonitor)
        i.waitForIdleSync();Thread.sleep(1400)
        ui {check(a.runtimeActiveForDiagnostics());check(a.simpleForDiagnostics()!!.distance>before);check(a.recordingForDiagnostics()==id)}
        check(db.samples(id).size>=3)
        capture("navigation-quests.png");click(quests,"screen_map");Thread.sleep(500)
        ui {check(a.hasWindowFocus());check(a.recordingForDiagnostics()==id)}
        val recordMonitor=i.addMonitor(PracticeRecordsActivity::class.java.name,null,false)
        click(a,"app_tab_records")
        val records=checkNotNull(i.waitForMonitorWithTimeout(recordMonitor,5000));i.removeMonitor(recordMonitor)
        i.waitForIdleSync();Thread.sleep(900);capture("navigation-records.png")
        click(records,"app_tab_more");textAction("튜토리얼 · 주차와 운전 순서 익히기");Thread.sleep(1400)
        capture("navigation-tutorial.png")
        textAction("← 지도로 돌아가기");Thread.sleep(500)
        ui {check(a.hasWindowFocus());check(a.recordingForDiagnostics()==id)}
        ui {context.startActivity(checkNotNull(context.packageManager.getLaunchIntentForPackage("ai.umos.maps.android.navigation.app")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
        Thread.sleep(1800)
        ui {check(!a.runtimeActiveForDiagnostics()) {"Runtime must pause outside app: ${AppNavigation.diagnostics()}"}}
        ui {AppNavigation.drive(records)};Thread.sleep(700)
        ui {check(a.hasWindowFocus());check(a.runtimeActiveForDiagnostics());check(a.pausedForDiagnostics());check(a.recordingForDiagnostics()==id)}
        capture("navigation-returned-map.png")
        ui {quests.finish();records.finish()}
    }
    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null;if(node.isEditable && node.className=="android.widget.EditText")return node
        for(index in 0 until node.childCount)findEditable(node.getChild(index))?.let {return it};return null
    }
    private fun migrationAndReopen() {
        val name="coach-integration-migration.db"
        fun open()=Room.databaseBuilder(context,TripDatabase::class.java,name).addMigrations(TripDatabase.MIGRATION_1_2).build()
        context.deleteDatabase(name)
        var fileDb: TripDatabase?=null
        try {
            // These six entity definitions are unchanged from v1. Remove only v2 additions from this test file.
            fileDb=open();val sql=fileDb.openHelper.writableDatabase
            sql.execSQL("INSERT INTO Trip VALUES ('legacy',1,2,'REPLAY',0.1)")
            sql.execSQL("INSERT INTO TripSample VALUES ('legacy',1,12)")
            sql.execSQL("INSERT INTO DrivingNote VALUES ('note','legacy',1,'keep me','OTHER')")
            sql.execSQL("INSERT INTO TripEvent VALUES ('event','legacy','HARSH_BRAKE',1,1,-12)")
            sql.execSQL("INSERT INTO TripReport VALUES ('legacy',2,'keep report','[]','[]','{}')")
            sql.execSQL("INSERT INTO Quest VALUES ('legacy','template','keep quest','reason','SELF_CHECK',1,0,'ACTIVE',1)")
            listOf("AppSnapshot","PracticeCompletion","CoachingEvent","room_master_table").forEach {sql.execSQL("DROP TABLE `$it`")}
            sql.version=1;fileDb.close();fileDb=null
            val migrated=SharedCoachRepository(open())
            check(migrated.trips().single().tripId=="legacy")
            check(migrated.samples("legacy").single().speedKmh==12f)
            check(migrated.notes("legacy").single().rawText=="keep me")
            check(migrated.report("legacy")!!.headline=="keep report")
            check(migrated.questRows()==1)
            check(migrated.database.coachDao().speedEvents("legacy").size==1)
            val previous=SharedCoachRepository.testOverride;SharedCoachRepository.testOverride=migrated
            try {
                val store=QuestStore(context,true)
                check(store.save(QuestEngine.recordPractice(QuestState(),PracticeEvidence("persistent",CoachSkill.PARKING,
                    com.hmgchallenge.pleosstarter.quests.EvidenceSource.SIMULATION,3,"legacy"))))
            } finally {SharedCoachRepository.testOverride=previous;migrated.close()}
            val reopened=SharedCoachRepository(open())
            try {check(reopened.snapshot("quest:demo")!=null);check(reopened.completions("demo","PARKING").size==1)
                check(reopened.notes("legacy").size==1);check(reopened.questRows()==25)}finally {reopened.close()}
        } finally {fileDb?.close();context.deleteDatabase(name)}
    }
}
