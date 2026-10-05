"""interpolate-slow-segments.py end to end, with the real ffmpeg, on 160x120 testsrc2 clips made on the fly.

    python3 -m unittest discover -s tools -v

Skipped when ffmpeg or ffprobe is not on the PATH. Asserts what the tool's own check() cannot see: that every
source frame in the output still shows that source frame's picture, and that the audio is the original's.
"""

import contextlib
import io
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from fractions import Fraction
from pathlib import Path
from unittest import mock

from test_interpolate_slow_segments import NTSC, expected_times, step, tool

HAS_FFMPEG = all(shutil.which(name) for name in ("ffmpeg", "ffprobe"))
TICK = 1 / 15360  # the output's timescale: a timestamp is rounded to the nearest tick
GRAY = 80 * 60  # bytes in one frame decoded to 80x60 grayscale


def ffmpeg(*args, stdout=None):
    return subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args],
                          check=True, stdout=stdout or subprocess.DEVNULL).stdout


def make_video(path: Path, rate="30", seconds=4, audio=True, *extra):
    inputs = ["-f", "lavfi", "-i", f"testsrc2=s=160x120:r={rate}:d={seconds}"]
    if audio:
        inputs += ["-f", "lavfi", "-i", f"sine=f=440:d={seconds}", "-c:a", "aac"]
    ffmpeg(*inputs, *extra, "-c:v", "libx264", "-pix_fmt", "yuv420p", str(path))


def frame_times(path: Path):
    return [float(f["pts_time"]) for f in tool.ffprobe(path, "v:0", "frame=pts_time")["frames"]]


def gray_frames(path: Path):
    # Every frame, at its own time: passthrough drops and repeats none, and the demuxer's time base has room for 120 fps
    raw = ffmpeg("-i", str(path), "-vf", "scale=80:60", "-pix_fmt", "gray", "-fps_mode", "passthrough",
                 "-enc_time_base", "demux", "-f", "rawvideo", "-", stdout=subprocess.PIPE)
    return [raw[i:i + GRAY] for i in range(0, len(raw), GRAY)]


def audio_md5(path: Path):
    return ffmpeg("-i", str(path), "-map", "0:a", "-c", "copy", "-f", "md5", "-", stdout=subprocess.PIPE)


def distance(a: bytes, b: bytes) -> float:
    return sum(abs(x - y) for x, y in zip(a, b)) / GRAY


