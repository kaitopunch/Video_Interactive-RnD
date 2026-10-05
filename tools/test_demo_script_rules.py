"""Tests for demo_script_rules.py and demo_script_advice.py.

    python3 -m unittest discover -s tools -v

The rule cases are DemoScriptParserTest's and DemoScriptValidatorTest's, so the tool and the app agree on every
script. AppSourcesTest reads the app's own Kotlin and strings, and fails when a button, mode, limit, field or message
changes there without demo_script_rules.py; it skips itself where the app's sources are not checked out.
"""

import json
import re
import unittest
import xml.etree.ElementTree as ElementTree
from pathlib import Path

import demo_script_advice as advice
import demo_script_rules as rules

APP = Path(__file__).resolve().parent.parent / "app" / "src" / "main"
KOTLIN = APP / "kotlin" / "com" / "pion" / "psremote" / "domain"
VALID = {"step_sequence": 1, "triggerTimeMs": 34000, "targetButtonIds": ["CROSS"], "playbackSpeed": 0.25,
         "slowDurationMs": 3000}


def script(*steps):
    return json.dumps(list(steps))


def fields(**changes):
    step = {**VALID, **changes}
    return {key: value for key, value in step.items() if value is not ...}  # `...` removes a field


def errors(text, duration=70_000):
    return rules.check_script(text, duration).errors


def m(key, *args):
    return rules.message(key, *args)


class ParserTest(unittest.TestCase):
    def assertRejected(self, text, *expected):
        check = rules.check_script(text, 70_000)
        self.assertEqual([], check.steps)
        self.assertEqual(list(expected), check.errors)

    def assertWrongType(self, field, value, type_key):
        self.assertRejected(script(fields(**{field: value})), m("wrong_type", 1, field, rules.FIELD_TYPES[type_key]))

    def test_valid_steps_keep_their_values_default_the_mode_and_ignore_unknown_fields(self):
        check = rules.check_script(script(fields(pauseTimeMs=34750),
                                          fields(step_sequence=2, triggerTimeMs=40000, inputMode="SIMULTANEOUS",
                                                 targetButtonIds=["L1", "R1"])), 70_000)
        self.assertEqual([rules.Step(1, 34000, ("CROSS",), "SEQUENCE", 0.25, 3000),
                          rules.Step(2, 40000, ("L1", "R1"), "SIMULTANEOUS", 0.25, 3000)], check.steps)
        self.assertEqual([], check.errors)

    def test_malformed_json_is_only_not_an_array_and_says_where(self):
        [error] = errors('[{"step_sequence": 1,\n  broken')
        self.assertTrue(error.startswith(m("not_json_array") + "\nLỗi cú pháp JSON ở dòng 2, cột 3: "), error)

    def test_anything_but_an_array_is_not_an_array(self):
        for text in ("{}", "null", "42", "", "   ", "\n\t"):
            with self.subTest(text=text):
                [error] = errors(text)
                self.assertTrue(error.startswith(m("not_json_array")), error)

    def test_a_script_pasted_as_a_quoted_string_is_not_an_array_and_is_told_so(self):
        [error] = errors(json.dumps(script(VALID)))
        self.assertIn(m("not_json_array") + "\nKịch bản đang nằm trong dấu ngoặc kép", error)

    def test_null_number_nested_and_string_elements_report_their_positions(self):
        self.assertRejected('[42,null,[],"step"]', *(m("step_not_object", i) for i in (1, 2, 3, 4)))

    def test_an_empty_array_has_no_steps_and_nothing_wrong(self):
        self.assertEqual(([], []), (rules.check_script("[]", 70_000).steps, errors("[]")))

    def test_each_missing_field_is_named(self):
        for field in ("step_sequence", "triggerTimeMs", "targetButtonIds", "playbackSpeed", "slowDurationMs"):
            with self.subTest(field=field):
                self.assertRejected(script(fields(**{field: ...})), m("missing_field", 1, field))

    def test_values_of_the_wrong_type(self):
        cases = [("step_sequence", 2147483648, "integer"), ("triggerTimeMs", 9223372036854775808, "integer"),
                 ("playbackSpeed", "0.25", "number"), ("triggerTimeMs", "34000", "integer"),
                 ("triggerTimeMs", None, "integer"), ("step_sequence", 1.0, "integer"), ("playbackSpeed", True, "number"),
                 ("slowDurationMs", False, "integer"), ("targetButtonIds", "CROSS", "string_array"),
                 ("targetButtonIds", [1], "string_array"), ("inputMode", 1, "string"), ("inputMode", None, "string")]
        for field, value, type_key in cases:
            with self.subTest(field=field, value=value):
                self.assertWrongType(field, value, type_key)

    def test_a_bare_NaN_speed_is_read_as_a_number_and_left_to_the_rules(self):
        text = script(VALID).replace("0.25", "NaN")
        self.assertEqual([m("speed_range", 1, "NaN")], errors(text))

    def test_the_legacy_target_field_is_reported_as_renamed_not_missing(self):
        self.assertRejected(script(fields(targetButtonIds=..., targetButtonId=["CROSS"])),
                            m("renamed_field", 1, "targetButtonId", "targetButtonIds"))

    def test_every_unknown_button_is_reported_including_lowercase_ids(self):
        self.assertRejected(script(fields(targetButtonIds=["cross", "CROSS", "X", "cross"])),
                            m("unknown_button", 1, "cross"), m("unknown_button", 1, "X"), m("unknown_button", 1, "cross"))

    def test_an_unknown_input_mode_is_reported(self):
        self.assertRejected(script(fields(inputMode="sequence")), m("unknown_input_mode", 1, "sequence"))

    def test_a_bad_step_reports_all_its_problems_and_its_good_neighbour_survives(self):
        bad = fields(triggerTimeMs=..., targetButtonIds=["cross"], playbackSpeed=True)
        check = rules.check_script(script(fields(step_sequence=7), bad), 70_000)
        self.assertEqual([rules.Step(7, 34000, ("CROSS",), "SEQUENCE", 0.25, 3000)], check.steps)
        self.assertEqual([m("missing_field", 2, "triggerTimeMs"), m("unknown_button", 2, "cross"),
                          m("wrong_type", 2, "playbackSpeed", "số")], check.errors)

    def test_steps_wrapped_in_an_object_are_refused_but_checked_as_if_unwrapped(self):
        # the BA's spiderman3 to 5: {"scriptId": …, "steps": [...]}
        wrapped = json.dumps({"scriptId": "x", "steps": [VALID, fields(step_sequence=2, triggerTimeMs=34000)]})
        check = rules.check_script(wrapped, 70_000)
        self.assertEqual(2, len(check.steps))
        self.assertTrue(check.timeline_checked)
        self.assertIn('các bước nằm trong trường "steps"', check.errors[0])
        self.assertEqual(m("trigger_before_previous_stop", 2, 34000, 1, 34750), check.errors[1])


