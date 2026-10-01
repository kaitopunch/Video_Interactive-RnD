package com.pion.psremote.domain.score

import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.TutorialStep
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreTierTest {
    @Test
    fun `a step done while the video still moves is perfect, in every input mode`() {
        InputMode.entries.forEach { mode ->
            assertEquals(mode.name, ScoreTier.PERFECT, ScoreTier.of(step(mode, speed = 0.25, slowDurationMs = 3_000), false))
        }
    }

    @Test
    fun `a step done after the video stopped and waited is good, in every input mode`() {
        InputMode.entries.forEach { mode ->
            assertEquals(mode.name, ScoreTier.GOOD, ScoreTier.of(step(mode, speed = 0.25, slowDurationMs = 3_000), true))
        }
    }

    /** It waits from its first frame: GOOD here would put its full points out of everyone's reach (rules R3). */
    @Test
    fun `a step that stops at once is perfect even though it was waiting`() {
        assertEquals(ScoreTier.PERFECT, ScoreTier.of(step(InputMode.SEQUENCE, speed = 0.0, slowDurationMs = 0), true))
        assertEquals(ScoreTier.PERFECT, ScoreTier.of(step(InputMode.SIMULTANEOUS, speed = 0.5, slowDurationMs = 0), true))
    }

    @Test
    fun `perfect is worth twice good`() {
        assertEquals(100, ScoreTier.PERFECT.points)
        assertEquals(50, ScoreTier.GOOD.points)
    }

    private fun step(mode: InputMode, speed: Double, slowDurationMs: Long) = TutorialStep(
        sequence = 1,
        triggerTimeMs = 34_000,
        targets = listOf(ControllerButton.L1, ControllerButton.R1),
        inputMode = mode,
        playbackSpeed = speed,
        slowDurationMs = slowDurationMs,
    )
}
