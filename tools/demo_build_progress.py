"""The line `demo-assets.py build` keeps on screen while it writes a video: bar, share done, time left, time spent, stage.

    ████████░░░░░░░░░░░░  42% · còn khoảng 3:40 · đã chạy 2:41 · làm mượt 12/22

The share is the engine's, each stage weighed by how long it takes (stage_weights() in interpolate-slow-segments.py).
Time left is the time spent scaled by the share still to do, and counts down between two reports: ffmpeg says
nothing for up to ~15 s at a time (decoding its way to a late slow phase, the final frame check), so in a terminal
the line is redrawn every second regardless. Anywhere else (a log file, a test) only the closing line is written.
"""

import math
import shutil
import sys
import threading
import time

STAGES = {"interpolate": "làm mượt", "encode": "nén video", "check": "kiểm tra"}
BAR = 20  # the line then fits 80 columns, but for a run of over an hour
FIRST_ESTIMATE_S = 3  # sooner, a quick first stage would set the estimate on its own
REDRAW_S = 1


class ProgressLine:
    """Use as `with ProgressLine() as line: engine.build(..., progress=line.update)`. Closes with the total time
    on success, and ends the line on failure so that the error starts on a line of its own."""

    def __init__(self, out=None, clock=time.monotonic, live=None):
        self.out = out or sys.stdout
        self.clock = clock
        self.live = self.out.isatty() if live is None else live
        self.started = clock()
        self.share, self.stage = 0.0, None
        self.estimate = None  # (seconds left, when it was worked out)
        self.width = 0
        self.lock = threading.Lock()
        self.stopped = threading.Event()
        self.ticker = threading.Thread(target=self.tick, daemon=True)

    def __enter__(self):
        if self.live:
            self.ticker.start()
        return self

    def __exit__(self, failure, *details):
        self.stopped.set()
        if self.ticker.is_alive():
            self.ticker.join()
        with self.lock:
            if failure is None:
                self.write(f"  {'█' * BAR} 100% · xong sau {duration(math.floor(self.clock() - self.started))}")
            if self.width or failure is None:
                self.out.write("\n")
                self.out.flush()

    def update(self, done: float, total: float, stage) -> None:
        """The engine's progress callback: [done] of [total] units of work, in [stage] = (name, index, count)."""
        with self.lock:
            now = self.clock()
            self.share, self.stage = min(done / total, 1), stage
            spent = now - self.started
            if done > 0 and spent >= FIRST_ESTIMATE_S:
                self.estimate = (spent * (total - done) / done, now)
            if self.live:
                self.write(self.text(now))

    def tick(self) -> None:
        while not self.stopped.wait(REDRAW_S):
            with self.lock:
                self.write(self.text(self.clock()))

    def text(self, now: float) -> str:
        filled = math.floor(self.share * BAR)
        parts = [f"  {'█' * filled}{'░' * (BAR - filled)} {math.floor(self.share * 100):3}%"]
        if self.estimate is None:
            parts.append("đang ước tính")
        else:
            left, at = self.estimate
            left -= now - at
            parts.append(f"còn khoảng {duration(math.ceil(left))}" if left >= 1 else "sắp xong")
        parts.append(f"đã chạy {duration(math.floor(now - self.started))}")
        if self.stage:
            name, index, count = self.stage
            parts.append(f"{STAGES[name]} {index}/{count}" if count > 1 else STAGES[name])
        return " · ".join(parts)

    def write(self, text: str) -> None:
        """In a terminal, over the previous line: cut to the window's width, as a line that wraps cannot be redrawn in
        place, and padded so that none of a longer one is left behind."""
        if self.live:
            columns = shutil.get_terminal_size().columns - 1
            text = text[:columns]
            text, self.width = "\r" + text.ljust(min(self.width, columns)), max(self.width, len(text))
        self.out.write(text)
        self.out.flush()


def duration(seconds: int) -> str:
    minutes, seconds = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    return f"{hours}:{minutes:02}:{seconds:02}" if hours else f"{minutes}:{seconds:02}"
