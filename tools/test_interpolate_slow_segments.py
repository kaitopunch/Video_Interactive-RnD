"""Unit tests for interpolate-slow-segments.py: its arithmetic, and main()'s flow with ffmpeg faked out.

    python3 -m unittest discover -s tools -v

No ffmpeg needed here. The runs against the real ffmpeg are in test_interpolate_slow_segments_ffmpeg.py, which
imports `tool`, `step` and `expected_times` from this file.
"""

import contextlib
import importlib.util
import io
import itertools
import json
import subprocess
import sys
import tempfile
import unittest
from fractions import Fraction
from pathlib import Path
from unittest import mock

TOOL_PATH = Path(__file__).with_name("interpolate-slow-segments.py")
_spec = importlib.util.spec_from_file_location("interpolate_slow_segments", TOOL_PATH)
tool = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(tool)

FPS = Fraction(30)
NTSC = Fraction(30000, 1001)


def step(trigger_ms, speed, slow_ms):
    return {"triggerTimeMs": trigger_ms, "playbackSpeed": speed, "slowDurationMs": slow_ms}


def expected_times(fps, frame_count, segments):
    """Every source frame at n / fps, plus factor - 1 frames evenly between each segment frame and the next."""
    times = [Fraction(n) for n in range(frame_count)]
    for first, end, factor in segments:
        times += [n + Fraction(j, factor) for n in range(first, end) for j in range(1, factor)]
    return [float(t / fps) for t in sorted(times)]


class ToolTest(unittest.TestCase):
    def assertExits(self, fragment, call, *args, **kwargs):
        with self.assertRaises(SystemExit) as raised:
            call(*args, **kwargs)
        self.assertIn(fragment, str(raised.exception.code))


class SlowSegmentsTest(unittest.TestCase):
    def segments(self, *steps, fps=FPS, frame_count=300):
        return tool.slow_segments(list(steps), fps, frame_count)

    def test_a_slow_phase_runs_from_its_trigger_frame_to_its_stop_frame(self):
        # 11000 + 0.25 x 2000 = 11500 ms, frame 345 at 30 fps: the stop is a source frame, so it is not interpolated
        self.assertEqual([[330, 345, 4]], self.segments(step(11000, 0.25, 2000), frame_count=4170))

    def test_a_step_that_does_not_slow_down_has_no_segment(self):
        # 1010 ms is between two frames: let through, even a phase of 0 ms would cover frame 30
        for speed, slow_ms in [(0, 0), (0, 2000), (1, 2000), (1.5, 2000), (-0.25, 2000), (0.25, 0), (0.25, -100)]:
            with self.subTest(speed=speed, slow_ms=slow_ms):
                self.assertEqual([], self.segments(step(1010, speed, slow_ms)))

    def test_the_factor_is_the_smallest_whole_one_that_restores_the_source_rate(self):
        # 1 / (1/49) is 49.00000000000001 in floating point: without the tolerance it would be 50x
        cases = {0.9: 2, 0.75: 2, 0.5: 2, 0.4: 3, 1 / 3: 3, 0.3: 4, 0.25: 4, 0.2: 5, 0.1: 10, 1 / 49: 49}
        for speed, factor in cases.items():
            with self.subTest(speed=speed):
                [[_, _, actual]] = self.segments(step(1000, speed, 1000))
                self.assertEqual(factor, actual)

    def test_the_stop_is_rounded_half_up_like_TutorialStep_stopPositionMs(self):
        # 1000 + 0.25 x 2 = 1000.5 -> 1001 ms (roundToLong), inside frame 30; 1000.25 -> 1000 ms, frame 30's start
        self.assertEqual([[30, 31, 4]], self.segments(step(1000, 0.25, 2)))
        self.assertEqual([], self.segments(step(1000, 0.25, 1)))

    def test_a_trigger_between_frames_starts_on_the_frame_on_screen_and_a_stop_between_frames_ends_after_it(self):
        # 1010 ms is inside frame 30 (1000-1033 ms); the stop at 1510 ms is inside frame 45, so 45 is interpolated
        self.assertEqual([[30, 46, 2]], self.segments(step(1010, 0.5, 1000)))

    def test_a_phase_at_the_very_start_begins_on_frame_0(self):
        self.assertEqual([[0, 3, 4]], self.segments(step(0, 0.25, 400)))

    def test_a_phase_running_past_the_end_stops_two_frames_short_of_it(self):
        # Frames 298 and 299 are only read, as what frame 297 is interpolated towards
        self.assertEqual([[285, 298, 4]], self.segments(step(9500, 0.25, 4000)))

    def test_a_phase_in_the_last_two_frames_or_past_the_end_has_no_segment(self):
        for trigger_ms in (9934, 9967, 10000, 20000):  # frames 298, 299, 300, 600 of 300
            with self.subTest(trigger_ms=trigger_ms):
                self.assertEqual([], self.segments(step(trigger_ms, 0.25, 4000)))

    def test_a_video_of_two_frames_or_fewer_has_no_segment(self):
        for frame_count in (0, 1, 2):
            with self.subTest(frame_count=frame_count):
                self.assertEqual([], self.segments(step(0, 0.25, 4000), frame_count=frame_count))

    def test_ntsc_frame_rate(self):
        # 29.97 fps: 1000 ms is inside frame 29 (967.7-1001 ms), the stop at 1400 ms inside frame 41
        self.assertEqual([[29, 42, 3]], self.segments(step(1000, 0.4, 1000), fps=NTSC))

    def test_segments_come_sorted_whatever_the_script_order(self):
        steps = [step(5000, 0.5, 1000), step(3000, 0, 0), step(1000, 0.25, 1000)]
        self.assertEqual([[30, 38, 4], [150, 165, 2]], self.segments(*steps))

    def test_a_real_script_ignores_every_field_but_the_three_timings(self):
        script = json.loads("""[
          {"step_sequence": 1, "triggerTimeMs": 5000, "targetButtonIds": ["CROSS"], "playbackSpeed": 0.5, "slowDurationMs": 2000},
          {"step_sequence": 2, "triggerTimeMs": 14000, "targetButtonIds": ["L1", "R1"], "inputMode": "SIMULTANEOUS", "playbackSpeed": 0.25, "slowDurationMs": 2000},
          {"step_sequence": 3, "triggerTimeMs": 34000, "targetButtonIds": ["DPAD_UP", "CROSS"], "inputMode": "SEQUENCE", "playbackSpeed": 0.25, "slowDurationMs": 3000},
          {"step_sequence": 4, "triggerTimeMs": 60000, "targetButtonIds": ["CROSS"], "playbackSpeed": 0, "slowDurationMs": 0}
        ]""")
        # 34000 + 0.25 x 3000 = 34750 ms = frame 1042.5, so 1042 is interpolated too
        self.assertEqual([[150, 180, 2], [420, 435, 4], [1020, 1043, 4]], tool.slow_segments(script, FPS, 2100))


