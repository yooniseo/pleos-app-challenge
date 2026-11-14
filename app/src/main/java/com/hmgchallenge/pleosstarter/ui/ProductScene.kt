package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import com.hmgchallenge.pleosstarter.R
import kotlin.math.roundToInt

object Palette {
    val ink=Color.rgb(54,63,128)
    val purple=Color.rgb(94,103,217)
    val cream=Color.rgb(255,249,232)
    val lime=Color.rgb(181,239,85)
}

abstract class SceneCanvas(context: Context,protected val assets: ProductIllustrations): View(context) {
    protected val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val medium=resources.getFont(R.font.asta_sans_medium)
    private val bold=resources.getFont(R.font.asta_sans_bold)
    final override fun onDraw(canvas: Canvas) {canvas.save();canvas.scale(width/1700f,height/1184f);drawScene(canvas);canvas.restore()}
    abstract fun drawScene(c: Canvas)
    protected fun box(c: Canvas,x: Float,y: Float,w: Float,h: Float,color: Int,r: Float=26f) {
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=color;c.drawRoundRect(RectF(x,y,x+w,y+h),r,r,paint)
    }
    protected fun text(c: Canvas,value: String,x: Float,y: Float,size: Float=30f,color: Int=Palette.ink,bold: Boolean=false) {
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=color;paint.textSize=size;paint.typeface=if(bold)this.bold else medium
        value.split('\n').forEachIndexed {i,line->c.drawText(line,x,y+size+i*size*1.4f,paint)}
    }
    protected fun wrappedText(c: Canvas,value: String,x: Float,y: Float,maxWidth: Float,size: Float,color: Int,bold: Boolean) {
        paint.textSize=size;paint.typeface=if(bold)this.bold else medium
        var remaining=value;var line=0
        while(remaining.isNotEmpty() && line<3) {
            var count=paint.breakText(remaining,true,maxWidth,null).coerceAtLeast(1)
            if(count<remaining.length)remaining.lastIndexOf(' ',count-1).takeIf {it>count/2}?.let {count=it}
            text(c,remaining.take(count).trim(),x,y+line*size*1.3f,size,color,bold)
            remaining=remaining.drop(count).trimStart();line++
        }
    }
}

/** Native controls provide focus and accessibility; the imagery beneath stays data driven. */
class ProductScene(context: Context,canvas: View): FrameLayout(context) {
    private val slots=mutableMapOf<View,RectF>()
    init {addView(canvas,LayoutParams(-1,-1));clipChildren=true}
    fun button(label: String,key: String,x: Float,y: Float,w: Float,h: Float,primary: Boolean=false,action: ()->Unit): Button = Button(context).apply {
        text=label;tag=key;contentDescription=label;isAllCaps=false;minimumWidth=0;minWidth=0;minHeight=0;minimumHeight=0
        setPadding(8,0,8,0);typeface=resources.getFont(R.font.asta_sans_medium)
        setTextColor(if(primary)Color.WHITE else Palette.ink)
        background=GradientDrawable().apply {setColor(if(primary)Palette.purple else Color.WHITE);cornerRadius=32f}
        elevation=2f;setOnClickListener {action()};addView(this);slots[this]=RectF(x,y,x+w,y+h)
    }
    override fun onMeasure(w: Int,h: Int) {
        super.onMeasure(w,h)
        slots.forEach {(v,r)->
            (v as? Button)?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,29f*measuredWidth/1700)
            v.measure(MeasureSpec.makeMeasureSpec((r.width()*measuredWidth/1700).roundToInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((r.height()*measuredHeight/1184).roundToInt(),MeasureSpec.EXACTLY))
        }
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
        getChildAt(0).layout(0,0,width,height)
        slots.forEach {(v,p)->v.layout((p.left*width/1700).roundToInt(),(p.top*height/1184).roundToInt(),(p.right*width/1700).roundToInt(),(p.bottom*height/1184).roundToInt())}
    }
}
