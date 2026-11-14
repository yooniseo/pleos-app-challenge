package com.hmgchallenge.pleosstarter.session

import android.content.Context
import com.hmgchallenge.pleosstarter.core.data.SharedCoachRepository
import com.hmgchallenge.pleosstarter.feature.quests.*
import java.util.UUID

data class VehicleQuestInput(
    val signal: Signal?,val signalAt: Long,
    val angle: Float?,val angleAt: Long,
    val brake: Boolean?,val brakeAt: Long,
    val speedMps: Double?,val speedAt: Long
)

/** The team's detector feeds a durable board and coach feedback. SDK observations retain their source. */
class VehicleQuestController(context: Context,val source: String="VEHICLE") {
    private val repository=SharedCoachRepository.get(context)
    var board=repository.vehicleQuestBoard(source);private set
    var tripId: String?=null;private set
    var status="차량 신호 대기";private set
    var error: String?=null;private set
    var coachMessage="";private set
    var coachMessageUntil=0L;private set
    var onCue: (String)->Unit={}
    var enabled=true;private set
    private var metres=0.0
    private var lastAt=0L
    private var lastSample=0L
    private var currentMonotonic=0L
    private var currentWall=0L
    private var lastRetry=0L
    private var lastStillAt=0L
    private var lastStillWall=0L
    private data class Pending(val id: String,val tripId: String,val attempt: Attempt)
    private var pending: Pending?=null
    private val detector=ManeuverDetector {attempt->
        val id=tripId
        if(id!=null) {
            pending=Pending("$id:attempt:${attempt.t}",id,attempt.copy(t=currentWall))
            persist()
        }
    }
    init {
        require(source in setOf("VEHICLE","SIMULATION"))
        detector.reset()
    }
    private fun fresh(now: Long,at: Long)=at>0 && now-at in 0..3000

    fun update(input: VehicleQuestInput,now: Long,wall: Long=System.currentTimeMillis()) {
        if(!enabled)return
        currentMonotonic=now;currentWall=wall
        if(pending!=null) {
            if(now-lastRetry>=1000){lastRetry=now;persist()}
            lastAt=now
            return
        }
        val speed=input.speedMps?.takeIf {it.isFinite() && it>=0 && fresh(now,input.speedAt)}
        val signal=input.signal?.takeIf {it!=Signal.UNKNOWN && fresh(now,input.signalAt)}
        val angle=input.angle?.takeIf {it.isFinite() && fresh(now,input.angleAt)}
        // 주행 기록(속도 샘플)은 속도만 있으면 남긴다. 깜빡이·조향각은 퀘스트 판정에만 필요하다.
        if(speed==null) {
            detector.reset();lastAt=now;status="속도 미수신 · 판정 대기"
            return
        }
        if(speed<=.1 && !detector.inProgress) {
            tripId?.takeIf {lastSample==0L || now-lastSample>=1000}?.let {id->
                repository.sample(id,wall,0f,(metres/1000).toFloat());lastSample=now
            }
            if(tripId==null){lastStillAt=now;lastStillWall=wall}
            detector.reset();lastAt=now;status="정차 중 · 주행 신호를 기다려요"
            return
        }
        if(tripId==null) {
            val id=UUID.randomUUID().toString()
            // 출발 직전 정차 값을 첫 샘플로 남겨야 급출발(0 → 이동)을 판단할 수 있다.
            val seeded=lastStillAt>0 && now-lastStillAt in 0..1500
            if(!repository.beginVehicleQuestTrip(id,if(seeded)lastStillWall else wall,source)) {
                error=repository.lastError;lastAt=now;detector.reset();return
            }
            tripId=id;metres=0.0;lastSample=0L
            if(seeded){repository.sample(id,lastStillWall,0f,0f);lastSample=lastStillAt}
            lastStillAt=0L
        }
        if(lastAt>0 && now-lastAt in 1..1000)metres+=speed*(now-lastAt)/1000
        lastAt=now
        if(lastSample==0L || now-lastSample>=1000) {
            repository.sample(tripId!!,wall,(speed*3.6).toFloat(),(metres/1000).toFloat());lastSample=now
        }
        if(signal==null || angle==null) {
            detector.reset()
            status=if(signal==null)"주행 기록 중 · 깜빡이 미확인이라 퀘스트 판정 대기" else "주행 기록 중 · 조향각 미수신이라 퀘스트 판정 대기"
            return
        }
        detector.onSignal(now,signal)
        if(input.brake!=null && fresh(now,input.brakeAt))detector.onBrake(now,input.brake) else detector.onBrakeMissing()
        status=if(input.brake==null || !fresh(now,input.brakeAt))"브레이크 미수신 · 우회전 판정 보류" else "차량 신호 수신 중 · 임시 각도 기준"
        detector.onAngle(now,angle)
    }
    private fun persist(): Boolean {
        val item=pending ?: return true
        if(!repository.recordVehicleQuestAttempt(item.id,item.tripId,source,item.attempt)) {
            error=repository.lastError ?: "차량 퀘스트 저장 실패"
            return false
        }
        board=repository.vehicleQuestBoard(source)
        pending=null;error=null
        coachMessage=if(item.attempt.success) {
            "${QuestCatalog.find(item.attempt.questId)?.title} 신호 조건을 기록했어요"
        } else item.attempt.result.message
        coachMessageUntil=currentMonotonic+8000
        onCue(coachMessage)
        return true
    }
    fun finish(complete: Boolean=true,at: Long=System.currentTimeMillis()): Boolean {
        detector.reset();lastAt=0
        if(!persist())return false
        val id=tripId
        if(id!=null && !repository.endTrip(id,at,(metres/1000).toFloat(),complete)) {
            error=repository.lastError;return false
        }
        tripId=null;error=null;enabled=false;status="차량 기록을 마쳤어요 · 다시 연결할 수 있어요"
        return true
    }
    fun resume() {enabled=true;suspendInputs()}
    fun suspendInputs() {detector.reset();lastAt=0;status="차량 연결 대기"}
}
