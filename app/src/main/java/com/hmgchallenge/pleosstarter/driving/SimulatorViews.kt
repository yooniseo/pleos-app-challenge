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
        c.drawText("${angle.roundToInt()}°",cx,min(cy+r+28*resources.displayMetrics.density,height-8*resources.displayMetrics.density),p)
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
        val r=model.road
        val atEntry=model.stage.ordinal<=SimStage.EXIT_ALIGN.ordinal
        val atJunction=model.stage.ordinal>=SimStage.APPROACH.ordinal
        val focusX=if(atEntry)8.0 else if(atJunction)r.junctionX-3 else model.x+17
        val focusY=if(atEntry)(if(r.entryFromNorth)10.0 else -10.0) else 0.0
        val scale=min(width/78f,height/65f)
        fun sx(x: Double)=width/2f+((x-focusX)*scale).toFloat()
        fun sy(y: Double)=height/2f-((y-focusY)*scale).toFloat()
        fun rect(a: Double,b: Double,d: Double,e: Double,color: Int) {
            p.color=color;p.style=Paint.Style.FILL;c.drawRect(sx(a),sy(e),sx(d),sy(b),p)
        }
        fun line(a: Double,b: Double,d: Double,e: Double,color: Int,w: Float=0.13f,dashed: Boolean=false) {
            p.color=color;p.style=Paint.Style.STROKE;p.strokeWidth=w*scale
            p.pathEffect=if(dashed)DashPathEffect(floatArrayOf(2*scale,2*scale),0f)else null
            c.drawLine(sx(a),sy(b),sx(d),sy(e),p);p.pathEffect=null;p.style=Paint.Style.FILL
        }
        fun text(t: String,x: Double,y: Double,color: Int=Color.WHITE,size: Float=10f) {
            p.color=color;p.textSize=size*resources.displayMetrics.scaledDensity;p.textAlign=Paint.Align.CENTER
            c.drawText(t,sx(x),sy(y),p);p.textAlign=Paint.Align.LEFT
        }
        val asphalt=Color.rgb(57,65,76);val white=Color.rgb(229,234,233);val yellow=Color.rgb(255,204,75)
        c.drawColor(Color.rgb(89,111,111))
        // Sidewalk blocks and buildings remain outside the paved road.
        for(bx in -30..170 step 28) for(by in listOf(-38,25)) {
            rect(bx.toDouble(),by.toDouble(),bx+18.0,by+12.0,Color.rgb(113,133,135))
            rect(bx+1.0,by+1.0,bx+17.0,by+11.0,Color.rgb(60,85,92))
        }
        rect(-100.0,-r.halfWidth,230.0,r.halfWidth,asphalt)
        rect(r.junctionX-r.halfWidth,-100.0,r.junctionX+r.halfWidth,100.0,asphalt)
        // Rounded curb corners: same 10 m corner radius used by road.paved().
        for(dx in listOf(-1,1)) for(dy in listOf(-1,1)) {
            fun point(a: Double,b: Double)=RoadPoint(r.junctionX+dx*a,dy*b)
            val h=r.halfWidth;val cr=r.cornerRadius
            val a=point(h,h);val b=point(h+cr,h);val end=point(h,h+cr)
            val q=Path();q.moveTo(sx(a.x),sy(a.y));q.lineTo(sx(b.x),sy(b.y))
            val center=point(h+cr,h+cr)
            // Exact circular boundary sampled in world metres.
            for(i in 0..40) {
                val theta=-PI/2-i*PI/80
                val px=center.x+dx*cr*cos(theta);val py=center.y+dy*cr*sin(theta)
                q.lineTo(sx(px),sy(py))
            }
            q.lineTo(sx(end.x),sy(end.y));q.close();p.color=asphalt;c.drawPath(q,p)
        }
        val lot=r.parkingLot
        rect(lot.left,lot.bottom,lot.right,lot.top,asphalt)
        if(r.entryFromNorth)rect(-1.75,10.5,5.25,41.0,asphalt)else rect(-5.25,-41.0,1.75,-10.5,asphalt)
        // Rounded curb on the parking exit right turn.
        if(!r.entryFromNorth) {
            val q=Path();q.moveTo(sx(1.75),sy(-10.5));q.lineTo(sx(1.75),sy(-16.5))
            for(i in 0..40) {
                val a=PI-i*PI/80
                q.lineTo(sx(7.75+6*cos(a)),sy(-16.5+6*sin(a)))
            }
            q.close();p.color=asphalt;c.drawPath(q,p)
        }
        // Parking stalls: the training car starts backed into the centre stall.
        val farY=if(r.entryFromNorth)35.0 else -35.0
        val nearY=if(r.entryFromNorth)29.0 else -29.0
        for(i in -2..2) {
            val px=i*3.0
            line(px-1.4,nearY,px-1.4,farY,white);line(px+1.4,nearY,px+1.4,farY,white)
            line(px-1.4,farY,px+1.4,farY,white)
        }
        // Double yellow centre line. No paint through either junction.
        for(y in listOf(-0.14,0.14)) {
            line(-100.0,y,-6.0,y,yellow,0.10f);line(20.0,y,r.junctionX-21,y,yellow,0.10f)
            line(r.junctionX+21,y,230.0,y,yellow,0.10f)
        }
        for(x in listOf(r.junctionX-0.14,r.junctionX+0.14)) {
            line(x,-100.0,x,-21.0,yellow,0.10f);line(x,21.0,x,100.0,yellow,0.10f)
        }
        for(y in listOf(-7.0,-3.5,3.5,7.0)) {
            line(-100.0,y,-6.0,y,white,0.12f,true);line(20.0,y,r.junctionX-33,y,white,0.12f,true)
            line(r.junctionX-33,y,r.junctionX-18,y,white)
            line(r.junctionX+18,y,r.junctionX+33,y,white);line(r.junctionX+33,y,230.0,y,white,0.12f,true)
        }
        for(x in listOf(r.junctionX-7,r.junctionX-3.5,r.junctionX+3.5,r.junctionX+7)) {
            line(x,-100.0,x,-18.0,white,0.12f,true);line(x,18.0,x,100.0,white,0.12f,true)
        }
        fun crosswalk(box: RoadBox) {
            if(box.right-box.left<box.top-box.bottom) {
                var y=box.bottom+0.2;while(y<box.top-0.2){rect(box.left,y,box.right,min(y+0.65,box.top),white);y+=1.2}
            } else {
                var x=box.left+0.2;while(x<box.right-0.2){rect(x,box.bottom,min(x+0.65,box.right),box.top,white);x+=1.2}
            }
        }
        r.mainCrosswalks.forEach(::crosswalk);crosswalk(r.entryCrosswalk)
        line(r.junctionX-18,-10.5,r.junctionX-18,0.0,white,0.45f)
        line(r.junctionX+18,0.0,r.junctionX+18,10.5,white,0.45f)
        line(r.junctionX-10.5,18.0,r.junctionX,18.0,white,0.45f)
        line(r.junctionX,-18.0,r.junctionX+10.5,-18.0,white,0.45f)
        if(r.entryFromNorth)line(-1.75,18.0,1.75,18.0,white,0.4f)else line(-1.75,-18.0,1.75,-18.0,white,0.4f)
        fun arrow(x: Double,y: Double,heading: Double,turn: Int) {
            c.save();c.translate(sx(x),sy(y));c.rotate(Math.toDegrees(heading).toFloat())
            p.color=white;p.style=Paint.Style.STROKE;p.strokeWidth=0.22f*scale
            val q=Path();q.moveTo(0f,1.8f*scale);q.lineTo(0f,-0.5f*scale)
            val tipX=turn*1.1f*scale;val tipY=-1.6f*scale
            q.lineTo(tipX,tipY);q.moveTo(tipX-0.6f*scale,tipY+0.65f*scale);q.lineTo(tipX,tipY);q.lineTo(tipX+0.6f*scale,tipY+0.65f*scale)
            c.drawPath(q,p);p.style=Paint.Style.FILL;c.restore()
        }
        for(lane in 1..3) {
            arrow(r.junctionX-27,r.laneY(lane),PI/2,if(lane==1)-1 else if(lane==3)1 else 0)
            arrow(r.junctionX+27,-r.laneY(lane),-PI/2,if(lane==1)-1 else if(lane==3)1 else 0)
            text("$lane",r.junctionX-33,r.laneY(lane),white,9f)
        }
        fun route(points: List<RoadPoint>,alpha: Int,dashed: Boolean) {
            val q=Path();points.forEachIndexed {i,a->if(i==0)q.moveTo(sx(a.x),sy(a.y))else q.lineTo(sx(a.x),sy(a.y))}
            p.color=Color.rgb(94,246,197);p.alpha=alpha;p.style=Paint.Style.STROKE;p.strokeWidth=0.45f*scale
            p.pathEffect=if(dashed)DashPathEffect(floatArrayOf(scale,scale),0f)else null;c.drawPath(q,p)
            p.pathEffect=null;p.alpha=255;p.style=Paint.Style.FILL
        }
        // Future turn is drawn in its actual turn lane, never across the carriageway.
        if(!atEntry)route(r.mainTurnPath(RoadPoint(r.mainStopX,r.laneY(r.turnLane))).points,85,true)
        model.activePath?.let {route(it.points,190,true)}
        route(model.trace.map {RoadPoint(it.x,it.y)},230,false)
        fun car(x: Double,y: Double,heading: Double,color: Int,own: Boolean=false) {
            c.save();c.translate(sx(x),sy(y));c.rotate(Math.toDegrees(heading).toFloat())
            p.color=color;c.drawRoundRect(-0.9f*scale,-2.1f*scale,0.9f*scale,2.1f*scale,0.3f*scale,0.3f*scale,p)
            p.color=Color.rgb(29,57,70);c.drawRect(-0.7f*scale,-1.55f*scale,0.7f*scale,-0.5f*scale,p)
            if(own && model.signal!=SimSignal.OFF){p.color=yellow;c.drawCircle((if(model.signal==SimSignal.LEFT)-0.9f else 0.9f)*scale,-1.4f*scale,0.25f*scale,p)}
            c.restore()
        }
        // Opposing traffic occupies its own carriageway and waits during our protected movement.
        val blocked=model.green
        val oncomingX=if(blocked)r.junctionX+20.5 else r.junctionX+44-(model.distance%20)
        car(oncomingX,5.25,-PI/2,Color.rgb(122,176,218));car(oncomingX+9,5.25,-PI/2,Color.rgb(212,160,127))
        car(r.junctionX-5.25,if(blocked)20.5 else 34-model.distance%10,PI,Color.rgb(175,160,210))
        if(atEntry)car(if(blocked)22.0 else 35-model.distance%9,5.25,-PI/2,Color.rgb(122,176,218))
        if(model.pedestrian) {
            val px=if(atEntry)3.2 else r.junctionX-14.5;val py=if(atEntry)(if(r.entryFromNorth)15.0 else -15.0)else -5.0
            p.color=Color.rgb(255,207,143);c.drawCircle(sx(px),sy(py),0.55f*scale,p)
            line(px,py-0.6,px,py-1.7,p.color,0.35f)
        }
        car(model.x,model.y,model.heading,Color.WHITE,true)
        // Signal heads at the active stop line, with opposing signal explicitly red on release.
        val lightX=if(atEntry)7.5 else r.junctionX-19
        val lightY=if(atEntry)(if(r.entryFromNorth)19.0 else -19.0)else -13.0
        p.color=Color.rgb(20,28,35);c.drawRoundRect(sx(lightX)-18,sy(lightY)-12,sx(lightX)+18,sy(lightY)+12,6f,6f,p)
        p.color=if(model.green)Color.rgb(70,228,143)else Color.rgb(255,100,99);c.drawCircle(sx(lightX),sy(lightY),7f,p)
        text(if(model.green)"내 진행 / 보호 신호" else "내 진행 / 정지",lightX,lightY-5,white,9f)
        if(atJunction) {
            p.color=if(blocked)Color.rgb(255,100,99)else Color.rgb(70,228,143)
            c.drawCircle(sx(r.junctionX+19),sy(13.0),7f,p)
            text("반대편 신호",r.junctionX+23,18.0,white,9f)
        }
        p.color=Color.rgb(16,32,41);c.drawRect(0f,0f,width.toFloat(),25*resources.displayMetrics.density,p)
        p.color=Color.WHITE;p.textSize=11*resources.displayMetrics.scaledDensity
        c.drawText(if(atEntry)"가상 주차장 출구 · 횡단보도 앞 정차" else "왕복 6차로 · 우회전 3차로 / 좌회전 1차로",10f,18*resources.displayMetrics.density,p)
        p.textSize=10*resources.displayMetrics.scaledDensity
        c.drawText("노랑: 중앙선  ·  흰색: 차선/횡단보도  ·  민트: 연습 경로",10f,height-7f,p)
    }
}
