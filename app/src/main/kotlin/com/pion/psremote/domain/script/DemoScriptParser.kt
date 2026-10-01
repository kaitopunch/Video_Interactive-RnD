package com.pion.psremote.domain.script

import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.FieldType
import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.ScriptViolation
import com.pion.psremote.domain.model.TutorialStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** What [DemoScriptParser] could read: every well-typed step, and every structural problem found. */
data class ParsedScript(
    val steps: List<TutorialStep>,
    val violations: List<ScriptViolation>,
)

/**
 * Reads a demo script's JSON into [TutorialStep]s. Checks structure and types only; value rules are
 * `DemoScriptValidator`'s.
 *
 * Read as a `JsonElement` tree rather than decoded with `@Serializable`, because the BA needs *every*
 * problem in one pass (confirm.md Q12) and a decoder stops at the first. A step with any problem is
 * left out of [ParsedScript.steps] and its problems are all reported.
 *
 * Unknown fields are ignored, so a script still carrying the dropped `pauseTimeMs` (Q6) loads.
 */
object DemoScriptParser {

    const val FIELD_SEQUENCE = "step_sequence"
    const val FIELD_TRIGGER = "triggerTimeMs"
    const val FIELD_TARGETS = "targetButtonIds"
    const val FIELD_INPUT_MODE = "inputMode"
    const val FIELD_SPEED = "playbackSpeed"
    const val FIELD_SLOW_DURATION = "slowDurationMs"
    const val LEGACY_FIELD_TARGETS = "targetButtonId"

    fun parse(json: String): ParsedScript {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (malformed: IllegalArgumentException) { // SerializationException extends it
            return ParsedScript(emptyList(), listOf(ScriptViolation.NotAJsonArray(malformed.message)))
        }
        val array = root as? JsonArray
            ?: return ParsedScript(emptyList(), listOf(ScriptViolation.NotAJsonArray(null)))

        val violations = mutableListOf<ScriptViolation>()
        val steps = array.mapIndexedNotNull { index, element -> StepReader(index, violations).read(element) }
        return ParsedScript(steps, violations)
    }
}

private class StepReader(
    private val index: Int,
    private val violations: MutableList<ScriptViolation>,
) {
    fun read(element: JsonElement): TutorialStep? {
        val step = element as? JsonObject
            ?: return reject(ScriptViolation.StepNotAnObject(index))
        val before = violations.size

        // Every field is read before bailing out, so one bad step reports all of its problems.
        val sequence = step.integer(DemoScriptParser.FIELD_SEQUENCE)
        val trigger = step.integer(DemoScriptParser.FIELD_TRIGGER)
        val targets = step.targets()
        val inputMode = step.inputMode()
        val speed = step.number(DemoScriptParser.FIELD_SPEED)
        val slowDuration = step.integer(DemoScriptParser.FIELD_SLOW_DURATION)

        val intSequence = sequence?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else null }
        if (sequence != null && intSequence == null) {
            violations += ScriptViolation.WrongType(index, DemoScriptParser.FIELD_SEQUENCE, FieldType.INTEGER)
        }
        if (violations.size > before) return null
        return TutorialStep(
            sequence = intSequence ?: return null,
            triggerTimeMs = trigger ?: return null,
            targets = targets ?: return null,
            inputMode = inputMode ?: return null,
            playbackSpeed = speed ?: return null,
            slowDurationMs = slowDuration ?: return null,
        )
    }

    private fun JsonObject.integer(field: String): Long? {
        val value = this[field] ?: return missing(field)
        return value.nonStringPrimitive()?.longOrNull ?: wrongType(field, FieldType.INTEGER)
    }

    private fun JsonObject.number(field: String): Double? {
        val value = this[field] ?: return missing(field)
        return value.nonStringPrimitive()?.doubleOrNull ?: wrongType(field, FieldType.NUMBER)
    }

    private fun JsonObject.targets(): List<ControllerButton>? {
        val field = DemoScriptParser.FIELD_TARGETS
        val value = this[field] ?: return if (containsKey(DemoScriptParser.LEGACY_FIELD_TARGETS)) {
            reject(ScriptViolation.RenamedField(index, DemoScriptParser.LEGACY_FIELD_TARGETS, field))
        } else {
            missing(field)
        }
        val items = value as? JsonArray ?: return wrongType(field, FieldType.STRING_ARRAY)
        val ids = items.map { item ->
            (item as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: return wrongType(field, FieldType.STRING_ARRAY)
        }
        val buttons = ids.mapNotNull { id ->
            ControllerButton.fromId(id).also { if (it == null) violations += ScriptViolation.UnknownButton(index, id) }
        }
        // Every unknown id is reported; a step missing one is not played with it silently dropped.
        return buttons.takeIf { it.size == ids.size }
    }

    /** Optional: a step without `inputMode` is a sequence (confirm.md Q8). */
    private fun JsonObject.inputMode(): InputMode? {
        val field = DemoScriptParser.FIELD_INPUT_MODE
        val value = this[field] ?: return InputMode.DEFAULT
        val id = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return wrongType(field, FieldType.STRING)
        return InputMode.fromId(id) ?: reject(ScriptViolation.UnknownInputMode(index, id))
    }

    /** `"34000"` is a string, `null` is nothing: neither is a number, whatever `content` would parse to. */
    private fun JsonElement.nonStringPrimitive(): JsonPrimitive? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }

    private fun <T> missing(field: String): T? = reject(ScriptViolation.MissingField(index, field))

    private fun <T> wrongType(field: String, expected: FieldType): T? =
        reject(ScriptViolation.WrongType(index, field, expected))

    private fun <T> reject(violation: ScriptViolation): T? {
        violations += violation
        return null
    }
}
