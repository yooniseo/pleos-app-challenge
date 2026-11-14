package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import java.util.Locale
import java.util.concurrent.Executors

/** Explicit reference-route preview. This is not the OEM Navigation app's route geometry. */
class ReferenceRouteClient(context: Context,private val endpoint: String="https://router.project-osrm.org") {
    private val directory=File(context.cacheDir,"reference-routes").apply {mkdirs()}
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var generation=0
    fun request(start: GeoPoint,end: GeoPoint,onRoute: (ReferenceRoute)->Unit,onError: (String)->Unit) {
        require(start.valid && end.valid && endpoint.startsWith("https://"))
        val token=++generation
        worker.execute {
            runCatching {
                val coordinates=String.format(Locale.US,"%.5f,%.5f;%.5f,%.5f",start.longitude,start.latitude,end.longitude,end.latitude)
                val cache=File(directory,coordinates.replace(';','_')+".json")
                val json=if(cache.exists() && System.currentTimeMillis()-cache.lastModified()<86_400_000) cache.readText() else {
                    val connection=URL("$endpoint/route/v1/driving/$coordinates?steps=true&geometries=geojson&overview=full").openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout=8_000;connection.readTimeout=12_000
                        connection.setRequestProperty("User-Agent","Chageun/0.3 (Pleos educational route preview)")
                        check(connection.responseCode==200) {"경로 서비스 응답 ${connection.responseCode}"}
                        connection.inputStream.bufferedReader().use {it.readText()}.also {parse(it);cache.writeText(it)}
                    } finally {connection.disconnect()}
                }
                parse(json)
            }.onSuccess {route->main.post {if(token==generation) onRoute(route)}}
                .onFailure {main.post {if(token==generation) onError("경로를 불러오지 못했어요 · 연결 후 다시 시도해 주세요")}}
        }
    }
    fun cancel() {generation++}
    fun close() {cancel();worker.shutdownNow()}
    companion object {
        fun parse(raw: String): ReferenceRoute {
            val root=JSONObject(raw);check(root.getString("code")=="Ok")
            val route=root.getJSONArray("routes").getJSONObject(0)
            val coordinates=route.getJSONObject("geometry").getJSONArray("coordinates")
            val points=(0 until coordinates.length()).map {index->coordinates.getJSONArray(index).let {GeoPoint(it.getDouble(1),it.getDouble(0))}}
            require(points.size>=2 && points.all {it.valid})
            val names=linkedSetOf<String>();var lanes=false
            var firstRightTurn: GeoPoint?=null
            val legs=route.getJSONArray("legs")
            for(i in 0 until legs.length()) {
                val steps=legs.getJSONObject(i).getJSONArray("steps")
                for(j in 0 until steps.length()) {
                    val step=steps.getJSONObject(j);step.optString("name").takeIf {it.isNotBlank()}?.let {names.add(it)}
                    val maneuver=step.optJSONObject("maneuver")
                    if(firstRightTurn==null && maneuver?.optString("modifier")=="right") {
                        maneuver.optJSONArray("location")?.let {firstRightTurn=GeoPoint(it.getDouble(1),it.getDouble(0))}
                    }
                    val intersections=step.optJSONArray("intersections") ?: continue
                    for(k in 0 until intersections.length()) if((intersections.getJSONObject(k).optJSONArray("lanes")?.length() ?: 0)>0) lanes=true
                }
            }
            return ReferenceRoute(points,route.getDouble("distance"),route.getDouble("duration"),names.toList(),lanes,firstRightTurn)
        }
    }
}
