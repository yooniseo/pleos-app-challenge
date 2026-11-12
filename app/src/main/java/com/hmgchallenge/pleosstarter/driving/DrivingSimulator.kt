package com.hmgchallenge.pleosstarter.driving

import kotlin.math.*

enum class SimGear { P,R,N,D }
enum class SimSignal { OFF,LEFT,RIGHT }
enum class SimStage(val title: String) {
    PREPARE("01 주차 상태에서 출발 준비"), PARKING_EXIT("02 후진 주차한 칸에서 앞으로 출차"), EXIT_PREPARE("03 출구 방향 확인"),
    EXIT_APPROACH("04 출구 횡단보도 앞 정차"), EXIT_CHECK("05 출구 신호·보행자 확인"),
    EXIT_TURN("06 출구에서 본선 진입"), EXIT_ALIGN("07 본선 차로에 정렬"),
    LANE_PREPARE("08 왼쪽 차로 변경 준비"), LANE_OUT("09 한 차로 이동"), LANE_RETURN("10 차로 중심에 정렬"),
    APPROACH("11 교차로 정지선 접근"), TURN_CHECK("12 신호·횡단보도 확인"), TURN("13 교차로 회전"), TURN_ALIGN("14 회전한 차로에 정렬"),
    FINAL_STOP("15 가상 체험 종료 지점"), SECURE("16 시뮬레이터 종료 준비"), COMPLETE("체험을 마쳤어요")
}
data class SimPose(val x: Double,val y: Double,val heading: Double)
data class SimPractice(val id: String,val message: String)

