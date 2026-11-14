package com.hmgchallenge.pleosstarter.driving

/** SDK callbacks and explicitly labelled speech requests. A request is never playback proof. */
data class ApiObservation(
    val api: String,
    val kind: String,
    val values: Map<String,String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

class RuntimeEvidence {
    private val entries = ArrayDeque<ApiObservation>()
    @Synchronized fun record(observation: ApiObservation) {
        entries.addLast(observation)
        while(entries.size > 512) entries.removeFirst()
    }
    @Synchronized fun snapshot(): List<ApiObservation> = entries.toList()
}

data class RuntimeVerification(
    val signalValueReceived: Boolean,
    val signalSubscriptionReceived: Boolean,
    val speedValueReceived: Boolean,
    val tbtValueReceived: Boolean,
    val speechPlaybackCompleted: Boolean
) {
    val allVerified get() = signalValueReceived && signalSubscriptionReceived && speedValueReceived && tbtValueReceived && speechPlaybackCompleted
    companion object {
        fun from(samples: List<ApiObservation>) = RuntimeVerification(
            samples.any {it.api=="TURN_SIGNAL" && it.kind=="VALUE" && it.values["operation"]=="GET" && it.values["state"] in setOf("OFF","LEFT","RIGHT")},
            samples.any {it.api=="TURN_SIGNAL" && it.kind=="VALUE" && it.values["operation"]=="SUBSCRIPTION" && it.values["state"] in setOf("OFF","LEFT","RIGHT")},
            samples.any {it.api=="SPEED" && it.kind=="VALUE" && it.values["metersPerSecond"]?.toFloatOrNull()?.isFinite()==true},
            samples.any {it.api=="NAVI_TBT" && it.kind=="VALUE" && (it.values["count"]?.toIntOrNull() ?: 0)>0 && it.values["distanceMeters"]?.toDoubleOrNull()?.let {v->v.isFinite() && v>=0}==true},
            samples.any {it.api=="TTS" && it.kind=="PLAYBACK_STARTED"} && samples.any {it.api=="TTS" && it.kind=="PLAYBACK_DONE"}
        )
    }
}
