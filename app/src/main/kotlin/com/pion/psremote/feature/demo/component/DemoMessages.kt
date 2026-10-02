package com.pion.psremote.feature.demo.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pion.psremote.R
import com.pion.psremote.core.common.AppError
import com.pion.psremote.domain.model.FieldType
import com.pion.psremote.domain.model.ScriptViolation

/**
 * The user-facing text for a script violation, resolved at render time (MVI doc §5). Written for the BA
 * fixing the JSON: it names the step and the field, and says what the value must be.
 *
 * Structural violations point at the step's position in the file, counted from 1, because the step may
 * have no readable `step_sequence` to name it by.
 */
@Composable
fun ScriptViolation.message(): String = when (this) {
    is ScriptViolation.NotAJsonArray ->
        stringResource(R.string.violation_not_json_array) + (detail?.let { "\n$it" } ?: "")
    is ScriptViolation.StepNotAnObject -> stringResource(R.string.violation_step_not_object, index + 1)
    is ScriptViolation.MissingField -> stringResource(R.string.violation_missing_field, index + 1, field)
    is ScriptViolation.WrongType ->
        stringResource(R.string.violation_wrong_type, index + 1, field, stringResource(expected.nameRes()))
    is ScriptViolation.RenamedField -> stringResource(R.string.violation_renamed_field, index + 1, oldName, newName)
    is ScriptViolation.UnknownButton -> stringResource(R.string.violation_unknown_button, index + 1, value)
    is ScriptViolation.UnknownInputMode -> stringResource(R.string.violation_unknown_input_mode, index + 1, value)
    is ScriptViolation.SequenceBelowOne -> stringResource(R.string.violation_sequence_below_one, sequence)
    is ScriptViolation.DuplicateSequence -> stringResource(R.string.violation_duplicate_sequence, sequence)
    is ScriptViolation.NegativeTriggerTime ->
        stringResource(R.string.violation_negative_trigger, sequence, triggerTimeMs)
    is ScriptViolation.EmptyTargets -> stringResource(R.string.violation_empty_targets, sequence)
    is ScriptViolation.SpeedOutOfRange -> stringResource(R.string.violation_speed_range, sequence, speed.toString())
    is ScriptViolation.NegativeSlowDuration ->
        stringResource(R.string.violation_negative_slow_duration, sequence, slowDurationMs)
    is ScriptViolation.ZeroSpeedNeedsZeroDuration ->
        stringResource(R.string.violation_zero_speed_duration, sequence, slowDurationMs)
    is ScriptViolation.TooManySimultaneousButtons ->
        stringResource(R.string.violation_too_many_simultaneous, sequence, count, max)
    is ScriptViolation.RepeatedSimultaneousButton ->
        stringResource(R.string.violation_repeated_simultaneous, sequence, button.name)
    is ScriptViolation.TriggerNotAfterPreviousStop -> stringResource(
        R.string.violation_trigger_before_previous_stop,
        sequence,
        triggerTimeMs,
        previousSequence,
        previousStopMs,
    )
    is ScriptViolation.StopNotBeforeVideoEnd ->
        stringResource(R.string.violation_stop_after_video_end, sequence, stopPositionMs, videoDurationMs)
}

@Composable
fun AppError.message(): String = when (this) {
    is AppError.NotFound -> stringResource(R.string.error_file_missing, what)
    is AppError.Network -> stringResource(R.string.error_network, reason ?: "?")
    is AppError.Playback -> stringResource(R.string.error_playback, reason ?: "?")
    is AppError.Unexpected -> stringResource(R.string.error_unexpected, message ?: "?")
}

private fun FieldType.nameRes(): Int = when (this) {
    FieldType.INTEGER -> R.string.field_type_integer
    FieldType.NUMBER -> R.string.field_type_number
    FieldType.STRING -> R.string.field_type_string
    FieldType.STRING_ARRAY -> R.string.field_type_string_array
}
