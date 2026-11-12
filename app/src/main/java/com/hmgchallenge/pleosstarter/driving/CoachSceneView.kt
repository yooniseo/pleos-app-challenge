package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.animation.ValueAnimator
import android.view.View

/** Educational scene, deliberately not a live camera, map, or obstacle detector. */
class CoachSceneView(context: Context) : View(context) {
    var phase = CoachPhase.LANE_PREPARATION
    var parking: ParkingStyle? = null
    var parkingStep = 0
    var indicator = Indicator.UNKNOWN
    var demo = true
    var demoStep = DemoDriveStep.SIGNAL
    var demoObstruction = false
    var laneCount=3
    var currentLane=1
    private var carX: Float?=null
    private var carY=302f
    private var carAngle=0f
    private var poseKey=""
    private var animator: ValueAnimator?=null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mint = Color.rgb(101, 233, 197)
    init { contentDescription = "설명용 도식 · 실제 도로와 차량을 추적하지 않음" }
    private fun laneX(lane: Int)=254f+(lane-0.5f)*166f/laneCount
    fun refreshPose() {
        val key="$phase:$currentLane:$laneCount"
        if(key==poseKey) return
        poseKey=key
        val x=when(phase) {CoachPhase.INTERSECTION_EXIT,CoachPhase.FINISHED->529f;else->laneX(currentLane)}
        val y=when(phase) {CoachPhase.RIGHT_TURN->244f;CoachPhase.INTERSECTION_EXIT,CoachPhase.FINISHED->156f;else->302f}
        val angle=if(phase==CoachPhase.INTERSECTION_EXIT || phase==CoachPhase.FINISHED) 90f else 0f
        animator?.cancel()
        val oldX=carX
        if(oldX==null || !demo) {carX=x;carY=y;carAngle=angle;invalidate();return}
        val oldY=carY;val oldAngle=carAngle
        val turning=angle==90f && oldAngle==0f
        val turnPath=if(turning) PathMeasure(Path().apply {
            moveTo(oldX,oldY);cubicTo(oldX,156f,460f,156f,x,y)
        },false) else null
        val position=FloatArray(2);val tangent=FloatArray(2)
        animator=ValueAnimator.ofFloat(0f,1f).apply {
            duration=1000
            addUpdateListener {
                val t=it.animatedValue as Float
                if(turnPath!=null) {
                    turnPath.getPosTan(turnPath.length*t,position,tangent)
                    carX=position[0];carY=position[1]
                    carAngle=Math.toDegrees(kotlin.math.atan2(tangent[1].toDouble(),tangent[0].toDouble())).toFloat()+90f
                } else {carX=oldX+(x-oldX)*t;carY=oldY+(y-oldY)*t;carAngle=oldAngle+(angle-oldAngle)*t}
                invalidate()
            };start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 640f, height / 350f)
        canvas.save(); canvas.translate((width - 640 * scale) / 2, (height - 350 * scale) / 2); canvas.scale(scale, scale)
        if (parking == null) road(canvas) else parking(canvas)
        canvas.restore()
    }
    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0f) {
        p.color = color; p.style = Paint.Style.FILL; c.drawRoundRect(RectF(l,t,r,b),radius,radius,p)
    }
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float = 17f, color: Int = Color.WHITE) {
        p.color = color; p.textSize = size; p.style = Paint.Style.FILL; c.drawText(value,x,y,p)
    }
    private fun line(c: Canvas, points: FloatArray, color: Int, stroke: Float = 3f) {
        p.color = color; p.strokeWidth = stroke; p.style = Paint.Style.STROKE
        c.drawLines(points,p); p.style = Paint.Style.FILL
    }
    private fun car(c: Canvas, x: Float, y: Float, rotation: Float = 0f) {
        c.save(); c.rotate(rotation,x,y)
        rect(c,x-18,y-31,x+18,y+31,mint,9f)
        rect(c,x-12,y-17,x+12,y-6,Color.rgb(15,50,50),4f)
        rect(c,x-12,y+12,x+12,y+21,Color.rgb(15,50,50),4f)
        if(indicator == Indicator.RIGHT) rect(c,x+17,y-20,x+23,y-10,Color.rgb(255,191,103),3f)
        c.restore()
    }
    private fun road(c: Canvas) {
        val block = Color.rgb(34,54,63); val road = Color.rgb(55,73,82)
        rect(c,25f,12f,240f,96f,block,18f); rect(c,435f,12f,615f,96f,block,18f)
        rect(c,25f,215f,240f,338f,block,18f); rect(c,435f,215f,615f,338f,block,18f)
        rect(c,254f,0f,420f,350f,road); rect(c,0f,108f,640f,203f,road)
        // A smaller driveway BEFORE the target intersection, explicitly demo-only.
        rect(c,420f,271f,590f,303f,road)
        if(demo) text(c,"작은 진입로",456f,329f,16f,Color.rgb(174,193,201))
        text(c,if(demo)"이번 교차로" else "우회전 흐름 예시",435f,87f,19f,mint)
        p.color = mint; p.style=Paint.Style.STROKE; p.strokeWidth=3f
        c.drawRoundRect(RectF(432f,99f,626f,212f),12f,12f,p); p.style=Paint.Style.FILL
        if(demo) {
            rect(c,254f+(laneCount-1)*166f/laneCount,225f,420f,350f,Color.rgb(47,91,87))
            for(lane in 1 until laneCount) for(y in 230..340 step 28) {
                val x=254f+lane*166f/laneCount
                line(c,floatArrayOf(x,y.toFloat(),x,y+14f),Color.rgb(168,184,192),2f)
            }
            for(lane in 1..laneCount) text(c,"$lane",laneX(lane)-4,345f,13f)
            text(c,"목표 ${laneCount}차로",435f,285f,16f,mint)
        } else for (y in 220..340 step 32) line(c,floatArrayOf(335f,y.toFloat(),335f,y+17f),Color.rgb(168,184,192))
        for (x in 268..403 step 23) rect(c,x.toFloat(),211f,x+12f,223f,Color.rgb(216,229,228))
        val routeX=if(demo) laneX(laneCount) else 376f
        val startX=if(demo) laneX(currentLane) else 295f
        val path=Path().apply { moveTo(startX,330f); cubicTo(startX,285f,routeX,280f,routeX,234f); lineTo(routeX,180f); quadTo(routeX,156f,432f,156f); lineTo(582f,156f) }
        p.color=mint; p.alpha=130; p.strokeWidth=7f; p.style=Paint.Style.STROKE;c.drawPath(path,p);p.alpha=255;p.style=Paint.Style.FILL
        if(demo && carX!=null) car(c,carX!!,carY,carAngle) else when(phase) {
            CoachPhase.LANE_PREPARATION -> car(c,295f,302f)
            CoachPhase.RIGHT_TURN -> car(c,376f,244f)
            CoachPhase.INTERSECTION_EXIT,CoachPhase.FINISHED -> car(c,529f,156f,90f)
            else -> car(c,295f,302f)
        }
        if(demo && phase!=CoachPhase.FINISHED) {
            val amber=Color.rgb(255,191,103)
            if(demoStep in setOf(DemoDriveStep.MIRROR,DemoDriveStep.LANE_CHANGE)) {
                rect(c,38f,32f,228f,87f,Color.rgb(13,25,34),8f)
                text(c,if(demoObstruction) "접근 차량 가정" else "미러 확인 가정",50f,58f,16f,amber)
                text(c,"실제 카메라 영상 아님",50f,78f,12f,Color.rgb(177,195,204))
                if(demoObstruction) {
                    rect(c,356f,278f,389f,330f,amber,7f)
                    text(c,"접근 차량 가정",435f,250f,16f,amber)
                }
            }
            if(demoStep in setOf(DemoDriveStep.STOP,DemoDriveStep.SURROUNDINGS,DemoDriveStep.TURN)) {
                rect(c,435f,18f,619f,67f,Color.rgb(13,25,34),8f)
                text(c,if(demoStep==DemoDriveStep.STOP) "신호 앞 정지 가정" else "신호·보행자 확인",447f,48f,17f,amber)
                if(demoObstruction) {
                    p.color=amber;c.drawCircle(358f,216f,9f,p)
                    line(c,floatArrayOf(358f,225f,358f,246f,345f,232f,370f,232f,358f,246f,347f,259f,358f,246f,369f,259f),amber)
                    text(c,"보행자 가정 · 대기",435f,250f,16f,amber)
                }
            }
        }
    }
    override fun onDetachedFromWindow() {animator?.cancel();super.onDetachedFromWindow()}
    private fun parking(c: Canvas) {
        val parallel=parking==ParkingStyle.PARALLEL
        rect(c,22f,20f,618f,330f,Color.rgb(34,54,63),20f)
        val bay=if(parallel) RectF(310f,95f,525f,190f) else RectF(390f,75f,505f,277f)
        p.color=Color.rgb(208,226,224);p.strokeWidth=4f;p.style=Paint.Style.STROKE;c.drawRect(bay,p);p.style=Paint.Style.FILL
        text(c,"주차 공간",390f,52f,18f)
        if(parkingStep==0){p.color=Color.rgb(255,191,103);p.alpha=90;c.drawCircle(235f,230f,96f,p);p.alpha=255}
        if(parkingStep<3) {
            car(c,235f,245f,if(parallel)90f else 90f)
            val path=Path().apply {moveTo(245f,245f);quadTo(445f,330f,bay.centerX(),bay.centerY())}
            p.color=mint;p.strokeWidth=5f;p.style=Paint.Style.STROKE;c.drawPath(path,p);p.style=Paint.Style.FILL
            text(c,if(parking==ParkingStyle.FORWARD)"전진 흐름 예시" else "후진 흐름 예시",55f,80f,20f,mint)
        } else car(c,bay.centerX(),bay.centerY(),if(parallel)90f else 0f)
        text(c,if(parkingStep==3)"필요하면 멈추고 다시 맞춰요" else "위치·회전량은 실제 공간에 따라 달라져요",45f,317f,16f,Color.rgb(174,193,201))
    }
}