class MergeTest(unittest.TestCase):
    def test_nothing_to_merge(self):
        self.assertEqual([], tool.merge([]))
        self.assertEqual([[30, 45, 4]], tool.merge([[30, 45, 4]]))

    def test_segments_with_a_source_frame_between_them_stay_apart(self):
        self.assertEqual([[30, 45, 4], [46, 60, 2]], tool.merge([[30, 45, 4], [46, 60, 2]]))

    def test_touching_segments_become_one(self):
        self.assertEqual([[30, 60, 4]], tool.merge([[30, 45, 4], [45, 60, 4]]))

    def test_overlapping_and_contained_segments_become_one_at_the_higher_factor(self):
        self.assertEqual([[30, 60, 4]], tool.merge([[30, 50, 2], [40, 60, 4]]))
        self.assertEqual([[30, 60, 4]], tool.merge([[30, 60, 4], [35, 40, 2]]))
        self.assertEqual([[30, 60, 10]], tool.merge([[30, 60, 4], [35, 40, 10]]))

    def test_a_chain_merges_into_one(self):
        self.assertEqual([[0, 40, 5], [50, 60, 2]],
                         tool.merge([[0, 10, 2], [10, 20, 4], [15, 40, 5], [50, 60, 2]]))

    def test_the_input_is_not_modified(self):
        segments = [[30, 45, 2], [40, 60, 4]]
        tool.merge(segments)
        self.assertEqual([[30, 45, 2], [40, 60, 4]], segments)


