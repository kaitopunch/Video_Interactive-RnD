"""Tests for demo_build_progress.py: the line `demo-assets.py build` keeps on screen while it writes a video.

    python3 -m unittest discover -s tools -v
"""

import io
import os
import threading
import unittest
from unittest import mock

import demo_build_progress as progress
from demo_build_progress import ProgressLine, duration


class Clock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now


class Terminal(io.StringIO):
    def isatty(self):
        return True


class ProgressLineTest(unittest.TestCase):
    def setUp(self):
        self.clock = Clock()

    def line(self, out=None, live=False):
        return ProgressLine(out or io.StringIO(), self.clock, live)

    def at(self, line, seconds, done=None, stage=("interpolate", 1, 22)):
        self.clock.now = 1000.0 + seconds
        if done is not None:
            line.update(done, 100, stage)
        return line.text(self.clock.now)

    def test_no_estimate_before_some_work_is_done_and_a_few_seconds_have_passed(self):
        line = self.line()
        self.assertEqual("  ░░░░░░░░░░░░░░░░░░░░   0% · đang ước tính · đã chạy 0:00 · làm mượt 1/22",
                         self.at(line, 0, 0))
        self.assertIn("đang ước tính", self.at(line, 2, 1))  # one quick stage alone would set it

    def test_time_left_is_the_time_spent_scaled_by_the_share_still_to_do_and_counts_down_until_the_next_report(self):
        line = self.line()
        self.assertEqual("  █████░░░░░░░░░░░░░░░  25% · còn khoảng 3:00 · đã chạy 1:00 · làm mượt 9/22",
                         self.at(line, 60, 25, ("interpolate", 9, 22)))
        self.assertIn("còn khoảng 2:50 · đã chạy 1:10", self.at(line, 70))
        self.assertIn("còn khoảng 2:00 · đã chạy 2:00", self.at(line, 120, 50, ("encode", 1, 2)))

    def test_time_left_run_out_before_the_next_report_says_almost_done(self):
        line = self.line()
        self.at(line, 90, 90, ("check", 1, 1))
        self.assertIn("còn khoảng 0:01", self.at(line, 99))
        self.assertEqual("  ██████████████████░░  90% · sắp xong · đã chạy 1:40 · kiểm tra", self.at(line, 100))

    def test_every_stage_is_named_for_the_ba_and_the_line_fits_80_columns(self):
        line = self.line()
        self.assertIn("· nén video 2/2", self.at(line, 0, 0, ("encode", 2, 2)))
        wide = self.at(line, 13 * 60 + 41, 51, ("interpolate", 22, 22))
        self.assertIn("còn khoảng 13:09 · đã chạy 13:41 · làm mượt 22/22", wide)
        self.assertLess(len(wide), 80)

    def test_the_share_never_shows_100_percent_before_the_end(self):
        line = self.line()
        self.assertIn(" 99% ", self.at(line, 10, 99.9))

    def test_in_a_terminal_each_report_redraws_the_line_in_place_and_success_closes_it_with_the_total_time(self):
        out = Terminal()
        with mock.patch.object(progress, "REDRAW_S", 3600), self.line(out, live=True) as line:
            self.at(line, 60, 25, ("interpolate", 9, 22))
            self.at(line, 61, 26, ("encode", 1, 2))
            self.clock.now = 1000.0 + 6 * 60 + 30
        first, second, closing = out.getvalue().split("\r")[1:]
        self.assertTrue(first.endswith("· làm mượt 9/22"))
        self.assertTrue(second.endswith("· nén video 1/2"))
        self.assertEqual("  ████████████████████ 100% · xong sau 6:30", closing.rstrip())
        self.assertEqual(len(first) + 1, len(closing))  # padded over the longer line before it, then a new line
        self.assertTrue(closing.endswith("\n"))

    def test_in_a_narrow_terminal_the_line_is_cut_to_its_width_rather_than_wrapped(self):
        out = Terminal()
        with mock.patch.object(progress.shutil, "get_terminal_size", lambda: os.terminal_size((46, 24))), \
                mock.patch.object(progress, "REDRAW_S", 3600), self.line(out, live=True) as line:
            self.at(line, 60, 25)
        drawn, closing = out.getvalue().split("\r")[1:]
        self.assertEqual("  █████░░░░░░░░░░░░░░░  25% · còn khoảng 3:00", drawn)  # 45 columns, the last one left free
        self.assertEqual("  ████████████████████ 100% · xong sau 1:00  \n", closing)

    def test_in_a_terminal_the_line_is_redrawn_every_second_while_ffmpeg_says_nothing(self):
        out = Terminal()
        drawn = threading.Semaphore(0)
        write = out.write
        out.write = lambda text: (write(text), drawn.release())[0]
        with mock.patch.object(progress, "REDRAW_S", 0.01), self.line(out, live=True):
            for _ in range(3):
                self.assertTrue(drawn.acquire(timeout=5))
        self.assertGreaterEqual(out.getvalue().count("\r"), 3)

    def test_a_failure_ends_the_line_so_the_error_starts_on_its_own_and_says_nothing_of_success(self):
        out = Terminal()
        with self.assertRaises(SystemExit), self.line(out, live=True) as line:
            self.at(line, 60, 25)
            raise SystemExit("check failed")
        self.assertTrue(out.getvalue().endswith("làm mượt 1/22\n"))
        self.assertNotIn("xong", out.getvalue())

    def test_outside_a_terminal_only_the_closing_line_is_written(self):
        out = io.StringIO()
        with self.line(out) as line:
            self.at(line, 60, 25)
            self.clock.now = 1000.0 + 75
        self.assertEqual("  ████████████████████ 100% · xong sau 1:15\n", out.getvalue())
        out = io.StringIO()
        with self.assertRaises(SystemExit), self.line(out) as line:
            self.at(line, 60, 25)
            raise SystemExit(1)
        self.assertEqual("", out.getvalue())


class DurationTest(unittest.TestCase):
    def test_minutes_and_seconds_then_hours(self):
        self.assertEqual(["0:00", "0:59", "1:01", "59:59", "1:02:05"], [duration(s) for s in (0, 59, 61, 3599, 3725)])


if __name__ == "__main__":
    unittest.main()
