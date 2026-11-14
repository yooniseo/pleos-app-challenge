package com.hmgchallenge.pleosstarter.ui

import android.graphics.*

/** Preserved from the earlier coaching wheel: live spokes, top marker and a faint straight reference. */
class SteeringWheelRenderer {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var displayed: Double?=null
    private var lastFrame=0L
    fun draw(c: Canvas,cx: Float,cy: Float,size: Float,angle: Double,dark: Boolean=false) {
        val now=android.os.SystemClock.uptimeMillis()
        val dt=(now-lastFrame).coerceIn(0,100);lastFrame=now
        displayed=displayed?.let {it+(angle-it)*(1-kotlin.math.exp(-dt/180.0))} ?: angle
        val radius=size*.36f
        fun wheel(rotation: Double,color: Int,alpha: Int) {
            c.save();c.rotate(rotation.toFloat(),cx,cy)
            paint.color=color;paint.alpha=alpha;paint.style=Paint.Style.STROKE;paint.strokeWidth=radius*.12f
            c.drawCircle(cx,cy,radius,paint)
            c.drawLine(cx-radius,cy,cx+radius,cy,paint)
            c.drawLine(cx,cy,cx,cy+radius,paint)
            paint.style=Paint.Style.FILL;c.drawCircle(cx,cy,radius*.25f,paint)
            paint.color=Color.rgb(255,194,94);paint.alpha=alpha
            c.drawRoundRect(cx-radius*.05f,cy-radius*1.02f,cx+radius*.05f,cy-radius*.82f,3f,3f,paint)
            c.restore()
        }
        wheel(0.0,if(dark)Palette.lime else Palette.purple,65)
        wheel(displayed!!,if(dark)Color.rgb(230,239,244) else Color.rgb(46,49,68),255)
    }
}
