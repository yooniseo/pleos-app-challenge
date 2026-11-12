package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class SimulatorWheelView(context: Context) : View(context) {
    var angle=0.0
    var target=0.0
    var onSteer: (Double)->Unit={}
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastTouch=0.0
    init {contentDescription="조작 가능한 핸들. 좌우로 돌려 현재 조향각을 바꿔요."}
    override fun onDraw(c: Canvas) {
        val cx=width/2f;val cy=height/2f;val r=min(width,height)*0.36f
        fun wheel(a: Double,color: Int,alpha: Int) {
            c.save();c.rotate(a.toFloat(),cx,cy);p.color=color;p.alpha=alpha;p.style=Paint.Style.STROKE;p.strokeWidth=r*0.12f
            c.drawCircle(cx,cy,r,p);c.drawLine(cx-r,cy,cx+r,cy,p);c.drawLine(cx,cy,cx,cy+r,p)
            p.style=Paint.Style.FILL;c.drawCircle(cx,cy,r*0.25f,p)
            p.color=Color.rgb(255,194,94);c.drawRoundRect(cx-r*0.05f,cy-r*1.02f,cx+r*0.05f,cy-r*0.82f,3f,3f,p);c.restore()
        }
        wheel(target,Color.rgb(101,233,197),70);wheel(angle,Color.rgb(230,239,244),255)
        p.textSize=14*resources.displayMetrics.scaledDensity;p.textAlign=Paint.Align.CENTER;p.color=Color.WHITE
        c.drawText("${angle.roundToInt()}°",cx,cy+r+28*resources.displayMetrics.density,p)
        p.textAlign=Paint.Align.LEFT
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val current=Math.toDegrees(atan2((e.y-height/2).toDouble(),(e.x-width/2).toDouble()))
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN->{lastTouch=current;parent?.requestDisallowInterceptTouchEvent(true);return true}
            MotionEvent.ACTION_MOVE->{
                val delta=(current-lastTouch+540)%360-180;lastTouch=current
                angle=(angle+delta).coerceIn(-450.0,450.0);onSteer(angle);invalidate();return true
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{parent?.requestDisallowInterceptTouchEvent(false);performClick();return true}
        }
        return super.onTouchEvent(e)
    }
    override fun performClick(): Boolean {super.performClick();return true}
}

class SimulatorRoadView(context: Context,var model: DrivingSimulator) : View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(28,52,57))
        val scale=min(width/105f,height/65f)
        val focusX=model.x.toFloat().coerceAtLeast(15f)
        val focusY=model.y.toFloat()
        fun sx(x: Double)=width*0.4f+(x.toFloat()-focusX)*scale
        fun sy(y: Double)=height*0.5f-(y.toFloat()-focusY)*scale
        // Fixed training roads. The driven trace is computed from steering and pedal inputs.
        p.color=Color.rgb(64,79,88);p.style=Paint.Style.STROKE;p.strokeWidth=12*scale
        val roadCenter=4.0+(if(model.laneRight)-3.2 else 3.2)
        c.drawLine(sx(-4.0),sy(roadCenter),sx(130.0),sy(roadCenter),p)
        c.drawLine(sx(2.0),sy(-18.0),sx(2.0),sy(10.0),p)
        val junctionX=78.0
        c.drawLine(sx(junctionX),sy(-75.0),sx(junctionX),sy(75.0),p)
        p.color=Color.rgb(184,204,205);p.strokeWidth=0.12f*scale;p.pathEffect=DashPathEffect(floatArrayOf(scale*2,scale*2),0f)
        listOf(roadCenter-1.6,roadCenter+1.6).forEach {lane->c.drawLine(sx(10.0),sy(lane),sx(130.0),sy(lane),p)}
        p.pathEffect=null;p.style=Paint.Style.FILL;p.color=Color.rgb(94,119,122)
        c.drawRect(sx(-2.0),sy(2.0),sx(2.0),sy(-3.0),p)
        p.color=Color.rgb(244,203,116);p.textSize=12*resources.displayMetrics.scaledDensity
        c.drawText("출발 주차칸",sx(-6.0),sy(-7.0),p)
        c.drawText("연습 교차로",sx(junctionX-8),sy(15.0),p)
        p.style=Paint.Style.STROKE;p.strokeWidth=0.4f*scale;p.color=Color.rgb(101,233,197)
        val preview=Path();var px=model.x;var py=model.y;var yaw=model.heading
        preview.moveTo(sx(px),sy(py));repeat(30) {
            val sign=if(model.stage==SimStage.REVERSE)-1 else 1
            yaw+=sign*0.5/model.wheelbase*tan(Math.toRadians(model.targetSteering/model.steeringRatio))
            px+=sign*0.5*sin(yaw);py+=sign*0.5*cos(yaw);preview.lineTo(sx(px),sy(py))
        }
        p.alpha=90;p.pathEffect=DashPathEffect(floatArrayOf(scale,scale),0f);c.drawPath(preview,p);p.pathEffect=null;p.alpha=255
        val path=Path();model.trace.forEachIndexed {i,pos->if(i==0)path.moveTo(sx(pos.x),sy(pos.y))else path.lineTo(sx(pos.x),sy(pos.y))};c.drawPath(path,p)
        p.style=Paint.Style.FILL
        c.save();c.translate(sx(model.x),sy(model.y));c.rotate(Math.toDegrees(model.heading).toFloat())
        p.color=Color.WHITE;c.drawRoundRect(-0.9f*scale,-2.1f*scale,0.9f*scale,2.1f*scale,0.4f*scale,0.4f*scale,p)
        p.color=Color.rgb(40,110,110);c.drawRect(-0.65f*scale,-1.6f*scale,0.65f*scale,-0.6f*scale,p)
        if(model.signal!=SimSignal.OFF) {p.color=Color.rgb(255,183,69);val side=if(model.signal==SimSignal.LEFT)-1 else 1;c.drawCircle(side*0.9f*scale,-1.3f*scale,0.25f*scale,p)}
        c.restore()
        p.color=Color.WHITE;p.textSize=12*resources.displayMetrics.scaledDensity
        c.drawText("가상 도로 · 실선: 조작 궤적 / 점선: 목표 각도의 예상 흐름",12f,height-12f,p)
    }
}
