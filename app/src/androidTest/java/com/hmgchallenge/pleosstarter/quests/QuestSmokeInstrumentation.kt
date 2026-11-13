package com.hmgchallenge.pleosstarter.quests

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ScrollView
import com.hmgchallenge.pleosstarter.driving.guideUiModel
import java.io.File

/** Emulator integration check: restores both profiles and refuses obscured screenshots. */
class QuestSmokeInstrumentation : Instrumentation() {
    private lateinit var activity: Activity
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle(); var exitCode = Activity.RESULT_CANCELED
        val isolated=com.hmgchallenge.pleosstarter.driving.IsolatedCoachDatabase(targetContext)
        val demo = QuestStore(targetContext, true); val personal = QuestStore(targetContext, false)
        var oldDemo: QuestState? = null; var oldPersonal: QuestState? = null
        try {
            check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
            oldDemo = demo.load(); oldPersonal = personal.load()
            check(demo.save(QuestState())); check(personal.save(QuestState(demo = false)))
            dismissBootSystemDialog(); launch()
            screenshot("01-quest-stages")
            click("1번 연결 틀"); click("시연: 새 주행 시작"); click("시연: 차로 변경 상황 진입")
            check(demo.load().session!!.current!!.context.kind == SituationKind.LANE_RIGHT)
            click("시연: 관측값만 전달")
            check(demo.load().practiceCount(CoachSkill.LANE_CHANGE) == 0)
            click("시연: 안내 제공 사실 전달")
            check(demo.load().session!!.events.last().assistanceUsed == AssistanceUsed.DETAILED)
            click("시연: 수행 확인 전달")
            check(demo.load().practiceCount(CoachSkill.LANE_CHANGE) == 1)
            click("시연: 우회전 상황 진입"); click("시연: 수행 확인 전달")
            check(demo.load().practiceCount(CoachSkill.INTERSECTION) == 0)
            click("시연: 교차로 빠져나오기 진입"); click("시연: 수행 확인 전달")
            check(demo.load().practiceCount(CoachSkill.INTERSECTION) == 1)
            click("시연: 주행 종료")
            check(demo.load().session!!.finishedAt != null)
            screenshot("02-part-one-frame")
            click("안내 설정"); click("간략히", 1)
            check(demo.load().settings.level(CoachSkill.LANE_CHANGE) == GuidanceLevel.BRIEF)
            screenshot("03-guidance-settings")
            click("1번 연결 틀"); click("시연: 새 주행 시작"); click("시연: 차로 변경 상황 진입")
            check(demo.load().guideUiModel().guidanceLevel == GuidanceLevel.BRIEF)
            click("설명 멈춤"); click("이어서 안내"); click("다시 안내")

            var full = QuestEngine.learn(demo.load(), CoachSkill.LANE_CHANGE)
            repeat(2) { index -> full = QuestEngine.recordPractice(full, PracticeEvidence("repeat-$index", CoachSkill.LANE_CHANGE, EvidenceSource.SIMULATION, 4000L + index)) }
            full = QuestEngine.completeFinalStage(full, CoachSkill.LANE_CHANGE, "단계별 연습을 돌아봤어요", 5000)
            full = QuestEngine.graduate(full, CoachSkill.LANE_CHANGE)
            check(full.completedStages(CoachSkill.LANE_CHANGE).size == 4)
            check(demo.save(full)); check(demo.load() == full)
            runOnMainSync { activity.finish() }; waitForIdleSync(); launch()
            check(demo.load().settings.level(CoachSkill.LANE_CHANGE) == GuidanceLevel.ON_REQUEST)
            screenshot("04-four-stages-complete")
            check(personal.load() == QuestState(demo = false))

            var callbacks = 0
            val bridge = QuestIntegration(targetContext, false) { callbacks++ }
            check(bridge.startTrip("real-trip", EvidenceSource.VEHICLE, 1000))
            val kinds = listOf(SituationKind.LANE_RIGHT, SituationKind.TURN_RIGHT, SituationKind.INTERSECTION_EXIT)
            kinds.forEachIndexed { index, kind ->
                val ctx = SituationContext("real-trip", "occ-$index", "same-intersection", kind, EvidenceSource.VEHICLE, 1001L + index * 3)
                check(bridge.enterSituation("enter-$index", ctx))
                check(bridge.confirmPractice("done-$index", ctx, ctx.at + 1, ConfirmationBasis.USER_CONFIRMATION))
                check(!bridge.confirmPractice("replay-$index", ctx, ctx.at + 2, ConfirmationBasis.USER_CONFIRMATION))
            }
            check(bridge.finishTrip("real-trip", 2000))
            val report = DrivingFeedback("real-trip", EvidenceSource.VEHICLE, 2000, noteCategories = setOf("PARKING"))
            check(bridge.onFeedback(report)); check(!bridge.onFeedback(report))
            check(callbacks == 9)
            check(personal.load().evidence.size == 2)
            check(personal.load().evidence.all { it.source == EvidenceSource.USER })
            check(demo.load() == full)
            var observedBrief = false
            val settingsObserver = QuestIntegration(targetContext, false).observe(
                onState = { observedBrief = it.settings.level(CoachSkill.LANE_CHANGE) == GuidanceLevel.BRIEF }, onError = { throw it })
            runOnMainSync {
                val current = personal.load()
                check(personal.save(current.copy(settings = current.settings.copy(levels = mapOf(CoachSkill.LANE_CHANGE to GuidanceLevel.BRIEF)))))
            }
            waitForIdleSync(); check(observedBrief); settingsObserver.close()
            result.putString("quest_smoke", "PASS")
            result.putString("stream", "PASS: three scenes in one intersection, observation/confirmation separation, settings round trip, four sequential quests, JSON persistence, profile isolation, part 1 bridge callbacks.\n")
            exitCode = Activity.RESULT_OK
        } catch (error: Throwable) {
            result.putString("quest_smoke", "FAIL"); result.putString("stream", error.stackTraceToString())
        } finally {
            if (::activity.isInitialized) runOnMainSync { activity.finish() }
            oldDemo?.let { demo.save(it) }; oldPersonal?.let { personal.save(it) }
        }
        isolated.close()
        finish(exitCode, result)
    }
    private fun launch() {
        activity = startActivitySync(Intent(targetContext, QuestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForIdleSync()
    }
    private fun click(text: String, index: Int = 0) {
        dismissBootSystemDialog()
        runOnMainSync {
            val matches = descendants(activity.window.decorView).filterIsInstance<Button>().filter { it.text.toString() == text }.toList()
            val button = matches.getOrNull(index) ?: error("Missing button: $text[$index]")
            check(button.isEnabled); check(button.performClick())
        }
        waitForIdleSync()
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
    private fun screenshot(name: String) {
        dismissBootSystemDialog()
        runOnMainSync { descendants(activity.window.decorView).filterIsInstance<ScrollView>().firstOrNull()?.scrollTo(0, 0) }
        waitForIdleSync(); uiAutomation.waitForIdle(500, 5000)
        check(uiAutomation.rootInActiveWindow?.packageName?.toString() == targetContext.packageName) { "App screenshot is obscured" }
        val directory = File(targetContext.getExternalFilesDir(null), "quest-smoke-v2").apply { mkdirs() }
        val bitmap = uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun dismissBootSystemDialog() {
        val root = uiAutomation.rootInActiveWindow ?: return
        if (root.findAccessibilityNodeInfosByText("System UI isn't responding").isEmpty()) return
        var wait = root.findAccessibilityNodeInfosByText("Wait").firstOrNull() ?: error("System UI boot dialog cannot be dismissed")
        while (!wait.isClickable) wait = wait.parent ?: error("Missing system Wait action")
        check(wait.performAction(AccessibilityNodeInfo.ACTION_CLICK)); uiAutomation.waitForIdle(500, 5000)
    }
}
