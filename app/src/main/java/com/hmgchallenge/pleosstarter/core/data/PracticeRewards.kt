package com.hmgchallenge.pleosstarter.core.data

/** Earn once per confirmed occurrence; authored demo rewards never certify driving skill. */
object PracticeRewards {
    fun points(skill: String): Int = when (skill) {
        "LANE_CHANGE" -> 200
        "INTERSECTION", "PARKING" -> 100
        else -> 0
    }
}