class H264LevelTest(ToolTest):
    def test_the_lowest_level_that_fits_the_frame_size_and_the_peak_rate(self):
        cases = [
            (160, 120, Fraction(120), "3.1"),
            (854, 480, Fraction(120), "3.2"),   # spiderman: 54 x 30 = 1620 macroblocks, 194 400 a second
            (1280, 720, Fraction(30), "3.1"),   # 3600 and 108 000: both of 3.1's limits exactly
            (1280, 722, Fraction(30), "3.2"),   # 722 is 45.1 macroblocks high: 46 rows, 3680, over 3.1's frame size
            (1280, 720, Fraction(3001, 100), "3.2"),  # 108 036 a second, over 3.1's rate
            (1280, 720, Fraction(120), "4.2"),
            (1920, 1080, Fraction(30), "4.0"),  # 1080 is 67.5 macroblocks high: rounded up to 68
            (1920, 1080, Fraction(60), "4.2"),
            (1080, 1920, Fraction(60), "4.2"),  # portrait: the same number of macroblocks
            (1920, 1080, NTSC * 4, "5.1"),
            (4096, 2304, Fraction(1), "5.1"),   # 36 864 macroblocks: the largest frame any level allows
        ]
        for width, height, fps, level in cases:
            with self.subTest(size=f"{width}x{height}", fps=float(fps)):
                self.assertEqual(level, tool.h264_level(width, height, fps))

    def test_beyond_level_5_2_is_refused(self):
        self.assertExits("beyond H.264 level 5.2", tool.h264_level, 3840, 2160, Fraction(120))
        self.assertExits("beyond H.264 level 5.2", tool.h264_level, 4112, 2304, Fraction(1))  # one column too wide


class ProbeVideoTest(ToolTest):
    STREAM = {"r_frame_rate": "30/1", "avg_frame_rate": "30/1", "nb_frames": "120", "start_time": "0.000000",
              "start_pts": 0, "time_base": "1/15360", "bit_rate": "140586", "width": 160, "height": 120}

    def probe(self, **overrides):
        stream = {**self.STREAM, **overrides}
        with mock.patch.object(tool, "ffprobe", lambda path, select, entries: {"streams": [stream]}):
            return tool.probe_video(Path("video.mp4"))

    def test_a_constant_rate_video_starting_at_0(self):
        self.assertEqual((Fraction(30), 120, 140586, 160, 120, 0), self.probe())

    def test_an_ntsc_rate_is_kept_exact(self):
        fps, *_ = self.probe(r_frame_rate="30000/1001", avg_frame_rate="30000/1001")
        self.assertEqual(NTSC, fps)

    def test_a_variable_rate_video_is_refused(self):
        self.assertExits("must be constant frame rate starting within 0.1 s of 0", self.probe, avg_frame_rate="55/2")

    def test_a_first_frame_up_to_a_tenth_of_a_second_off_0_is_taken_at_its_exact_time(self):
        # the B-frame delay of the BA's spiderman2: 507 ticks of 1/15360 s, which ffprobe prints rounded as 0.033008
        self.assertEqual(Fraction(507, 15360), self.probe(start_pts=507, start_time="0.033008").start)
        self.assertEqual(Fraction(-1, 10), self.probe(start_pts=-1536, start_time="-0.100000").start)

    def test_a_video_starting_further_from_0_is_refused(self):
        self.assertExits("start=0.500000", self.probe, start_pts=7680, start_time="0.500000")
        self.assertExits("start=0.100065", self.probe, start_pts=1537, start_time="0.100065")


class CheckTest(ToolTest):
    SEGMENTS = [[30, 45, 4], [60, 70, 2]]

    def check(self, times, segments=SEGMENTS, fps=FPS, frame_count=120):
        frames = {"frames": [{"pts_time": f"{t:.6f}"} for t in times]}
        with mock.patch.object(tool, "ffprobe", lambda path, select, entries: frames), \
                contextlib.redirect_stdout(io.StringIO()) as printed:
            tool.check(Path("video.mp4"), segments, fps, frame_count)
        return printed.getvalue()

    def test_the_expected_frames_pass_silently(self):
        self.assertEqual("", self.check(expected_times(FPS, 120, self.SEGMENTS)))

    def test_timestamps_rounded_to_the_mp4_timescale_still_pass(self):
        segments = [[29, 42, 3]]
        times = [round(t * 15360) / 15360 for t in expected_times(NTSC, 90, segments)]
        self.check(times, segments, NTSC, 90)

    def test_a_missing_or_an_extra_frame_fails(self):
        times = expected_times(FPS, 120, self.SEGMENTS)
        self.assertExits("174 frames, expected 175", self.check, times[:-1])
        self.assertExits("176 frames, expected 175", self.check, times + [times[-1] + 1 / 30])

    def test_a_repeated_timestamp_fails(self):
        times = expected_times(FPS, 120, self.SEGMENTS)
        times[31] = times[30]
        self.assertExits("not strictly increasing", self.check, times)

    def test_a_source_frame_moved_off_its_time_fails(self):
        times = expected_times(FPS, 120, [])
        times[50] = 50.5 / 30
        self.assertExits("a source frame is missing or has moved", self.check, times, [])

    def test_a_frame_past_the_end_instead_of_an_interpolated_one_fails(self):
        times = expected_times(FPS, 120, self.SEGMENTS)
        times.remove(30.25 / 30)
        self.assertExits("a source frame is missing or has moved", self.check, times + [120 / 30])


