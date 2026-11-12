package com.hmgchallenge.pleosstarter.driving

import kotlin.math.*

data class RoadPoint(val x: Double,val y: Double)
data class RoadBox(val left: Double,val bottom: Double,val right: Double,val top: Double) {
    fun contains(x: Double,y: Double)=x in left..right && y in bottom..top
}

/** Shared metre coordinates for road paint, planned paths, and maneuver validation. */
class TrainingRoadLayout(val rightTurn: Boolean) {
    val laneWidth=3.5
    val halfWidth=3*laneWidth
    val junctionX=120.0
    val cornerRadius=10.0
    val carHalfLength=2.1
    val carHalfWidth=0.9
    val entryFromNorth=rightTurn
    val initialHeading=if(entryFromNorth)PI else 0.0
    val initialY=if(entryFromNorth)32.0 else -32.0
    val startLane=if(rightTurn)1 else 3
    val turnLane=if(rightTurn)3 else 1
    val entryRadius=if(entryFromNorth)12.0 else 7.0
    val entryTurnSign=if(entryFromNorth)-1 else 1
    val entryStopY=if(entryFromNorth)20.2 else -20.2
    val entryArcY=laneY(startLane)+(if(entryFromNorth)entryRadius else -entryRadius)
    val entryLaneEnd=RoadPoint(entryRadius+4,laneY(startLane))
    val mainStopX=junctionX-18.0-carHalfLength-0.1
    val turnRadius=if(rightTurn)8.0 else 18.0
    val departureHeading=if(rightTurn)PI else 0.0
    val departureLaneX=if(rightTurn)junctionX-(turnLane-0.5)*laneWidth else junctionX+(turnLane-0.5)*laneWidth
    val turnEnd=RoadPoint(departureLaneX,if(rightTurn)-23.5 else 23.5)
    val mainCrosswalks=listOf(
        RoadBox(junctionX-16.5,-halfWidth,junctionX-12.5,halfWidth),
        RoadBox(junctionX+12.5,-halfWidth,junctionX+16.5,halfWidth),
        RoadBox(junctionX-halfWidth,12.5,junctionX+halfWidth,16.5),
        RoadBox(junctionX-halfWidth,-16.5,junctionX+halfWidth,-12.5))
    val entryCrosswalk=if(entryFromNorth)RoadBox(-1.75,13.5,5.25,16.5) else RoadBox(-5.25,-16.5,1.75,-13.5)
    val parkingLot=if(entryFromNorth)RoadBox(-12.0,24.0,12.0,41.0) else RoadBox(-12.0,-41.0,12.0,-24.0)
    fun laneY(lane: Int)=-(lane-0.5)*laneWidth
    fun eastboundLane(y: Double): Int?=if(y<0 && y>=-halfWidth)floor(-y/laneWidth).toInt().plus(1).coerceAtMost(3) else null
    fun isIntersection(x: Double,y: Double)=
        (x in -7.0..19.0 && abs(y)<=halfWidth) || (abs(x-junctionX)<=halfWidth+cornerRadius && abs(y)<=halfWidth+cornerRadius)
    fun paved(x: Double,y: Double): Boolean {
        if(abs(y)<=halfWidth)return true
        if(abs(x-junctionX)<=halfWidth)return true
        if(parkingLot.contains(x,y))return true
        if(entryFromNorth && x in -1.75..5.25 && y in halfWidth..41.0)return true
        if(!entryFromNorth && x in -5.25..1.75 && y in -41.0..-halfWidth)return true
        if(!entryFromNorth && x in 1.75..7.75 && y in -16.5..-10.5 && hypot(x-7.75,y+16.5)>=5.95)return true
        val dx=abs(x-junctionX);val dy=abs(y)
        if(dx in halfWidth..halfWidth+cornerRadius && dy in halfWidth..halfWidth+cornerRadius)
            return hypot(dx-(halfWidth+cornerRadius),dy-(halfWidth+cornerRadius))>=cornerRadius-0.05
        return false
    }
    fun entryTurnPath(from: RoadPoint): TrainingRoadPath {
        val arcStart=RoadPoint(0.0,entryArcY)
        val points=line(from,arcStart).toMutableList()
        points+=arc(arcStart,initialHeading,entryTurnSign,entryRadius).drop(1)
        points+=line(points.last(),entryLaneEnd).drop(1)
        return TrainingRoadPath(points)
    }
    fun laneChangePath(from: RoadPoint,toLane: Int): TrainingRoadPath {
        val endY=laneY(toLane)
        return TrainingRoadPath((0..72).map {i->
            val t=i/72.0;RoadPoint(from.x+26*t,from.y+(endY-from.y)*(3*t*t-2*t*t*t))
        })
    }
    fun mainTurnPath(from: RoadPoint): TrainingRoadPath {
        val arcStart=RoadPoint(departureLaneX-turnRadius,laneY(turnLane))
        val points=line(from,arcStart).toMutableList()
        points+=arc(arcStart,PI/2,if(rightTurn)1 else -1,turnRadius).drop(1)
        points+=line(points.last(),turnEnd).drop(1)
        return TrainingRoadPath(points)
    }
    fun mainStraight(from: RoadPoint,lane: Int,endX: Double)=TrainingRoadPath(line(from,RoadPoint(endX,laneY(lane))))
    fun departureStraight(from: RoadPoint,length: Double)=TrainingRoadPath(line(from,
        RoadPoint(departureLaneX,from.y+(if(rightTurn)-length else length))))
    companion object {
        fun line(a: RoadPoint,b: RoadPoint): List<RoadPoint> {
            val n=maxOf(1,ceil(hypot(b.x-a.x,b.y-a.y)/0.5).toInt())
            return (0..n).map {i->val t=i.toDouble()/n;RoadPoint(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t)}
        }
        fun arc(start: RoadPoint,heading: Double,sign: Int,radius: Double): List<RoadPoint> {
            val cx=start.x+sign*radius*cos(heading);val cy=start.y-sign*radius*sin(heading)
            return (0..90).map {i->
                val angle=heading+sign*Math.toRadians(i.toDouble())
                RoadPoint(cx-sign*radius*cos(angle),cy+sign*radius*sin(angle))
            }
        }
        fun angleError(target: Double,current: Double)=atan2(sin(target-current),cos(target-current))
    }
}

