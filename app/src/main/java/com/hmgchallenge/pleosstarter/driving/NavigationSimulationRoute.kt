package com.hmgchallenge.pleosstarter.driving

import kotlin.math.*

/** OSM/OSRM road geometry. Position is authored simulation progress, never vehicle GPS. */
class NavigationSimulationRoute(val points: List<GeoPoint>) {
    private val segments=points.zipWithNext {a,b->metres(a,b)}
    val length=segments.sum()
    val turnDistance=segments.first()
    fun at(metres: Double): GeoPoint {
        var remaining=metres.coerceIn(0.0,length)
        segments.forEachIndexed {i,len->
            if(remaining<=len && len>0) {val t=remaining/len;val a=points[i];val b=points[i+1]
                return GeoPoint(a.latitude+(b.latitude-a.latitude)*t,a.longitude+(b.longitude-a.longitude)*t)}
            remaining-=len
        }
        return points.last()
    }
    fun bearing(metres: Double): Double {
        val a=at((metres-2).coerceAtLeast(0.0));val b=at((metres+2).coerceAtMost(length))
        return Math.toDegrees(atan2((b.longitude-a.longitude)*cos(Math.toRadians(a.latitude)),b.latitude-a.latitude))
    }
    fun frame(model: DrivingSimulator): NavigationSimulationFrame {
        val start=model.road.entryLaneEnd.x
        val onMain=model.stage.ordinal>=SimStage.EXIT_ALIGN.ordinal
        val beforeTurn=model.stage.ordinal<SimStage.TURN.ordinal
        val afterTurn=model.stage.ordinal>SimStage.TURN.ordinal
        val turnEnd=length-6.3
        val distance=when {
            !onMain->0.0
            beforeTurn->((model.x-start)/(model.road.mainStopX-start)).coerceIn(0.0,1.0)*turnDistance
            !afterTurn->{val path=model.activePath!!;turnDistance+path.progress(model.x,model.y)/path.length*(turnEnd-turnDistance)}
            model.stage==SimStage.TURN_ALIGN->turnEnd
            else->turnEnd+abs(model.y-model.road.turnEnd.y).coerceAtMost(6.3)
        }.coerceIn(0.0,length)
        // Lane offset is authored. Map/OSRM do not establish the current physical lane.
        val offset=if(beforeTurn && onMain)(-model.y-5.25).coerceIn(-3.5,3.5) else if(model.stage==SimStage.TURN)3.5*(1-(distance-turnDistance)/15).coerceIn(0.0,1.0) else 0.0
        val yaw=bearing(distance)
        val base=at(distance)
        val point=offset(base,yaw,offset)
        val planned=mutableListOf(point)
        val end=length
        val laneChange=model.stage in setOf(SimStage.LANE_OUT,SimStage.LANE_RETURN)
        if(laneChange) {
            model.activePath?.let {path->
                path.points.filter {it.x>=model.x}.forEach {p->
                    val d=((p.x-start)/(model.road.mainStopX-start)).coerceIn(0.0,1.0)*turnDistance
                    if(d>=distance)planned.add(offset(at(d),bearing(d),(-p.y-5.25).coerceIn(-3.5,3.5)))
                }
            }
        }
        val from=if(laneChange && planned.size>1)projectedDistance(planned.last()) else distance
        var d=from+2
        while(d<end){
            val laneOffset=if(d<=turnDistance) {if(laneChange)(-(model.activePath!!.points.last().y)-5.25).coerceIn(-3.5,3.5) else offset} else 3.5*(1-(d-turnDistance)/15).coerceIn(0.0,1.0)
            planned.add(offset(at(d),bearing(d),laneOffset));d+=2
        };planned.add(at(end))
        return NavigationSimulationFrame(point,yaw,distance,(turnDistance-distance).coerceAtLeast(0.0),planned)
    }
    private fun projectedDistance(point: GeoPoint): Double =
        (0..ceil(length).toInt()).minBy {metres(at(it.toDouble()),point)}.toDouble()
    companion object {
        fun metres(a: GeoPoint,b: GeoPoint)=hypot((b.longitude-a.longitude)*111_320*cos(Math.toRadians(a.latitude)),(b.latitude-a.latitude)*111_320)
        fun offset(p: GeoPoint,bearing: Double,rightMetres: Double): GeoPoint {
            val yaw=Math.toRadians(bearing)
            return GeoPoint(p.latitude-sin(yaw)*rightMetres/111_320,p.longitude+cos(yaw)*rightMetres/(111_320*cos(Math.toRadians(p.latitude))))
        }
    }
}
data class NavigationSimulationFrame(val position: GeoPoint,val bearing: Double,val distance: Double,val turnRemaining: Double,val planned: List<GeoPoint>)
