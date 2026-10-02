package com.pion.psremote.feature.demo

import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.ControllerButton.CROSS
import com.pion.psremote.domain.model.ControllerButton.DPAD_UP
import com.pion.psremote.domain.model.ControllerButton.R1
import com.pion.psremote.domain.model.ControllerButton.TRIANGLE
import com.pion.psremote.domain.playback.PlaybackEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Events that arrive when they should not, and a player that stops answering: none may crash or revive a run. */
class DemoViewModelFailureTest : DemoViewModelTestFixture() {

    @Test
    fun `a player whose event stream breaks ends the run on the error screen, paused`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))

        playback.breakEvents(IllegalStateException("player released"))

        assertEquals(DemoPhase.Failed(AppError.Unexpected("player released")), state.phase)
        assertNull(state.tutorial)
        assertNull(playback.pendingCueMs)
        assertFalse(playback.isPlaying)
    }

    @Test
    fun `the error screen is final, so a late end, a replay or a return change nothing`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.Failed("ERROR_CODE_DECODING_FAILED"))
        val failed = state

        playback.emit(PlaybackEvent.Ended)
        vm.onIntent(DemoIntent.ReplayClicked)
        vm.onIntent(DemoIntent.ScreenStopped)
        vm.onIntent(DemoIntent.ScreenStarted)
        playback.emit(PlaybackEvent.Prepared)

        assertEquals(failed, state)
        assertEquals(1, repository.calls)
        assertFalse(playback.isPlaying)
    }

    @Test
    fun `stray player events on an invalid script change nothing and never touch the video`() = runTest(dispatcher) {
        repository.result = AppResult.Success(source("""[{"step_sequence":0}]"""))
        vm.onIntent(DemoIntent.ScreenStarted)
        val invalid = state
        assertTrue(invalid.phase is DemoPhase.InvalidScript)

        listOf(PlaybackEvent.Prepared, PlaybackEvent.CueReached(34_000), PlaybackEvent.Ended).forEach(playback::emit)

        assertEquals(invalid, state)
        assertNull(playback.preparedUri)
        assertFalse(playback.isPlaying)
    }

    @Test
    fun `a release with no tutorial up, or of a button never pressed, changes nothing`() = runTest(dispatcher) {
        startPlaying()
        val playing = state
        release(CROSS)
        assertEquals(playing, state)

        playback.emit(PlaybackEvent.CueReached(34_000))
        val shown = state
        release(TRIANGLE)
        assertEquals(shown, state)
    }

    @Test
    fun `nothing is armed past the last step, and a stray cue there changes nothing`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        listOf(DPAD_UP, CROSS, R1).forEach(::press)
        playback.emit(PlaybackEvent.CueReached(60_000))
        press(CROSS)
        val done = state
        assertNull(playback.pendingCueMs)

        playback.emit(PlaybackEvent.CueReached(60_000))
        playback.emit(PlaybackEvent.CueReached(65_000))

        assertEquals(done, state)
        assertEquals(2, state.score.awards.size)
    }
}
