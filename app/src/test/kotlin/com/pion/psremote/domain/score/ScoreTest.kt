package com.pion.psremote.domain.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreTest {
    @Test
    fun `a fresh score is zero out of one hundred per step`() {
        val score = Score(stepCount = 3)

        assertEquals(0, score.points)
        assertEquals(300, score.maxPoints)
        assertTrue(score.awards.isEmpty())
    }

    @Test
    fun `points add up the tiers awarded, and each tier is counted`() {
        val score = Score(stepCount = 3).award(ScoreTier.PERFECT).award(ScoreTier.GOOD).award(ScoreTier.PERFECT)

        assertEquals(250, score.points)
        assertEquals(300, score.maxPoints)
        assertEquals(2, score.count(ScoreTier.PERFECT))
        assertEquals(1, score.count(ScoreTier.GOOD))
    }

    @Test
    fun `an award returns a new score and leaves the old one as it was`() {
        val before = Score(stepCount = 2)

        val after = before.award(ScoreTier.GOOD)

        assertEquals(Score(stepCount = 2), before)
        assertEquals(listOf(ScoreTier.GOOD), after.awards)
    }

    @Test
    fun `a script with no steps has nothing to reach`() {
        assertEquals(0, Score().maxPoints)
    }
}
