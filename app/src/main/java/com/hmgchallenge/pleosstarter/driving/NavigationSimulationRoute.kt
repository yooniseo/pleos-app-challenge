package com.hmgchallenge.pleosstarter.driving

import kotlin.math.*

/** OSM/OSRM road geometry. Position is authored simulation progress, never vehicle GPS. */
class NavigationSimulationRoute(val points: List<GeoPoint>, private val maneuverDistance: Double?=null, private val receivingRoad: List<GeoPoint> = emptyList()) {
    private val segments=points.zipWithNext {a,b->metres(a,b)}
    val length=segments.sum()
    val turnDistance=maneuverDistance ?: segments.first()
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
    /** Join the main road's third lane to the mapped single-lane connector with continuous tangents. */
    fun drivingCourse(): NavigationSimulationRoute {
        val junction=points.indices.minByOrNull {metres(points[it],at(turnDistance))}!!
        fun heading(a: GeoPoint,b: GeoPoint)=atan2((b.longitude-a.longitude)*cos(Math.toRadians(a.latitude)),b.latitude-a.latitude)
        val lane=points.mapIndexed {i,p->
            if(i>junction) {
                // Match OSRM nodes to the two-way receiving road, then follow the rightmost lane.
                val node=receivingRoad.indexOfFirst {metres(it,p)<1.0}
                if(node>=0 || i==points.lastIndex && receivingRoad.isNotEmpty()) {
                    val yaw=if(i<points.lastIndex)heading(p,points[i+1]) else heading(points[i-1],p)
                    offset(p,Math.toDegrees(yaw),1.5*LANE_WIDTH)
                } else p
            } else {
                val a=points[(i-1).coerceAtLeast(0)];val b=points[(i+1).coerceAtMost(points.lastIndex)]
                val yaw=if(i==junction && i>0)heading(a,p) else heading(a,b)
                offset(p,Math.toDegrees(yaw),LANE_WIDTH)
            }
        }
        val tangents=lane.indices.map {i->
            val before=heading(lane[(i-1).coerceAtLeast(0)],lane[i])
            val after=heading(lane[i],lane[(i+1).coerceAtMost(lane.lastIndex)])
            when(i) {0->after;lane.lastIndex->before;else->atan2(sin(before)+sin(after),cos(before)+cos(after))}
        }
        val result=mutableListOf(lane.first())
        var length=0.0
        var turn=turnDistance
        for(i in 0 until lane.lastIndex) {
            val a=lane[i];val b=lane[i+1]
            val span=metres(a,b)
            val handle=minOf(12.0,span/3)
            val c1=offset(a,Math.toDegrees(tangents[i])+90,-handle)
            val c2=offset(b,Math.toDegrees(tangents[i+1])+90,handle)
            val count=ceil(span/.5).toInt().coerceAtLeast(2)
            for(j in 1..count) {
                val t=j.toDouble()/count;val u=1-t
                fun coordinate(av: Double,cv1: Double,cv2: Double,bv: Double)=u*u*u*av+3*u*u*t*cv1+3*u*t*t*cv2+t*t*t*bv
                val p=GeoPoint(coordinate(a.latitude,c1.latitude,c2.latitude,b.latitude),coordinate(a.longitude,c1.longitude,c2.longitude,b.longitude))
                length+=metres(result.last(),p);result.add(p)
            }
            if(i+1==junction)turn=(length-20).coerceAtLeast(0.0)
        }
        result.add(offset(lane.last(),Math.toDegrees(tangents.last())+90,-8.0))
        return NavigationSimulationRoute(result,turn)
    }
    /** Sub-metre samples round the illustrative route's sharp polyline corners. */
    fun rounded(): NavigationSimulationRoute {
        val result=mutableListOf(points.first())
        var turn=turnDistance
        var originalDistance=0.0
        for(i in 1 until points.lastIndex) {
            val a=points[i-1];val b=points[i];val next=points[i+1]
            val incoming=metres(a,b);val outgoing=metres(b,next)
            val trim=minOf(8.0,incoming*.25,outgoing*.25)
            originalDistance+=incoming
            if(trim<=0)continue
            fun mix(p: GeoPoint,q: GeoPoint,t: Double)=GeoPoint(p.latitude+(q.latitude-p.latitude)*t,p.longitude+(q.longitude-p.longitude)*t)
            val entry=mix(b,a,trim/incoming);val exit=mix(b,next,trim/outgoing)
            result.add(entry)
            if(abs(originalDistance-turnDistance)<1.0)turn=result.zipWithNext {p,q->metres(p,q)}.sum()
            for(j in 1..40) {
                val t=j/40.0
                result.add(mix(mix(entry,b,t),mix(b,exit,t),t))
            }
        }
        result.add(points.last())
        result.add(offset(points.last(),bearing(length)+90,-8.0))
        return NavigationSimulationRoute(result,turn)
    }
    companion object {
        const val LANE_WIDTH=3.5
        const val CAR_WIDTH=1.85
        const val CAR_LENGTH=4.65
        fun reference(route: ReferenceRoute,receivingRoad: List<GeoPoint> = emptyList()): NavigationSimulationRoute {
            val index=route.firstRightTurn?.let {turn->route.points.indices.minByOrNull {metres(route.points[it],turn)}}
            val distance=index?.let {route.points.take(it+1).zipWithNext {a,b->metres(a,b)}.sum()}
            return NavigationSimulationRoute(route.points,distance,receivingRoad)
        }
        fun metres(a: GeoPoint,b: GeoPoint)=hypot((b.longitude-a.longitude)*111_320*cos(Math.toRadians(a.latitude)),(b.latitude-a.latitude)*111_320)
        fun offset(p: GeoPoint,bearing: Double,rightMetres: Double): GeoPoint {
            val yaw=Math.toRadians(bearing)
            return GeoPoint(p.latitude-sin(yaw)*rightMetres/111_320,p.longitude+cos(yaw)*rightMetres/(111_320*cos(Math.toRadians(p.latitude))))
        }
    }
}
data class NavigationSimulationFrame(val position: GeoPoint,val bearing: Double,val distance: Double,val turnRemaining: Double,val planned: List<GeoPoint>)
