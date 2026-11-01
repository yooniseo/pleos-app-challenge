package com.hmgchallenge.pleosstarter

import kotlin.math.abs

/** AAOS speed is signed m/s; preserve missing values, display reverse motion as positive. */
internal fun speedKmh(metersPerSecond: Float?): Float? = metersPerSecond
    ?.takeIf { it.isFinite() }
    ?.let { abs(it) * 3.6f }
    ?.takeIf { it.isFinite() }
