package com.pion.psremote.domain.script

import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.ScriptViolation
import com.pion.psremote.domain.model.TutorialStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScriptValidatorTest {
    private val step = TutorialStep(1, 34_000, listOf(ControllerButton.CROSS), InputMode.SEQUENCE, 0.25, 3_000)

    @Test
    fun `sequence zero is rejected`() {
        assertEquals(listOf(ScriptViolation.SequenceBelowOne(0)), validate(step.copy(sequence = 0)))
    }

    @Test
    fun `a negative sequence is rejected`() {
        assertEquals(listOf(ScriptViolation.SequenceBelowOne(-3)), validate(step.copy(sequence = -3)))
    }

    @Test
    fun `speed exactly one is accepted`() {
        assertTrue(validate(step.copy(playbackSpeed = 1.0)).isEmpty())
    }

    /**
     * `stopPositionMs` rounds a Double, and `roundToLong` throws on NaN: a NaN that reached the timeline rules would
     * escape `LoadDemoUseCase` as an exception instead of reaching the BA as a violation.
     */
    @Test
    fun `a NaN or infinite speed is out of range and never reaches the stop arithmetic`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { speed ->
            assertEquals(listOf(ScriptViolation.SpeedOutOfRange(1, speed)), validate(step.copy(playbackSpeed = speed)))
        }
    }

    @Test
    fun `every rule one step breaks is reported, not only the first`() {
        val broken = TutorialStep(-1, -5, emptyList(), InputMode.SEQUENCE, 0.0, -10)

        assertEquals(
            listOf(
                ScriptViolation.SequenceBelowOne(-1),
                ScriptViolation.NegativeTriggerTime(-1, -5),
                ScriptViolation.EmptyTargets(-1),
                ScriptViolation.NegativeSlowDuration(-1, -10),
                ScriptViolation.ZeroSpeedNeedsZeroDuration(-1, -10),
            ),
            validate(broken),
        )
    }

    @Test
    fun `six presses of one button together break both simultaneous rules`() {
        val crowded = step.copy(targets = List(6) { ControllerButton.CROSS }, inputMode = InputMode.SIMULTANEOUS)

        assertEquals(
            listOf(
                ScriptViolation.TooManySimultaneousButtons(1, 6, DemoScriptValidator.MAX_SIMULTANEOUS_BUTTONS),
                ScriptViolation.RepeatedSimultaneousButton(1, ControllerButton.CROSS),
            ),
            validate(crowded),
        )
    }

    /** A file the duration read could open but whose length is 0: no stop point can be before its end. */
    @Test
    fun `a video with no length rejects every step`() {
        val parsed = ParsedScript(listOf(step, step.copy(sequence = 2, triggerTimeMs = 40_000)), emptyList())

        assertEquals(
            listOf(
                ScriptViolation.StopNotBeforeVideoEnd(1, 34_750, 0),
                ScriptViolation.StopNotBeforeVideoEnd(2, 40_750, 0),
            ),
            DemoScriptValidator.validate(parsed, 0),
        )
    }

    @Test
    fun `negative trigger is rejected`() {
        assertEquals(listOf(ScriptViolation.NegativeTriggerTime(1, -1)), validate(step.copy(triggerTimeMs = -1)))
    }

    @Test
    fun `empty targets are rejected`() {
        assertEquals(listOf(ScriptViolation.EmptyTargets(1)), validate(step.copy(targets = emptyList())))
    }

    @Test
    fun `speed above one is rejected`() {
        assertEquals(listOf(ScriptViolation.SpeedOutOfRange(1, 1.5)), validate(step.copy(playbackSpeed = 1.5)))
    }

    @Test
    fun `speed below the 0_1 floor is rejected`() {
        assertEquals(listOf(ScriptViolation.SpeedOutOfRange(1, 0.05)), validate(step.copy(playbackSpeed = 0.05)))
    }

    @Test
    fun `speed exactly at the 0_1 floor is accepted`() {
        assertTrue(validate(step.copy(playbackSpeed = 0.1)).isEmpty())
    }

    @Test
    fun `a trigger far past the end is reported instead of overflowing past every check`() {
        val far = step.copy(triggerTimeMs = Long.MAX_VALUE - 1_000, playbackSpeed = 1.0, slowDurationMs = 1_000)

        assertEquals(listOf(ScriptViolation.StopNotBeforeVideoEnd(1, Long.MAX_VALUE, 70_000)), validate(far))
    }

    @Test
    fun `negative speed is rejected`() {
        assertEquals(listOf(ScriptViolation.SpeedOutOfRange(1, -0.1)), validate(step.copy(playbackSpeed = -0.1)))
    }

    @Test
    fun `negative slow duration is rejected`() {
        assertEquals(listOf(ScriptViolation.NegativeSlowDuration(1, -5)), validate(step.copy(slowDurationMs = -5)))
    }

    @Test
    fun `zero speed requires zero slow duration`() {
        assertEquals(
            listOf(ScriptViolation.ZeroSpeedNeedsZeroDuration(1, 100)),
            validate(step.copy(playbackSpeed = 0.0, slowDurationMs = 100)),
        )
    }

    @Test
    fun `six simultaneous buttons exceed the maximum of five`() {
        val simultaneous = step.copy(inputMode = InputMode.SIMULTANEOUS, targets = ControllerButton.entries.take(6))

        assertEquals(listOf(ScriptViolation.TooManySimultaneousButtons(1, 6, 5)), validate(simultaneous))
    }

    @Test
    fun `repeated simultaneous button is rejected`() {
        val simultaneous = step.copy(inputMode = InputMode.SIMULTANEOUS, targets = List(2) { ControllerButton.CROSS })

        assertEquals(listOf(ScriptViolation.RepeatedSimultaneousButton(1, ControllerButton.CROSS)), validate(simultaneous))
    }

    @Test
    fun `exactly five simultaneous buttons are accepted`() {
        assertTrue(validate(step.copy(inputMode = InputMode.SIMULTANEOUS, targets = ControllerButton.entries.take(5))).isEmpty())
    }

    @Test
    fun `sequence permits repeated buttons`() {
        assertTrue(validate(step.copy(targets = List(2) { ControllerButton.CROSS })).isEmpty())
    }

    @Test
    fun `any order permits repeated buttons`() {
        assertTrue(validate(step.copy(inputMode = InputMode.ANY_ORDER, targets = List(2) { ControllerButton.CROSS })).isEmpty())
    }

    @Test
    fun `duplicate sequences are reported`() {
        assertEquals(listOf(ScriptViolation.DuplicateSequence(1)), validate(step, step.copy(triggerTimeMs = 60_000)))
    }

    @Test
    fun `trigger equal to previous stop is rejected in sequence order`() {
        val next = step.copy(sequence = 2, triggerTimeMs = 34_750)

        assertEquals(listOf(ScriptViolation.TriggerNotAfterPreviousStop(2, 34_750, 1, 34_750)), validate(next, step))
    }

    @Test
    fun `trigger one millisecond after previous stop is accepted in sequence order`() {
        val next = step.copy(sequence = 2, triggerTimeMs = 34_751)

        assertTrue(validate(next, step).isEmpty())
    }

    @Test
    fun `stop equal to video duration is rejected`() {
        assertEquals(
            listOf(ScriptViolation.StopNotBeforeVideoEnd(1, 70_000, 70_000)),
            validate(step.copy(triggerTimeMs = 69_250)),
        )
    }

    @Test
    fun `stop one millisecond before video end is accepted`() {
        assertTrue(validate(step.copy(triggerTimeMs = 69_249)).isEmpty())
    }

    @Test
    fun `speed violation suppresses overlap and video end violations`() {
        val invalid = step.copy(triggerTimeMs = 69_000, playbackSpeed = 1.5)
        val overlapping = step.copy(sequence = 2, triggerTimeMs = 69_001)

        assertEquals(listOf(ScriptViolation.SpeedOutOfRange(1, 1.5)), validate(invalid, overlapping))
    }

    @Test
    fun `parser violations are preserved and suppress timeline rules`() {
        val violation = ScriptViolation.MissingField(2, "triggerTimeMs")
        val parsed = ParsedScript(listOf(step, step.copy(sequence = 2)), listOf(violation))

        assertEquals(listOf(violation), DemoScriptValidator.validate(parsed, 34_000))
    }

    @Test
    fun `parser violations precede per step and duplicate violations`() {
        val violation = ScriptViolation.StepNotAnObject(2)
        val parsed = ParsedScript(listOf(step.copy(targets = emptyList()), step), listOf(violation))

        assertEquals(
            listOf(violation, ScriptViolation.EmptyTargets(1), ScriptViolation.DuplicateSequence(1)),
            DemoScriptValidator.validate(parsed, 70_000),
        )
    }

    private fun validate(vararg steps: TutorialStep) =
        DemoScriptValidator.validate(ParsedScript(steps.toList(), emptyList()), 70_000)
}
