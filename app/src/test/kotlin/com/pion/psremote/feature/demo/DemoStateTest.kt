package com.pion.psremote.feature.demo

import com.pion.psremote.core.common.AppError
import com.pion.psremote.domain.input.StepProgress
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.ControllerButton.CROSS
import com.pion.psremote.domain.model.ControllerButton.R1
import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.ScriptViolation
import com.pion.psremote.domain.model.TutorialStep
import com.pion.psremote.domain.score.Score
import com.pion.psremote.domain.score.ScoreTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the screen derives from one state, for every phase — including combinations the ViewModel never builds today,
 * because these properties are the last line that keeps touches off a panel or an error screen.
 */
class DemoStateTest {
    private val step = TutorialStep(1, 34_000, listOf(CROSS, R1), InputMode.SEQUENCE, 0.25, 3_000)
    private val tutorial = ActiveTutorial(step, StepProgress.start(step), isWaiting = false)
    private val everyButton = ControllerButton.entries.toSet()

    private val phases = listOf(
        DemoPhase.Loading,
        DemoPhase.Playing,
        DemoPhase.Finished,
        DemoPhase.InvalidScript(listOf(ScriptViolation.EmptyTargets(1))),
        DemoPhase.Failed(AppError.Network("timeout")),
    )

    @Test
    fun `only a running demo takes touches`() {
        phases.forEach { phase ->
            val expected = if (phase == DemoPhase.Playing) everyButton else emptySet()
            assertEquals("$phase", expected, DemoState(phase = phase).enabledButtons)
        }
    }

    @Test
    fun `a tutorial lets through only its next button, and the countdown lets through none`() {
        val showing = DemoState(phase = DemoPhase.Playing, tutorial = tutorial)

        assertEquals(setOf(CROSS), showing.enabledButtons)
        assertEquals(emptySet<ControllerButton>(), showing.copy(resumeCountdown = 1).enabledButtons)
        assertEquals(emptySet<ControllerButton>(), DemoState(phase = DemoPhase.Playing, resumeCountdown = 5).enabledButtons)
    }

    @Test
    fun `a tutorial left over outside play takes no touches`() {
        phases.filter { it != DemoPhase.Playing }.forEach { phase ->
            assertEquals("$phase", emptySet<ControllerButton>(), DemoState(phase = phase, tutorial = tutorial).enabledButtons)
        }
    }

    @Test
    fun `each phase shows the controls it needs and no others`() {
        // phase → controller, score, exit button
        val expected = mapOf(
            DemoPhase.Loading to Triple(false, false, true),
            DemoPhase.Playing to Triple(true, true, true),
            DemoPhase.Finished to Triple(true, false, false),
            phases[3] to Triple(false, false, false),
            phases[4] to Triple(false, false, false),
        )

        expected.forEach { (phase, visible) ->
            val state = DemoState(phase = phase)
            assertEquals("$phase", visible, Triple(state.isControllerVisible, state.isScoreVisible, state.isExitButtonVisible))
        }
    }

    @Test
    fun `the last award shows between steps only, and nothing shows before the first`() {
        val scored = Score(awards = listOf(ScoreTier.PERFECT, ScoreTier.GOOD), stepCount = 3)

        assertNull(DemoState(phase = DemoPhase.Playing).lastAward)
        assertEquals(ScoreTier.GOOD, DemoState(phase = DemoPhase.Playing, score = scored).lastAward)
        assertNull(DemoState(phase = DemoPhase.Playing, score = scored, tutorial = tutorial).lastAward)
    }

    @Test
    fun `nothing is lit without a tutorial, whatever the phase`() {
        phases.forEach { phase -> assertEquals("$phase", emptyMap<ControllerButton, ButtonHighlight>(), DemoState(phase).highlights) }
    }
}
