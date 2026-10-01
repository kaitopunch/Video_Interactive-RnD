package com.pion.psremote.feature.demo

import app.cash.turbine.test
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.ControllerButton.*
import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.ScriptViolation
import com.pion.psremote.domain.model.TutorialStep
import com.pion.psremote.domain.playback.PlaybackEvent
import com.pion.psremote.domain.playback.SlowMotionRamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoViewModelTest : DemoViewModelTestFixture() {
    @Test
    fun `screen start loads and prepares only once while remaining Loading`() = runTest(dispatcher) {
        vm.onIntent(DemoIntent.ScreenStarted)

        assertEquals(1, repository.calls)
        assertEquals(source().videoUri, playback.preparedUri)
        assertEquals(DemoPhase.Loading, state.phase)
        assertFalse(playback.isPlaying)
        vm.onIntent(DemoIntent.ScreenStarted)
        assertEquals(1, repository.calls)
        assertEquals(1, playback.calls.count { it.startsWith("prepare:") })
        assertEquals(DemoPhase.Loading, state.phase)
    }

    @Test
    fun `prepared starts normal unmuted playback from zero with the first cue`() = runTest(dispatcher) {
        vm.onIntent(DemoIntent.ScreenStarted)
        playback.calls.clear()

        playback.emit(PlaybackEvent.Prepared)

        assertEquals(DemoPhase.Playing, state.phase)
        assertEquals(0L, playback.lastSeekMs)
        assertEquals(1f, playback.speed, 0f)
        assertFalse(playback.muted)
        assertEquals(34_000L, playback.pendingCueMs)
        assertTrue(playback.isPlaying)
        assertTrue(playback.calls.indexOf("seekTo:0") < playback.calls.indexOf("scheduleCue:34000"))
    }

    @Test
    fun `trigger shows the step, mutes, and eases down to the slow speed before its stop cue`() = runTest(dispatcher) {
        startPlaying()

        playback.emit(PlaybackEvent.CueReached(34_000))

        val tutorial = requireNotNull(state.tutorial)
        assertEquals(TutorialStep(1, 34_000, listOf(DPAD_UP, CROSS, R1), InputMode.SEQUENCE, 0.25, 3_000), tutorial.step)
        assertFalse(tutorial.isWaiting)
        assertEquals(1f, playback.speed, 0f)
        assertTrue(playback.muted)
        assertEquals(34_750L, playback.pendingCueMs)
        assertTrue(playback.isPlaying)
        advanceTimeBy(RAMP_MILLIS)
        runCurrent()
        assertEquals(0.25f, playback.speed, 0f)
    }

    @Test
    fun `the slow-down is applied tick by tick and only ever brakes`() = runTest(dispatcher) {
        startPlaying()
        playback.calls.clear()

        playback.emit(PlaybackEvent.CueReached(34_000))
        advanceTimeBy(RAMP_MILLIS)
        runCurrent()

        val speeds = playback.calls.filter { it.startsWith("setSpeed:") }.map { it.removePrefix("setSpeed:").toFloat() }
        assertEquals(SlowMotionRamp.speeds(0.25f), speeds)
        assertTrue(speeds.zipWithNext().all { (before, after) -> after < before })
    }

    @Test
    fun `completing mid ramp stays at normal speed after the ramp would have ended`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        advanceTimeBy(SlowMotionRamp.TICK_MILLIS * 3)
        runCurrent()
        assertTrue(playback.speed < 1f)

        press(DPAD_UP)
        press(CROSS)
        press(R1)
        advanceTimeBy(RAMP_MILLIS)
        runCurrent()

        assertNull(state.tutorial)
        assertEquals(1f, playback.speed, 0f)
        assertTrue(playback.isPlaying)
    }

    @Test
    fun `a sequence lights every button still to come, numbered, with only the next one pressable`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))

        assertEquals(
            mapOf(
                DPAD_UP to ButtonHighlight(isActive = true, badge = ButtonBadge.Position(1)),
                CROSS to ButtonHighlight(isActive = false, badge = ButtonBadge.Position(2)),
                R1 to ButtonHighlight(isActive = false, badge = ButtonBadge.Position(3)),
            ),
            state.highlights,
        )
        assertEquals(setOf(DPAD_UP), state.enabledButtons)

        press(DPAD_UP)

        assertEquals(setOf(CROSS, R1), state.highlights.keys)
        assertEquals(ButtonHighlight(isActive = true, badge = ButtonBadge.Position(2)), state.highlights[CROSS])
        assertEquals(setOf(CROSS), state.enabledButtons)
    }

    @Test
    fun `a single button step and a simultaneous step are lit without numbers`() = runTest(dispatcher) {
        repository.result = AppResult.Success(source(SIMULTANEOUS_SCRIPT))
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))

        assertEquals(
            mapOf(L1 to ButtonHighlight(isActive = true, badge = null), R1 to ButtonHighlight(isActive = true, badge = null)),
            state.highlights,
        )
    }

    @Test
    fun `an any order button owed more than once shows how many presses are left`() = runTest(dispatcher) {
        repository.result = AppResult.Success(source(ANY_ORDER_SCRIPT))
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))

        assertEquals(
            mapOf(
                CROSS to ButtonHighlight(isActive = true, badge = ButtonBadge.Presses(2)),
                R1 to ButtonHighlight(isActive = true, badge = null),
            ),
            state.highlights,
        )

        press(CROSS)
        assertEquals(ButtonHighlight(isActive = true, badge = null), state.highlights[CROSS])
        press(R1)
        assertEquals(setOf(CROSS), state.highlights.keys)
    }

    @Test
    fun `no tutorial means nothing is lit`() = runTest(dispatcher) {
        startPlaying()

        assertTrue(state.highlights.isEmpty())
    }

    @Test
    fun `stop cue without input pauses and waits for input`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))

        playback.emit(PlaybackEvent.CueReached(34_750))

        val tutorial = requireNotNull(state.tutorial)
        assertTrue(tutorial.isWaiting)
        assertFalse(tutorial.progress.isDone(0))
        assertFalse(playback.isPlaying)
    }

    @Test
    fun `stop cue preserves progress already made during the slow phase`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        press(DPAD_UP)
        val progress = requireNotNull(state.tutorial).progress

        playback.emit(PlaybackEvent.CueReached(34_750))

        assertEquals(progress, requireNotNull(state.tutorial).progress)
        assertTrue(requireNotNull(state.tutorial).isWaiting)
        assertFalse(playback.isPlaying)
    }

    @Test
    fun `completing during slow playback cancels stop and arms next trigger without pausing`() = runTest(dispatcher) {
        startPlaying()
        playback.calls.clear()
        playback.emit(PlaybackEvent.CueReached(34_000))
        val cancellations = playback.cancelCueCount

        press(DPAD_UP)
        press(CROSS)
        press(R1)

        assertEquals(cancellations + 1, playback.cancelCueCount)
        assertNull(state.tutorial)
        assertEquals(1f, playback.speed, 0f)
        assertFalse(playback.muted)
        assertEquals(60_000L, playback.pendingCueMs)
        assertTrue(playback.isPlaying)
        assertFalse(playback.calls.contains("pause"))
        assertFalse(playback.calls.any { it.startsWith("seekTo:") })
    }

    @Test
    fun `completing while waiting resumes without seeking`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        playback.emit(PlaybackEvent.CueReached(34_750))
        playback.calls.clear()

        press(DPAD_UP)
        press(CROSS)
        press(R1)

        assertNull(state.tutorial)
        assertTrue(playback.isPlaying)
        assertEquals(1f, playback.speed, 0f)
        assertFalse(playback.muted)
        assertFalse(playback.calls.any { it.startsWith("seekTo:") })
    }

    @Test
    fun `unexpected cue before a tutorial changes nothing`() = runTest(dispatcher) {
        startPlaying()
        val before = state
        playback.calls.clear()

        playback.emit(PlaybackEvent.CueReached(34_001))

        assertEquals(before, state)
        assertEquals(34_000L, playback.pendingCueMs)
        assertTrue(playback.calls.isEmpty())
    }

    @Test
    fun `unexpected cue during a tutorial changes nothing`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        val before = state
        playback.calls.clear()

        playback.emit(PlaybackEvent.CueReached(34_751))

        assertEquals(before, state)
        assertEquals(34_750L, playback.pendingCueMs)
        assertTrue(playback.calls.isEmpty())
    }

    @Test
    fun `press outside a tutorial changes nothing`() = runTest(dispatcher) {
        startPlaying()
        val before = state
        playback.calls.clear()

        press(CROSS)

        assertEquals(before, state)
        assertTrue(playback.calls.isEmpty())
    }

    @Test
    fun `wrong tutorial press changes nothing`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        press(DPAD_UP)
        val before = state
        playback.calls.clear()

        press(R1)

        assertEquals(before, state)
        assertTrue(playback.calls.isEmpty())
    }

    @Test
    fun `zero speed step waits immediately without changing speed or scheduling a stop`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        press(DPAD_UP)
        press(CROSS)
        press(R1)
        playback.calls.clear()

        playback.emit(PlaybackEvent.CueReached(60_000))
        advanceTimeBy(RAMP_MILLIS)
        runCurrent()

        assertEquals(2, requireNotNull(state.tutorial).step.sequence)
        assertEquals(ButtonHighlight(isActive = true, badge = null), state.highlights[CROSS])
        assertTrue(requireNotNull(state.tutorial).isWaiting)
        assertFalse(playback.isPlaying)
        assertEquals(1f, playback.speed, 0f)
        assertNull(playback.pendingCueMs)
        assertFalse(playback.calls.any { it.startsWith("setSpeed:") || it.startsWith("scheduleCue:") })
    }

    @Test
    fun `simultaneous step completes only with overlapping presses`() = runTest(dispatcher) {
        repository.result = AppResult.Success(source(SIMULTANEOUS_SCRIPT))
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))

        press(L1)
        release(L1)
        press(R1)
        assertFalse(requireNotNull(state.tutorial).progress.isComplete)
        press(L1)

        assertNull(state.tutorial)
        assertTrue(playback.isPlaying)
        assertNull(playback.pendingCueMs)
    }

    @Test
    fun `exit emits exactly one exit effect`() = runTest(dispatcher) {
        vm.effects.test {
            vm.onIntent(DemoIntent.ExitClicked)
            assertEquals(DemoEffect.Exit, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `invalid script exposes violations without preparing video`() = runTest(dispatcher) {
        repository.result = AppResult.Success(source("[42]"))

        vm.onIntent(DemoIntent.ScreenStarted)

        assertEquals(DemoPhase.InvalidScript(listOf(ScriptViolation.StepNotAnObject(0))), state.phase)
        assertNull(playback.preparedUri)
        assertFalse(playback.calls.any { it.startsWith("prepare:") })
    }

    @Test
    fun `repository not found failure becomes Failed`() = runTest(dispatcher) {
        val error = AppError.NotFound("script.json")
        repository.result = AppResult.Failure(error)

        vm.onIntent(DemoIntent.ScreenStarted)

        assertEquals(DemoPhase.Failed(error), state.phase)
        assertFalse(playback.isPlaying)
    }

    @Test
    fun `throwing repository becomes Unexpected without escaping`() = runTest(dispatcher) {
        repository.throwOnLoad = IllegalStateException("load failed")

        vm.onIntent(DemoIntent.ScreenStarted)

        assertEquals(DemoPhase.Failed(AppError.Unexpected("load failed")), state.phase)
        assertEquals(1, repository.calls)
        assertFalse(playback.isPlaying)
    }

    @Test
    fun `playback failure reports its reason and pauses`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))

        playback.emit(PlaybackEvent.Failed("ERROR_CODE_IO"))

        assertEquals(DemoPhase.Failed(AppError.Playback("ERROR_CODE_IO")), state.phase)
        assertNull(state.tutorial)
        assertNull(playback.pendingCueMs)
        assertFalse(playback.isPlaying)
    }

    private companion object {
        /** Past the last tick of the slow-down. */
        val RAMP_MILLIS = SlowMotionRamp.TICK_MILLIS * SlowMotionRamp.speeds(0.25f).size
        const val ANY_ORDER_SCRIPT = """[
            {"step_sequence":1,"triggerTimeMs":34000,"targetButtonIds":["CROSS","R1","CROSS"],
             "inputMode":"ANY_ORDER","playbackSpeed":0.25,"slowDurationMs":3000}
        ]"""
    }
}
