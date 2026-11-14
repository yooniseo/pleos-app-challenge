package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.graphics.*
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Offline OSM geometry, with illustrative widths/markings. No network-dependent blank screen. */
class ScenarioRoadMapView(context: Context,private val art: com.hmgchallenge.pleosstarter.ui.ProductIllustrations=com.hmgchallenge.pleosstarter.ui.ProductIllustrations()): View(context) {
    private val data=JSONObject(context.assets.open("road_reference/kgu-streets.json").bufferedReader().use {it.readText()})
    private data class Street(val points: List<GeoPoint>,val width: Float,val lanes: Int,val oneway: Boolean)
    private fun points(a: JSONArray)=(0 until a.length()).map {a.getJSONArray(it).let {p->GeoPoint(p.getDouble(0),p.getDouble(1))}}
    private val streets=data.getJSONArray("roads").let {a->(0 until a.length()).map {a.getJSONObject(it).let {s->Street(points(s.getJSONArray("points")),s.getDouble("width").toFloat(),s.getInt("lanes"),s.getBoolean("oneway"))}}}
    private val areas=data.getJSONArray("areas").let {a->(0 until a.length()).map {a.getJSONObject(it).let {s->points(s.getJSONArray("points")) to s.getBoolean("green")}}}
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private var course: NavigationSimulationRoute?=null
    private var frame: NavigationSimulationFrame?=null
    private var parking=false
    var showCautions=false
    fun setFrame(route: NavigationSimulationRoute,value: NavigationSimulationFrame,parking: Boolean=false) {course=route;frame=value;this.parking=parking;invalidate()}
    fun diagnostics()=mapOf("visibleTiles" to "0","offlineRoads" to streets.size.toString(),"routePoints" to (course?.points?.size ?: 0).toString(),
        "marker" to (frame?.position?.let {"${it.latitude},${it.longitude}"} ?: ""),"size" to "${width}x$height")
    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(239,240,246))
        val f=frame ?: return;val route=course ?: return
        if(parking){drawParking(c,f,route);return}
        val yaw=Math.toRadians(route.bearing(0.0));val scale=width/76f
        fun local(g: GeoPoint): PointF {
            val east=(g.longitude-f.position.longitude)*111320*cos(Math.toRadians(f.position.latitude))
            val north=(g.latitude-f.position.latitude)*111320
            return PointF((east*cos(yaw)-north*sin(yaw)).toFloat(),(-east*sin(yaw)-north*cos(yaw)).toFloat())
        }
        fun path(points: List<GeoPoint>)=Path().apply {points.forEachIndexed {i,g->val a=local(g);if(i==0)moveTo(a.x,a.y)else lineTo(a.x,a.y)}}
        fun stroke(path: Path,color: Int,width: Float,dash: Boolean=false) {
            p.style=Paint.Style.STROKE;p.color=color;p.strokeWidth=width;p.strokeJoin=Paint.Join.ROUND;p.strokeCap=Paint.Cap.BUTT
            p.pathEffect=if(dash)DashPathEffect(floatArrayOf(3f,4f),0f)else null;c.drawPath(path,p);p.pathEffect=null
        }
        c.save();c.translate(width*.52f,height*.70f);c.scale(scale,scale*.72f)
        areas.forEach {(pts,green)->p.style=Paint.Style.FILL;p.color=if(green)Color.rgb(202,220,208)else Color.rgb(224,226,236);c.drawPath(path(pts).apply {close()},p)}
        streets.forEach {s->stroke(path(s.points),Color.rgb(250,251,254),s.width+2.2f)}
        streets.forEach {s->stroke(path(s.points),Color.rgb(198,202,216),s.width)}
        // Widths and lane boundaries are an educational overlay, not lane-level map data.
        streets.forEach {s->
            val line=path(s.points)
            if(!s.oneway)stroke(line,Color.rgb(245,212,108),.24f)
            if(s.lanes>1)for(i in 1 until s.lanes) {
                if(!s.oneway && i==s.lanes/2)continue
                val offset=-s.width/2+s.width*i/s.lanes
                val shifted=s.points.mapIndexed {j,g->
                    val a=s.points[(j-1).coerceAtLeast(0)];val b=s.points[(j+1).coerceAtMost(s.points.lastIndex)]
                    val bearing=Math.toDegrees(atan2((b.longitude-a.longitude)*cos(Math.toRadians(a.latitude)),b.latitude-a.latitude))
                    NavigationSimulationRoute.offset(g,bearing,offset.toDouble())
                }
                stroke(path(shifted),Color.rgb(250,251,255),.20f,true)
            }
        }
        // Explicitly virtual crossing positions, never used as pedestrian/signal evidence.
        for(d in listOf(route.turnDistance-8,route.turnDistance+54)) {
            val pos=local(route.at(d));c.save();c.translate(pos.x,pos.y)
            c.rotate((route.bearing(d)-route.bearing(0.0)).toFloat())
            art.crossing(c,RectF(-9f,-1.6f,9f,1.6f))
            c.restore()
        }
        stroke(path(route.points),Color.rgb(169,186,229),1.2f)
        stroke(path(f.planned),Color.rgb(165,196,255),1.9f)
        stroke(path(f.planned),Color.rgb(64,127,242),.92f)
        c.restore()
        if(showCautions && f.turnRemaining<=140 && f.distance<=route.turnDistance+30) {
            val warning=local(route.at(route.turnDistance+14))
            val x=width*.52f+warning.x*scale;val y=height*.70f+warning.y*scale*.72f
            val size=width*.075f
            art.figma?.draw(c,"warning_marker",RectF(x-size/2,y-size,x+size/2,y))
        }
        // Original app illustrations are anchored beside the road, and follow the geographic camera.
        listOf(0.0 to -19.0,45.0 to 16.0,95.0 to -18.0,145.0 to 16.0).forEachIndexed {i,(d,offset)->
            val pos=local(NavigationSimulationRoute.offset(route.at(d),route.bearing(d),offset))
            val x=width*.52f+pos.x*scale;val y=height*.70f+pos.y*scale*.72f
            val w=width*.105f;val h=w*1.306f
            art.tree(c,x-w/2,y-h,w,h)
        }
        c.save();c.translate(width*.52f,height*.70f);c.rotate((f.bearing-route.bearing(0.0)).toFloat())
        val w=width*.085f;val h=w/1.047f
        art.car(c,RectF(-w/2,-h/2,w/2,h/2));c.restore()
        p.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);p.textSize=width*.018f;p.color=Color.rgb(94,100,121)
        c.drawText("창룡대로",width*.76f,height*.82f,p)
        p.textSize=width*.013f;p.color=Color.rgb(84,88,108)
        c.drawText("© OpenStreetMap contributors · OSRM | 가상 차로·횡단보도",width*.02f,height*.982f,p)
    }
    private fun drawParking(c: Canvas,f: NavigationSimulationFrame,route: NavigationSimulationRoute) {
        c.drawColor(Color.rgb(229,232,241))
        val scale=min(width/30f,height/30f)
        fun point(g: GeoPoint): PointF {
            val a=ParkingLessonGeometry.anchor
            val x=(g.longitude-a.longitude)*111320*cos(Math.toRadians(a.latitude))
            val y=(g.latitude-a.latitude)*111320
            return PointF(width*.55f+((x-2)*scale).toFloat(),height*.28f-((y+1)*scale).toFloat())
        }
        fun line(points: List<GeoPoint>,color: Int,w: Float) {
            val path=Path();points.forEachIndexed {i,g->val v=point(g);if(i==0)path.moveTo(v.x,v.y)else path.lineTo(v.x,v.y)}
            p.style=Paint.Style.STROKE;p.color=color;p.strokeWidth=w;p.strokeJoin=Paint.Join.ROUND;c.drawPath(path,p)
        }
        p.style=Paint.Style.FILL;p.color=Color.rgb(192,198,213);c.drawRect(width*.3f,0f,width*.96f,height.toFloat(),p)
        for(i in 0..2) {
            val x=4.65+i*2.7
            val a=point(ParkingLessonGeometry.point(x,-7.5));val b=point(ParkingLessonGeometry.point(x+2.7,-12.5))
            p.style=Paint.Style.STROKE;p.strokeWidth=2f;p.color=Color.WHITE;c.drawRect(a.x,a.y,b.x,b.y,p)
        }
        val a=point(ParkingLessonGeometry.point(4.65,-7.5));val b=point(ParkingLessonGeometry.point(7.35,-12.5))
        p.style=Paint.Style.FILL;p.color=Color.argb(60,108,76,247);c.drawRect(a.x,a.y,b.x,b.y,p)
        p.style=Paint.Style.STROKE;p.color=Color.rgb(108,76,247);p.strokeWidth=3f;c.drawRect(a.x,a.y,b.x,b.y,p)
        line(route.points,Color.rgb(163,174,200),3f);line(f.planned,Color.rgb(64,127,242),7f)
        val car=point(f.position);c.save();c.rotate(f.bearing.toFloat(),car.x,car.y)
        val w=scale*1.8f;val h=w/1.047f;art.car(c,RectF(car.x-w/2,car.y-h/2,car.x+w/2,car.y+h/2));c.restore()
        p.color=Color.rgb(77,80,104);p.textSize=width*.016f;c.drawText("가상 주차 연습장 · 실제 주차장 위치/공간과 무관",width*.02f,height*.982f,p)
    }
}
