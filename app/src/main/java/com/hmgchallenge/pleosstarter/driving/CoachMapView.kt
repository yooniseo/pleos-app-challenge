package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.*
import android.view.View
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.*

/** Native raster map: requests only visible tiles, identifies the app, caches and attributes OSM. */
class CoachMapView(context: Context,private val tileTemplate: String="https://tile.openstreetmap.org/{z}/{x}/{y}.png") : View(context) {
    private data class Tile(val zoom: Int,val x: Int,val y: Int) {val id get()="$zoom-$x-$y"}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var worker=Executors.newFixedThreadPool(2)
    private val directory=File(context.cacheDir,"osm-tiles").apply {mkdirs()}
    private val tiles=linkedMapOf<String,Bitmap>()
    private val pending=mutableSetOf<String>()
    private val failed=mutableSetOf<String>()
    private var center=GeoPoint(37.401,127.106)
    private var marker: GeoPoint?=null
    private var heading=0.0
    private var zoom=16
    private var route: ReferenceRoute?=null
    private var closed=false
    private var generation=0
    private var motion: ValueAnimator?=null
    private var progress=0.0
    private var visibleTileCount=0
    fun diagnostics()=mapOf("visibleTiles" to visibleTileCount.toString(),"routePoints" to (route?.points?.size ?: 0).toString(),
        "progress" to progress.toString(),"marker" to (marker?.let {"${it.latitude},${it.longitude}"} ?: ""),
        "attached" to isAttachedToWindow.toString(),"closed" to closed.toString(),"size" to "${width}x$height")
    var onMapStatus: (String)->Unit={}
    init {contentDescription="OpenStreetMap 실지도와 경로 미리보기. 현재 차로를 자동으로 인식하지 않아요."}
    fun setPosition(point: GeoPoint,bearing: Double=0.0,follow: Boolean=true) {
        if(!point.valid) return
        marker=point;heading=bearing.takeIf {it.isFinite()} ?: 0.0
        if(follow) center=point
        invalidate()
    }
    fun setRoute(value: ReferenceRoute?) {
        route=value
        if(value!=null) fit(value)
        invalidate()
    }
    fun moveAlongRoute(target: Double,animate: Boolean=true) {
        val path=route ?: return
        motion?.cancel()
        fun move(value: Double) {
            progress=value
            val p=path.positionAt(value);val next=path.positionAt((value+0.003).coerceAtMost(1.0))
            val bearing=Math.toDegrees(atan2((next.longitude-p.longitude)*cos(Math.toRadians(p.latitude)),next.latitude-p.latitude))
            setPosition(p,bearing,false)
        }
        if(!animate) move(target) else motion=ValueAnimator.ofFloat(progress.toFloat(),target.toFloat()).apply {
            duration=1_500;addUpdateListener {move((it.animatedValue as Float).toDouble())};start()
        }
    }
    private fun fit(value: ReferenceRoute) {
        zoom=WebMercator.fitZoom(value.points,width,height)
        center=GeoPoint((value.points.minOf {it.latitude}+value.points.maxOf {it.latitude})/2,
            (value.points.minOf {it.longitude}+value.points.maxOf {it.longitude})/2)
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {super.onSizeChanged(w,h,oldw,oldh);route?.let(::fit)}
    fun retry() {failed.clear();invalidate()}
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(229,235,230))
        val origin=WebMercator.project(center,zoom)
        val left=origin.x-width/2.0;val top=origin.y-height/2.0
        val tileMinX=floor(left/256).toInt();val tileMaxX=floor((left+width)/256).toInt()
        val tileMinY=floor(top/256).toInt();val tileMaxY=floor((top+height)/256).toInt()
        visibleTileCount=0
        for(x in tileMinX..tileMaxX) for(y in tileMinY..tileMaxY) {
            if(x<0 || y<0 || x>=1.shl(zoom) || y>=1.shl(zoom)) continue
            val key=Tile(zoom,x,y)
            val bitmap=tiles[key.id]
            val px=(x*256-left).toFloat();val py=(y*256-top).toFloat()
            if(bitmap!=null) {canvas.drawBitmap(bitmap,px,py,paint);visibleTileCount++}
            else {paint.color=Color.rgb(210,219,212);paint.style=Paint.Style.STROKE;canvas.drawRect(px,py,px+256,py+256,paint);paint.style=Paint.Style.FILL;load(key)}
        }
        fun point(p: GeoPoint)=WebMercator.project(p,zoom).let {PointF((it.x-left).toFloat(),(it.y-top).toFloat())}
        route?.let {value->
            val path=Path();value.points.forEachIndexed {i,p->val pos=point(p);if(i==0)path.moveTo(pos.x,pos.y) else path.lineTo(pos.x,pos.y)}
            paint.style=Paint.Style.STROKE;paint.strokeJoin=Paint.Join.ROUND;paint.strokeCap=Paint.Cap.ROUND
            paint.color=Color.WHITE;paint.strokeWidth=13f;canvas.drawPath(path,paint)
            paint.color=Color.rgb(0,140,119);paint.strokeWidth=8f;canvas.drawPath(path,paint);paint.style=Paint.Style.FILL
            val end=point(value.points.last());paint.color=Color.rgb(244,112,72);canvas.drawCircle(end.x,end.y,10f,paint)
        }
        marker?.let {value->
            val pos=point(value);paint.color=Color.WHITE;canvas.drawCircle(pos.x,pos.y,17f,paint)
            canvas.save();canvas.rotate(heading.toFloat(),pos.x,pos.y)
            val arrow=Path().apply {moveTo(pos.x,pos.y-16);lineTo(pos.x+11,pos.y+12);lineTo(pos.x,pos.y+6);lineTo(pos.x-11,pos.y+12);close()}
            paint.color=Color.rgb(0,139,124);canvas.drawPath(arrow,paint);canvas.restore()
        }
        val density=resources.displayMetrics.density
        paint.color=Color.argb(225,255,255,255);canvas.drawRect(0f,height-27*density,width.toFloat(),height.toFloat(),paint)
        paint.color=Color.rgb(34,60,49);paint.textSize=12*density
        canvas.drawText("© OpenStreetMap contributors · 참고 경로 OSRM",8*density,height-8*density,paint)
        if(tiles.isEmpty()) {
            paint.textSize=14*density;paint.color=Color.rgb(45,65,55)
            canvas.drawText(if(failed.isEmpty()) "지도를 불러오고 있어요" else "지도 연결을 확인한 뒤 다시 불러와 주세요",12*density,25*density,paint)
        }
    }
    private fun load(tile: Tile) {
        if(closed || tile.id in pending || tile.id in failed) return
        pending.add(tile.id)
        val token=generation
        worker.execute {
            val result=runCatching {
                val file=File(directory,tile.id+".png")
                val fresh=file.exists() && System.currentTimeMillis()-file.lastModified()<SEVEN_DAYS
                if(!fresh) {
                    val address=tileTemplate.replace("{z}","${tile.zoom}").replace("{x}","${tile.x}").replace("{y}","${tile.y}")
                    require(address.startsWith("https://"))
                    val connection=URL(address).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout=7_000;connection.readTimeout=10_000
                        connection.setRequestProperty("User-Agent",USER_AGENT)
                        if(file.exists()) connection.ifModifiedSince=file.lastModified()
                        when(connection.responseCode) {
                            200->connection.inputStream.use {input->File(file.path+".tmp").let {temp->temp.outputStream().use {input.copyTo(it)};check(temp.renameTo(file))}}
                            304->file.setLastModified(System.currentTimeMillis())
                            else->check(file.exists()) {"Tile response ${connection.responseCode}"}
                        }
                    } finally {connection.disconnect()}
                }
                checkNotNull(BitmapFactory.decodeFile(file.path))
            }
            post {
                if(closed || token!=generation) {result.getOrNull()?.recycle();return@post}
                pending.remove(tile.id)
                result.onSuccess {bitmap->
                    tiles[tile.id]=bitmap
                    while(tiles.size>48) {val first=tiles.keys.first();tiles.remove(first)?.recycle()}
                    onMapStatus("실지도 표시됨")
                }.onFailure {failed.add(tile.id);onMapStatus("지도 연결 제한 · 다시 불러오기를 눌러 주세요")}
                invalidate()
            }
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if(closed) {closed=false;worker=Executors.newFixedThreadPool(2);pending.clear();failed.clear();invalidate()}
    }
    override fun onDetachedFromWindow() {closed=true;generation++;motion?.cancel();worker.shutdownNow();tiles.values.forEach {it.recycle()};tiles.clear();super.onDetachedFromWindow()}
    companion object {
        const val USER_AGENT="TutorialPleosCoach/0.3 (+https://github.com/yooniseo/pleos-app-challenge)"
        private const val SEVEN_DAYS=7L*24*60*60*1000
    }
}
