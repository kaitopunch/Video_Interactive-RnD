package com.pion.psremote.domain.script

import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.ScriptViolation
import com.pion.psremote.domain.model.TutorialStep

/**
 * The value rules of README §5, plus the limits agreed in confirm.md. Pure: JVM-testable with no fakes.
 *
 * One rule of §5 is deliberately not here: "the stop point is before the action scene". The app cannot
 * see what a frame shows, so that one is the BA's to guarantee (D9).
 */
object DemoScriptValidator {

    /** Agreed ceiling for a SIMULTANEOUS step (confirm.md Q11). */
    const val MAX_SIMULTANEOUS_BUTTONS = 5

    /**
     * Slowest speed a slow phase may use. README allows anything above 0, but the platform audio path
     * (`AudioTrack` playback params) is only reliable from 0.1×: below that a device may reject the speed
     * and keep the previous one, so the "slow" phase runs fast and stops early.
     */
    const val MIN_SLOW_SPEED = 0.1

    /**
     * Returns every violation in [parsed], its own structural ones first. Empty means playable.
     *
     * The timeline rules — ordering against the previous stop, and the end of the video — run only
     * when everything else passed: on a script with a missing step or a nonsense speed, they would
     * report consequences of the first error rather than errors of their own.
     */
    fun validate(parsed: ParsedScript, videoDurationMs: Long): List<ScriptViolation> {
        val violations = parsed.violations.toMutableList()
        parsed.steps.forEach { violations += stepViolations(it) }
        violations += duplicateSequences(parsed.steps)
        if (violations.isEmpty()) violations += timelineViolations(parsed.steps, videoDurationMs)
        return violations
    }

    private fun stepViolations(step: TutorialStep): List<ScriptViolation> = buildList {
        val sequence = step.sequence
        if (sequence < 1) add(ScriptViolation.SequenceBelowOne(sequence))
        if (step.triggerTimeMs < 0) add(ScriptViolation.NegativeTriggerTime(sequence, step.triggerTimeMs))
        if (step.targets.isEmpty()) add(ScriptViolation.EmptyTargets(sequence))
        if (step.playbackSpeed != 0.0 && step.playbackSpeed !in MIN_SLOW_SPEED..1.0) {
            add(ScriptViolation.SpeedOutOfRange(sequence, step.playbackSpeed))
        }
        if (step.slowDurationMs < 0) add(ScriptViolation.NegativeSlowDuration(sequence, step.slowDurationMs))
        if (step.playbackSpeed == 0.0 && step.slowDurationMs != 0L) {
            add(ScriptViolation.ZeroSpeedNeedsZeroDuration(sequence, step.slowDurationMs))
        }
        if (step.inputMode == InputMode.SIMULTANEOUS) {
            if (step.targets.size > MAX_SIMULTANEOUS_BUTTONS) {
                add(ScriptViolation.TooManySimultaneousButtons(sequence, step.targets.size, MAX_SIMULTANEOUS_BUTTONS))
            }
            step.targets.groupingBy { it }.eachCount()
                .filterValues { it > 1 }
                .keys
                .forEach { add(ScriptViolation.RepeatedSimultaneousButton(sequence, it)) }
        }
    }

    private fun duplicateSequences(steps: List<TutorialStep>): List<ScriptViolation> =
        steps.groupingBy { it.sequence }.eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
            .map { ScriptViolation.DuplicateSequence(it) }

    private fun timelineViolations(steps: List<TutorialStep>, videoDurationMs: Long): List<ScriptViolation> {
        val ordered = steps.sortedBy { it.sequence }
        val ordering = ordered.zipWithNext().mapNotNull { (previous, step) ->
            if (step.triggerTimeMs > previous.stopPositionMs) {
                null
            } else {
                ScriptViolation.TriggerNotAfterPreviousStop(
                    sequence = step.sequence,
                    triggerTimeMs = step.triggerTimeMs,
                    previousSequence = previous.sequence,
                    previousStopMs = previous.stopPositionMs,
                )
            }
        }
        val pastEnd = ordered
            .filter { it.stopPositionMs >= videoDurationMs }
            .map { ScriptViolation.StopNotBeforeVideoEnd(it.sequence, it.stopPositionMs, videoDurationMs) }
        return ordering + pastEnd
    }
}
