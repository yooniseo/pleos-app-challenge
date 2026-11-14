package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.*

/** Local exports of individual Figma illustration layers. Labels and interactions stay native. */
class FigmaCoachAssets(context: Context) {
    private val assets=context.applicationContext.assets
    private val images=mutableMapOf<String,Bitmap>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    fun draw(canvas: Canvas,name: String,bounds: RectF) {
        val bitmap=images.getOrPut(name) {
            assets.open("figma/$name.png").use {requireNotNull(BitmapFactory.decodeStream(it))}
        }
        val scale=minOf(bounds.width()/bitmap.width,bounds.height()/bitmap.height)
        val width=bitmap.width*scale;val height=bitmap.height*scale
        val rect=RectF(bounds.centerX()-width/2,bounds.centerY()-height/2,bounds.centerX()+width/2,bounds.centerY()+height/2)
        canvas.drawBitmap(bitmap,null,rect,paint)
    }
}
