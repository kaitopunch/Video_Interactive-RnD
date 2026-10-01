package com.pion.psremote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialStepTest {
    @Test
    fun `README example stops after 750 milliseconds of video`() {
        val step = step(speed = 0.25, slowDurationMs = 3_000)

        assertEquals(34_750L, step.stopPositionMs)
        assertFalse(step.stopsImmediately)
    }

    @Test
    fun `zero speed and duration stop immediately`() {
        val step = step(speed = 0.0, slowDurationMs = 0)

        assertEquals(step.triggerTimeMs, step.stopPositionMs)
        assertTrue(step.stopsImmediately)
    }

    @Test
    fun `zero duration stops immediately at a nonzero speed`() {
        val step = step(speed = 0.5, slowDurationMs = 0)

        assertEquals(step.triggerTimeMs, step.stopPositionMs)
        assertTrue(step.stopsImmediately)
    }

    @Test
    fun `fractional video duration rounds to the nearest millisecond`() {
        val step = step(speed = 0.3, slowDurationMs = 1_001)

        assertEquals(step.triggerTimeMs + 300, step.stopPositionMs)
    }

    @Test
    fun `huge values saturate instead of wrapping to a negative stop`() {
        val huge = step(1.0, Long.MAX_VALUE - 10).copy(triggerTimeMs = Long.MAX_VALUE - 10)

        assertEquals(Long.MAX_VALUE, huge.stopPositionMs)
    }

    private fun step(speed: Double, slowDurationMs: Long) = TutorialStep(
        sequence = 1,
        triggerTimeMs = 34_000,
        targets = listOf(ControllerButton.CROSS),
        inputMode = InputMode.SEQUENCE,
        playbackSpeed = speed,
        slowDurationMs = slowDurationMs,
    )
}
