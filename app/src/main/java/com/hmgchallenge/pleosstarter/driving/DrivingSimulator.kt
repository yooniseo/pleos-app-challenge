package com.hmgchallenge.pleosstarter.driving

import kotlin.math.*

enum class SimGear { P,R,N,D }
enum class SimSignal { OFF,LEFT,RIGHT }
enum class SimStage(val title: String) {
    PREPARE("01 주차 상태에서 출발 준비"), REVERSE("02 주차 공간에서 곧게 후진"), EXIT_PREPARE("03 출차 방향 확인"),
    EXIT_TURN("04 주차장 출구 우회전"), EXIT_ALIGN("05 핸들 풀고 직진"),
    LANE_PREPARE("06 왼쪽 차로 변경 준비"), LANE_OUT("07 왼쪽으로 방향 만들기"), LANE_RETURN("08 핸들 되돌려 차로 정렬"),
    APPROACH("09 교차로 정지선 접근"), TURN_CHECK("10 신호·보행자 확인"), TURN("11 교차로 회전"), TURN_ALIGN("12 회전 후 핸들 풀기"),
    FINAL_STOP("13 도착 후 정차"), SECURE("14 주차 마무리"), COMPLETE("체험을 마쳤어요")
}
data class SimPose(val x: Double,val y: Double,val heading: Double)
data class SimPractice(val id: String,val message: String)

