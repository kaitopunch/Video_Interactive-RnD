"""The warnings demo-assets.py adds to the app's errors: what the app plays but tools/demo-script-format.md advises
against, and fields the app skips without a word. None of them stops a demo from playing.
"""

import difflib
from typing import List

from demo_script_rules import FIELDS, LEGACY_TARGETS, ScriptCheck, Step

DROPPED_PAUSE = "pauseTimeMs"  # confirm.md Q6: the app computes the stop itself

# tools/demo-script-format.md, "Tính điểm": under 250 ms of slow video the PERFECT window is shorter than a reaction;
# under about 100 the video is already waiting when the tutorial appears (LLM.md §11 #11).
ADVISED_SLOW_WINDOW_MS, WAITING_SLOW_WINDOW_MS = 250, 100
COMFORTABLE_SIMULTANEOUS_BUTTONS = 2  # two thumbs


def advise(check: ScriptCheck) -> List[str]:
    warnings = [warning for position, element in enumerate(check.elements, start=1) if isinstance(element, dict)
                for warning in ignored_fields(position, element)]
    if check.timeline_checked:  # every step is well formed: what they ask of the user can be judged
        warnings += step_advice(check.steps)
    return warnings


def step_advice(steps: List[Step]) -> List[str]:
    if not steps:
        return ["Kịch bản không có bước nào: video phát hết mà không hiện tutorial, màn Hoàn thành ghi 0/0 điểm."]
    warnings = []
    for step in sorted(steps, key=lambda step: step.sequence):
        window = step.stop_ms - step.trigger_ms
        if not step.stops_immediately and window < WAITING_SLOW_WINDOW_MS:
            warnings.append(f"Bước {step.sequence}: đoạn chạy chậm chỉ dài {window} ms trên video (playbackSpeed × "
                            f"slowDurationMs), nên video gần như dừng ngay khi tutorial hiện và bước này chỉ được tối "
                            f"đa 50 điểm. Muốn dừng ngay mà vẫn được 100 điểm: đặt playbackSpeed = 0 và slowDurationMs = 0.")
        elif not step.stops_immediately and window < ADVISED_SLOW_WINDOW_MS:
            warnings.append(f"Bước {step.sequence}: đoạn chạy chậm chỉ dài {window} ms trên video (playbackSpeed × "
                            f"slowDurationMs). Nên từ {ADVISED_SLOW_WINDOW_MS} trở lên, nếu không user khó kịp bấm "
                            f"để đạt Hoàn hảo.")
        if step.mode == "SIMULTANEOUS" and len(step.targets) > COMFORTABLE_SIMULTANEOUS_BUTTONS:
            warnings.append(f"Bước {step.sequence}: bấm cùng lúc {len(step.targets)} nút bằng hai ngón cái là khó. "
                            f"Nên chọn 2 nút ở hai bên màn hình, ví dụ L1 + R1.")
    return warnings


def ignored_fields(position: int, element: dict) -> List[str]:
    """Fields the app skips without a word: a typo such as "inputmode" silently makes a step a SEQUENCE."""
    warnings = []
    for field in element:
        if field in FIELDS or (field == LEGACY_TARGETS and "targetButtonIds" not in element):
            continue  # a field the app reads, or the rename it already reports as an error
        if field == DROPPED_PAUSE:
            warnings.append(f"Bước thứ {position} trong file: pauseTimeMs không còn dùng, app tự tính mốc dừng "
                            f"từ triggerTimeMs, playbackSpeed và slowDurationMs nên bỏ qua trường này.")
            continue
        guess = difflib.get_close_matches(field, FIELDS, n=1)
        hint = f" Có phải ý bạn là \"{guess[0]}\"?" if guess else ""
        warnings.append(f"Bước thứ {position} trong file: app không đọc trường \"{field}\" nên bỏ qua nó.{hint}")
    return warnings
