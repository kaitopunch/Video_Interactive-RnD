package com.pion.psremote.domain.model

/**
 * One reason a demo script cannot be played. Every violation is shown to the BA on the error screen
 * (confirm.md Q12), so each case carries exactly what its message needs and no user-facing text.
 *
 * Structural cases come from `DemoScriptParser` and point at [index], the step's 0-based position in
 * the JSON array — the step may have no readable `step_sequence`. Rule cases come from
 * `DemoScriptValidator` and point at the step's `step_sequence`.
 */
sealed interface ScriptViolation {

    data class NotAJsonArray(val detail: String?) : ScriptViolation

    data class StepNotAnObject(val index: Int) : ScriptViolation

    data class MissingField(val index: Int, val field: String) : ScriptViolation

    data class WrongType(val index: Int, val field: String, val expected: FieldType) : ScriptViolation

    /** The draft's `targetButtonId` was renamed to `targetButtonIds` (confirm.md Q5). */
    data class RenamedField(val index: Int, val oldName: String, val newName: String) : ScriptViolation

    data class UnknownButton(val index: Int, val value: String) : ScriptViolation

    data class UnknownInputMode(val index: Int, val value: String) : ScriptViolation

    data class SequenceBelowOne(val sequence: Int) : ScriptViolation

    data class DuplicateSequence(val sequence: Int) : ScriptViolation

    data class NegativeTriggerTime(val sequence: Int, val triggerTimeMs: Long) : ScriptViolation

    data class EmptyTargets(val sequence: Int) : ScriptViolation

    data class SpeedOutOfRange(val sequence: Int, val speed: Double) : ScriptViolation

    data class NegativeSlowDuration(val sequence: Int, val slowDurationMs: Long) : ScriptViolation

    data class ZeroSpeedNeedsZeroDuration(val sequence: Int, val slowDurationMs: Long) : ScriptViolation

    data class TooManySimultaneousButtons(val sequence: Int, val count: Int, val max: Int) : ScriptViolation

    /** One button listed twice in a SIMULTANEOUS step: a single button cannot be down twice at once. */
    data class RepeatedSimultaneousButton(val sequence: Int, val button: ControllerButton) : ScriptViolation

    data class TriggerNotAfterPreviousStop(
        val sequence: Int,
        val triggerTimeMs: Long,
        val previousSequence: Int,
        val previousStopMs: Long,
    ) : ScriptViolation

    data class StopNotBeforeVideoEnd(
        val sequence: Int,
        val stopPositionMs: Long,
        val videoDurationMs: Long,
    ) : ScriptViolation
}

/** The JSON type a field must have, for [ScriptViolation.WrongType]. */
enum class FieldType { INTEGER, NUMBER, STRING, STRING_ARRAY }
