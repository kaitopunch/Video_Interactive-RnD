package com.pion.psremote.domain.model

import kotlin.math.roundToLong

/**
 * One interactive step of a demo script, after parsing. May still break a rule: only a script that
 * `DemoScriptValidator` accepted is ever played.
 *
 * `pauseTimeMs` from the requirements.md draft is not here on purpose (confirm.md Q6): it duplicated what
 * [stopPositionMs] computes, and two fields that can disagree will disagree.
 */
data class TutorialStep(
    /** `step_sequence` — both the step's id and its order in the timeline. */
    val sequence: Int,
    /** Video position at which the tutorial appears and the slow-down starts. */
    val triggerTimeMs: Long,
    val targets: List<ControllerButton>,
    val inputMode: InputMode,
    /** 0 < speed ≤ 1 slows the video; exactly 0 stops it at [triggerTimeMs]. */
    val playbackSpeed: Double,
    /** Real time spent at [playbackSpeed] before the video stops and waits. */
    val slowDurationMs: Long,
) {
    /**
     * Where the video stops if the user has not finished: `triggerTimeMs + playbackSpeed × slowDurationMs`
     * (requirements.md §5). The pause is scheduled at this *video position*, not after a wall-clock delay, which is
     * what keeps buffering and time spent in the background out of `slowDurationMs` (requirements.md §4, D5).
     */
    val stopPositionMs: Long
        // Summed as a Double: `roundToLong` saturates, where a Long sum of two huge values wraps negative
        // and a nonsense step would pass every "before the end of the video" check.
        get() = (triggerTimeMs + playbackSpeed * slowDurationMs).roundToLong()

    /** True when the video stops the moment the tutorial appears: speed 0, or no slow phase at all. */
    val stopsImmediately: Boolean
        get() = stopPositionMs <= triggerTimeMs
}