/** A low-speed training model, not calibrated to a real vehicle or road. Positive wheel angle = right. */
class DrivingSimulator(val turnRight: Boolean=true,val laneRight: Boolean=false) {
    val wheelbase=2.75
    val steeringRatio=14.5
    val exitRadius=7.0
    val turnRadius=9.0
    val laneRadius=50.0
    val laneWidth=3.2
    var stage=SimStage.PREPARE;private set
    var gear=SimGear.P;private set
    var signal=SimSignal.OFF;private set
    var parkingBrake=true;private set
    var belt=false
    var windowOpen=0
    var wiper=0
    var rain=false
    var green=false
    var pedestrian=true
    var mirrorChecked=false;private set
    var steering=0.0;private set
    var accelerator=0.0;private set
    var brake=0.0;private set
    var speed=0.0;private set
    var distance=0.0;private set
    var x=0.0;private set
    var y=0.0;private set
    var heading=0.0;private set
    var currentLane=if(laneRight)1 else 3;private set
    val targetLane get()=if(laneRight)3 else 1
    val lanesRemaining get()=abs(currentLane-targetLane)
    val laneDirection get()=if(laneRight)"오른쪽" else "왼쪽"
    private val laneSign get()=if(laneRight)1 else -1
    var feedback="브레이크를 밟고 출발 준비부터 해봐요.";private set
    val practices=mutableListOf<SimPractice>()
    val trace=mutableListOf(SimPose(0.0,0.0,0.0))
    private var stageDistance=0.0
    private var turnStart=0.0
    private var laneStart=SimPose(0.0,0.0,0.0)
    private val laneYaw=acos(1-laneWidth/(2*laneRadius))
    val stopped get()=speed<0.08
    val stageTravel get()=distance-stageDistance
    val expectedSignal get()=when(stage) {
        SimStage.EXIT_PREPARE,SimStage.EXIT_TURN->SimSignal.RIGHT
        SimStage.LANE_PREPARE,SimStage.LANE_OUT,SimStage.LANE_RETURN->if(laneRight)SimSignal.RIGHT else SimSignal.LEFT
        SimStage.APPROACH,SimStage.TURN_CHECK,SimStage.TURN->if(turnRight)SimSignal.RIGHT else SimSignal.LEFT
        else->SimSignal.OFF
    }
    fun wheelForRadius(radius: Double)=Math.toDegrees(atan(wheelbase/radius))*steeringRatio
    val targetSteering get()=when(stage) {
        SimStage.EXIT_TURN->wheelForRadius(exitRadius)
        SimStage.LANE_OUT->laneSign*wheelForRadius(laneRadius)
        SimStage.LANE_RETURN->-laneSign*wheelForRadius(laneRadius)
        SimStage.TURN->wheelForRadius(turnRadius)*(if(turnRight)1 else -1)
        else->0.0
    }
    fun steer(value: Double) {if(value.isFinite())steering=value.coerceIn(-450.0,450.0)}
    fun pedals(accel: Double,braking: Double) {
        if(!accel.isFinite() || !braking.isFinite())return
        accelerator=accel.coerceIn(0.0,100.0);brake=braking.coerceIn(0.0,100.0)
    }
    fun selectGear(value: SimGear): Boolean {
        if(value==gear)return true
        if(!stopped || brake<20) {feedback="기어를 바꾸기 전 브레이크로 완전히 정차해 주세요.";return false}
        gear=value;return true
    }
    fun setParkingBrake(value: Boolean): Boolean {
        if(!stopped) {feedback="이 체험에서는 완전히 정차한 뒤 주차 브레이크를 조작해요.";return false}
        parkingBrake=value;return true
    }
    fun setSignal(value: SimSignal) {if(signal!=value)mirrorChecked=false;signal=value}
    fun checkMirrors() {mirrorChecked=true;feedback="미러·사각지대를 직접 확인한 체험 입력을 기록했어요."}
    fun freeze() {accelerator=0.0;brake=100.0;speed=0.0}
    private fun enter(value: SimStage) {stage=value;stageDistance=distance;mirrorChecked=false}
    private fun practice(id: String,message: String) {if(practices.none {it.id==id})practices.add(SimPractice(id,message));feedback=message}
    fun tick(seconds: Double) {
        if(!seconds.isFinite() || seconds<=0)return
        val dt=seconds.coerceAtMost(0.1)
        val driveAllowed=belt && !parkingBrake && gear in setOf(SimGear.R,SimGear.D) && stage!=SimStage.COMPLETE
        val a=if(driveAllowed)accelerator/100*1.6-brake/100*5.0-0.2 else -6.0
        speed=(speed+a*dt).coerceIn(0.0,if(gear==SimGear.R)1.4 else 3.3)
        if(speed<0.03 && a<=0)speed=0.0
        val signed=speed*(if(gear==SimGear.R)-1 else 1)
        heading+=signed/wheelbase*tan(Math.toRadians(steering/steeringRatio))*dt
        x+=signed*sin(heading)*dt;y+=signed*cos(heading)*dt;distance+=speed*dt
        if(trace.last().let {hypot(x-it.x,y-it.y)}>0.12) {trace.add(SimPose(x,y,heading));if(trace.size>2400)trace.removeAt(0)}
        when(stage) {
            SimStage.PREPARE->if(belt && brake>=20 && !parkingBrake && gear==SimGear.R && (!rain || wiper>0)) {
                practice("prepare","좋아요. 벨트·브레이크·기어를 확인했어요.");enter(SimStage.REVERSE)
            }
            SimStage.REVERSE->if(stageTravel>=3.0 && stopped && brake>=20)enter(SimStage.EXIT_PREPARE)
            SimStage.EXIT_PREPARE->if(gear==SimGear.D && signal==SimSignal.RIGHT && mirrorChecked) {turnStart=heading;enter(SimStage.EXIT_TURN)}
            SimStage.EXIT_TURN->if(signal==expectedSignal && heading-turnStart>=Math.toRadians(89.5)) {practice("exit","좋아요. 출구를 돌았어요. 핸들을 풀어 직진해요.");enter(SimStage.EXIT_ALIGN)}
            SimStage.EXIT_ALIGN->if(abs(steering)<12) {laneStart=SimPose(x,y,heading);enter(SimStage.LANE_PREPARE)}
            SimStage.LANE_PREPARE->if(signal==expectedSignal && mirrorChecked) {laneStart=SimPose(x,y,heading);enter(SimStage.LANE_OUT)}
            SimStage.LANE_OUT->if((heading-laneStart.heading)*laneSign>=laneYaw)enter(SimStage.LANE_RETURN)
            SimStage.LANE_RETURN->{
                val lateral=(x-laneStart.x)*cos(laneStart.heading)-(y-laneStart.y)*sin(laneStart.heading)
                if(signal==expectedSignal && (heading-laneStart.heading)*laneSign<=Math.toRadians(1.0) && lateral*laneSign > laneWidth*0.72) {
                    currentLane+=laneSign;practice("lane$currentLane","좋아요. 체험에서 한 차로 이동했어요. 남은 ${lanesRemaining}개 차로를 확인해요.")
                    enter(if(lanesRemaining>0)SimStage.LANE_PREPARE else SimStage.APPROACH)
                }
            }
            SimStage.APPROACH->if(stageTravel>=10 && stopped && brake>=20)enter(SimStage.TURN_CHECK)
            SimStage.TURN_CHECK->if(stopped && brake>=20 && green && !pedestrian && mirrorChecked && signal==expectedSignal && gear==SimGear.D) {turnStart=heading;enter(SimStage.TURN)}
            SimStage.TURN->if(signal==expectedSignal && (heading-turnStart)*(if(turnRight)1 else -1)>=Math.toRadians(89.5))enter(SimStage.TURN_ALIGN)
            SimStage.TURN_ALIGN->if(abs(steering)<12) {practice("intersection","좋아요. ${if(turnRight)"우회전" else "좌회전"}한 뒤 핸들을 풀었어요.");enter(SimStage.FINAL_STOP)}
            SimStage.FINAL_STOP->if(stageTravel>=3 && stopped && brake>=20)enter(SimStage.SECURE)
            SimStage.SECURE->if(stopped && gear==SimGear.P && parkingBrake && signal==SimSignal.OFF && mirrorChecked) {
                practice("finish","잘 마쳤어요. 내리기 전 뒤쪽 차량과 사람을 확인해요.");enter(SimStage.COMPLETE)
            }
            SimStage.COMPLETE->Unit
        }
    }
    /** Explains a stopped training car without advancing the scenario or touching pedals. */
    fun movementHint(): String? {
        val needsMotion=stage in setOf(SimStage.EXIT_TURN,SimStage.LANE_OUT,SimStage.LANE_RETURN,SimStage.TURN) ||
            (stage==SimStage.REVERSE && stageTravel<3) ||
            (stage==SimStage.APPROACH && stageTravel<10) || (stage==SimStage.FINAL_STOP && stageTravel<3)
        if(!needsMotion)return null
        if(!belt)return "벨트가 미착용 상태예요. 정차 중 ‘벨트 미착용’을 눌러 착용해 주세요."
        if(parkingBrake)return "사이드브레이크가 체결되어 있어요. 정차 중 해제한 뒤 진행해요."
        val expected=if(stage==SimStage.REVERSE)SimGear.R else SimGear.D
        if(gear!=expected)return "현재 ${gear}단이에요. 완전히 정차하고 브레이크를 켠 채 ${expected}단으로 바꿔 주세요."
        if(brake>0)return "브레이크가 켜져 있어 이동을 멈추고 있어요. 이동할 때 ‘액셀 켜기’를 누르면 브레이크가 풀려요."
        if(accelerator==0.0 && stopped)return "주변 확인 입력은 자동 출발이 아니에요. 준비되면 오른쪽의 ‘액셀 켜기’를 눌러 이동해요."
        return null
    }
    fun instruction(): String {
        val direction=if(expectedSignal==SimSignal.LEFT)"왼쪽" else "오른쪽"
        if(stage==SimStage.PREPARE)return when {
            !belt->"안전벨트를 착용해 주세요."
            rain && wiper==0->"비 오는 체험이에요. 앞 와이퍼를 켜 시야를 준비해요."
            brake<20->"브레이크를 밟은 채 출발을 준비해요."
            parkingBrake->"브레이크를 유지하고 주차 브레이크를 해제해요."
            gear!=SimGear.R->"브레이크를 밟은 채 R단을 선택해요."
            else->"주차 공간에서 나갈 준비가 됐어요."
        }
        if(stage==SimStage.REVERSE)return if(stageTravel<3)"핸들을 가운데 두고 천천히 후진해요. 체험 구간 ${(3-stageTravel).coerceAtLeast(0.0).let {"%.1f".format(java.util.Locale.US,it)}}m 남았어요." else "후진 구간을 지났어요. 브레이크로 완전히 정차해 주세요."
        if(stage==SimStage.EXIT_PREPARE && gear!=SimGear.D)return "완전히 정차하고 브레이크를 밟은 채 D단으로 바꿔요."
        if(expectedSignal!=SimSignal.OFF && signal!=expectedSignal)
            return "$direction 깜빡이를 켜고 ${if(stage in setOf(SimStage.LANE_PREPARE,SimStage.LANE_OUT,SimStage.LANE_RETURN))"차로 변경을" else if(stage in setOf(SimStage.APPROACH,SimStage.TURN_CHECK,SimStage.TURN))if(turnRight)"우회전을" else "좌회전을" else "출차를"} 준비하세요."
        return when(stage) {
            SimStage.EXIT_PREPARE,SimStage.LANE_PREPARE->"미러·사각지대와 주변을 직접 확인하고 ‘주변 확인 입력’을 눌러요."
            SimStage.EXIT_TURN,SimStage.LANE_OUT,SimStage.LANE_RETURN,SimStage.TURN->
                "체험 목표: 핸들을 ${if(targetSteering<0)"왼쪽" else "오른쪽"} ${abs(targetSteering).roundToInt()}°. ${if(stage==SimStage.LANE_RETURN)"차체 방향을 도로와 나란히 되돌려요." else "페달을 조금씩 눌러 천천히 진행해요."}"
            SimStage.EXIT_ALIGN,SimStage.TURN_ALIGN->"핸들을 0°로 풀어 도로 방향과 나란히 해요."
            SimStage.APPROACH->if(stageTravel<10)"핸들을 가운데 두고 정지선까지 약 ${(10-stageTravel).roundToInt().coerceAtLeast(0)}m 이동해요." else "정지선이에요. 브레이크로 완전히 정차해 주세요."
            SimStage.TURN_CHECK->when { !green->"체험 신호가 빨강이에요. 브레이크를 유지하고 기다려요."
                pedestrian->"보행자 접근 상황이에요. 브레이크를 유지하고 기다려요."
                !mirrorChecked->"신호·횡단보도·주변을 직접 확인한 상황을 입력해 주세요."
                else->"회전 준비를 확인하고 있어요." }
            SimStage.FINAL_STOP->if(stageTravel<3)"회전한 도로에서 3m 이상 직진한 뒤 브레이크로 멈춰요." else "브레이크로 완전히 정차해요."
            SimStage.SECURE->"브레이크를 밟은 채 P단 → 주차 브레이크 체결 → 깜빡이 끄기 → 하차 전 주변 확인 입력으로 마무리해요."
            SimStage.COMPLETE->"주행 피드백에서 잘한 단계와 다시 연습할 부분을 확인해요."
            else->"차량 상태를 확인해요."
        }
    }
}
