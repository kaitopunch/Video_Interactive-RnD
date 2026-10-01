package com.pion.psremote.domain.playback

import kotlinx.coroutines.delay

/**
 * How the video eases from 1× into a step's slow speed instead of dropping to it in one frame, which
 * read as a jolt (asked for on 2026-10-01).
 *
 * Ease-out: most of the drop happens in the first ticks, like braking, so the video spends as little
 * extra time as possible above the target speed. The stop is still a cue at a video *position*
 * (`TutorialStep.stopPositionMs`), so the ramp never moves where the video stops. It does shorten the
 * real time spent slow, because the video covers ground faster while it brakes: about 0.1 s at 0.5×,
 * 0.35 s at 0.25×, 1 s at 0.1×.
 */
object SlowMotionRamp {

    /** 10 ticks × 30 ms = 0.3 s, the length agreed with the user. A tick is about one video frame. */
    const val TICK_MILLIS = 30L
    private const val TICKS = 10

    /** The speed to set after each tick, the last one exactly [target]. */
    fun speeds(target: Float): List<Float> = (1..TICKS).map { tick ->
        val remaining = 1f - tick.toFloat() / TICKS
        target + (NORMAL_SPEED - target) * remaining * remaining
    }

    /**
     * Applies [speeds] one tick apart, and stops for good the first time [isWanted] is false. Asked before
     * every tick rather than relying on a cancel: completing or replaying a step sets 1×, and a ramp that
     * one of them forgot to cancel would drag the resumed video back into slow motion.
     */
    suspend fun run(target: Float, isWanted: () -> Boolean, setSpeed: (Float) -> Unit) {
        for (speed in speeds(target)) {
            delay(TICK_MILLIS)
            if (!isWanted()) return
            setSpeed(speed)
        }
    }

    private const val NORMAL_SPEED = 1f
}
