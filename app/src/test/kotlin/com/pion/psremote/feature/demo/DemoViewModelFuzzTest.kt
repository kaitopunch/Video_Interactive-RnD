package com.pion.psremote.feature.demo

import androidx.lifecycle.viewModelScope
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.playback.PlaybackEvent
import com.pion.psremote.domain.usecase.LoadDemoUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Intent fuzzing (MVI doc §7): `DemoViewModel` runs four jobs at once — the event collector, the load, the
 * countdown and the slow-motion ramp — and an exhaustive `when` proves nothing about their combinations.
 *
 * Each round drives a fresh ViewModel with a random mix of what a phone can deliver in any order: lifecycle
 * changes, presses and releases, the player's events (the expected cue, a stale one, a second `Prepared`, the end,
 * a failure) and time passing. After every action the screen's invariants must hold. Fixed seed: a failure
 * reproduces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoViewModelFuzzTest : DemoViewModelTestFixture() {

    @Test
    fun `no ordering of intents and player events breaks an invariant`() = runTest(dispatcher, timeout = 120.seconds) {
        val random = Random(seed = 20261001)
        val seen = Coverage()
        repeat(FUZZ_ROUNDS) { round ->
            val script = SCRIPTS[round % SCRIPTS.size]
            playback = FakeVideoPlayback()
            repository = FakeDemoRepository(AppResult.Success(source(script)))
            vm = DemoViewModel("sample", LoadDemoUseCase(repository, dispatcher), playback)
            val world = World()
            val collector = backgroundScope.launch { vm.effects.collect { world.exitsDelivered++ } }

            repeat(ACTIONS_PER_ROUND) { action ->
                val before = state
                val label = act(random, world)
                runCurrent()
                assertInvariants("round $round, action $action ($label)", world)
                seen.record(before, state)
            }
            // Quiet for longer than the ramp and the countdown: whatever was in flight has landed.
            advanceTimeBy(SETTLE_MS)
            runCurrent()
            assertInvariants("round $round, settled", world)
            assertSettled("round $round, settled")
            assertEquals("round $round: every Exit delivered exactly once", world.exitsSent, world.exitsDelivered)
            assertTrue("round $round: the demo was read more than once", repository.calls <= 1)

            collector.cancel()
            vm.viewModelScope.cancel()
        }
        // A fuzz that never reaches a state proves nothing about it: every one of these must have happened.
        assertTrue("coverage too thin: $seen", seen.isThorough())
    }

    /** What the rounds reached, so a change that makes the fuzz miss the interesting states fails it. */
    private class Coverage {
        var tutorialsShown = 0
        var stepsCompleted = 0
        var stopsReached = 0
        var countdowns = 0
        var finished = 0
        var failed = 0

        fun record(before: DemoState, after: DemoState) {
            if (before.tutorial == null && after.tutorial != null) tutorialsShown++
            if (before.tutorial != null && after.tutorial == null && after.phase == DemoPhase.Playing) stepsCompleted++
            if (before.tutorial?.isWaiting == false && after.tutorial?.isWaiting == true) stopsReached++
            if (before.resumeCountdown == null && after.resumeCountdown != null) countdowns++
            if (before.phase != DemoPhase.Finished && after.phase == DemoPhase.Finished) finished++
            if (before.phase !is DemoPhase.Failed && after.phase is DemoPhase.Failed) failed++
        }

        fun isThorough() = listOf(tutorialsShown, stepsCompleted, stopsReached, countdowns, finished, failed)
            .all { it >= MIN_OCCURRENCES }

        override fun toString() = "tutorials=$tutorialsShown completed=$stepsCompleted stops=$stopsReached " +
            "countdowns=$countdowns finished=$finished failed=$failed"
    }

    /** What the test knows that the ViewModel does not expose. */
    private class World {
        var screenStarted = false
        var exitsSent = 0
        var exitsDelivered = 0
    }

    private fun TestScope.act(random: Random, world: World): String = when (random.nextInt(100)) {
        in 0..9 -> {
            world.screenStarted = true
            vm.onIntent(DemoIntent.ScreenStarted)
            "ScreenStarted"
        }
        in 10..15 -> {
            world.screenStarted = false
            vm.onIntent(DemoIntent.ScreenStopped)
            "ScreenStopped"
        }
        in 16..35 -> randomButton(random).let { press(it); "press $it" }
        in 36..45 -> randomButton(random).let { release(it); "release $it" }
        in 46..48 -> {
            vm.onIntent(DemoIntent.ReplayClicked)
            "Replay"
        }
        49 -> {
            world.exitsSent++
            vm.onIntent(DemoIntent.ExitClicked)
            "Exit"
        }
        in 50..54 -> {
            playback.emit(PlaybackEvent.Prepared)
            "Prepared"
        }
        in 55..74 -> {
            // Mostly the cue the ViewModel asked for; sometimes a stale or foreign one, as a late delivery would be.
            val position = playback.pendingCueMs?.takeIf { random.nextInt(4) != 0 }
                ?: STALE_POSITIONS[random.nextInt(STALE_POSITIONS.size)]
            playback.emit(PlaybackEvent.CueReached(position))
            "CueReached($position)"
        }
        in 75..77 -> {
            playback.emit(PlaybackEvent.Ended)
            "Ended"
        }
        78 -> {
            playback.emit(PlaybackEvent.Failed("ERROR_CODE_IO_UNSPECIFIED"))
            "Failed"
        }
        else -> {
            val ms = random.nextLong(1, 2_000)
            advanceTimeBy(ms)
            "wait ${ms}ms"
        }
    }

    /**
     * Half the time a button that makes progress, so steps do get completed; then one the step names, in or out
     * of turn; then any button at all.
     */
    private fun randomButton(random: Random): ControllerButton {
        val tutorial = state.tutorial
        val pool = when {
            tutorial == null -> ControllerButton.entries
            random.nextBoolean() -> tutorial.progress.activeButtons.toList()
            random.nextBoolean() -> tutorial.step.targets
            else -> ControllerButton.entries
        }.ifEmpty { ControllerButton.entries }
        return pool[random.nextInt(pool.size)]
    }

    private fun assertInvariants(where: String, world: World) {
        val s = state
        if (playback.isPlaying) {
            assertTrue("$where: playing while the screen is stopped", world.screenStarted)
            assertEquals("$where: playing outside Playing", DemoPhase.Playing, s.phase)
            assertEquals("$where: playing under the countdown", null, s.resumeCountdown)
            assertTrue("$where: playing while a step waits", s.tutorial?.isWaiting != true)
        }
        if (s.tutorial != null) assertEquals("$where: a tutorial outside Playing", DemoPhase.Playing, s.phase)
        if (s.resumeCountdown != null) assertEquals("$where: a countdown outside Playing", DemoPhase.Playing, s.phase)
        if (s.phase != DemoPhase.Playing || s.resumeCountdown != null) {
            assertTrue("$where: a button is live with nothing to press", s.enabledButtons.isEmpty())
        }
        s.tutorial?.let { tutorial ->
            assertEquals("$where: progress is for another step", tutorial.step.targets, tutorial.progress.targets)
            assertTrue("$where: a live button is not lit", s.enabledButtons.all { it in s.highlights })
        }
        if (s.phase is DemoPhase.Finished || s.phase is DemoPhase.Failed || s.phase is DemoPhase.InvalidScript) {
            assertEquals("$where: a cue outlived the run", null, playback.pendingCueMs)
            assertEquals("$where: a countdown outlived the run", null, s.resumeCountdown)
        }
    }

    /** Once nothing is in flight: between steps the video runs at 1× with sound, never stuck in slow motion. */
    private fun assertSettled(where: String) {
        val s = state
        if (s.phase == DemoPhase.Playing && s.tutorial == null) {
            assertEquals("$where: slow motion with no tutorial", 1f, playback.speed)
            assertEquals("$where: muted with no tutorial", false, playback.muted)
        }
        s.tutorial?.takeIf { !it.step.stopsImmediately }?.let { tutorial ->
            assertEquals("$where: the ramp did not land on the step's speed", tutorial.step.playbackSpeed.toFloat(), playback.speed)
        }
    }

    private companion object {
        const val FUZZ_ROUNDS = 300
        const val ACTIONS_PER_ROUND = 60
        const val SETTLE_MS = 10_000L
        const val MIN_OCCURRENCES = 20

        const val ANY_ORDER_SCRIPT = """[
            {"step_sequence":1,"triggerTimeMs":0,"targetButtonIds":["CROSS","CROSS","R1"],
             "inputMode":"ANY_ORDER","playbackSpeed":0.5,"slowDurationMs":2000},
            {"step_sequence":2,"triggerTimeMs":5000,"targetButtonIds":["R2","CROSS","R2"],
             "playbackSpeed":0.1,"slowDurationMs":1000}
        ]"""

        val SCRIPTS = listOf(SCRIPT, SIMULTANEOUS_SCRIPT, ANY_ORDER_SCRIPT)

        /** Positions a cue might report that the ViewModel is not waiting for. */
        val STALE_POSITIONS = listOf(0L, 1_000L, 34_000L, 34_750L, 60_000L, 69_999L)
    }
}
