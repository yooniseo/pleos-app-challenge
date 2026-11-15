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
    var tilted=true
    fun setFrame(route: NavigationSimulationRoute,value: NavigationSimulationFrame,parking: Boolean=false) {course=route;frame=value;this.parking=parking;invalidate()}
    fun diagnostics()=mapOf("visibleTiles" to "0","offlineRoads" to streets.size.toString(),"routePoints" to (course?.points?.size ?: 0).toString(),
        "marker" to (frame?.position?.let {"${it.latitude},${it.longitude}"} ?: ""),"mapHeading" to frame?.bearing.toString(),"size" to "${width}x$height","mode" to if(tilted)"3D" else "2D")
    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(239,240,246))
        val f=frame ?: return;val route=course ?: return
        if(parking){drawParking(c,f,route);return}
        val anchorX=width/2f;val anchorY=height/2f
        val yaw=Math.toRadians(f.bearing);val scale=width/39f;val projection=if(tilted).72f else 1f
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
        c.save();c.translate(anchorX,anchorY);c.scale(scale,scale*projection)
        areas.forEach {(pts,green)->p.style=Paint.Style.FILL;p.color=if(green)Color.rgb(202,220,208)else Color.rgb(224,226,236);c.drawPath(path(pts).apply {close()},p)}
        streets.forEach {s->stroke(path(s.points),Color.rgb(250,251,254),s.width+2.2f)}
        streets.forEach {s->stroke(path(s.points),Color.rgb(198,202,216),s.width)}
        // Widths and lane boundaries are an educational overlay, not lane-level map data.
        streets.forEach {s->
            val line=path(s.points)
            fun parallel(offset: Double)=s.points.mapIndexed {j,g->
                val a=s.points[(j-1).coerceAtLeast(0)];val b=s.points[(j+1).coerceAtMost(s.points.lastIndex)]
                val yaw=Math.toDegrees(atan2((b.longitude-a.longitude)*cos(Math.toRadians(a.latitude)),b.latitude-a.latitude))
                NavigationSimulationRoute.offset(g,yaw,offset)
            }
            if(!s.oneway)stroke(line,Color.rgb(243,198,79),.20f)
            // The divided approach has a median on the left and a shoulder on the right.
            if(s.oneway && s.lanes>=3) {
                for(edge in listOf(-1,1)) {
                    val edgePoints=parallel(edge*(s.width/2-.12))
                    stroke(path(edgePoints),if(edge<0)Color.rgb(243,198,79) else Color.WHITE,.16f)
                }
            }
            if(s.lanes>1)for(i in 1 until s.lanes) {
                if(!s.oneway && i==s.lanes/2)continue
                val offset=-s.width/2+s.width*i/s.lanes
                val shifted=parallel(offset.toDouble())
                stroke(path(shifted),Color.rgb(250,251,255),.20f,true)
            }
        }
        // Explicitly virtual crossing positions, never used as pedestrian/signal evidence.
        for(d in listOf(route.turnDistance-8,route.turnDistance+54)) {
            val pos=local(route.at(d));c.save();c.translate(pos.x,pos.y)
            c.rotate((route.bearing(d)-f.bearing).toFloat())
            // The route origin is the middle of lane three on approach, then a single connector lane.
            val crossing=if(d<route.turnDistance)RectF(-8.75f,-1.6f,1.75f,1.6f) else RectF(-1.75f,-1.6f,1.75f,1.6f)
            art.figma?.draw(c,"crosswalk",crossing) ?: art.crossing(c,crossing)
            c.restore()
        }

        stroke(path(f.planned),Color.rgb(165,196,255),1.9f)
        stroke(path(f.planned),Color.rgb(64,127,242),.92f)
        // The authored route follows lane 3 at the junction; adjacent approach lanes continue straight.
        for(distance in listOf(route.turnDistance-38,route.turnDistance-18)) {
            val bearing=route.bearing(distance)
            for(lane in 1..3) {
                val mark=local(NavigationSimulationRoute.offset(route.at(distance),bearing,(lane-3)*3.5))
                c.save()
                c.translate(mark.x,mark.y)
                c.rotate((bearing-f.bearing).toFloat())
                val arrow=Path().apply {
                    moveTo(0f,2f)
                    if(lane==3) {
                        lineTo(0f,-.8f);lineTo(1.15f,-.8f)
                        moveTo(.45f,-1.5f);lineTo(1.15f,-.8f);lineTo(.45f,-.1f)
                    } else {
                        lineTo(0f,-2f);moveTo(-.8f,-1f);lineTo(0f,-2f);lineTo(.8f,-1f)
                    }
                }
                stroke(arrow,Color.WHITE,.36f)
                c.restore()
            }
        }
        c.restore()
        if(showCautions && f.turnRemaining<=140 && f.distance<=route.turnDistance+30) {
            val warning=local(route.at(route.turnDistance+14))
            val x=anchorX+warning.x*scale;val y=anchorY+warning.y*scale*projection
            val size=width*.075f
            art.figma?.draw(c,"warning_marker",RectF(x-size/2,y-size,x+size/2,y))
        }
        // Original app illustrations are anchored beside the road, and follow the geographic camera.
        listOf(0.0 to -19.0,45.0 to 16.0,95.0 to -18.0,145.0 to 16.0,route.turnDistance-25 to -18.0,route.turnDistance+5 to 15.0,route.turnDistance-50 to 19.0).forEachIndexed {index,(d,offset)->
            val pos=local(NavigationSimulationRoute.offset(route.at(d),route.bearing(d),offset))
            val x=anchorX+pos.x*scale;val y=anchorY+pos.y*scale*projection
            val variant=index%3
            val w=width*listOf(172f,175f,284f)[variant]/1700f;val h=w*1.306f
            val icon=listOf("road_tree","road_tree_far","road_tree_near")[variant]
            art.figma?.draw(c,icon,RectF(x-w/2,y-h,x+w/2,y)) ?: art.tree(c,x-w/2,y-h,w,h)
        }
        c.save();c.translate(anchorX,anchorY)
        // The original 357×286 Figma export has a 151px-wide opaque body centered at (169,132).
        val pixelScale=scale*NavigationSimulationRoute.CAR_WIDTH.toFloat()/151f
        val vehicleBounds=RectF(-169f*pixelScale,-132f*pixelScale,188f*pixelScale,154f*pixelScale)
        art.figma?.draw(c,"drive_car",vehicleBounds) ?: art.car(c,vehicleBounds)
        c.restore()
        p.style=Paint.Style.FILL;p.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);p.textSize=width*.018f;p.color=Color.rgb(94,100,121)
        // The host draws attribution in a reserved HUD slot, above the navigation tabs.
        c.drawText(if(f.distance<route.turnDistance)"창룡대로" else "대학로",anchorX-width*.09f,anchorY-height*.15f,p)
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