class ValidatorTest(unittest.TestCase):
    def test_every_rule_one_step_breaks_is_reported_in_the_app_s_order(self):
        broken = fields(step_sequence=-1, triggerTimeMs=-5, targetButtonIds=[], playbackSpeed=0, slowDurationMs=-10)
        self.assertEqual([m("sequence_below_one", -1), m("negative_trigger", -1, -5), m("empty_targets", -1),
                          m("negative_slow_duration", -1, -10), m("zero_speed_duration", -1, -10)], errors(script(broken)))

    def test_speed_is_0_or_from_0_1_to_1(self):
        for speed, accepted in ((0, True), (0.1, True), (1, True), (0.05, False), (1.5, False), (-0.25, False)):
            with self.subTest(speed=speed):
                text = script(fields(playbackSpeed=speed, slowDurationMs=0 if speed == 0 else 3000))
                self.assertEqual([] if accepted else [m("speed_range", 1, repr(float(speed)))], errors(text))

    def test_an_infinite_speed_is_out_of_range_and_never_reaches_the_stop_arithmetic(self):
        for literal, shown in (("Infinity", "Infinity"), ("-Infinity", "-Infinity"), ("1e400", "Infinity")):
            with self.subTest(literal=literal):
                self.assertEqual([m("speed_range", 1, shown)], errors(script(VALID).replace("0.25", literal)))

    def test_six_presses_of_one_button_together_break_both_simultaneous_rules(self):
        text = script(fields(targetButtonIds=["CROSS"] * 6, inputMode="SIMULTANEOUS"))
        self.assertEqual([m("too_many_simultaneous", 1, 6, 5), m("repeated_simultaneous", 1, "CROSS")], errors(text))

    def test_five_simultaneous_buttons_and_repeats_in_sequence_or_any_order_are_accepted(self):
        for mode, targets in (("SIMULTANEOUS", ["L1", "R1", "L2", "R2", "CROSS"]), ("SEQUENCE", ["CROSS", "CROSS"]),
                              ("ANY_ORDER", ["CROSS", "CROSS"])):
            with self.subTest(mode=mode):
                self.assertEqual([], errors(script(fields(targetButtonIds=targets, inputMode=mode))))

    def test_duplicate_sequences_are_reported_once_each_in_order(self):
        text = script(fields(step_sequence=3), fields(step_sequence=1, triggerTimeMs=40000),
                      fields(step_sequence=3, triggerTimeMs=50000), fields(step_sequence=1, triggerTimeMs=60000))
        self.assertEqual([m("duplicate_sequence", 1), m("duplicate_sequence", 3)], errors(text))

    def test_a_trigger_must_be_after_the_previous_stop_in_sequence_order(self):
        # file order is not timeline order: step 2 is listed first
        self.assertEqual([m("trigger_before_previous_stop", 2, 34750, 1, 34750)],
                         errors(script(fields(step_sequence=2, triggerTimeMs=34750), VALID)))
        self.assertEqual([], errors(script(fields(step_sequence=2, triggerTimeMs=34751), VALID)))

    def test_a_stop_must_be_before_the_end_of_the_video(self):
        self.assertEqual([m("stop_after_video_end", 1, 34750, 34750)], errors(script(VALID), 34750))
        self.assertEqual([], errors(script(VALID), 34751))
        self.assertEqual([m("stop_after_video_end", 1, 34750, 0)], errors(script(VALID), 0))

    def test_with_no_video_length_the_end_of_video_rule_is_skipped(self):
        self.assertEqual([], errors(script(VALID), None))

    def test_a_trigger_far_past_the_end_saturates_like_roundToLong(self):
        far = fields(triggerTimeMs=2 ** 63 - 1001, playbackSpeed=1, slowDurationMs=1000)
        self.assertEqual([m("stop_after_video_end", 1, 2 ** 63 - 1, 70000)], errors(script(far)))

    def test_a_non_finite_speed_still_gives_a_stop_to_show(self):
        step = lambda speed: rules.Step(1, 1000, ("CROSS",), "SEQUENCE", speed, 2000)
        self.assertEqual(1000, step(float("nan")).stop_ms)
        self.assertEqual(2 ** 63 - 1, step(float("inf")).stop_ms)
        self.assertEqual(-2 ** 63, step(float("-inf")).stop_ms)
        self.assertEqual(1000, rules.Step(1, 1000, ("CROSS",), "SEQUENCE", float("inf"), 0).stop_ms)  # inf x 0 is NaN

    def test_the_stop_is_rounded_half_up(self):
        self.assertEqual(1001, rules.Step(1, 1000, ("CROSS",), "SEQUENCE", 0.25, 2).stop_ms)
        self.assertEqual(1000, rules.Step(1, 1000, ("CROSS",), "SEQUENCE", 0.25, 1).stop_ms)

    def test_any_other_error_suppresses_the_timeline_rules(self):
        overlapping = fields(step_sequence=2, triggerTimeMs=34000)
        self.assertEqual([m("speed_range", 1, "2.0")], errors(script(fields(playbackSpeed=2), overlapping), 100))
        self.assertEqual([m("missing_field", 3, "slowDurationMs")],
                         errors(script(VALID, overlapping, fields(step_sequence=3, slowDurationMs=...)), 100))

    def test_parser_errors_come_before_step_and_duplicate_errors(self):
        text = script(fields(step_sequence=0), fields(triggerTimeMs="x"), VALID, fields(triggerTimeMs=50000))
        self.assertEqual([m("wrong_type", 2, "triggerTimeMs", "số nguyên"), m("sequence_below_one", 0),
                          m("duplicate_sequence", 1)], errors(text))

    def test_the_sample_script_passes(self):
        sample = Path(__file__).resolve().parent.parent / "demo-sources" / "sample" / "script.json"
        if not sample.is_file():
            self.skipTest("demo-sources is not in git")
        self.assertEqual([], errors(sample.read_text(), 70_000))