class FakeFfmpeg:
    """subprocess.Popen as run() uses it: progress lines on stdout, an error written to stderr, an exit code."""

    def __init__(self, lines=(), error=b"", code=0):
        self.lines, self.error, self.returncode, self.commands = lines, error, code, []

    def __call__(self, command, stdout, stderr, text):
        self.commands.append(command)
        stderr.write(self.error)
        self.stdout = iter(self.lines)
        return self

    def __enter__(self):
        return self

    def __exit__(self, *failure):
        return False


class SubprocessTest(ToolTest):
    def test_run_quiets_ffmpeg_overwrites_and_passes_on_the_frame_counts_it_reports(self):
        ffmpeg = FakeFfmpeg(["frame=0\n", "fps=0.00\n", "progress=continue\n", "frame=12\n", "progress=end\n"])
        counts = []
        with mock.patch.object(tool.subprocess, "Popen", ffmpeg):
            tool.run(["ffmpeg", "-i", "in.mp4", "out.mp4"], counts.append)
            tool.run(["ffmpeg", "-i", "in.mp4", "out.mp4"])
        self.assertEqual(["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostats", "-progress", "pipe:1", "-y",
                          "-i", "in.mp4", "out.mp4"], ffmpeg.commands[0])
        self.assertEqual([0, 12], counts)

    def test_an_interrupted_run_stops_ffmpeg(self):
        ffmpeg = FakeFfmpeg(["frame=1\n"])
        ffmpeg.kill = lambda: setattr(ffmpeg, "killed", True)

        def interrupted(count):
            raise KeyboardInterrupt

        with mock.patch.object(tool.subprocess, "Popen", ffmpeg), self.assertRaises(KeyboardInterrupt):
            tool.run(["ffmpeg", "-i", "in.mp4", "out.mp4"], interrupted)
        self.assertTrue(ffmpeg.killed)

    def test_a_failed_run_exits_with_ffmpeg_s_error(self):
        with mock.patch.object(tool.subprocess, "Popen", FakeFfmpeg(error="Invalid data found ü".encode(), code=1)):
            self.assertExits("ffmpeg -copyts -i... failed:\nInvalid data found ü",
                             tool.run, ["ffmpeg", "-copyts", "-i", "in.mp4", "out.mp4"])

    def test_ffprobe_reads_the_entries_as_json(self):
        calls = []
        answer = lambda command, **kwargs: calls.append(command) or subprocess.CompletedProcess(command, 0, '{"streams": []}', "")
        with mock.patch.object(tool.subprocess, "run", answer):
            self.assertEqual({"streams": []}, tool.ffprobe(Path("in.mp4"), "v:0", "stream=width"))
        self.assertEqual([["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width",
                           "-of", "json", "in.mp4"]], calls)

    def test_a_source_starting_at_0_is_read_as_it_is_and_one_starting_later_is_moved_to_0(self):
        self.assertEqual(["-copyts", "-i", "in.mp4"], tool.source_input(Path("in.mp4"), Fraction(0)))
        self.assertEqual(["-copyts", "-itsoffset", "-0.033008", "-i", "in.mp4"],
                         tool.source_input(Path("in.mp4"), Fraction(507, 15360)))

    def test_the_command_line_without_a_demo_id_prints_the_usage(self):
        result = subprocess.run([sys.executable, str(TOOL_PATH)], capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertIn("python3 tools/interpolate-slow-segments.py <demo-id>", result.stderr)


class StampTest(ToolTest):
    def read(self, tags):
        answer = {"format": {"tags": tags}} if tags is not None else {"format": {}}
        with mock.patch.object(tool, "ffprobe", lambda path, select, entries: answer):
            return tool.read_stamp(Path("video-interpolated.mp4"))

    def test_the_stamp_names_the_source_by_its_hash_and_the_segments_built(self):
        with tempfile.TemporaryDirectory() as scratch:
            source = Path(scratch) / "video.mp4"
            source.write_bytes(b"original")
            text = tool.stamp(source, [[30, 45, 4]])
        self.assertTrue(text.startswith(tool.STAMP_PREFIX))
        self.assertEqual({"source_sha256": "0682c5f2076f099c34cfdd15a9e063849ed437a49677e6fcc5b4198c76575be5",
                          "segments": [[30, 45, 4]]}, self.read({"comment": text}))

    def test_an_output_without_the_tool_s_stamp_has_none(self):
        for tags in (None, {}, {"comment": "made in an editor"}, {"comment": tool.STAMP_PREFIX + "{broken"}):
            with self.subTest(tags=tags):
                self.assertIsNone(self.read(tags))


class MainTest(ToolTest):
    """main() in a scratch ROOT, with ffprobe and every ffmpeg run replaced by fakes."""

    PROBE = tool.Video(FPS, 120, 100000, 160, 120, Fraction(0))

    def setUp(self):
        scratch = tempfile.TemporaryDirectory()
        self.addCleanup(scratch.cleanup)
        self.root = Path(scratch.name)
        self.demo = self.root / "demo-sources" / "demo"
        self.demo.mkdir(parents=True)
        (self.demo / "video.mp4").write_bytes(b"original")
        self.target = self.demo / "video-interpolated.mp4"
        self.calls = {}

    def write_script(self, *steps):
        (self.demo / "script.json").write_text(json.dumps(list(steps)))

    def main(self, *args, probe=PROBE, check=None, which=lambda name: f"/usr/bin/{name}", root=None, call=None):
        def interpolate(source, segment, fps, start, out):
            out.write_bytes(b"part")
            self.calls.setdefault("interpolate", []).append((segment, start, out.name))
            return out

        def assemble(source, start, segments, parts, bitrate, level, stamp_text, work, out, encoded):
            self.assertTrue(all(part.is_file() for part in parts))
            out.write_bytes(b"interpolated")
            self.calls["assemble"] = (start, segments, bitrate, level, stamp_text)
            encoded(1, tool.output_frames(segments, probe.frame_count) // 2)
            encoded(2, 10 ** 6)  # x264 counts no more than it writes; a share past the whole is held at the whole

        def fake_check(out, segments, fps, frame_count):
            self.calls["check"] = (out.read_bytes(), segments, fps, frame_count)
            if check:
                check()

        with mock.patch.object(tool, "ROOT", root or self.root), \
                mock.patch.object(tool.shutil, "which", which), \
                mock.patch.object(sys, "argv", ["interpolate-slow-segments.py", *args]), \
                mock.patch.object(tool, "probe_video", lambda path: probe), \
                mock.patch.object(tool, "interpolate", interpolate), \
                mock.patch.object(tool, "assemble", assemble), \
                mock.patch.object(tool, "check", fake_check), \
                contextlib.redirect_stdout(io.StringIO()) as printed:
            (call or tool.main)()
        return printed.getvalue()

    def test_anything_but_one_demo_id_prints_the_usage(self):
        for args in ((), ("demo", "extra")):
            with self.subTest(args=args):
                with self.assertRaises(SystemExit) as raised:
                    self.main(*args)
                self.assertEqual(tool.__doc__, raised.exception.code)

    def test_a_missing_ffmpeg_or_ffprobe_is_named(self):
        self.write_script(step(1000, 0.25, 2000))
        for missing in ("ffmpeg", "ffprobe"):
            which = lambda name, missing=missing: None if name == missing else f"/usr/bin/{name}"
            with self.subTest(missing=missing):
                with self.assertRaises(SystemExit) as raised:
                    self.main("demo", which=which)
                self.assertIn(f"{missing} is not on the PATH", raised.exception.code)

    def test_a_missing_input_is_named_relative_to_the_repo(self):
        self.assertExits("missing demo-sources/demo/script.json", self.main, "demo")
        self.assertExits("missing demo-sources/other/video.mp4", self.main, "other")
        self.assertFalse(self.target.exists())

    def test_a_broken_script_fails_before_touching_the_output(self):
        (self.demo / "script.json").write_text("[{")
        self.target.write_bytes(b"previous")
        with self.assertRaises(json.JSONDecodeError):
            self.main("demo")
        self.assertEqual(b"previous", self.target.read_bytes())

    def test_a_script_with_no_slow_phase_replaces_the_output_with_the_original(self):
        self.write_script(step(1000, 0, 0), step(2000, 1, 1000), step(10000, 0.25, 2000))  # the last is past the end
        self.target.write_bytes(b"made for an older script")
        printed = self.main("demo")
        self.assertEqual(b"original", self.target.read_bytes())
        self.assertIn("no slow phase; copied the original to demo-sources/demo/video-interpolated.mp4", printed)
        self.assertNotIn("interpolate", self.calls)

    def test_slow_phases_are_merged_interpolated_assembled_checked_and_written(self):
        self.write_script(step(1500, 0.25, 1000), step(1000, 0.5, 1000), step(3000, 0.5, 600))
        printed = self.main("demo")
        segments = [[30, 53, 4], [90, 99, 2]]  # 1000-1500 ms at 2x and 1500-1750 ms at 4x touch
        self.assertEqual([(segments[0], 0, "segment-0.nut"), (segments[1], 0, "segment-1.nut")],
                         self.calls["interpolate"])
        stamp = tool.stamp(self.demo / "video.mp4", segments)
        self.assertEqual((0, segments, 110000, "3.1", stamp), self.calls["assemble"])  # bitrate + 10 %, 120 fps peak
        self.assertEqual((b"interpolated", segments, FPS, 120), self.calls["check"])
        self.assertEqual(b"interpolated", self.target.read_bytes())
        self.assertIn("    1000-1767    ms  4x  +69 frames", printed)
        self.assertIn("    3000-3300    ms  2x  +9 frames", printed)
        self.assertIn("wrote demo-sources/demo/video-interpolated.mp4", printed)

    def test_a_source_starting_after_0_is_moved_to_0_in_every_ffmpeg_run(self):
        self.write_script(step(1000, 0.25, 2000))
        start = Fraction(507, 15360)
        self.main("demo", probe=self.PROBE._replace(start=start))
        self.assertEqual(start, self.calls["interpolate"][0][1])
        self.assertEqual(start, self.calls["assemble"][0])

    def test_build_reports_each_stage_s_share_of_the_work_and_takes_a_folder_outside_the_repo(self):
        self.write_script(step(1000, 0.25, 2000), step(3000, 0.5, 600))
        ticks, lines = [], []
        self.main(root=self.root / "repo", call=lambda: tool.build(self.demo, lines.append, lambda *t: ticks.append(t)))
        segments = [segment for segment, _, _ in self.calls["interpolate"]]
        w = tool.stage_weights(segments, 120, 160 * 120)
        at = [0, *itertools.accumulate(w)]
        self.assertEqual([(0, at[5], ("interpolate", 1, 2)),
                          (at[1], at[5], ("interpolate", 2, 2)),
                          (at[2], at[5], ("encode", 1, 2)),
                          (at[2] + w[2] / 2, at[5], ("encode", 1, 2)),  # 87 of the 174 frames
                          (at[4], at[5], ("encode", 2, 2)),  # each stage ends exactly where the next one starts
                          (at[4], at[5], ("check", 1, 1)),
                          (at[5], at[5], ("check", 1, 1))], ticks)
        self.assertEqual(f"wrote {self.target} (0.0 MB, source 0.0 MB)", lines[-1])

    def test_progress_weighs_a_stage_by_the_frames_it_decodes_makes_and_encodes(self):
        far, near = tool.stage_weights([[0, 10, 4], [600, 610, 4]], 1200, 10 ** 6)[:2]
        self.assertAlmostEqual(600 * tool.SEEK_COST, near - far)  # trim decodes every frame before a segment
        self.assertAlmostEqual(12 * 4 * tool.INTERPOLATE_COST, far)
        passes = tool.stage_weights([[0, 10, 4]], 1200, 2 * 10 ** 6)[1:]
        self.assertEqual([2 * cost * 1230 for cost in (*tool.PASS_COSTS, tool.CHECK_COST)], passes)

    def test_a_failed_check_leaves_the_previous_output_untouched(self):
        self.write_script(step(1000, 0.25, 2000))
        self.target.write_bytes(b"previous")
        self.assertExits("check failed", self.main, "demo", check=lambda: sys.exit("check failed: 1 frames"))
        self.assertEqual(b"previous", self.target.read_bytes())
        self.assertEqual({"video.mp4", "script.json", "video-interpolated.mp4"}, {p.name for p in self.demo.iterdir()})

    def test_a_frame_too_large_for_h264_is_refused_before_any_ffmpeg_run(self):
        self.write_script(step(1000, 0.25, 2000))
        self.assertExits("beyond H.264 level 5.2", self.main, "demo", probe=self.PROBE._replace(width=3840, height=2160))
        self.assertNotIn("interpolate", self.calls)
        self.assertFalse(self.target.exists())


if __name__ == "__main__":
    unittest.main()
