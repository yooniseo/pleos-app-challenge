package com.hmgchallenge.pleosstarter.driving

import org.junit.Assert.*
import org.junit.Test

class SteeringFollowPracticeTest {
    @Test fun directionOnlyUpToHalfTurnAndLargerAmountsStayConversational() {
        assertEquals("핸들을 오른쪽으로 꺾으세요",BeginnerScenarioCoach.steeringAction(90))
        assertEquals("핸들을 왼쪽으로 꺾으세요",BeginnerScenarioCoach.steeringAction(-180))
        assertTrue(BeginnerScenarioCoach.steeringAction(360).contains("한 바퀴"))
    }
    @Test fun needsRealInputCorrectDirectionAndStableMatchAndRewardsOnlyOnce() {
        val p=SteeringFollowPractice(90.0)
        assertFalse(p.check(1000));p.input(-90.0,1100);assertFalse(p.check(2000))
        p.input(90.0,2100);assertFalse(p.check(2400));assertTrue(p.check(2450))
        assertFalse(p.check(3000));p.input(90.0,3100);assertFalse(p.check(4000))
    }
    @Test fun driftingOutOfTargetResetsTheHoldAndNonFiniteInputCannotReward() {
        val p=SteeringFollowPractice(360.0)
        p.input(Double.NaN,100);p.input(Double.POSITIVE_INFINITY,200);assertFalse(p.check(1000))
        p.input(360.0,1100);p.input(280.0,1300);assertFalse(p.check(2000))
        p.input(355.0,2100);assertFalse(p.check(2400));assertTrue(p.check(2500))
    }
}