class AdviceTest(unittest.TestCase):
    def advise(self, *steps, text=None):
        return advice.advise(rules.check_script(text or script(*steps), 70_000))

    def test_a_field_the_app_skips_is_named_with_the_field_it_was_probably_meant_to_be(self):
        [warning] = self.advise(fields(inputmode="SIMULTANEOUS", targetButtonIds=["L1", "R1"]))
        self.assertIn('"inputmode" nên bỏ qua nó. Có phải ý bạn là "inputMode"?', warning)
        [warning] = self.advise(fields(note="boss fight"))
        self.assertTrue(warning.endswith('trường "note" nên bỏ qua nó.'), warning)

    def test_pause_time_and_a_leftover_legacy_field_are_named(self):
        warnings = self.advise(fields(pauseTimeMs=34750, targetButtonId=["CROSS"]))
        self.assertIn("pauseTimeMs không còn dùng", warnings[0])
        self.assertIn('"targetButtonId"', warnings[1])

    def test_a_short_slow_phase_is_flagged_and_one_too_short_to_score_perfect_more_so(self):
        cases = {(0.25, 1000): None, (0.25, 996): "dài 249 ms", (0.25, 396): "tối đa 50 điểm", (0.25, 1): None}
        for (speed, slow), expected in cases.items():  # 0.25 x 1 = 0 ms of slow video: an immediate stop
            with self.subTest(speed=speed, slow=slow):
                warnings = self.advise(fields(playbackSpeed=speed, slowDurationMs=slow))
                if expected:
                    self.assertIn(expected, " ".join(warnings))
                else:
                    self.assertEqual([], warnings)

    def test_three_buttons_at_once_are_flagged(self):
        [warning] = self.advise(fields(inputMode="SIMULTANEOUS", targetButtonIds=["L1", "R1", "CROSS"]))
        self.assertIn("bấm cùng lúc 3 nút", warning)
        self.assertEqual([], self.advise(fields(inputMode="ANY_ORDER", targetButtonIds=["L1", "R1", "CROSS"])))

    def test_a_script_with_no_steps_is_flagged_but_one_that_is_not_an_array_is_not(self):
        [warning] = self.advise(text="[]")
        self.assertIn("không có bước nào", warning)
        for text in ("[{broken", "{}", "null"):
            with self.subTest(text=text):
                self.assertEqual([], self.advise(text=text))

    def test_steps_are_not_judged_while_the_script_has_errors(self):
        self.assertEqual([], self.advise(fields(slowDurationMs=100), fields(step_sequence=1, triggerTimeMs=50000)))


