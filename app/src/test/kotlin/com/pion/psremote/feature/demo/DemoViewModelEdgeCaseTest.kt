package com.pion.psremote.feature.demo

import app.cash.turbine.test
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.ControllerButton.CROSS
import com.pion.psremote.domain.model.ControllerButton.DPAD_UP
import com.pion.psremote.domain.model.ControllerButton.L1
import com.pion.psremote.domain.model.ControllerButton.R1
import com.pion.psremote.domain.playback.PlaybackEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Orders of events a phone produces that the main suites do not walk through one by one. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoViewModelEdgeCaseTest : DemoViewModelTestFixture() {

    @Test
    fun `the whole script plays through to the finished panel`() = runTest(dispatcher) {
        startPlaying()

        playback.emit(PlaybackEvent.CueReached(34_000))
        listOf(DPAD_UP, CROSS, R1).forEach(::press)
        assertNull(state.tutorial)
        assertEquals(60_000L, playback.pendingCueMs)
        assertTrue(playback.isPlaying)

        playback.emit(PlaybackEvent.CueReached(60_000))
        assertTrue(requireNotNull(state.tutorial).isWaiting)
        assertFalse(playback.isPlaying)
        press(CROSS)
        assertNull(state.tutorial)
        assertNull("no step left to arm", playback.pendingCueMs)
        assertTrue(playback.isPlaying)

        playback.emit(PlaybackEvent.Ended)
        assertEquals(DemoPhase.Finished, state.phase)
        assertFalse(playback.isPlaying)
    }

    @Test
    fun `a second Prepared does not restart the run`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        press(DPAD_UP)
        val tutorial = state.tutorial
        playback.calls.clear()

        playback.emit(PlaybackEvent.Prepared)

        assertEquals(tutorial, state.tutorial)
        assertFalse(playback.calls.any { it.startsWith("seekTo") })
    }

    @Test
    fun `a playback failure while a step waits clears it and pauses`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        playback.emit(PlaybackEvent.CueReached(34_750))

        playback.emit(PlaybackEvent.Failed("ERROR_CODE_DECODING_FAILED"))

        assertEquals(DemoPhase.Failed(AppError.Playback("ERROR_CODE_DECODING_FAILED")), state.phase)
        assertNull(state.tutorial)
        assertNull(playback.pendingCueMs)
        assertFalse(playback.isPlaying)
        assertTrue(state.enabledButtons.isEmpty())
    }

    @Test
    fun `a failure before the video is prepared is not undone by a late Prepared`() = runTest(dispatcher) {
        vm.onIntent(DemoIntent.ScreenStarted)
        playback.emit(PlaybackEvent.Failed("ERROR_CODE_IO_FILE_NOT_FOUND"))

        playback.emit(PlaybackEvent.Prepared)

        assertTrue(state.phase is DemoPhase.Failed)
        assertFalse(playback.isPlaying)
    }

    @Test
    fun `the video ending mid slow phase, then replay, plays at normal speed with sound`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        advanceTimeBy(100)
        runCurrent()
        assertTrue(playback.speed < 1f)
        assertTrue(playback.muted)

        playback.emit(PlaybackEvent.Ended)
        vm.onIntent(DemoIntent.ReplayClicked)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(DemoPhase.Playing, state.phase)
        assertEquals(1f, playback.speed)
        assertFalse(playback.muted)
        assertEquals(0L, playback.lastSeekMs)
        assertEquals(34_000L, playback.pendingCueMs)
        assertTrue(playback.isPlaying)
    }

    @Test
    fun `going to the background mid ramp keeps the stop and comes back into the slow phase`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        advanceTimeBy(60)
        runCurrent()

        vm.onIntent(DemoIntent.ScreenStopped)
        assertFalse(playback.isPlaying)
        assertEquals(34_750L, playback.pendingCueMs)

        vm.onIntent(DemoIntent.ScreenStarted)
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(playback.isPlaying)
        assertEquals(34_750L, playback.pendingCueMs)
        assertEquals(0.25f, playback.speed)
        assertFalse(requireNotNull(state.tutorial).isWaiting)
    }

    @Test
    fun `a combination completes on the press that makes it whole, after the video stopped`() = runTest(dispatcher) {
        repository.result = AppResult.Success(source(SIMULTANEOUS_SCRIPT))
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        press(L1)
        release(L1)
        playback.emit(PlaybackEvent.CueReached(34_750))
        press(R1)
        assertTrue("one held button is not the combination", requireNotNull(state.tutorial).isWaiting)

        press(L1)

        assertNull(state.tutorial)
        assertTrue(playback.isPlaying)
    }

    @Test
    fun `stopping twice still counts down once from five`() = runTest(dispatcher) {
        startPlaying()
        vm.onIntent(DemoIntent.ScreenStopped)
        vm.onIntent(DemoIntent.ScreenStopped)

        vm.onIntent(DemoIntent.ScreenStarted)
        assertEquals(5, state.resumeCountdown)
        advanceTimeBy(5_000)
        runCurrent()

        assertNull(state.resumeCountdown)
        assertTrue(playback.isPlaying)
    }

    @Test
    fun `presses before anything is on screen change nothing`() = runTest(dispatcher) {
        press(CROSS)
        release(CROSS)
        vm.onIntent(DemoIntent.ReplayClicked)

        assertEquals(DemoState(), state)
        assertTrue(playback.calls.isEmpty())
    }

    @Test
    fun `an exit raised with no collector is delivered once, when one arrives`() = runTest(dispatcher) {
        vm.onIntent(DemoIntent.ExitClicked)

        vm.effects.test {
            assertEquals(DemoEffect.Exit, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `exit works from every phase`() = runTest(dispatcher) {
        val phases = mutableListOf<DemoPhase>()
        vm.effects.test {
            phases += state.phase
            vm.onIntent(DemoIntent.ExitClicked)
            assertEquals(DemoEffect.Exit, awaitItem())

            startPlaying()
            phases += state.phase
            vm.onIntent(DemoIntent.ExitClicked)
            assertEquals(DemoEffect.Exit, awaitItem())

            playback.emit(PlaybackEvent.Ended)
            phases += state.phase
            vm.onIntent(DemoIntent.ExitClicked)
            assertEquals(DemoEffect.Exit, awaitItem())

            playback.emit(PlaybackEvent.Failed(null))
            phases += state.phase
            vm.onIntent(DemoIntent.ExitClicked)
            assertEquals(DemoEffect.Exit, awaitItem())
            expectNoEvents()
        }
        assertEquals(
            listOf(DemoPhase.Loading, DemoPhase.Playing, DemoPhase.Finished, DemoPhase.Failed(AppError.Playback(null))),
            phases,
        )
    }
}
