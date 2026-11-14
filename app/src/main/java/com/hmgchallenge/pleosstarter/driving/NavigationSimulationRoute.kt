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
    companion object {
        fun metres(a: GeoPoint,b: GeoPoint)=hypot((b.longitude-a.longitude)*111_320*cos(Math.toRadians(a.latitude)),(b.latitude-a.latitude)*111_320)
        fun offset(p: GeoPoint,bearing: Double,rightMetres: Double): GeoPoint {
            val yaw=Math.toRadians(bearing)
            return GeoPoint(p.latitude-sin(yaw)*rightMetres/111_320,p.longitude+cos(yaw)*rightMetres/(111_320*cos(Math.toRadians(p.latitude))))
        }
    }
}
data class NavigationSimulationFrame(val position: GeoPoint,val bearing: Double,val distance: Double,val turnRemaining: Double,val planned: List<GeoPoint>)
