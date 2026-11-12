package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Educational scene, deliberately not a live camera, map, or obstacle detector. */
class CoachSceneView(context: Context) : View(context) {
    var phase = CoachPhase.LANE_PREPARATION
    var parking: ParkingStyle? = null
    var parkingStep = 0
    var indicator = Indicator.UNKNOWN
    var demo = true
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mint = Color.rgb(101, 233, 197)
    init { contentDescription = "설명용 도식 · 실제 도로와 차량을 추적하지 않음" }
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
        for (y in 220..340 step 32) line(c,floatArrayOf(335f,y.toFloat(),335f,y+17f),Color.rgb(168,184,192))
        for (x in 268..403 step 23) rect(c,x.toFloat(),211f,x+12f,223f,Color.rgb(216,229,228))
        val path=Path().apply { moveTo(295f,330f); cubicTo(295f,285f,376f,280f,376f,234f); lineTo(376f,180f); quadTo(376f,156f,412f,156f); lineTo(582f,156f) }
        p.color=mint; p.alpha=130; p.strokeWidth=7f; p.style=Paint.Style.STROKE;c.drawPath(path,p);p.alpha=255;p.style=Paint.Style.FILL
        when(phase) {
            CoachPhase.LANE_PREPARATION -> car(c,295f,302f)
            CoachPhase.RIGHT_TURN -> car(c,376f,244f)
            CoachPhase.INTERSECTION_EXIT,CoachPhase.FINISHED -> car(c,529f,156f,90f)
            else -> car(c,295f,302f)
        }
    }
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