class TrainingRoadPath(val points: List<RoadPoint>) {
    private val lengths=points.zipWithNext {a,b->hypot(b.x-a.x,b.y-a.y)}
    val length=lengths.sum()
    fun progress(x: Double,y: Double): Double {
        var offset=0.0;var best=Double.POSITIVE_INFINITY;var result=0.0
        lengths.forEachIndexed {i,len->
            val a=points[i];val b=points[i+1]
            val t=if(len==0.0)0.0 else (((x-a.x)*(b.x-a.x)+(y-a.y)*(b.y-a.y))/(len*len)).coerceIn(0.0,1.0)
            val distance=hypot(x-a.x-(b.x-a.x)*t,y-a.y-(b.y-a.y)*t)
            if(distance<best){best=distance;result=offset+t*len};offset+=len
        }
        return result
    }
    fun at(distance: Double): RoadPoint {
        var remaining=distance.coerceAtLeast(0.0)
        lengths.forEachIndexed {i,len->
            if(remaining<=len && len>0){val t=remaining/len;val a=points[i];val b=points[i+1];return RoadPoint(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t)}
            remaining-=len
        }
        val a=points[points.lastIndex-1];val b=points.last();val len=hypot(b.x-a.x,b.y-a.y).coerceAtLeast(0.001)
        return RoadPoint(b.x+(b.x-a.x)/len*remaining,b.y+(b.y-a.y)/len*remaining)
    }
    fun steering(x: Double,y: Double,heading: Double,wheelbase: Double,ratio: Double): Double {
        val target=at(progress(x,y)+4.5)
        val alpha=TrainingRoadLayout.angleError(atan2(target.x-x,target.y-y),heading)
        return (Math.toDegrees(atan2(2*wheelbase*sin(alpha),hypot(target.x-x,target.y-y).coerceAtLeast(0.2)))*ratio).coerceIn(-450.0,450.0)
    }
    fun reached(x: Double,y: Double,tolerance: Double=0.75)=
        progress(x,y)>=length-tolerance && hypot(x-points.last().x,y-points.last().y)<=tolerance
}
