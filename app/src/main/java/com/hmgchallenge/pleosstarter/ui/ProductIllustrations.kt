package com.hmgchallenge.pleosstarter.ui

import android.graphics.*
import android.content.Context
import kotlin.math.*

/** Dynamic vehicle/map artwork with the designer's local, individual coach illustration assets. */
class ProductIllustrations(context: Context?=null) {
    val figma=context?.let(::FigmaCoachAssets)
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private fun fill(color: Int) {p.shader=null;p.style=Paint.Style.FILL;p.color=color}
    private fun rounded(c: Canvas,r: RectF,radius: Float,color: Int) {fill(color);c.drawRoundRect(r,radius,radius,p)}
    fun car(c: Canvas,r: RectF,rearView: Boolean=false) {
        c.save();c.translate(r.left,r.top);c.scale(r.width()/200,r.height()/200)
        fill(Color.argb(45,26,34,56));c.drawOval(14f,160f,189f,199f,p)
        rounded(c,RectF(21f,83f,44f,181f),10f,Color.rgb(35,42,58))
        rounded(c,RectF(157f,83f,180f,181f),10f,Color.rgb(35,42,58))
        rounded(c,RectF(26f,70f,41f,157f),7f,Color.rgb(71,78,97))
        rounded(c,RectF(159f,70f,174f,157f),7f,Color.rgb(71,78,97))
        // Body, shoulders and glass are independent layers, not a flattened photo.
        p.shader=LinearGradient(20f,35f,180f,155f,intArrayOf(Color.rgb(230,235,255),Color.rgb(168,180,219),Color.rgb(111,127,177)),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(30f,28f,171f,183f),23f,23f,p);p.shader=null
        val roof=Path().apply {moveTo(44f,81f);lineTo(54f,22f);quadTo(100f,4f,147f,22f);lineTo(158f,81f);close()}
        p.shader=LinearGradient(60f,10f,140f,95f,Color.rgb(242,245,255),Color.rgb(143,157,201),Shader.TileMode.CLAMP);c.drawPath(roof,p);p.shader=null
        rounded(c,RectF(40f,74f,161f,126f),17f,Color.rgb(62,76,111))
        val glass=Path().apply {moveTo(57f,30f);quadTo(100f,16f,143f,30f);lineTo(153f,82f);quadTo(100f,94f,47f,82f);close()}
        p.shader=LinearGradient(70f,25f,135f,92f,Color.rgb(72,91,130),Color.rgb(26,39,65),Shader.TileMode.CLAMP);c.drawPath(glass,p);p.shader=null
        fill(Color.argb(75,205,232,255));c.drawPath(Path().apply {moveTo(61f,33f);lineTo(97f,26f);lineTo(73f,79f);lineTo(51f,78f);close()},p)
        rounded(c,RectF(18f,73f,35f,86f),5f,Color.rgb(124,139,184));rounded(c,RectF(166f,73f,183f,86f),5f,Color.rgb(124,139,184))
        if(!rearView) {
            rounded(c,RectF(55f,93f,146f,130f),13f,Color.rgb(190,204,238))
            fill(Color.argb(80,255,255,255));c.drawRoundRect(RectF(62f,98f,69f,127f),3f,3f,p)
        }
        p.shader=LinearGradient(0f,129f,0f,182f,Color.rgb(176,192,228),Color.rgb(108,123,170),Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(35f,128f,166f,179f),13f,13f,p);p.shader=null
        rounded(c,RectF(37f,148f,164f,154f),3f,Color.rgb(255,108,118))
        rounded(c,RectF(42f,148f,62f,160f),4f,Color.rgb(255,126,138));rounded(c,RectF(139f,148f,159f,160f),4f,Color.rgb(255,126,138))
        rounded(c,RectF(39f,170f,163f,184f),7f,Color.rgb(49,60,87))
        rounded(c,RectF(78f,166f,124f,177f),3f,Color.rgb(237,240,249))
        fill(Color.argb(90,255,255,255));c.drawCircle(100f,140f,3f,p)
        c.restore()
    }
    fun mascot(c: Canvas,r: RectF) {
        figma?.let {it.draw(c,"mascot",r);return}
        c.save();c.translate(r.left,r.top);c.scale(r.width()/100,r.height()/100)
        fill(Color.rgb(216,244,234));c.drawCircle(50f,51f,47f,p)
        rounded(c,RectF(25f,53f,76f,96f),22f,Color.rgb(101,195,165))
        fill(Color.rgb(252,250,239));c.drawOval(20f,16f,80f,74f,p)
        // A sprout-shaped road buddy, deliberately different from the reference cap character.
        fill(Palette.purple);c.drawOval(28f,9f,52f,25f,p);c.save();c.rotate(-32f,54f,12f);c.drawOval(46f,1f,69f,19f,p);c.restore()
        fill(Color.rgb(41,55,87));c.drawOval(34f,39f,40f,48f,p);c.drawOval(60f,39f,66f,48f,p)
        fill(Color.rgb(255,193,172));c.drawOval(25f,50f,38f,57f,p);c.drawOval(63f,50f,76f,57f,p)
        p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeWidth=3f;p.color=Color.rgb(41,55,87)
        c.drawArc(RectF(39f,46f,62f,61f),20f,140f,false,p)
        p.strokeWidth=7f;p.color=Palette.purple;c.drawLine(28f,72f,65f,95f,p)
        p.strokeWidth=4f;p.color=Color.rgb(41,55,87);c.drawCircle(51f,81f,12f,p);c.drawLine(39f,81f,63f,81f,p)
        fill(Color.rgb(252,250,239));c.drawCircle(36f,79f,5f,p);c.drawCircle(66f,79f,5f,p)
        c.restore()
    }
    fun cloud(c: Canvas,x: Float,y: Float,w: Float,h: Float,light: Boolean) {
        fill(if(light)Color.argb(210,255,255,255)else Color.argb(70,130,175,242))
        c.drawOval(x,y+h*.25f,x+w,y+h,p);c.drawOval(x+w*.22f,y,x+w*.65f,y+h,p)
    }
    fun hills(c: Canvas) {
        fill(Color.rgb(222,242,192));c.drawPath(Path().apply {moveTo(-40f,1000f);cubicTo(400f,740f,1180f,780f,1740f,970f);lineTo(1740f,1184f);lineTo(-40f,1184f);close()},p)
        fill(Color.rgb(184,215,143));c.drawPath(Path().apply {moveTo(550f,1184f);cubicTo(620f,970f,1150f,865f,1740f,930f);lineTo(1740f,1184f);close()},p)
    }
    fun questBadge(c: Canvas,x: Float,y: Float,kind: Int,active: Boolean) {
        fill(if(active)Color.WHITE else Color.rgb(237,235,214));c.drawCircle(x+119,y+133,118f,p)
        fill(if(active)Color.rgb(241,241,255)else listOf(Color.rgb(248,208,197),Color.rgb(216,237,189),Color.rgb(224,221,255),Color.rgb(249,221,194),Color.rgb(216,237,189))[kind])
        c.drawCircle(x+119,y+133,99f,p)
        c.save();c.translate(x+119,y+104)
        p.style=Paint.Style.STROKE;p.strokeWidth=14f;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND;p.color=if(active)Palette.purple else Palette.ink
        when(kind) {
            0->{p.style=Paint.Style.FILL;repeat(5){c.save();c.rotate(it*72f);c.drawOval(-11f,-47f,11f,-7f,p);c.restore()};c.drawCircle(0f,0f,12f,p)}
            1->c.drawPath(Path().apply {moveTo(-34f,0f);lineTo(-10f,28f);lineTo(38f,-35f)},p)
            2->{c.drawLine(-38f,0f,38f,0f,p);c.drawLine(12f,-27f,40f,0f,p);c.drawLine(12f,27f,40f,0f,p)}
            3->{c.drawLine(-42f,0f,42f,0f,p);c.drawLine(-18f,-23f,-42f,0f,p);c.drawLine(-18f,23f,-42f,0f,p);c.drawLine(18f,-23f,42f,0f,p);c.drawLine(18f,23f,42f,0f,p)}
            4->{c.drawLine(-20f,35f,-20f,-35f,p);c.drawPath(Path().apply {moveTo(-20f,-35f);lineTo(10f,-35f);cubicTo(45f,-35f,45f,3f,10f,3f);lineTo(-20f,3f)},p)}
        }
        p.style=Paint.Style.FILL;c.restore()
    }
    fun turnArrow(c: Canvas,x: Float,y: Float,w: Float,h: Float) {
        c.save();c.translate(x,y);c.scale(w/80,h/111)
        p.style=Paint.Style.STROKE;p.strokeWidth=12f;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND;p.color=Palette.purple
        c.drawPath(Path().apply {moveTo(15f,100f);lineTo(15f,44f);quadTo(15f,31f,29f,31f);lineTo(66f,31f)},p)
        c.drawLine(47f,11f,68f,31f,p);c.drawLine(47f,51f,68f,31f,p);p.style=Paint.Style.FILL;c.restore()
    }
    fun tree(c: Canvas,x: Float,y: Float,w: Float,h: Float) {
        c.save();c.translate(x,y);c.scale(w/100,h/130)
        fill(Color.argb(20,30,53,65));c.drawOval(15f,113f,98f,129f,p)
        rounded(c,RectF(44f,75f,53f,119f),4f,Color.rgb(159,145,116))
        fill(Color.rgb(131,181,153));c.drawPath(Path().apply {moveTo(50f,0f);lineTo(90f,30f);lineTo(98f,78f);lineTo(57f,104f);lineTo(14f,81f);lineTo(4f,35f);close()},p)
        fill(Color.rgb(160,201,173));c.drawPath(Path().apply {moveTo(50f,0f);lineTo(57f,55f);lineTo(4f,35f);close()},p)
        fill(Color.rgb(110,161,137));c.drawPath(Path().apply {moveTo(57f,55f);lineTo(98f,78f);lineTo(57f,104f);close()},p)
        fill(Color.rgb(145,192,161));c.drawPath(Path().apply {moveTo(4f,35f);lineTo(57f,55f);lineTo(14f,81f);close()},p)
        c.restore()
    }
    fun crossing(c: Canvas,r: RectF) {
        fill(Color.rgb(249,250,255));val stripes=(r.width()/.8f).toInt().coerceAtLeast(1);val gap=r.width()/stripes
        repeat(stripes){i->c.drawRect(r.left+i*gap,r.top,r.left+i*gap+gap*.52f,r.bottom,p)}
    }
    fun completionPanel(c: Canvas) {
        fill(Color.rgb(23,27,35));c.drawPath(Path().apply {moveTo(0f,0f);lineTo(686f,0f);lineTo(615f,1184f);lineTo(0f,1184f);close()},p)
        fill(Color.rgb(249,217,77));c.drawPath(Path().apply {moveTo(669f,0f);lineTo(681f,0f);lineTo(610f,1184f);lineTo(598f,1184f);close()},p)
        fill(Color.rgb(46,52,66));c.drawPath(Path().apply {moveTo(0f,0f);lineTo(500f,0f);lineTo(260f,360f);close()},p)
    }
}
