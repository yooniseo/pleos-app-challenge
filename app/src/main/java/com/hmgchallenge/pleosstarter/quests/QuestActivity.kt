package com.hmgchallenge.pleosstarter.quests

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import com.hmgchallenge.pleosstarter.R
import com.hmgchallenge.pleosstarter.driving.*
import java.time.LocalDate
import java.util.UUID

/** Quest host imported from scratch/quests; coaching opens through DrivingGuidePort. */
class QuestActivity : Activity() {
    private val bg = Color.rgb(13, 22, 31)
    private val accent = Color.rgb(97, 230, 198)
    private val muted = Color.rgb(173, 188, 202)
    private var demo = true
    private var tab = 0
    private var phase = QuestPhase.BEFORE
    private lateinit var state: QuestState
    private lateinit var store: QuestStore
    private lateinit var host: QuestIntegration
    private var scroll: ScrollView? = null
    private var dialog: AlertDialog? = null
    private var guard: QuestDrivingGuard? = null
    private var restricted = false
    private var storageAvailable = true
    // Driving guide integration point: replace this implementation, not the quest screens or engine.
    private val guide: DrivingGuidePort = DrivingCoachLauncher()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        demo = savedInstanceState?.getBoolean("demo") ?: intent.getBooleanExtra("quest_demo", true)
        tab = savedInstanceState?.getInt("tab") ?: intent.getIntExtra("quest_tab",0)
        phase = QuestPhase.values().getOrElse(savedInstanceState?.getInt("phase") ?: 0) { QuestPhase.BEFORE }
        loadProfile()
    }
    override fun onResume() {
        super.onResume(); loadProfile()
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            guard?.stop(); restricted = true
            guard = QuestDrivingGuard(this) { value ->
                restricted = value
                if (value) { dialog?.dismiss(); guide.onCommand(CoachCommand.STOP) }
                render()
            }
        }
        render()
    }
    private fun loadProfile() {
        store = QuestStore(this, demo); storageAvailable = true
        state = try { synchronized(QuestIntegration.lock) { store.load() } }
        catch (_: Exception) { storageAvailable = false; QuestState(demo = demo) }
        host = QuestIntegration(this, demo) { next -> runOnUiThread {
            if (!isDestroyed) { state = next; render() }
        } }
    }
    private fun update(transform: (QuestState) -> QuestState) {
        if (restricted || !storageAvailable) { toast(getString(R.string.quest_storage_failed)); return }
        try {
            val previousGrowth = state.growthPercent
            synchronized(QuestIntegration.lock) {
                val next = transform(store.load()); check(store.save(next)); state = next
            }
            render()
            if (state.settings.celebration && state.growthPercent > previousGrowth) toast("단계 퀘스트를 완료했어요. 한 걸음 쌓았네요.")
        } catch (_: Exception) { toast(getString(R.string.quest_save_failed)) }
    }
    private fun render(reset: Boolean = false) {
        val position = if (reset) 0 else scroll?.scrollY ?: 0
        val root = column().apply { setPadding(dp(20), dp(12), dp(20), dp(12)); setBackgroundColor(bg) }
        if (restricted) {
            root.addView(label(getString(R.string.quest_parked_only), 26f, accent))
            root.addView(label(getString(R.string.quest_parked_only_hint), 18f, muted))
            setContentView(root); return
        }
        root.addView(label("차근차근  /  퀘스트 · 성장 · 안내 설정", 21f, accent))
        val modes = row()
        listOf(true to "시연 모드", false to "내 연습 기록").forEach { (value, title) ->
            modes.addView(button(title, demo == value) {
                if (demo != value) { guide.release(); demo = value; loadProfile(); render(true) }
            }, weight())
        }
        root.addView(modes)
        root.addView(label(if (demo) "시연 기록 · 개인 기록과 분리됨" else "개인 기록 · 직접 확인 또는 연동 근거", 13f, muted))
        val tabs = row()
        listOf("퀘스트 · 성장", "안내 설정", "주행 안내 연결").forEachIndexed { index, title ->
            tabs.addView(button(title, tab == index) { tab = index; render(true) }, weight())
        }
        root.addView(tabs)
        val body = column()
        if (!storageAvailable) body.addView(label(getString(R.string.quest_storage_failed), 16f))
        when (tab) { 0 -> quests(body); 1 -> settings(body); else -> connection(body) }
        scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        scroll?.post { scroll?.scrollTo(0, position) }
        guide.render(state.guideUiModel())
    }
    private fun quests(parent: LinearLayout) {
        val total = card(parent)
        total.addView(label("${state.levelName}  ·  ${state.growthPercent}%", 26f, accent))
        total.addView(progress(state.growthPercent))
        total.addView(label("동그라미 하나는 단계 퀘스트 하나의 완료예요. 모든 영역은 동시에 기록되며, 레벨은 활동 진행도를 보여줘요.", 14f, muted))
        val focus = state.focusGoalId?.let(QuestCatalog::get)?.title ?: "오늘 집중하고 싶은 목표를 골라 보세요"
        total.addView(button(focus) { chooseGoal() })
        total.addView(button("오늘 목표로 주행 안내 열기") { tab=2;render(true) })
        for (skill in CoachSkill.values()) {
            val area = card(parent)
            val completed = state.completedStages(skill)
            val graduated = skill in state.settings.graduated
            area.addView(label(skillName(skill) + " · ${completed.size}/4" + if (graduated) " · 코칭 줄임" else "", 20f))
            val circles = row()
            StageQuestCatalog.forSkill(skill).forEach { quest ->
                circles.addView(button((if (quest.stage in completed) "● " else "○ ") + "${quest.stage.ordinal + 1}단계", quest.stage in completed) {
                    stageDetails(skill, quest)
                }.apply { contentDescription = skillName(skill) + " ${quest.stage.ordinal + 1}단계 " + if (quest.stage in completed) "완료" else "미완료" }, weight())
            }
            area.addView(circles)
            StageQuestCatalog.forSkill(skill).firstOrNull { it.stage !in completed }?.let { next ->
                area.addView(label(next.title, 16f, accent))
                area.addView(label(next.description, 14f, muted))
                if (next.practiceTarget > 0) area.addView(label("확인된 경험 ${state.practiceCount(skill)} / ${next.practiceTarget}회", 14f, muted))
            }
            if (completed.size == 4) {
                area.addView(button(if (graduated) "다시 자세한 코칭" else "이 영역 코칭 줄이기") {
                    if (graduated) update { QuestEngine.restoreCoach(it, skill) }
                    else show(AlertDialog.Builder(this).setTitle("코칭을 줄여 볼까요?")
                        .setMessage("이 영역의 추가 설명을 ‘요청할 때만’으로 바꿔요. 언제든 다시 켤 수 있으며 차량 자체 경고는 바꾸지 않아요.")
                        .setNegativeButton("유지하기", null).setPositiveButton("요청할 때만") { _, _ -> update { QuestEngine.graduate(it, skill) } }.create())
                })
            }
        }
        val filters = row()
        QuestPhase.values().forEach { item -> filters.addView(button(phaseName(item), phase == item) { phase = item; render() }, weight()) }
        parent.addView(filters)
        parent.addView(label("상황별 퀘스트 목록", 18f, accent))
        QuestCatalog.all.filter { it.phase == phase }.forEach { quest ->
            val item = card(parent)
            item.addView(label((if (quest.id in state.completed) "✓ " else "") + quest.title, 19f))
            item.addView(label(quest.description, 15f, muted))
            item.addView(button("살펴보기") { openQuest(quest) })
        }
    }
    private fun stageDetails(skill: CoachSkill, quest: StageQuest) {
        val previous = QuestStage.values().take(quest.stage.ordinal).all { it in state.completedStages(skill) }
        if (!previous) { toast("앞 단계 퀘스트를 먼저 완료해 주세요."); return }
        when (quest.stage) {
            QuestStage.FIRST -> lesson(skill)
            QuestStage.SECOND, QuestStage.THIRD -> show(AlertDialog.Builder(this).setTitle(quest.title)
                .setMessage(quest.description + "\n\n확인된 경험 ${state.practiceCount(skill)}회. 주행 코치에서 수행 확인을 보내면 자동 반영돼요. 직접 기록은 안전하게 주차한 뒤 경험한 경우에만 선택해 주세요.")
                .setNegativeButton("닫기", null).setNeutralButton("연결 틀 보기") { _, _ -> tab = 2; render(true) }
                .setPositiveButton("직접 경험 기록") { _, _ -> manualPractice(skill) }.create())
            QuestStage.FOURTH -> reflection(skill)
        }
    }
    private fun lesson(skill: CoachSkill, questId: String? = null) {
        val content = when (skill) {
            CoachSkill.LANE_CHANGE -> "우회전 준비를 위해 오른쪽 차로로 이동하는 상황을 익혀요. 방향지시등·미러·사각지대 확인 순서는 주행 코치 주행 안내와 연결돼요."
            CoachSkill.INTERSECTION -> "같은 교차로에서 차로 변경 → 우회전 → 교차로 빠져나오기 순서예요. 우회전과 출구 확인을 하나의 교차로 경험으로 기록해요."
            CoachSkill.PREPARATION -> "시트·미러·안전벨트와 시야, 차량 설명서의 출발 준비 항목을 확인해요."
            CoachSkill.SPEED -> "필요한 감속과 제동을 우선하고, 주행 후 당시 상황과 속도 변화를 함께 돌아봐요."
            CoachSkill.PARKING -> "주변과 공간 확인 → 시작 위치 이해 → 천천히 진입 → 정렬 → 마무리 순서를 익혀요."
            CoachSkill.FINISH -> "차량 설명서에 따른 주차 조치와 창문·조명·소지품을 직접 확인해요."
        }
        show(AlertDialog.Builder(this).setTitle("1단계 · " + skillName(skill)).setMessage(content)
            .setNegativeButton("닫기", null).setPositiveButton("준비 내용 확인") { _, _ -> update {
                val learned = QuestEngine.learn(it, skill)
                if (questId != null) QuestEngine.confirmQuest(learned, questId) else learned
            } }.create())
    }
    private fun manualPractice(skill: CoachSkill, questId: String? = null) {
        val evidenceId = if (demo) "demo-manual:${UUID.randomUUID()}" else "manual:${LocalDate.now()}:${skill.name}"
        val before = state.practiceCount(skill)
        update { current ->
            if (questId == null) QuestEngine.recordPractice(current, PracticeEvidence(evidenceId, skill,
                if (demo) EvidenceSource.SIMULATION else EvidenceSource.USER, System.currentTimeMillis()))
            else QuestEngine.confirmQuest(current, questId, evidenceId = evidenceId, at = System.currentTimeMillis())
        }
        if (state.practiceCount(skill) == before) toast("오늘 이 영역의 직접 확인은 이미 기록했어요.")
    }
    private fun reflection(skill: CoachSkill) {
        val input = EditText(this).apply {
            hint = "경험에서 배운 점과 다음에 연습할 점을 적어 주세요."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3; maxLines = 5; filters = arrayOf(android.text.InputFilter.LengthFilter(500))
            setText(state.assessments[skill]?.text.orEmpty())
        }
        val popup = AlertDialog.Builder(this).setTitle("4단계 · 내 경험 정리").setView(input)
            .setNegativeButton("닫기", null).setPositiveButton("단계 완료 기록", null).create()
        popup.setOnShowListener { popup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (input.text.isBlank()) toast("돌아본 내용을 한 문장 남겨 주세요.")
            else { update { QuestEngine.completeFinalStage(it, skill, input.text.toString(), System.currentTimeMillis()) }; popup.dismiss() }
        } }
        show(popup)
    }
    private fun openQuest(quest: QuestDefinition) {
        when (quest.action) {
            QuestAction.CHECKLIST -> checklist(quest)
            QuestAction.LESSON -> lesson(quest.skill, quest.id)
            QuestAction.SELF_CHECK -> show(AlertDialog.Builder(this).setTitle(quest.title)
                .setMessage(quest.description + "\n\n안전하게 주차한 뒤 실제 경험한 항목만 직접 기록해요.")
                .setNegativeButton("아직이에요", null).setPositiveButton("경험했어요") { _, _ -> manualPractice(quest.skill, quest.id) }.create())
            QuestAction.GOAL -> chooseGoal(quest.id == "next_goal")
            QuestAction.GRADUATION -> toast("해당 영역의 1~4단계를 마치면 코칭을 줄일 수 있어요.")
            QuestAction.REFLECTION -> stageDetails(quest.skill, StageQuestCatalog.forSkill(quest.skill).last())
            else -> { tab = 2; render(true) }
        }
    }
    private fun checklist(quest: QuestDefinition) {
        val checked = BooleanArray(quest.checks.size)
        val popup = AlertDialog.Builder(this).setTitle(quest.title)
            .setMultiChoiceItems(quest.checks.toTypedArray(), checked) { _, index, selected -> checked[index] = selected }
            .setNegativeButton("닫기", null).setPositiveButton("직접 확인 완료", null).create()
        popup.setOnShowListener { popup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (checked.any { !it }) toast("각 항목을 직접 확인해 주세요.")
            else {
                val key = if (demo) "demo-check:${UUID.randomUUID()}" else "manual:${LocalDate.now()}:${quest.skill.name}"
                update { QuestEngine.confirmQuest(it, quest.id, checked.indices.toSet(), key, System.currentTimeMillis()) }
                popup.dismiss()
            }
        } }
        show(popup)
    }
    private fun chooseGoal(nextTrip: Boolean = false) {
        if (nextTrip && state.session?.reviewed != true) { toast("이번 주행 기록을 먼저 돌아봐 주세요."); return }
        val goals = QuestEngine.recommendations(state)
        show(AlertDialog.Builder(this).setTitle("집중 목표 · 다른 영역도 함께 기록돼요")
            .setItems(goals.map { it.title }.toTypedArray()) { _, index -> update {
                if (nextTrip) QuestEngine.selectNextGoal(it, goals[index].id) else QuestEngine.selectGoal(it, goals[index].id)
            } }
            .setNegativeButton("닫기", null).create())
    }
    private fun settings(parent: LinearLayout) {
        parent.addView(label("내 속도에 맞는 추가 코칭", 23f, accent))
        parent.addView(label("변경한 설정은 주행 코치에 바로 전달돼요. 기본 내비게이션과 차량 자체 경고는 바꾸지 않아요.", 15f, muted))
        CoachSkill.values().forEach { skill ->
            val item = card(parent)
            item.addView(label(skillName(skill), 21f))
            item.addView(Switch(this).apply {
                text = "이 영역 추가 코칭"; textSize = 16f; setTextColor(Color.WHITE); minHeight = dp(64)
                isChecked = skill !in state.settings.disabled
                setOnCheckedChangeListener { _, checked -> update { it.copy(settings = it.settings.copy(
                    disabled = if (checked) it.settings.disabled - skill else it.settings.disabled + skill)) } }
            })
            val levels = row()
            GuidanceLevel.values().forEach { level -> levels.addView(button(levelName(level), state.settings.level(skill) == level) {
                update { it.copy(settings = it.settings.copy(levels = it.settings.levels + (skill to level), graduated = it.settings.graduated - skill)) }
            }, weight()) }
            item.addView(levels)
        }
        val notifications = card(parent)
        notifications.addView(Switch(this).apply {
            text = "주차 후 단계 완료 알림"; textSize = 16f; setTextColor(Color.WHITE); minHeight = dp(64)
            isChecked = state.settings.celebration
            setOnCheckedChangeListener { _, checked -> update { it.copy(settings = it.settings.copy(celebration = checked)) } }
        })
    }
    private fun connection(parent: LinearLayout) {
        val frame = card(parent)
        frame.addView(label("퀘스트 → 주행 안내 → 피드백", 22f, accent))
        frame.addView(label("같은 교차로: 차로 준비 → 신호·주변 확인 → 우회전 → 교차로 빠져나오기\n오늘 목표와 안내 설정을 공유해요. 체험은 시연 프로필에만 쌓이고 개인 기록과 분리돼요.", 15f, muted))
        frame.addView(QuestStatusView(this).apply { render(state.guideUiModel()) })
        if (storageAvailable) frame.addView(guide.createView(this, host))
        val session = state.session ?: return
        val current = card(parent)
        current.addView(label("퀘스트에서 보는 현재 상황", 19f, accent))
        current.addView(label(session.current?.context?.kind?.title ?: if (session.finishedAt != null) "주행 종료" else "다음 상황 대기", 18f))
        session.situations.forEach { record -> current.addView(label(record.context.kind.title + " · " + when (record.outcome) {
            SituationEventType.PRACTICED -> if (record.difficult) "경험 기록 · 다시 연습 후보" else "수행 확인"
            SituationEventType.SKIPPED -> "건너뜀"
            SituationEventType.CANCELLED -> "취소"
            SituationEventType.UNVERIFIED -> "데이터 없음 · 확인 불가"
            SituationEventType.DEFERRED -> "다음 기회에 연습"
            else -> "진행 중"
        }, 15f, muted)) }
        if (session.finishedAt != null) {
            current.addView(button("기록을 돌아봤어요") { update { QuestEngine.review(it) } })
            current.addView(button("다음 목표 예약") { chooseGoal(true) }.apply { isEnabled = session.reviewed })
        }
    }
    private fun show(popup: AlertDialog) {
        if (restricted) return
        dialog?.dismiss(); dialog = popup
        popup.setOnDismissListener { if (dialog === popup) dialog = null }
        popup.show()
    }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun weight() = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) }
    private fun label(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(6), 0, dp(6))
    }
    private fun card(parent: LinearLayout) = column().apply {
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply { setColor(Color.rgb(24, 38, 50)); cornerRadius = dp(18).toFloat() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(4) })
    }
    private fun button(title: String, selected: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = title; textSize = 15f; isAllCaps = false; minHeight = dp(56); minimumHeight = dp(56)
        setTextColor(if (selected) bg else Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(if (selected) accent else Color.rgb(38, 57, 72))
        setOnClickListener { action() }; setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    private fun progress(value: Int) = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100; progress = value; progressTintList = ColorStateList.valueOf(accent)
        contentDescription = "전체 단계 완료율 $value 퍼센트"
        layoutParams = LinearLayout.LayoutParams(-1, dp(20))
    }
    private fun skillName(skill: CoachSkill) = when (skill) {
        CoachSkill.PREPARATION -> "출발 준비"; CoachSkill.LANE_CHANGE -> "차로 변경"; CoachSkill.INTERSECTION -> "교차로"
        CoachSkill.SPEED -> "속도 관리"; CoachSkill.PARKING -> "주차"; CoachSkill.FINISH -> "하차 준비"
    }
    private fun levelName(level: GuidanceLevel) = when (level) { GuidanceLevel.DETAILED -> "자세히"; GuidanceLevel.BRIEF -> "간략히"; GuidanceLevel.ON_REQUEST -> "요청 시" }
    private fun phaseName(value: QuestPhase) = when (value) { QuestPhase.BEFORE -> "주행 전"; QuestPhase.DRIVING -> "주행 중"; QuestPhase.PARKING -> "주차"; QuestPhase.AFTER -> "주행 후" }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onStop() { guard?.stop(); guard = null; dialog?.dismiss(); guide.release(); super.onStop() }
    override fun onSaveInstanceState(out: Bundle) { out.putBoolean("demo", demo); out.putInt("tab", tab); out.putInt("phase", phase.ordinal); super.onSaveInstanceState(out) }
}
