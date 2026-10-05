"""A demo script's rules, as the app checks them: DemoScriptParser + DemoScriptValidator, in Python, so the BA can check
a script before it reaches the CMS (LLM.md §12). Errors read exactly as the app's error screen words them
(values-vi/strings.xml). The warnings the tool adds are demo_script_advice.py's.

test_demo_script_rules.py fails when the app's buttons, modes, limits or messages change without this file.
One divergence: the app's JSON reader takes any bare word as a value (so `abc` is "not a number"); Python's refuses
the whole file. Either way the script does not play.
"""

import json
import math
from dataclasses import dataclass
from typing import List, Optional, Tuple

# ControllerButton, InputMode, DemoScriptValidator's limits, DemoScriptParser's field names.
BUTTONS = ("DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "CROSS", "CIRCLE", "SQUARE", "TRIANGLE",
           "L1", "L2", "R1", "R2", "L3", "R3", "OPTIONS", "CREATE", "PS", "TOUCHPAD")
INPUT_MODES = ("SEQUENCE", "SIMULTANEOUS", "ANY_ORDER")
MAX_SIMULTANEOUS_BUTTONS = 5
MIN_SLOW_SPEED = 0.1
FIELDS = ("step_sequence", "triggerTimeMs", "targetButtonIds", "inputMode", "playbackSpeed", "slowDurationMs")
LEGACY_TARGETS = "targetButtonId"

# values-vi/strings.xml, `violation_<key>` and `field_type_<key>`, with %1$d / %1$s as {0}.
MESSAGES = {
    "not_json_array": "File không phải là một mảng JSON các bước.",
    "step_not_object": "Bước thứ {0} trong file không phải là một object JSON.",
    "missing_field": "Bước thứ {0} trong file: thiếu trường \"{1}\".",
    "wrong_type": "Bước thứ {0} trong file: \"{1}\" phải là {2}.",
    "renamed_field": "Bước thứ {0} trong file: trường \"{1}\" đã đổi tên thành \"{2}\".",
    "unknown_button": "Bước thứ {0} trong file: không có nút nào có ID \"{1}\".",
    "unknown_input_mode": "Bước thứ {0} trong file: inputMode \"{1}\" phải là SEQUENCE, SIMULTANEOUS hoặc ANY_ORDER.",
    "sequence_below_one": "Bước {0}: step_sequence bắt đầu từ 1.",
    "duplicate_sequence": "step_sequence {0} bị trùng ở nhiều bước.",
    "negative_trigger": "Bước {0}: triggerTimeMs ({1}) phải từ 0 trở lên.",
    "empty_targets": "Bước {0}: targetButtonIds phải có ít nhất một nút.",
    "speed_range": "Bước {0}: playbackSpeed ({1}) phải bằng 0 (dừng ngay), hoặc từ 0.1 đến 1.",
    "negative_slow_duration": "Bước {0}: slowDurationMs ({1}) phải từ 0 trở lên.",
    "zero_speed_duration": "Bước {0}: playbackSpeed bằng 0 là dừng ngay, nên slowDurationMs phải bằng 0 (đang là {1}).",
    "too_many_simultaneous": "Bước {0}: bước SIMULTANEOUS có {1} nút, tối đa là {2}.",
    "repeated_simultaneous": "Bước {0}: {1} xuất hiện hai lần trong bước SIMULTANEOUS. "
                             "Một nút không thể nhấn hai lần cùng lúc.",
    "trigger_before_previous_stop": "Bước {0}: triggerTimeMs ({1}) phải sau mốc dừng của bước {2} ({3} ms).",
    "stop_after_video_end": "Bước {0}: mốc dừng ({1} ms) phải trước khi video kết thúc ({2} ms).",
}
FIELD_TYPES = {"integer": "số nguyên", "number": "số", "string": "chuỗi", "string_array": "mảng ID nút"}

LONG_MIN, LONG_MAX, INT_MIN, INT_MAX = -2 ** 63, 2 ** 63 - 1, -2 ** 31, 2 ** 31 - 1