@unittest.skipUnless(HAS_FFMPEG, "ffmpeg and ffprobe are not on the PATH")
class EndToEndTest(unittest.TestCase):
    # 4 s at 30 fps, 120 frames. In script order: a phase clamped two frames short of the end, one starting on
    # frame 0, a 2x phase touching a 4x one (merged, at 4x), a stop, a separate 2x phase, a step at 1x.
    SCRIPT = [step(3800, 0.25, 2000), step(0, 0.25, 400), step(1000, 0.5, 1000), step(1500, 0.25, 1000),
              step(2000, 0, 0), step(2400, 0.5, 600), step(3000, 1, 1000)]
    SEGMENTS = [[0, 3, 4], [30, 53, 4], [72, 81, 2], [114, 118, 4]]
    # 29.97 fps, no audio, a 3x phase: 1000-1400 ms is frames 29 to 42
    NTSC_SCRIPT = [step(1000, 0.4, 1000)]
    NTSC_SEGMENTS = [[29, 42, 3]]

    # 60 fps whose first frame is 33 ms in, as the BA's spiderman2 is: 500-750 ms is frames 30 to 45
    LATE_SCRIPT = [step(500, 0.25, 1000)]
    LATE_SEGMENTS = [[30, 45, 4]]

    @classmethod
    def setUpClass(cls):
        scratch = tempfile.TemporaryDirectory()
        cls.addClassCleanup(scratch.cleanup)
        cls.root = Path(scratch.name)
        cls.demo("main", cls.SCRIPT)
        cls.demo("ntsc", cls.NTSC_SCRIPT, "30000/1001", 3, False)
        cls.demo("late", cls.LATE_SCRIPT, "60", 2, True, extra=["-output_ts_offset", "0.033"])
        for demo_id in ("main", "ntsc", "late"):
            cls.run_tool(demo_id)

    @classmethod
    def demo(cls, demo_id, script, *video, extra=()):
        folder = cls.root / "demo-sources" / demo_id
        folder.mkdir(parents=True)
        (folder / "script.json").write_text(json.dumps(script))
        make_video(folder / "video.mp4", *video, *extra)
        return folder

    @classmethod
    def run_tool(cls, demo_id):
        with mock.patch.object(tool, "ROOT", cls.root), \
                mock.patch.object(sys, "argv", ["interpolate-slow-segments.py", demo_id]), \
                contextlib.redirect_stdout(io.StringIO()) as printed:
            tool.main()
        return printed.getvalue()

    def paths(self, demo_id):
        folder = self.root / "demo-sources" / demo_id
        return folder / "video.mp4", folder / "video-interpolated.mp4"

    def assertSegments(self, demo_id, script, segments):
        source, _ = self.paths(demo_id)
        fps, frame_count, *_ = tool.probe_video(source)
        self.assertEqual(segments, tool.merge(tool.slow_segments(script, fps, frame_count)))

    def assertTimestamps(self, demo_id, segments):
        source, output = self.paths(demo_id)
        fps, frame_count, *_ = tool.probe_video(source)
        expected = expected_times(fps, frame_count, segments)
        actual = frame_times(output)
        self.assertEqual(len(expected), len(actual))
        for i, (want, got) in enumerate(zip(expected, actual)):
            self.assertAlmostEqual(want, got, delta=TICK, msg=f"frame {i}")

    def assertSourcePictures(self, demo_id):
        """Each output frame on a source frame's time looks like that source frame more than like its neighbours."""
        source, output = self.paths(demo_id)
        fps = tool.probe_video(source)[0]
        originals, frames = gray_frames(source), gray_frames(output)
        times = frame_times(output)
        self.assertEqual(len(times), len(frames))
        checked = 0
        for t, frame in zip(times, frames):
            n = round(t * fps)
            if abs(t * fps - n) >= 0.01:
                continue  # an interpolated frame
            neighbours = [originals[m] for m in (n - 1, n + 1) if 0 <= m < len(originals)]
            own = distance(frame, originals[n])
            self.assertLess(own, min(distance(frame, other) for other in neighbours), f"source frame {n}")
            checked += 1
        self.assertEqual(len(originals), checked)

    def test_the_script_gives_the_segments_this_test_is_written_for(self):
        self.assertSegments("main", self.SCRIPT, self.SEGMENTS)
        self.assertSegments("ntsc", self.NTSC_SCRIPT, self.NTSC_SEGMENTS)

    def test_frames_are_added_only_in_the_slow_phases_and_every_source_frame_keeps_its_time(self):
        self.assertTimestamps("main", self.SEGMENTS)

    def test_ntsc_frame_rate_at_3x(self):
        self.assertTimestamps("ntsc", self.NTSC_SEGMENTS)

    def test_every_source_frame_shows_its_own_picture(self):
        for demo_id in ("main", "ntsc"):
            with self.subTest(demo_id=demo_id):
                self.assertSourcePictures(demo_id)

    def test_the_audio_is_copied_untouched(self):
        source, output = self.paths("main")
        self.assertEqual(audio_md5(source), audio_md5(output))

    def test_a_source_without_audio_gives_an_output_without_audio(self):
        _, output = self.paths("ntsc")
        self.assertEqual([], tool.ffprobe(output, "a", "stream=index")["streams"])

    def test_the_output_is_h264_at_the_stated_level_and_as_long_as_the_source(self):
        source, output = self.paths("main")
        stream = tool.ffprobe(output, "v:0", "stream=codec_name,profile,level,width,height,pix_fmt")["streams"][0]
        self.assertEqual({"codec_name": "h264", "profile": "High", "level": 31, "width": 160, "height": 120,
                          "pix_fmt": "yuv420p"}, stream)
        self.assertEqual("3.1", tool.h264_level(160, 120, Fraction(30) * 4))
        last = frame_times(output)[-1]
        self.assertAlmostEqual(119 / 30, last, delta=TICK)

    def test_running_again_gives_the_same_frames(self):
        _, output = self.paths("ntsc")
        before = frame_times(output)
        self.run_tool("ntsc")
        self.assertEqual(before, frame_times(output))

    def test_a_script_with_no_slow_phase_gives_the_original_byte_for_byte(self):
        folder = self.demo("plain", [step(1000, 0, 0), step(2000, 1, 1000)], "30", 2)
        (folder / "video-interpolated.mp4").write_bytes(b"made for an older script")
        self.run_tool("plain")
        self.assertEqual((folder / "video.mp4").read_bytes(), (folder / "video-interpolated.mp4").read_bytes())

    def test_a_source_starting_33_ms_late_is_moved_to_0_with_its_audio(self):
        source, output = self.paths("late")
        self.assertAlmostEqual(0.033, float(tool.probe_video(source).start), delta=0.001)
        self.assertSegments("late", self.LATE_SCRIPT, self.LATE_SEGMENTS)
        self.assertTimestamps("late", self.LATE_SEGMENTS)
        self.assertSourcePictures("late")
        self.assertEqual(audio_md5(source), audio_md5(output))

    def test_the_output_names_its_source_and_segments(self):
        for demo_id, segments in (("main", self.SEGMENTS), ("late", self.LATE_SEGMENTS)):
            with self.subTest(demo_id=demo_id):
                source, output = self.paths(demo_id)
                self.assertEqual({"source_sha256": tool.sha256(source), "segments": segments}, tool.read_stamp(output))

    def test_a_build_reports_its_progress_from_ffmpeg_s_own_frame_counter(self):
        folder = self.demo("progress", [step(500, 0.25, 1000)], "30", 2, False)  # frames 15-23 at 4x: 60 + 24 frames
        ticks = []
        tool.build(folder, report=lambda line: None, progress=lambda *tick: ticks.append(tick))
        w = tool.stage_weights([[15, 23, 4]], 60, 160 * 120)
        for pass_number in (1, 2):  # x264 said frame=84 at the end of each pass
            last = [done for done, _, stage in ticks if stage == ("encode", pass_number, 2)][-1]
            self.assertAlmostEqual(sum(w[:pass_number + 1]), last)
        done = [tick[0] for tick in ticks]
        self.assertEqual(sorted(done), done)
        self.assertEqual(0, done[0])
        self.assertAlmostEqual(sum(w), done[-1])

    def test_a_variable_rate_source_or_one_not_starting_at_0_is_refused_and_nothing_is_written(self):
        sources = {"vfr": ["-vf", "select='not(between(n,10,14))'", "-fps_mode", "passthrough"],
                   "offset": ["-output_ts_offset", "0.5"]}
        for demo_id, extra in sources.items():
            with self.subTest(demo_id=demo_id):
                folder = self.demo(demo_id, [step(500, 0.25, 1000)], "30", 2, False, extra=extra)
                with self.assertRaises(SystemExit) as raised:
                    self.run_tool(demo_id)
                self.assertIn("must be constant frame rate starting within 0.1 s of 0", str(raised.exception.code))
                self.assertFalse((folder / "video-interpolated.mp4").exists())


if __name__ == "__main__":
    unittest.main()
