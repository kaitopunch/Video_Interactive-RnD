package com.pion.psremote.domain.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SlowMotionRampTest {
    @Test
    fun `ramp lasts three hundred milliseconds and lands exactly on the target`() {
        listOf(0.1f, 0.25f, 0.5f, 1f).forEach { target ->
            val speeds = SlowMotionRamp.speeds(target)
            assertEquals(300L, SlowMotionRamp.TICK_MILLIS * speeds.size)
            assertEquals(target, speeds.last(), 0f)
        }
    }

    @Test
    fun `every tick brakes, never below the target`() {
        val speeds = SlowMotionRamp.speeds(0.25f)

        assertTrue(speeds.all { it in 0.25f..1f })
        assertTrue(speeds.zipWithNext().all { (before, after) -> after < before })
    }

    @Test
    fun `most of the drop happens in the first half`() {
        val speeds = SlowMotionRamp.speeds(0.25f)

        // Ease-out: half-way through, at least three quarters of the way down.
        assertTrue(speeds[speeds.size / 2 - 1] <= 0.25f + 0.75f * 0.25f)
    }

    @Test
    fun `run sets one speed per tick and stops for good once no longer wanted`() = runTest {
        val applied = mutableListOf<Float>()
        var wanted = true
        launch { SlowMotionRamp.run(0.25f, { wanted }, applied::add) }

        advanceTimeBy(SlowMotionRamp.TICK_MILLIS * 3)
        runCurrent()
        assertEquals(SlowMotionRamp.speeds(0.25f).take(3), applied)

        wanted = false
        advanceTimeBy(SlowMotionRamp.TICK_MILLIS)
        runCurrent()
        wanted = true
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, applied.size)
    }
}