@dataclass(frozen=True)
class Step:
    sequence: int
    trigger_ms: int
    targets: Tuple[str, ...]
    mode: str
    speed: float
    slow_ms: int

    @property
    def stop_ms(self) -> int:
        """TutorialStep.stopPositionMs: summed as a double, rounded half up, saturated to a Long like roundToLong."""
        total = self.trigger_ms + self.speed * self.slow_ms
        if math.isnan(total):
            return self.trigger_ms  # for the report only: a NaN speed is a speed_range error, so no rule reads this
        if math.isinf(total):
            return LONG_MAX if total > 0 else LONG_MIN
        return min(max(math.floor(total + 0.5), LONG_MIN), LONG_MAX)

    @property
    def stops_immediately(self) -> bool:
        return self.stop_ms <= self.trigger_ms


@dataclass
class ScriptCheck:
    steps: List[Step]  # every well-typed step, in file order: the app plays none of them if `errors` is not empty
    errors: List[str]  # what the app's error screen lists, in its order
    elements: list  # the steps as written, for demo_script_advice
    timeline_checked: bool  # the ordering and end-of-video rules ran: they run only when nothing else is wrong


def check_script(text: str, video_duration_ms: Optional[int]) -> ScriptCheck:
    """Every error the app would show for this script. With no duration (the video could not be read), the
    end-of-video rule is skipped: the video's own error says why."""
    elements, root_error = read_root(text)
    steps, errors = [], []
    for position, element in enumerate(elements or [], start=1):
        step = read_step(position, element, errors)
        if step is not None:
            steps.append(step)
    errors += [e for step in steps for e in step_errors(step)]
    sequences = [step.sequence for step in steps]
    errors += [message("duplicate_sequence", s) for s in sorted({s for s in sequences if sequences.count(s) > 1})]
    timeline_checked = elements is not None and not errors
    if timeline_checked:
        errors += timeline_errors(steps, video_duration_ms)
    return ScriptCheck(steps, ([root_error] if root_error else []) + errors, elements or [], timeline_checked)


def message(key: str, *args) -> str:
    return MESSAGES[key].format(*args)


def read_root(text: str):
    """(the step elements, or None when there are none to read; the error that the file is not an array of them)."""
    try:
        root = json.loads(text)
    except ValueError as malformed:
        where = f"dòng {malformed.lineno}, cột {malformed.colno}: " if hasattr(malformed, "lineno") else ""
        return None, f"{message('not_json_array')}\nLỗi cú pháp JSON ở {where}{getattr(malformed, 'msg', malformed)}"
    if isinstance(root, list):
        return root, None
    lists = [key for key, value in root.items() if isinstance(value, list)] if isinstance(root, dict) else []
    if len(lists) == 1:
        # {"scriptId": …, "steps": [...]}: the app reads none of it, but the steps inside are checked as if unwrapped,
        # so the BA fixes everything in one round rather than unwrapping first and meeting the rest next time.
        return root[lists[0]], (f"{message('not_json_array')}\nFile đang là một object, các bước nằm trong trường "
                                f"\"{lists[0]}\". Chỉ giữ phần mảng [ ... ] của \"{lists[0]}\", bỏ phần bao ngoài.")
    hint = ""
    if isinstance(root, str) and root.lstrip().startswith("["):
        hint = "\nKịch bản đang nằm trong dấu ngoặc kép: bỏ cặp ngoặc kép bao ngoài và các dấu \\ trước ngoặc kép."
    return None, message("not_json_array") + hint


