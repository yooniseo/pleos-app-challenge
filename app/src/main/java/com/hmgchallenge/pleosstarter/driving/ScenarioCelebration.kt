package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.media.*
import com.hmgchallenge.pleosstarter.R

/** App-owned short melody; capability-checked parked Pleos ambient pulse restores its colour. */
class ScenarioCelebration(private val context: Context,private val stationary: ()->Boolean,
                          private val onStatus: (String)->Unit) {
    private var state=VehicleCoachState()
    private var vehicle: VehicleCoachSource?=null
    private var pendingLight=false
    private var player: MediaPlayer?=null
    private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener {change->if(change<0)stopSound()}.build()
    private val observations=mutableListOf<ApiObservation>()
    var soundStarts=0;private set
    fun start() {
        if(context.packageManager.hasSystemFeature("android.hardware.type.automotive")) {
            vehicle=VehicleCoachSource(context,{state=it;tryLight()},{onStatus(it)},{event->
                if(event.api.startsWith("AMBIENT")){observations.add(event);if(observations.size>64)observations.removeAt(0)}
            }).also {it.start()}
        }
    }
    fun celebrate(enabled: Boolean) {
        if(!enabled){onStatus("축하음·조명 꺼짐");return}
        playSound(R.raw.quest_success)
        pendingLight=true;onStatus(if(stationary() && state.gear=="P")"축하음 · 조명 응원" else "축하음 · 조명은 P단 정차 후")
        tryLight()
    }
    fun ding(enabled: Boolean) {if(enabled)playSound(R.raw.steering_ding)}
    private fun playSound(resource: Int) {
        stopSound()
        if(audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)runCatching {
            val descriptor=context.resources.openRawResourceFd(resource)
            val next=MediaPlayer();player=next
            next.setAudioAttributes(attributes);next.setDataSource(descriptor.fileDescriptor,descriptor.startOffset,descriptor.length);descriptor.close()
            next.setVolume(.35f,.35f);next.setOnCompletionListener {stopSound()};next.prepare();next.start();soundStarts++
        }.onFailure {stopSound()}
    }
    fun tryLight() {
        if(!pendingLight || !stationary() || state.gear!="P")return
        pendingLight=false;vehicle?.pulse {stationary() && state.gear=="P"}
    }
    fun diagnostics()=observations.toList()
    private fun stopSound() {runCatching {player?.release()};player=null;audio.abandonAudioFocusRequest(focus)}
    fun stop() {pendingLight=false;stopSound();vehicle?.stop();vehicle=null}
}