/** Low-speed virtual bicycle model. Road, renderer and maneuver checks share metre coordinates. */
class DrivingSimulator(val turnRight: Boolean=true,val laneRight: Boolean=turnRight) {
    init {require(laneRight==turnRight) {"The marked right turn requires lane 3; left turn requires lane 1"}}
    val road=TrainingRoadLayout(turnRight)
    val wheelbase=2.75
    val steeringRatio=14.5
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
    var y=road.initialY;private set
    var heading=road.initialHeading;private set
    var currentLane=road.startLane;private set
    val targetLane get()=road.turnLane
    val lanesRemaining get()=abs(currentLane-targetLane)
    val laneDirection get()=if(laneRight)"오른쪽" else "왼쪽"
    private val laneSign get()=if(laneRight)1 else -1
    var feedback="후진 주차한 차량이에요. 벨트와 브레이크부터 확인해요.";private set
    val practices=mutableListOf<SimPractice>()
    val trace=mutableListOf(SimPose(x,y,heading))
    var activePath: TrainingRoadPath?=null;private set
    private var stageDistance=0.0
    val stopped get()=speed<0.08
    val stageTravel get()=distance-stageDistance
    val exitApproachRemaining get()=if(road.entryFromNorth)y-road.entryStopY else road.entryStopY-y
    val approachRemaining get()=road.mainStopX-x
    val inTurnLane get()=road.eastboundLane(y)==targetLane && abs(y-road.laneY(targetLane))<0.6
    val laneStatus get()=when(stage) {
        SimStage.PREPARE,SimStage.PARKING_EXIT,SimStage.EXIT_PREPARE,SimStage.EXIT_APPROACH,SimStage.EXIT_CHECK,SimStage.EXIT_TURN->"주차장 출구 → 본선 ${road.startLane}차로"
        SimStage.TURN,SimStage.TURN_ALIGN,SimStage.FINAL_STOP,SimStage.SECURE,SimStage.COMPLETE->"${if(turnRight)"3차로 우회전 → 3차로" else "1차로 좌회전 → 1차로"}"
        else->"현재 ${currentLane}차로 → 목표 ${targetLane}차로 · ${lanesRemaining}개 남음"
    }
    val expectedSignal get()=when(stage) {
        SimStage.EXIT_PREPARE,SimStage.EXIT_APPROACH,SimStage.EXIT_CHECK,SimStage.EXIT_TURN->if(road.entryFromNorth)SimSignal.LEFT else SimSignal.RIGHT
        SimStage.LANE_PREPARE,SimStage.LANE_OUT,SimStage.LANE_RETURN->if(laneRight)SimSignal.RIGHT else SimSignal.LEFT
        SimStage.APPROACH,SimStage.TURN_CHECK,SimStage.TURN->if(turnRight)SimSignal.RIGHT else SimSignal.LEFT
        else->SimSignal.OFF
    }
    fun wheelForRadius(radius: Double)=Math.toDegrees(atan(wheelbase/radius))*steeringRatio
    val targetSteering get()=activePath?.steering(x,y,heading,wheelbase,steeringRatio) ?: 0.0
    fun steer(value: Double) {if(value.isFinite())steering=value.coerceIn(-450.0,450.0)}
    fun pedals(accel: Double,braking: Double) {
        if(!accel.isFinite() || !braking.isFinite())return
        accelerator=accel.coerceIn(0.0,100.0);brake=braking.coerceIn(0.0,100.0)
    }
    fun selectGear(value: SimGear): Boolean {
        if(value==gear)return true
        if(!stopped || brake<20) {feedback="기어 변경 전 브레이크로 완전히 정차해 주세요.";return false}
        gear=value;return true
    }
    fun setParkingBrake(value: Boolean): Boolean {
        if(!stopped) {feedback="완전히 정차한 뒤 주차 브레이크를 조작해요.";return false}
        parkingBrake=value;return true
    }
    fun setSignal(value: SimSignal) {if(signal!=value)mirrorChecked=false;signal=value}
    fun checkMirrors() {mirrorChecked=true;feedback="미러·사각지대를 직접 확인한 체험 입력을 기록했어요."}
    fun freeze() {accelerator=0.0;brake=100.0;speed=0.0}
    /** Navigation lesson starts on the carriageway; the cockpit retains the parking lesson. */
    fun startRoadPractice() {
        require(stage==SimStage.PREPARE)
        belt=true;parkingBrake=false;gear=SimGear.D;brake=80.0
        x=road.entryLaneEnd.x;y=road.laneY(currentLane);heading=PI/2
        trace.clear();trace.add(SimPose(x,y,heading));enter(SimStage.LANE_PREPARE)
        feedback="본선 ${currentLane}차로에서 시작해요. 한 차로씩 이동해 회전 차로를 준비해요."
    }
    private fun enter(value: SimStage) {
        stage=value;stageDistance=distance;mirrorChecked=false
        val here=RoadPoint(x,y)
        activePath=when(value) {
            SimStage.EXIT_APPROACH->TrainingRoadPath(TrainingRoadLayout.line(here,RoadPoint(0.0,road.entryStopY)))
            SimStage.EXIT_TURN->road.entryTurnPath(here)
            SimStage.EXIT_ALIGN,SimStage.LANE_PREPARE->road.mainStraight(here,currentLane,x+20)
            SimStage.LANE_OUT->road.laneChangePath(here,currentLane+laneSign)
            SimStage.LANE_RETURN->activePath
            SimStage.APPROACH->road.mainStraight(here,targetLane,road.mainStopX)
            SimStage.TURN->road.mainTurnPath(here)
            SimStage.TURN_ALIGN,SimStage.FINAL_STOP->road.departureStraight(here,18.0)
            else->null
        }
        if(value==SimStage.APPROACH) {green=false;pedestrian=true}
    }
    private fun aligned(target: Double)=abs(TrainingRoadLayout.angleError(target,heading))<Math.toRadians(2.5)
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
            SimStage.PREPARE->if(belt && brake>=20 && !parkingBrake && gear==SimGear.D && (!rain || wiper>0)) {
                practice("prepare","좋아요. 벨트·브레이크·기어를 확인했어요.");enter(SimStage.PARKING_EXIT)
            }
            SimStage.PARKING_EXIT->if(stageTravel>=3 && stopped && brake>=20)enter(SimStage.EXIT_PREPARE)
            SimStage.EXIT_PREPARE->if(gear==SimGear.D && signal==expectedSignal && mirrorChecked)enter(SimStage.EXIT_APPROACH)
            SimStage.EXIT_APPROACH->if(exitApproachRemaining in -0.15..0.5 && stopped && brake>=20)enter(SimStage.EXIT_CHECK)
            SimStage.EXIT_CHECK->if(exitApproachRemaining in -0.15..0.5 && aligned(road.initialHeading) && stopped && brake>=20 && green && !pedestrian && mirrorChecked && signal==expectedSignal && gear==SimGear.D)enter(SimStage.EXIT_TURN)
            SimStage.EXIT_TURN->if(signal==expectedSignal && activePath!!.reached(x,y) && aligned(PI/2)) {
                practice("exit","좋아요. 횡단보도를 지나 본선 ${currentLane}차로에 진입했어요.");enter(SimStage.EXIT_ALIGN)
            }
            SimStage.EXIT_ALIGN->if(aligned(PI/2) && abs(y-road.laneY(currentLane))<0.5 && abs(steering)<12)enter(SimStage.LANE_PREPARE)
            SimStage.LANE_PREPARE->if(signal==expectedSignal && mirrorChecked)enter(SimStage.LANE_OUT)
            SimStage.LANE_OUT->if(activePath!!.progress(x,y)>=activePath!!.length/2)enter(SimStage.LANE_RETURN)
            SimStage.LANE_RETURN->if(signal==expectedSignal && activePath!!.reached(x,y) && aligned(PI/2)) {
                currentLane+=laneSign;practice("lane$currentLane","좋아요. ${currentLane}차로에 정렬했어요. ${if(lanesRemaining==0)"회전 차로 준비 완료." else "한 차로 더 이동해요."}")
                enter(if(lanesRemaining>0)SimStage.LANE_PREPARE else SimStage.APPROACH)
            }
            SimStage.APPROACH->if(approachRemaining in -0.15..0.5 && inTurnLane && stopped && brake>=20)enter(SimStage.TURN_CHECK)
            SimStage.TURN_CHECK->if(approachRemaining in -0.15..0.5 && aligned(PI/2) && stopped && brake>=20 && green && !pedestrian && mirrorChecked && signal==expectedSignal && gear==SimGear.D && inTurnLane)enter(SimStage.TURN)
            SimStage.TURN->if(signal==expectedSignal && activePath!!.reached(x,y) && aligned(road.departureHeading))enter(SimStage.TURN_ALIGN)
            SimStage.TURN_ALIGN->if(aligned(road.departureHeading) && abs(x-road.departureLaneX)<0.5 && abs(steering)<12) {
                practice("intersection","좋아요. ${if(turnRight)"3차로에서 우회전" else "1차로에서 좌회전"}하고 횡단보도를 통과했어요.");enter(SimStage.FINAL_STOP)
            }
            SimStage.FINAL_STOP->if(stageTravel>=6 && stopped && brake>=20)enter(SimStage.SECURE)
            SimStage.SECURE->if(stopped && gear==SimGear.P && parkingBrake && signal==SimSignal.OFF && mirrorChecked) {
                practice("complete","체험을 마쳤어요. 실제 주차 동작은 별도 연습이 필요해요.");enter(SimStage.COMPLETE)
            }
            SimStage.COMPLETE->Unit
        }
    }
    fun movementHint(): String? {
        val needsMotion=activePath!=null || (stage==SimStage.PARKING_EXIT && stageTravel<3)
        if(!needsMotion)return null
        if(!belt)return "벨트 미착용이에요. 정차 중 벨트를 착용해 주세요."
        if(parkingBrake)return "사이드브레이크가 체결되어 있어요. 정차 중 해제해요."
        if(gear!=SimGear.D)return "현재 ${gear}단이에요. 정차하고 브레이크를 켠 채 D단으로 바꿔 주세요."
        if(brake>0)return "브레이크 유지 중이에요. 이동할 때 ‘액셀 켜기’를 누르면 브레이크가 풀려요."
        if(accelerator==0.0 && stopped)return "주변 확인 후 준비되면 ‘액셀 켜기’를 눌러 이동해요."
        return null
    }
    fun instruction(): String {
        val direction=if(expectedSignal==SimSignal.LEFT)"왼쪽" else "오른쪽"
        if(!road.paved(x,y))return "연습 도로 밖으로 벗어났어요. 브레이크로 멈추고 ‘처음부터’로 다시 연습해요."
        if((stage in setOf(SimStage.APPROACH,SimStage.TURN_CHECK) && approachRemaining< -0.15) || (stage in setOf(SimStage.EXIT_APPROACH,SimStage.EXIT_CHECK) && exitApproachRemaining< -0.15))
            return "정지선을 넘었어요. 브레이크로 멈추고 ‘처음부터’로 정지선 앞 정차를 다시 연습해요."
        if(stage==SimStage.PREPARE)return when {
            !belt->"안전벨트를 착용해 주세요."
            rain && wiper==0->"비 오는 체험이에요. 앞 와이퍼를 켜요."
            brake<20->"브레이크를 밟은 채 출발을 준비해요."
            parkingBrake->"브레이크를 유지하고 주차 브레이크를 해제해요."
            gear!=SimGear.D->"후진 주차한 차량이에요. 브레이크를 밟고 D단을 선택해요."
            else->"주차칸에서 앞으로 출차할 준비가 됐어요."
        }
        if(expectedSignal!=SimSignal.OFF && signal!=expectedSignal)return "$direction 깜빡이를 켜고 ${if(stage in setOf(SimStage.LANE_PREPARE,SimStage.LANE_OUT,SimStage.LANE_RETURN))"차로 변경" else "회전"}을 준비하세요."
        return when(stage) {
            SimStage.PARKING_EXIT->if(stageTravel<3)"핸들을 가운데 두고 주차칸에서 앞으로 천천히 나와요." else "브레이크로 완전히 멈추고 출구를 확인해요."
            SimStage.EXIT_PREPARE,SimStage.LANE_PREPARE->"미러·사각지대와 주변을 확인하고 ‘주변 확인 입력’을 눌러요."
            SimStage.EXIT_APPROACH->"출구 횡단보도 앞 정지선까지 ${exitApproachRemaining.coerceAtLeast(0.0).roundToInt()}m. 선을 넘기 전에 완전히 멈춰요."
            SimStage.EXIT_CHECK,SimStage.TURN_CHECK->when {
                !green->"가상 차량 신호가 빨강이에요. 정지선 앞에서 기다려요."
                pedestrian->"횡단보도에 보행자가 있어요. 브레이크를 유지해요."
                !mirrorChecked->"신호·횡단보도·주변을 확인하고 확인 입력을 눌러요."
                else->"주변 확인 후 천천히 회전해요."
            }
            SimStage.EXIT_TURN,SimStage.LANE_OUT,SimStage.LANE_RETURN,SimStage.TURN->"연습 경로 목표: 핸들 ${if(targetSteering<0)"왼쪽" else "오른쪽"} ${abs(targetSteering).roundToInt()}°. ${if(stage==SimStage.TURN)"${targetLane}차로에서 ${if(turnRight)"우회전" else "좌회전"}해요." else "민트색 경로를 따라 천천히 진행해요."}"
            SimStage.EXIT_ALIGN,SimStage.TURN_ALIGN->"회전한 차로의 중심으로 정렬하고 핸들을 풀어요."
            SimStage.APPROACH->"${targetLane}차로 유지 · 정지선까지 ${approachRemaining.coerceAtLeast(0.0).roundToInt()}m. 횡단보도 전에 정차해요."
            SimStage.FINAL_STOP->"회전한 차로로 직진한 뒤 가상 체험을 정지해요. 실제 주차 구간은 아니에요."
            SimStage.SECURE->"시뮬레이터 종료: 브레이크 → P단 → 사이드브레이크 → 깜빡이 끄기 → 주변 확인 입력."
            SimStage.COMPLETE->"주행 피드백에서 연습한 단계를 확인해요."
            else->"차량 상태를 확인해요."
        }
    }
}
