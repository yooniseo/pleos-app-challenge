package com.hmgchallenge.pleosstarter.driving

import kotlin.math.abs

/** Planned, stable hand movements from the same illustrative path as the rendered wheel. */
class SteeringCoachingTimeline(end: Double,parking: Boolean,angle: (Double)->Double) {
    enum class Action(val text: String,val spoken: String) {
        RIGHT_MORE("그림처럼 핸들을 오른쪽으로 조금 더 돌리세요","오른쪽으로 더 돌리세요"),
        LEFT_MORE("그림처럼 핸들을 왼쪽으로 조금 더 돌리세요","왼쪽으로 더 돌리세요"),
        LEFT_UNWIND("이제 왼쪽으로 풀어 핸들을 가운데로 맞추세요","왼쪽으로 풀어 가운데로"),
        RIGHT_UNWIND("이제 오른쪽으로 풀어 핸들을 가운데로 맞추세요","오른쪽으로 풀어 가운데로"),
        HOLD("좋아요. 그림의 핸들 위치를 잠시 유지하세요","이대로 유지하세요"),
        CENTER("핸들을 가운데 두고 천천히 움직이세요","가운데 유지하세요")
    }
    data class Movement(val start: Double,val end: Double,val action: Action) {
        val key get()="steering:$start:$action"
    }
    private val movements: List<Movement>
    init {
        val groups=mutableListOf<Movement>()
        var d=0.0
        while(d<end) {
            val current=angle(d)
            val next=angle((d+.75).coerceAtMost(end))
            val action=when {
                abs(current)<1 && abs(next)<1->Action.CENTER
                abs(next-current)<(if(parking)4.0 else .15)->Action.HOLD
                abs(next)<abs(current) && current>0->Action.LEFT_UNWIND
                abs(next)<abs(current)->Action.RIGHT_UNWIND
                next>0->Action.RIGHT_MORE
                else->Action.LEFT_MORE
            }
            val until=(d+.25).coerceAtMost(end)
            val previous=groups.lastOrNull()
            if(previous?.action==action)groups[groups.lastIndex]=previous.copy(end=until)
            else groups.add(Movement(d,until,action))
            d=until
        }
        // Very short holds and numerical boundary changes inherit the adjacent stable action.
        val minimum=if(parking).6 else 1.5
        val stable=mutableListOf<Movement>()
        groups.forEach {movement->
            val previous=stable.lastOrNull()
            if(previous!=null && (movement.end-movement.start<minimum || previous.action==movement.action))
                stable[stable.lastIndex]=previous.copy(end=movement.end)
            else stable.add(movement)
        }
        movements=stable
    }
    fun at(distance: Double): Movement=movements.firstOrNull {distance<it.end} ?: movements.last()
}