def read_step(position: int, element, errors: List[str]) -> Optional[Step]:
    if not isinstance(element, dict):
        errors.append(message("step_not_object", position))
        return None
    before = len(errors)

    def reject(key, *args):
        errors.append(message(key, position, *args))

    def value(field, accepts, type_key):
        if field not in element:
            return reject("missing_field", field)
        if not accepts(element[field]):
            return reject("wrong_type", field, FIELD_TYPES[type_key])
        return element[field]

    sequence = value("step_sequence", is_long, "integer")
    trigger = value("triggerTimeMs", is_long, "integer")
    targets = read_targets(element, reject)
    mode = element.get("inputMode", "SEQUENCE")
    if not isinstance(mode, str):
        mode = reject("wrong_type", "inputMode", FIELD_TYPES["string"])
    elif mode not in INPUT_MODES:
        mode = reject("unknown_input_mode", mode)
    speed = value("playbackSpeed", is_number, "number")
    slow = value("slowDurationMs", is_long, "integer")
    if sequence is not None and not INT_MIN <= sequence <= INT_MAX:
        reject("wrong_type", "step_sequence", FIELD_TYPES["integer"])
    if len(errors) > before:
        return None
    return Step(sequence, trigger, tuple(targets), mode, to_double(speed), slow)


def read_targets(element, reject):
    if "targetButtonIds" not in element:
        if LEGACY_TARGETS in element:
            return reject("renamed_field", LEGACY_TARGETS, "targetButtonIds")
        return reject("missing_field", "targetButtonIds")
    ids = element["targetButtonIds"]
    if not isinstance(ids, list) or not all(isinstance(i, str) for i in ids):
        return reject("wrong_type", "targetButtonIds", FIELD_TYPES["string_array"])
    unknown = [i for i in ids if i not in BUTTONS]
    for i in unknown:  # every one, so a step is never played with one silently dropped
        reject("unknown_button", i)
    return None if unknown else ids


def is_long(v) -> bool:
    return isinstance(v, int) and not isinstance(v, bool) and LONG_MIN <= v <= LONG_MAX


def is_number(v) -> bool:
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def to_double(v) -> float:
    """String.toDouble: an integer too large for a double is infinite, not an error."""
    try:
        return float(v)
    except OverflowError:
        return math.copysign(math.inf, v)


def step_errors(step: Step) -> List[str]:
    """DemoScriptValidator.stepViolations, in its order."""
    s, errors = step.sequence, []
    if s < 1:
        errors.append(message("sequence_below_one", s))
    if step.trigger_ms < 0:
        errors.append(message("negative_trigger", s, step.trigger_ms))
    if not step.targets:
        errors.append(message("empty_targets", s))
    if step.speed != 0 and not MIN_SLOW_SPEED <= step.speed <= 1:  # NaN fails both: out of range, as in Kotlin
        errors.append(message("speed_range", s, kotlin_double(step.speed)))
    if step.slow_ms < 0:
        errors.append(message("negative_slow_duration", s, step.slow_ms))
    if step.speed == 0 and step.slow_ms != 0:
        errors.append(message("zero_speed_duration", s, step.slow_ms))
    if step.mode == "SIMULTANEOUS":
        if len(step.targets) > MAX_SIMULTANEOUS_BUTTONS:
            errors.append(message("too_many_simultaneous", s, len(step.targets), MAX_SIMULTANEOUS_BUTTONS))
        repeated = [b for b in dict.fromkeys(step.targets) if step.targets.count(b) > 1]
        errors += [message("repeated_simultaneous", s, b) for b in repeated]
    return errors


def timeline_errors(steps: List[Step], video_duration_ms: Optional[int]) -> List[str]:
    ordered = sorted(steps, key=lambda step: step.sequence)
    errors = [message("trigger_before_previous_stop", step.sequence, step.trigger_ms, previous.sequence, previous.stop_ms)
              for previous, step in zip(ordered, ordered[1:]) if step.trigger_ms <= previous.stop_ms]
    if video_duration_ms is not None:
        errors += [message("stop_after_video_end", step.sequence, step.stop_ms, video_duration_ms)
                   for step in ordered if step.stop_ms >= video_duration_ms]
    return errors


def kotlin_double(value: float) -> str:
    """Double.toString for the values a script holds: 2.0, 0.05, NaN, Infinity."""
    if math.isnan(value):
        return "NaN"
    if math.isinf(value):
        return "Infinity" if value > 0 else "-Infinity"
    return repr(value)
