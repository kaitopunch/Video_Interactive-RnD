package com.pion.psremote.feature.demo

import com.pion.psremote.domain.model.ControllerButton.CROSS
import com.pion.psremote.domain.model.ControllerButton.DPAD_UP
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

@OptIn(ExperimentalCoroutinesApi::class)
class DemoViewModelLifecycleTest : DemoViewModelTestFixture() {
    @Test
    fun `screen stop pauses and countdown resumes playback after five seconds`() = runTest(dispatcher) {
        startPlaying()

        vm.onIntent(DemoIntent.ScreenStopped)

        assertFalse(playback.isPlaying)
        assertEquals(5, state.resumeCountdown)
        vm.onIntent(DemoIntent.ScreenStarted)
        for (seconds in 5 downTo 1) {
            assertEquals(seconds, state.resumeCountdown)
            assertFalse(playback.isPlaying)
            advanceTimeBy(1_000)
            runCurrent()
        }
        assertNull(state.resumeCountdown)
        assertTrue(playback.isPlaying)
        assertEquals(1, repository.calls)
    }

    @Test
    fun `presses during countdown leave tutorial progress unchanged`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        val tutorial = requireNotNull(state.tutorial)
        vm.onIntent(DemoIntent.ScreenStopped)
        vm.onIntent(DemoIntent.ScreenStarted)

        repeat(5) {
            press(DPAD_UP)
            press(CROSS)
            press(R1)
            assertEquals(tutorial, state.tutorial)
            assertTrue(state.enabledButtons.isEmpty())
            advanceTimeBy(1_000)
            runCurrent()
        }

        assertNull(state.resumeCountdown)
        assertEquals(tutorial, state.tutorial)
        assertTrue(playback.isPlaying)
    }

    @Test
    fun `a cue arriving during the countdown does not start playback under the dialog`() = runTest(dispatcher) {
        startPlaying()
        vm.onIntent(DemoIntent.ScreenStopped)
        vm.onIntent(DemoIntent.ScreenStarted)
        advanceTimeBy(1_000)
        runCurrent()

        // A cue at or behind the current position fires at once (VideoPlayback.scheduleCue), so one can land
        // while the video is held by the countdown. It may show the tutorial; it must not start the video.
        playback.emit(PlaybackEvent.CueReached(34_000))

        assertEquals(4, state.resumeCountdown)
        assertEquals(1, requireNotNull(state.tutorial).step.sequence)
        assertFalse(playback.isPlaying)
        advanceTimeBy(4_000)
        runCurrent()
        assertNull(state.resumeCountdown)
        assertTrue(playback.isPlaying)
    }

    @Test
    fun `waiting tutorial remains paused after resume countdown`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        press(DPAD_UP)
        playback.emit(PlaybackEvent.CueReached(34_750))
        val tutorial = requireNotNull(state.tutorial)
        playback.calls.clear()

        vm.onIntent(DemoIntent.ScreenStopped)
        vm.onIntent(DemoIntent.ScreenStarted)
        advanceTimeBy(5_000)
        runCurrent()

        assertNull(state.resumeCountdown)
        assertEquals(tutorial, state.tutorial)
        assertTrue(requireNotNull(state.tutorial).isWaiting)
        assertFalse(playback.isPlaying)
        assertFalse(playback.calls.contains("play"))
    }

    @Test
    fun `stopping a countdown cancels it and next start counts from five`() = runTest(dispatcher) {
        startPlaying()
        vm.onIntent(DemoIntent.ScreenStopped)
        vm.onIntent(DemoIntent.ScreenStarted)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3, state.resumeCountdown)

        vm.onIntent(DemoIntent.ScreenStopped)
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(5, state.resumeCountdown)
        assertFalse(playback.isPlaying)
        vm.onIntent(DemoIntent.ScreenStarted)
        for (seconds in 5 downTo 1) {
            assertEquals(seconds, state.resumeCountdown)
            assertFalse(playback.isPlaying)
            advanceTimeBy(1_000)
            runCurrent()
        }
        assertNull(state.resumeCountdown)
        assertTrue(playback.isPlaying)
    }

    @Test
    fun `prepared while stopped during Loading stays paused until screen starts without countdown`() = runTest(dispatcher) {
        vm.onIntent(DemoIntent.ScreenStarted)
        assertEquals(DemoPhase.Loading, state.phase)
        vm.onIntent(DemoIntent.ScreenStopped)

        playback.emit(PlaybackEvent.Prepared)

        assertEquals(DemoPhase.Playing, state.phase)
        assertFalse(playback.isPlaying)
        assertNull(state.resumeCountdown)
        vm.onIntent(DemoIntent.ScreenStarted)
        assertNull(state.resumeCountdown)
        assertTrue(playback.isPlaying)
        assertEquals(1, repository.calls)
    }

    @Test
    fun `ended finishes and clears the tutorial while pausing`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))

        playback.emit(PlaybackEvent.Ended)

        assertEquals(DemoPhase.Finished, state.phase)
        assertNull(state.tutorial)
        assertNull(playback.pendingCueMs)
        assertFalse(playback.isPlaying)
    }

    @Test
    fun `replay after finishing seeks to zero and rearms the first step`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        press(DPAD_UP)
        press(CROSS)
        press(R1)
        playback.emit(PlaybackEvent.Ended)
        playback.calls.clear()

        vm.onIntent(DemoIntent.ReplayClicked)

        assertEquals(DemoPhase.Playing, state.phase)
        assertNull(state.tutorial)
        assertEquals(0L, playback.lastSeekMs)
        assertTrue(playback.calls.contains("seekTo:0"))
        assertEquals(34_000L, playback.pendingCueMs)
        assertTrue(playback.isPlaying)
        assertEquals(1, repository.calls)
    }

    @Test
    fun `an end queued behind a stop leaves no countdown over the finished panel`() = runTest(dispatcher) {
        startPlaying()
        vm.onIntent(DemoIntent.ScreenStopped)
        assertEquals(5, state.resumeCountdown)

        playback.emit(PlaybackEvent.Ended)
        vm.onIntent(DemoIntent.ScreenStarted)

        assertEquals(DemoPhase.Finished, state.phase)
        assertNull(state.resumeCountdown)
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(state.resumeCountdown)
        assertFalse(playback.isPlaying)
    }

    @Test
    fun `replay while Playing is ignored`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        press(DPAD_UP)
        val before = state
        playback.calls.clear()

        vm.onIntent(DemoIntent.ReplayClicked)

        assertEquals(before, state)
        assertTrue(playback.calls.isEmpty())
    }
}
