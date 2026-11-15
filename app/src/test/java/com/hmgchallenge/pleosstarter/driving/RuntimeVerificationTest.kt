package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test

class RuntimeVerificationTest {
    @Test fun connectedOrEmptyCallbacksCannotPassAsActualValues() {
        val samples=listOf(ApiObservation("NAVI","CONNECTED"),ApiObservation("NAVI_TBT","EMPTY",mapOf("count" to "0")),
            ApiObservation("TURN_SIGNAL","NULL"),ApiObservation("TURN_SIGNAL","VALUE",mapOf("state" to "UNKNOWN","operation" to "GET")),
            ApiObservation("SPEED","NULL"),ApiObservation("TTS","CONNECTED"))
        val proof=RuntimeVerification.from(samples)
        assertFalse(proof.allVerified);assertFalse(proof.signalValueReceived);assertFalse(proof.tbtValueReceived)
        assertFalse(proof.speedValueReceived);assertFalse(proof.speechPlaybackCompleted)
    }
    @Test fun realValuesWithoutSubscriptionAndPlaybackAreIncomplete() {
        val proof=RuntimeVerification.from(listOf(
            ApiObservation("TURN_SIGNAL","VALUE",mapOf("state" to "OFF","operation" to "GET")),
            ApiObservation("SPEED","VALUE",mapOf("metersPerSecond" to "0.0")),
            ApiObservation("NAVI_TBT","VALUE",mapOf("count" to "1","distanceMeters" to "240.0")),
            ApiObservation("TTS","CONNECTED")))
        assertTrue(proof.signalValueReceived);assertTrue(proof.speedValueReceived);assertTrue(proof.tbtValueReceived)
        assertFalse(proof.allVerified)
    }
    @Test fun allRequiredCallbacksAreNecessaryForPass() {
        val proof=RuntimeVerification.from(listOf(
            ApiObservation("TURN_SIGNAL","VALUE",mapOf("state" to "RIGHT","operation" to "GET")),
            ApiObservation("TURN_SIGNAL","VALUE",mapOf("state" to "RIGHT","operation" to "SUBSCRIPTION")),
            ApiObservation("SPEED","VALUE",mapOf("metersPerSecond" to "3.0")),
            ApiObservation("NAVI_TBT","VALUE",mapOf("count" to "1","distanceMeters" to "20.0")),
            ApiObservation("TTS","PLAYBACK_STARTED"),ApiObservation("TTS","PLAYBACK_DONE")))
        assertTrue(proof.allVerified)
    }
}
