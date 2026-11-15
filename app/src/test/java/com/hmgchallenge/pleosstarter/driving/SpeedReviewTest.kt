package com.hmgchallenge.pleosstarter.driving

import com.hmgchallenge.pleosstarter.core.data.SpeedReview
import org.junit.Assert.*
import org.junit.Test

class SpeedReviewTest {
    @Test fun flagsOnlyAdjacentValidChangesAndKeepsTheirTime() {
        val result=SpeedReview.analyze(listOf(SpeedReview.Sample(1000,0.0),SpeedReview.Sample(2000,15.0),SpeedReview.Sample(3000,0.0)))
        assertEquals(listOf("HARSH_START","HARSH_BRAKE"),result.map {it.type})
        assertEquals(2000L,result.first().at)
    }
    @Test fun missingValuesAndLongGapsDoNotFabricateBraking() {
        val samples=listOf(SpeedReview.Sample(1000,40.0),SpeedReview.Sample(2000,null),SpeedReview.Sample(3000,0.0),SpeedReview.Sample(9000,40.0),SpeedReview.Sample(10000,Double.NaN))
        assertTrue(SpeedReview.analyze(samples).isEmpty())
    }
}