def kotlin_constants(path: Path):
    return dict(re.findall(r'const val (\w+) = "?([^"\s]+)"?', path.read_text()))


def enum_entries(path: Path, name: str):
    code = re.sub(r"/\*.*?\*/|//[^\n]*", "", path.read_text(), flags=re.S)  # a KDoc may hold a ';'
    body = re.search(r"enum class " + name + r" \{(.*?);", code, re.S).group(1)
    return tuple(re.findall(r"^\s*([A-Z][A-Z0-9_]*),", body, re.M))


def android_strings(prefix: str):
    """values-vi strings named prefix + key, as {key: template}, %1$d / %1$s turned into {0}."""
    found = {}
    for node in ElementTree.parse(APP / "res" / "values-vi" / "strings.xml").getroot().iter("string"):
        name = node.get("name")
        if name.startswith(prefix):
            text = node.text.replace('\\"', '"').replace("\\'", "'")
            found[name[len(prefix):]] = re.sub(r"%(\d)\$[ds]", lambda g: "{" + str(int(g.group(1)) - 1) + "}", text)
    return found


@unittest.skipUnless(APP.is_dir(), "the app's sources are not checked out")
class AppSourcesTest(unittest.TestCase):
    def test_the_buttons_and_modes_are_the_app_s(self):
        self.assertEqual(enum_entries(KOTLIN / "model" / "ControllerButton.kt", "ControllerButton"), rules.BUTTONS)
        self.assertEqual(enum_entries(KOTLIN / "model" / "InputMode.kt", "InputMode"), rules.INPUT_MODES)

    def test_the_limits_and_field_names_are_the_app_s(self):
        validator = kotlin_constants(KOTLIN / "script" / "DemoScriptValidator.kt")
        self.assertEqual(int(validator["MAX_SIMULTANEOUS_BUTTONS"]), rules.MAX_SIMULTANEOUS_BUTTONS)
        self.assertEqual(float(validator["MIN_SLOW_SPEED"]), rules.MIN_SLOW_SPEED)
        parser = kotlin_constants(KOTLIN / "script" / "DemoScriptParser.kt")
        self.assertEqual(rules.FIELDS, tuple(value for key, value in parser.items() if key.startswith("FIELD_")))
        self.assertEqual(parser["LEGACY_FIELD_TARGETS"], rules.LEGACY_TARGETS)

    def test_the_messages_are_the_app_s_vietnamese_strings(self):
        self.assertEqual(android_strings("violation_"), rules.MESSAGES)
        self.assertEqual(android_strings("field_type_"), rules.FIELD_TYPES)


if __name__ == "__main__":
    unittest.main()
