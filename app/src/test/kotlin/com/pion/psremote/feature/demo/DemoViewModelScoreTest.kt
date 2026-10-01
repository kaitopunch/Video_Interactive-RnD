package com.pion.psremote.feature.demo

import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.ControllerButton.CROSS
import com.pion.psremote.domain.model.ControllerButton.DPAD_UP
import com.pion.psremote.domain.model.ControllerButton.L1
import com.pion.psremote.domain.model.ControllerButton.R1
import com.pion.psremote.domain.playback.PlaybackEvent
import com.pion.psremote.domain.score.Score
import com.pion.psremote.domain.score.ScoreTier.GOOD
import com.pion.psremote.domain.score.ScoreTier.PERFECT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/button-press-scoring-rules.md, rule by rule. [SCRIPT]'s first step slows from 34 000 ms and stops at
 * 34 750 ms; its second stops at once at 60 000 ms.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoViewModelScoreTest : DemoViewModelTestFixture() {
    @Test
    fun `a run starts at zero out of one hundred per step, shown only while playing`() = runTest(dispatcher) {
        vm.onIntent(DemoIntent.ScreenStarted)
        assertFalse(state.isScoreVisible)

        playback.emit(PlaybackEvent.Prepared)

        assertEquals(Score(stepCount = 2), state.score)
        assertEquals(200, state.score.maxPoints)
        assertTrue(state.isScoreVisible)
        assertNull(state.lastAward)
    }

    /** LLM.md §12: the step count comes with the steps, so a run started before they are read still gets it. */
    @Test
    fun `a run started before the script is read still counts its steps`() = runTest(dispatcher) {
        playback.emit(PlaybackEvent.Prepared)
        assertEquals(DemoPhase.Playing, state.phase)

        vm.onIntent(DemoIntent.ScreenStarted)

        assertEquals(200, state.score.maxPoints)
    }

    @Test
    fun `a step done while its slow phase runs is perfect`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))

        completeFirstStep()

        assertEquals(listOf(PERFECT), state.score.awards)
        assertEquals(100, state.score.points)
        assertEquals(PERFECT, state.lastAward)
    }

    @Test
    fun `a step done after the video stopped is good, and what was pressed before the stop still counts`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        press(DPAD_UP)
        playback.emit(PlaybackEvent.CueReached(34_750))
        assertTrue(requireNotNull(state.tutorial).isWaiting)

        press(CROSS)
        press(R1)

        assertEquals(listOf(GOOD), state.score.awards)
        assertEquals(50, state.score.points)
    }

    @Test
    fun `a step that stops at once is perfect, so the maximum can be reached`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        completeFirstStep()
        playback.emit(PlaybackEvent.CueReached(60_000))
        assertTrue(requireNotNull(state.tutorial).isWaiting)

        press(CROSS)

        assertEquals(listOf(PERFECT, PERFECT), state.score.awards)
        assertEquals(state.score.maxPoints, state.score.points)
    }

    @Test
    fun `presses outside a tutorial and under the resume countdown score nothing`() = runTest(dispatcher) {
        startPlaying()
        ControllerButton.entries.forEach(::press)
        assertEquals(0, state.score.points)

        playback.emit(PlaybackEvent.CueReached(34_000))
        vm.onIntent(DemoIntent.ScreenStopped)
        vm.onIntent(DemoIntent.ScreenStarted)
        completeFirstStep()

        assertEquals(0, state.score.points)
        assertTrue(state.score.awards.isEmpty())
    }

    /** §1: a score per press could be farmed here, by pressing and releasing one button of the pair. */
    @Test
    fun `pressing part of a simultaneous pair over and over scores nothing, and the whole pair scores once`() = runTest(dispatcher) {
        repository.result = AppResult.Success(source(SIMULTANEOUS_SCRIPT))
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))

        repeat(5) {
            press(L1)
            release(L1)
        }
        assertEquals(0, state.score.points)
        press(L1)
        press(R1)
        release(R1)
        press(R1)

        assertEquals(listOf(PERFECT), state.score.awards)
    }

    /** R8: time in the background does not move the video, so it cannot use up the perfect window. */
    @Test
    fun `leaving for the background mid slow phase does not lower the tier`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        press(DPAD_UP)

        vm.onIntent(DemoIntent.ScreenStopped)
        vm.onIntent(DemoIntent.ScreenStarted)
        advanceTimeBy(COUNTDOWN_MILLIS)
        runCurrent()
        press(CROSS)
        press(R1)

        assertEquals(listOf(PERFECT), state.score.awards)
    }

    @Test
    fun `the last tier shows between steps and gives way to the next tutorial`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        completeFirstStep()
        assertEquals(PERFECT, state.lastAward)

        playback.emit(PlaybackEvent.CueReached(60_000))

        assertNull(state.lastAward)
        assertEquals(100, state.score.points)
    }

    @Test
    fun `the end keeps the score for the finished panel, and replay starts the next run from zero`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        completeFirstStep()
        playback.emit(PlaybackEvent.CueReached(60_000))
        press(CROSS)
        playback.emit(PlaybackEvent.Ended)
        assertEquals(DemoPhase.Finished, state.phase)
        assertEquals(200, state.score.points)
        assertFalse(state.isScoreVisible)

        vm.onIntent(DemoIntent.ReplayClicked)

        assertEquals(DemoPhase.Playing, state.phase)
        assertEquals(Score(stepCount = 2), state.score)
        assertNull(state.lastAward)
    }

    @Test
    fun `a failed run shows no score`() = runTest(dispatcher) {
        startPlaying()
        playback.emit(PlaybackEvent.CueReached(34_000))
        completeFirstStep()

        playback.emit(PlaybackEvent.Failed("ERROR_CODE_IO_UNSPECIFIED"))

        assertFalse(state.isScoreVisible)
    }

    private fun completeFirstStep() {
        press(DPAD_UP)
        press(CROSS)
        press(R1)
    }

    private companion object {
        const val COUNTDOWN_MILLIS = 5_000L
    }
}
