package com.hmgchallenge.pleosstarter.driving

import com.hmgchallenge.pleosstarter.session.ReplayCatalog
import com.hmgchallenge.pleosstarter.core.data.PracticeRewards
import org.junit.Assert.*
import org.junit.Test

class ReplayCatalogTest {
    @Test fun fourFixturesHaveIndependentExpectedOutcomesAndNoUnsupportedAwards() {
        assertEquals(RoadLesson.values().toSet(),ReplayCatalog.all.map {it.lesson}.toSet())
        assertEquals(4,ReplayCatalog.all.map {it.id}.toSet().size)
        assertEquals(800,ReplayCatalog.all.sumOf {it.rewardSkills.sumOf(PracticeRewards::points)})
        assertEquals(0,PracticeRewards.points("SPEED"));assertEquals(0,PracticeRewards.points("PREPARATION"))
    }
}
