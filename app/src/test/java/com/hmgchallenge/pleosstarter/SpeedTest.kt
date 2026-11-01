package com.hmgchallenge.pleosstarter

import org.junit.Assert.*
import org.junit.Test

class SpeedTest {
    @Test fun convertsSensorUnitsAndReverseMotion() {
        assertEquals(36f, speedKmh(10f)!!, 0.001f)
        assertEquals(18f, speedKmh(-5f)!!, 0.001f)
        assertEquals(0f, speedKmh(0f)!!, 0.001f)
    }
    @Test fun missingOrInvalidDataIsNeverPresentedAsStopped() {
        assertNull(speedKmh(null))
        assertNull(speedKmh(Float.NaN))
        assertNull(speedKmh(Float.POSITIVE_INFINITY))
        assertNull(speedKmh(Float.MAX_VALUE))
    }
}
