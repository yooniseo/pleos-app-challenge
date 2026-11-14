package com.hmgchallenge.pleosstarter.driving

import kotlin.math.*

/** OSM/OSRM road geometry. Position is authored simulation progress, never vehicle GPS. */
class NavigationSimulationRoute(val points: List<GeoPoint>, private val maneuverDistance: Double?=null) {
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
    /** Sub-metre samples round the illustrative route's sharp polyline corners. */
    fun rounded(): NavigationSimulationRoute {
        val result=mutableListOf(points.first())
        var turn=turnDistance
        for(i in 1 until points.lastIndex) {
            val a=points[i-1];val b=points[i];val next=points[i+1]
            val incoming=metres(a,b);val outgoing=metres(b,next)
            val trim=minOf(8.0,incoming*.25,outgoing*.25)
            if(trim<=0)continue
            fun mix(p: GeoPoint,q: GeoPoint,t: Double)=GeoPoint(p.latitude+(q.latitude-p.latitude)*t,p.longitude+(q.longitude-p.longitude)*t)
            val entry=mix(b,a,trim/incoming);val exit=mix(b,next,trim/outgoing)
            result.add(entry)
            if(i==1)turn=incoming-trim
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
        fun metres(a: GeoPoint,b: GeoPoint)=hypot((b.longitude-a.longitude)*111_320*cos(Math.toRadians(a.latitude)),(b.latitude-a.latitude)*111_320)
        fun offset(p: GeoPoint,bearing: Double,rightMetres: Double): GeoPoint {
            val yaw=Math.toRadians(bearing)
            return GeoPoint(p.latitude-sin(yaw)*rightMetres/111_320,p.longitude+cos(yaw)*rightMetres/(111_320*cos(Math.toRadians(p.latitude))))
        }
    }
}
data class NavigationSimulationFrame(val position: GeoPoint,val bearing: Double,val distance: Double,val turnRemaining: Double,val planned: List<GeoPoint>)
