package com.hmgchallenge.pleosstarter.driving

import java.util.UUID

/** Pure state machine. All callbacks are serialized by the Activity on the main thread. */
class CoachEngine(
    val demo: Boolean,
    private val emit: (CoachEvent) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    val tripId: String = UUID.randomUUID().toString()
) {
    var phase = CoachPhase.WAITING; private set
    var instanceId: String = ""; private set
    var preferences = CoachPreferences()
    var indicator = Indicator.UNKNOWN; private set
    var distanceMeters: Double? = null; private set
    var navigationDescription = ""; private set
    var demoStep = DemoDriveStep.SIGNAL; private set
    var demoObstruction = false; private set
    var demoLaneCount = 3; private set
    var demoStartLane = 1; private set
    var demoCurrentLane = 1; private set
    val demoTargetLane get() = demoLaneCount
    val demoLaneChangesRemaining get() = demoTargetLane-demoCurrentLane
    var demoFeedback = ""; private set
    private var requested = false
    private val shownCues = mutableSetOf<String>()
    private val assisted = mutableSetOf<SituationType>()
    private val externalEvidence = mutableSetOf<String>()
    private var latestNavigationAt: Long? = null
    private var lastSignalAt: Long? = null

    val type: SituationType get() = when (phase) {
        CoachPhase.RIGHT_TURN -> SituationType.RIGHT_TURN
        CoachPhase.INTERSECTION_EXIT, CoachPhase.FINISHED -> SituationType.INTERSECTION_EXIT
        else -> SituationType.LANE_CHANGE_RIGHT
    }
    val active get() = phase in setOf(CoachPhase.LANE_PREPARATION, CoachPhase.RIGHT_TURN, CoachPhase.INTERSECTION_EXIT)

    fun startDemo() {
        require(demo)
        if (active) cancel("체험 다시 시작")
        begin(EvidenceSource.DEMO)
        distanceMeters = 240.0
        indicator = if (preferences.demoAutomaticIndicator) Indicator.RIGHT else Indicator.OFF
        demoStep = if (indicator == Indicator.RIGHT) DemoDriveStep.MIRROR else DemoDriveStep.SIGNAL
        demoObstruction = false
        demoCurrentLane=demoStartLane;demoFeedback=""
        if (indicator == Indicator.RIGHT) record(CoachEventType.SIGNAL_OBSERVED, EvidenceSource.DEMO, "자동 점등 체험 · 실제 차량 제어 없음")
    }

    private fun begin(source: EvidenceSource) {
        instanceId = UUID.randomUUID().toString()
        shownCues.clear(); assisted.clear(); externalEvidence.clear(); requested = false
        phase = CoachPhase.LANE_PREPARATION
        record(CoachEventType.STARTED, source)
    }

    /** TBT describes a maneuver, not lanes or safe gaps. Threshold is a tunable prototype policy. */
    fun onNavigation(rightTurn: Boolean, distance: Double?, description: String) {
        if (demo) return
        if (!rightTurn || distance == null || !distance.isFinite() || distance < 0) {
            if (active) {
                record(CoachEventType.UNVERIFIED, EvidenceSource.NAVIGATION, "다음 안내가 바뀜 · 통과/차로 변경 성공은 확인되지 않음")
                cancel("다음 내비 안내 변경 또는 유효한 데이터 없음")
            }
            distanceMeters = null; latestNavigationAt = null
            if (!active) phase = CoachPhase.WAITING
            return
        }
        latestNavigationAt = now()
        // A discontinuity can be a different intersection even if its description is identical.
        if (active && distanceMeters?.let { distance - it > 120 } == true) cancel("안내 거리 급변 · 다른 상황일 수 있음")
        distanceMeters = distance; navigationDescription = description.take(160)
        if (!active && phase != CoachPhase.FINISHED && distance <= 300) begin(EvidenceSource.NAVIGATION)
        if (phase == CoachPhase.LANE_PREPARATION && distance <= 80) {
            record(CoachEventType.UNVERIFIED, EvidenceSource.NAVIGATION, "차로 변경 수행 여부 미확인 · 우회전 지점 안내로 전환")
            phase = CoachPhase.RIGHT_TURN; requested = false
        }
        // This is a cue transition, never PHASE_RECORDED or proof of lane-change success.
    }

    fun expireNavigation() {
        if (!demo && active && latestNavigationAt?.let { now() - it > 8_000 } != false) {
            record(CoachEventType.UNVERIFIED, EvidenceSource.NAVIGATION, "내비 데이터가 오래되어 안내 중단")
            cancel("내비 데이터 응답 없음")
            distanceMeters = null
        }
        if (!demo && lastSignalAt?.let { now() - it > 6_000 } != false) indicator = Indicator.UNKNOWN
    }

    fun onIndicator(value: Indicator) {
        if (demo) return
        val changed = value != indicator
        indicator = value; lastSignalAt = now()
        if (active && changed && value != Indicator.UNKNOWN) {
            record(CoachEventType.SIGNAL_OBSERVED, EvidenceSource.VEHICLE, value.name)
        }
    }

    fun nextDemo() {
        if (!demo || !active) return
        advance(EvidenceSource.DEMO, "시나리오 재생 · 실제 수행 판정 아님")
    }

    fun demoAction(step: DemoDriveStep): Boolean {
        if (!demo || !active || step != demoStep || demoObstruction) return false
        record(CoachEventType.CHECK_CONFIRMED, EvidenceSource.DEMO, "행동 버튼 · ${step.title} · 사용자 가정 입력, 실제 수행 검증 아님")
        when (step) {
            DemoDriveStep.SIGNAL -> {
                indicator = Indicator.RIGHT
                record(CoachEventType.SIGNAL_OBSERVED, EvidenceSource.DEMO, "RIGHT · 버튼 체험, 실제 차량 제어 없음")
                demoStep = DemoDriveStep.MIRROR
                demoFeedback="방향지시등 체험을 기록했어요. 미러와 사각지대를 확인해요."
            }
            DemoDriveStep.MIRROR -> {demoStep = DemoDriveStep.LANE_CHANGE;demoFeedback="주변 확인 입력을 기록했어요. 한 차로씩 체험해요."}
            DemoDriveStep.LANE_CHANGE -> {
                demoCurrentLane++
                if(demoCurrentLane<demoTargetLane) {
                    demoStep=DemoDriveStep.MIRROR
                    demoFeedback="좋아요. 체험에서 한 차로 이동했어요. 남은 ${demoLaneChangesRemaining}개 차로도 다시 확인해요."
                } else {
                    advance(EvidenceSource.DEMO, "차로 변경 버튼 체험 · 실제 수행 검증 아님")
                    demoFeedback="좋아요. 체험에서 목표 차로에 도착했어요."
                }
            }
            DemoDriveStep.STOP -> {demoStep = DemoDriveStep.SURROUNDINGS;demoFeedback="멈춤 입력을 기록했어요. 신호와 보행자를 살펴봐요."}
            DemoDriveStep.SURROUNDINGS -> {demoStep = DemoDriveStep.TURN;demoFeedback="주변 확인 입력을 기록했어요."}
            DemoDriveStep.TURN -> {advance(EvidenceSource.DEMO, "우회전 버튼 체험 · 실제 수행 검증 아님");demoFeedback="좋아요. 체험에서 우회전을 마쳤어요."}
            DemoDriveStep.EXIT -> {advance(EvidenceSource.DEMO, "같은 교차로 이탈 버튼 체험 · 실제 수행 검증 아님");demoFeedback="이번 교차로 체험을 마쳤어요. 수고했어요."}
            DemoDriveStep.FINISHED -> return false
        }
        requested = false
        return true
    }

    fun configureDemoLanes(count: Int, start: Int): Boolean {
        if(!demo || demoStep!=DemoDriveStep.SIGNAL || count !in 2..4 || start !in 1 until count) return false
        demoLaneCount=count;demoStartLane=start;demoCurrentLane=start
        return true
    }

    fun setDemoObstruction(present: Boolean): Boolean {
        if (!demo || !active || demoStep !in setOf(DemoDriveStep.MIRROR, DemoDriveStep.SURROUNDINGS)) return false
        if (demoObstruction == present) return true
        demoObstruction = present
        demoFeedback=if(present) "잘 멈췄어요. 체험에서는 다시 확인할 때까지 기다려요." else "다시 확인한 상황을 입력했어요."
        record(if (present) CoachEventType.DEFERRED else CoachEventType.CHECK_CONFIRMED,
            EvidenceSource.DEMO, if (present) "접근 차량/보행자 가정 · 진행 보류" else "다시 확인한 상황 가정 · 자동 안전 판정 아님")
        return true
    }

    fun verified(input: VerifiedManeuver): Boolean {
        if (demo || !active || input.situationInstanceId != instanceId || input.completedPhase != phase ||
            input.evidenceId.isBlank() || !externalEvidence.add(input.evidenceId)) return false
        advance(EvidenceSource.VERIFIED_PROVIDER, input.evidenceId)
        return true
    }

    private fun advance(source: EvidenceSource, detail: String) {
        record(CoachEventType.PHASE_RECORDED, source, detail)
        phase = when (phase) {
            CoachPhase.LANE_PREPARATION -> CoachPhase.RIGHT_TURN
            CoachPhase.RIGHT_TURN -> CoachPhase.INTERSECTION_EXIT
            CoachPhase.INTERSECTION_EXIT -> CoachPhase.FINISHED
            else -> phase
        }
        distanceMeters = when (phase) { CoachPhase.RIGHT_TURN -> 35.0; else -> null }
        if (demo) {
            demoStep = when (phase) {
                CoachPhase.RIGHT_TURN -> DemoDriveStep.STOP
                CoachPhase.INTERSECTION_EXIT -> DemoDriveStep.EXIT
                CoachPhase.FINISHED -> DemoDriveStep.FINISHED
                else -> demoStep
            }
            demoObstruction = false
            if(phase==CoachPhase.RIGHT_TURN) demoCurrentLane=demoTargetLane
        }
        requested = false
    }

    fun cancel(reason: String) {
        if (active) record(CoachEventType.CANCELLED, if (demo) EvidenceSource.DEMO else EvidenceSource.NAVIGATION, reason)
        phase = CoachPhase.CANCELLED; requested = false; distanceMeters = null; navigationDescription = ""
    }

    fun requestExplanation() { requested = true }
    fun cue(): GuidanceCue? {
        if (!active || type in preferences.disabled || (preferences.level(type) == GuidanceLevel.ON_REQUEST && !requested)) return null
        val brief = preferences.level(type) != GuidanceLevel.DETAILED
        if (demo) return GuidanceCue("demo.${demoStep.name}.${demoObstruction}",
            if (demoObstruction) "잠깐 기다리고 다시 확인해요" else demoStep.title,
            if (demoObstruction) "접근 차량 또는 보행자가 있는 장면을 가정했어요. 진행을 보류하고 다시 확인해요."
            else if(demoStep==DemoDriveStep.LANE_CHANGE || demoStep==DemoDriveStep.SIGNAL) "체험 도로 ${demoLaneCount}개 차로 중 ${demoCurrentLane}차로예요. 우회전 준비를 위해 오른쪽으로 ${demoLaneChangesRemaining}개 차로를 한 칸씩 준비해요.\n${demoStep.instruction}"
            else demoStep.instruction,
            when (demoStep) {
                DemoDriveStep.SIGNAL, DemoDriveStep.MIRROR, DemoDriveStep.LANE_CHANGE -> "미러와 사각지대 · 사용자 확인 체험"
                else -> "신호·전방·횡단보도 · 사용자 확인 체험"
            })
        val cue = when (phase) {
            CoachPhase.LANE_PREPARATION -> GuidanceCue("lane.prepare", "우회전 전에, 차로부터",
                if (brief) "오른쪽 차로를 미리 준비해 주세요."
                else "앞 교차로에서 우회전해요. 오른쪽 차로를 미리 준비해 주세요. 여유가 없으면 무리하지 않아도 괜찮아요.",
                "사이드미러와 주변 차량을 직접 확인해 주세요")
            CoachPhase.RIGHT_TURN -> GuidanceCue("intersection.right", "돌아갈 곳을 확인해요",
                if (demo) "작은 진입로는 지나가고, 표시된 교차로에서 우회전을 준비해요."
                else "차량 내비게이션에서 우회전할 지점을 확인해 주세요.",
                "전방과 보행 공간을 직접 확인해 주세요")
            else -> GuidanceCue("intersection.exit", "교차로를 빠져나와요",
                if (brief) "진행할 차로와 주변을 확인해 주세요."
                else "우회전한 교차로를 빠져나오고 있어요. 진행할 차로와 주변을 확인하며 이어가 주세요.",
                "진행 방향과 주변 차량을 확인해 주세요")
        }
        return cue
    }

    fun markCueShown(cue: GuidanceCue) {
        val key = "${cue.id}:${preferences.level(type)}"
        if (active && shownCues.add(key)) {
            assisted.add(type)
            record(CoachEventType.CUE_SHOWN, if (demo) EvidenceSource.DEMO else EvidenceSource.NAVIGATION, cue.text, cue.id)
        }
    }

    fun noteDifficulty(text: String? = null) {
        if (instanceId.isNotBlank()) record(CoachEventType.DIFFICULTY_NOTED,
            if (demo) EvidenceSource.DEMO else EvidenceSource.USER, text ?: when(type) {
                SituationType.LANE_CHANGE_RIGHT -> "차로 변경이 어려웠어요"
                SituationType.RIGHT_TURN, SituationType.INTERSECTION_EXIT -> "우회전이 어려웠어요"
                else -> "이 상황을 주행 후 다시 보기"
            })
    }

    private fun record(eventType: CoachEventType, source: EvidenceSource, detail: String = "", cueId: String? = null) {
        emit(CoachEvent(UUID.randomUUID().toString(), tripId, instanceId, type, eventType, now(), source,
            type in assisted, preferences.level(type), cueId, detail))
    }
}

/** A recent, valid zero speed and a non-restricted UX state are both required for detailed pages. */
class DetailGate(private val now: () -> Long = System::currentTimeMillis) {
    private var speed: Float? = null
    private var speedAt = 0L
    var uxAllowed = false
    fun updateSpeed(metersPerSecond: Float?) {
        speed = metersPerSecond?.takeIf { it.isFinite() }; speedAt = now()
    }
    fun allows(demo: Boolean) = demo || (uxAllowed && speed?.let { kotlin.math.abs(it) < 0.01f } == true && now() - speedAt <= 5_000)
}
