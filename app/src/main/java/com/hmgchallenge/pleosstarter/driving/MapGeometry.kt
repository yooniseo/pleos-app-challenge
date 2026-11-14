package com.hmgchallenge.pleosstarter.driving

import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double) {
    val valid get()=latitude.isFinite() && longitude.isFinite() && latitude in -85.05112878..85.05112878 && longitude in -180.0..180.0
}
data class MapPixel(val x: Double,val y: Double)
object WebMercator {
    fun project(point: GeoPoint,zoom: Int): MapPixel {
        require(point.valid && zoom in 0..20)
        val size=256.0*2.0.pow(zoom)
        val lat=Math.toRadians(point.latitude)
        return MapPixel((point.longitude+180.0)/360.0*size,(1.0-ln(tan(lat)+1.0/cos(lat))/PI)/2.0*size)
    }
    fun fitZoom(points: List<GeoPoint>,width: Int,height: Int): Int {
        if(points.isEmpty() || width<=80 || height<=80) return 16
        return (18 downTo 1).firstOrNull {z->
            val p=points.map {project(it,z)}
            p.maxOf {it.x}-p.minOf {it.x} <= width-72 && p.maxOf {it.y}-p.minOf {it.y} <= height-72
        } ?: 1
    }
}

/** Counts are supplied by the driver for one route; GPS or a turn signal never confirms a lane. */
data class DriverLanePlan(val routeId: String,val current: Int,val target: Int,val total: Int) {
    val valid get()=routeId.isNotBlank() && total in 2..8 && current in 1..total && target in 1..total
    fun instruction(activeRouteId: String): String? {
        if(!valid || activeRouteId!=routeId) return null
        val count=abs(target-current)
        return if(count==0) "목표 차로로 지정했어요 · 주변을 직접 확인해요" else
            "${if(target>current) "오른쪽" else "왼쪽"}으로 ${count}개 차로 준비 · 한 차로씩 주변 확인"
    }
}
data class ReferenceRoute(val points: List<GeoPoint>,val distanceMeters: Double,val durationSeconds: Double,
    val roadNames: List<String>,val laneDataAvailable: Boolean,val firstRightTurn: GeoPoint?=null) {
    fun positionAt(fraction: Double): GeoPoint {
        require(points.size>=2 && fraction.isFinite())
        val lengths=points.zipWithNext {a,b->hypot((b.longitude-a.longitude)*cos(Math.toRadians(a.latitude)),b.latitude-a.latitude)}
        var remaining=lengths.sum()*fraction.coerceIn(0.0,1.0)
        lengths.forEachIndexed {i,length->
            if(remaining<=length && length>0) {
                val t=remaining/length;val a=points[i];val b=points[i+1]
                return GeoPoint(a.latitude+(b.latitude-a.latitude)*t,a.longitude+(b.longitude-a.longitude)*t)
            }
            remaining-=length
        }
        return points.last()
    }
}
