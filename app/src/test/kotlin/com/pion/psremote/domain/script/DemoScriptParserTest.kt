package com.pion.psremote.domain.script

import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.FieldType
import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.ScriptViolation
import com.pion.psremote.domain.model.TutorialStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScriptParserTest {
    @Test
    fun `valid steps preserve values default the mode and ignore unknown fields`() {
        val parsed = DemoScriptParser.parse(
            """[
                {"step_sequence":1,"triggerTimeMs":34000,"targetButtonIds":["DPAD_UP","CROSS","R1"],
                 "inputMode":"ANY_ORDER","playbackSpeed":0.25,"slowDurationMs":3000},
                {"step_sequence":2,"triggerTimeMs":60000,"targetButtonIds":["CROSS"],
                 "playbackSpeed":0,"slowDurationMs":0,"pauseTimeMs":60000}
            ]""",
        )

        assertEquals(
            listOf(
                TutorialStep(1, 34_000, listOf(ControllerButton.DPAD_UP, ControllerButton.CROSS, ControllerButton.R1),
                    InputMode.ANY_ORDER, 0.25, 3_000),
                TutorialStep(2, 60_000, listOf(ControllerButton.CROSS), InputMode.SEQUENCE, 0.0, 0),
            ),
            parsed.steps,
        )
        assertTrue(parsed.violations.isEmpty())
    }

    @Test
    fun `malformed JSON reports only NotAJsonArray`() {
        val parsed = DemoScriptParser.parse("[{broken")

        assertTrue(parsed.steps.isEmpty())
        assertEquals(1, parsed.violations.size)
        assertTrue(parsed.violations.single() is ScriptViolation.NotAJsonArray)
    }

    @Test
    fun `object root reports only NotAJsonArray`() {
        assertRejected("{}", ScriptViolation.NotAJsonArray(null))
    }

    @Test
    fun `numeric array element reports its array position`() {
        assertRejected("[42]", ScriptViolation.StepNotAnObject(0))
    }

    @Test
    fun `missing sequence is reported`() = assertMissing("step_sequence")

    @Test
    fun `missing trigger is reported`() = assertMissing("triggerTimeMs")

    @Test
    fun `missing targets is reported`() = assertMissing("targetButtonIds")

    @Test
    fun `missing speed is reported`() = assertMissing("playbackSpeed")

    @Test
    fun `missing slow duration is reported`() = assertMissing("slowDurationMs")

    @Test
    fun `string trigger is not an integer`() =
        assertWrongType("triggerTimeMs", "\"34000\"", FieldType.INTEGER)

    @Test
    fun `null trigger is not an integer`() =
        assertWrongType("triggerTimeMs", "null", FieldType.INTEGER)

    @Test
    fun `fractional sequence is not an integer`() =
        assertWrongType("step_sequence", "1.5", FieldType.INTEGER)

    @Test
    fun `boolean speed is not a number`() =
        assertWrongType("playbackSpeed", "true", FieldType.NUMBER)

    @Test
    fun `single string target is not an array`() =
        assertWrongType("targetButtonIds", "\"CROSS\"", FieldType.STRING_ARRAY)

    @Test
    fun `numeric target is not a string array member`() =
        assertWrongType("targetButtonIds", "[1]", FieldType.STRING_ARRAY)

    @Test
    fun `numeric input mode is not a string`() =
        assertWrongType("inputMode", "5", FieldType.STRING)

    @Test
    fun `legacy target field reports rename instead of missing field`() {
        val fields = validFields().apply {
            remove("targetButtonIds")
            put("targetButtonId", "[\"CROSS\"]")
        }

        assertRejected("[${jsonObject(fields)}]", ScriptViolation.RenamedField(0, "targetButtonId", "targetButtonIds"))
    }

    @Test
    fun `every unknown button is reported including lowercase ids`() {
        val fields = validFields().apply { put("targetButtonIds", "[\"UNKNOWN\",\"cross\",\"CROSS\",\"BAD\"]") }

        assertRejected(
            "[${jsonObject(fields)}]",
            ScriptViolation.UnknownButton(0, "UNKNOWN"),
            ScriptViolation.UnknownButton(0, "cross"),
            ScriptViolation.UnknownButton(0, "BAD"),
        )
    }

    @Test
    fun `unknown input mode is reported`() {
        val fields = validFields().apply { put("inputMode", "\"HOLD\"") }

        assertRejected("[${jsonObject(fields)}]", ScriptViolation.UnknownInputMode(0, "HOLD"))
    }

    @Test
    fun `all problems exclude the bad step while the good neighbor survives`() {
        val bad = validFields().apply {
            remove("triggerTimeMs")
            put("targetButtonIds", "[\"cross\"]")
            put("playbackSpeed", "true")
        }
        val good = validFields().apply { put("step_sequence", "7") }

        val parsed = DemoScriptParser.parse("[${jsonObject(good)},${jsonObject(bad)}]")

        assertEquals(listOf(TutorialStep(7, 34_000, listOf(ControllerButton.CROSS), InputMode.SEQUENCE, 0.25, 3_000)),
            parsed.steps)
        assertEquals(
            listOf(
                ScriptViolation.MissingField(1, "triggerTimeMs"),
                ScriptViolation.UnknownButton(1, "cross"),
                ScriptViolation.WrongType(1, "playbackSpeed", FieldType.NUMBER),
            ),
            parsed.violations,
        )
    }

    private fun assertMissing(field: String) {
        val fields = validFields().apply { remove(field) }
        assertRejected("[${jsonObject(fields)}]", ScriptViolation.MissingField(0, field))
    }

    private fun assertWrongType(field: String, value: String, expected: FieldType) {
        val fields = validFields().apply { put(field, value) }
        assertRejected("[${jsonObject(fields)}]", ScriptViolation.WrongType(0, field, expected))
    }

    private fun assertRejected(json: String, vararg violations: ScriptViolation) {
        val parsed = DemoScriptParser.parse(json)
        assertTrue(parsed.steps.isEmpty())
        assertEquals(violations.toList(), parsed.violations)
    }

    private fun validFields() = linkedMapOf(
        "step_sequence" to "1",
        "triggerTimeMs" to "34000",
        "targetButtonIds" to "[\"CROSS\"]",
        "playbackSpeed" to "0.25",
        "slowDurationMs" to "3000",
    )

    private fun jsonObject(fields: Map<String, String>) =
        fields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) -> "\"$key\":$value" }
}
