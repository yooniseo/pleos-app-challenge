package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.*

abstract class QuestPageCanvas(context: Context,assets: ProductIllustrations): SceneCanvas(context,assets) {
    protected val blue=Color.rgb(85,139,237)
    protected val ink=Color.rgb(79,86,143)
    protected val muted=Color.rgb(110,119,141)
    protected fun image(c: Canvas,name: String,x: Float,y: Float,w: Float,h: Float,stretch: Boolean=false) {
        assets.figma?.draw(c,name,RectF(x,y,x+w,y+h),stretch)
    }
    protected fun page(c: Canvas,title: String,subtitle: String) {
        c.drawColor(Color.rgb(200,217,255))
        image(c,"quest_landscape",0f,0f,1700f,1184f)
        paint.style=Paint.Style.FILL
        paint.color=Color.WHITE
        paint.shader=LinearGradient(0f,400f,0f,1125f,Color.TRANSPARENT,0x80FFFFFF.toInt(),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,1700f,1184f,paint)
        paint.shader=null
        box(c,-1f,1042f,1708f,200f,Color.WHITE,40f)
        text(c,title,66f,69f,66f,Color.WHITE,true)
        text(c,subtitle,66f,169f,27f,Color.WHITE)
    }
}
