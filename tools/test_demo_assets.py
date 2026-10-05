"""Tests for demo-assets.py and demo_asset_inspection.py: what the BA is told, on 160x120 clips made with the real ffmpeg.

    python3 -m unittest discover -s tools -v

The video checks and the upload video's freshness are read from real files, so this suite skips itself without
ffmpeg and ffprobe. The command line's own edges (usage, a missing ffmpeg, a failed build, a crash) run anyway.
"""

import contextlib
import importlib.util
import io
import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import demo_asset_inspection as inspection
from test_interpolate_slow_segments import step
from test_interpolate_slow_segments_ffmpeg import HAS_FFMPEG, make_video

_spec = importlib.util.spec_from_file_location("demo_assets", Path(__file__).with_name("demo-assets.py"))
cli = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cli)
engine = inspection.engine


def full_step(sequence, trigger_ms, speed, slow_ms, buttons=("CROSS",)):
    return {"step_sequence": sequence, **step(trigger_ms, speed, slow_ms), "targetButtonIds": list(buttons)}


SCRIPT = [full_step(1, 500, 0.25, 1000), full_step(2, 1500, 0, 0)]  # 4 s at 30 fps; one slow phase, 500-750 ms


def run(*argv):
    with contextlib.redirect_stdout(io.StringIO()) as printed:
        code = cli.run(list(argv))
    return code, printed.getvalue()


class CommandLineTest(unittest.TestCase):
    def test_no_command_or_no_folder_prints_the_usage(self):
        for argv in ((), ("check",), ("verify", "x")):
            with self.subTest(argv=argv):
                code, printed = run(*argv)
                self.assertEqual(2, code)
                self.assertIn("python3 tools/demo-assets.py check <thư-mục-game>", printed)

    def test_a_missing_ffmpeg_says_how_to_install_it(self):
        with mock.patch.object(cli.shutil, "which", lambda name: None if name == "ffprobe" else "/usr/bin/ffmpeg"):
            code, printed = run("check", "anything")
        self.assertEqual(2, code)
        self.assertIn("brew install ffmpeg", printed)

    def test_a_crash_becomes_a_message_to_forward_to_a_developer(self):
        with mock.patch.object(cli, "main", lambda argv: 1 / 0):
            code, printed = run("check", "anything")
        self.assertEqual(3, code)
        self.assertIn("Lỗi không mong đợi. Gửi nguyên văn nội dung dưới đây cho dev:", printed)
        self.assertIn("ZeroDivisionError", printed)

    def test_a_missing_folder_is_named(self):
        with mock.patch.object(cli.shutil, "which", lambda name: f"/usr/bin/{name}"):
            code, printed = run("check", "/nonexistent/game")
        self.assertEqual(1, code)
        self.assertIn("Không tìm thấy thư mục /nonexistent/game.", printed)


@unittest.skipUnless(HAS_FFMPEG, "ffmpeg and ffprobe are not on the PATH")
class GameFolderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        scratch = tempfile.TemporaryDirectory()
        cls.addClassCleanup(scratch.cleanup)
        cls.clips = Path(scratch.name)
        make_video(cls.clips / "landscape.mp4", "30", 4)
        make_video(cls.clips / "other.mp4", "30", 4, True, "-vf", "hflip")
        make_video(cls.clips / "vfr.mp4", "30", 2, False, "-vf", "select='not(between(n,10,14))'", "-fps_mode",
                   "passthrough")
        make_video(cls.clips / "late.mp4", "30", 2, False, "-output_ts_offset", "0.5")
        make_video(cls.clips / "portrait.mp4", "30", 4, False, "-vf", "transpose=1")

    def setUp(self):
        scratch = tempfile.TemporaryDirectory()
        self.addCleanup(scratch.cleanup)
        self.root = Path(scratch.name)

    def game(self, name="game", video="landscape.mp4", script=SCRIPT):
        folder = self.root / name
        folder.mkdir()
        if video:
            shutil.copyfile(self.clips / video, folder / "video.mp4")
        if script is not None:
            (folder / "script.json").write_text(script if isinstance(script, str) else json.dumps(script))
        return folder

    def test_a_clean_game_without_an_upload_video_is_sent_to_build(self):
        code, printed = run("check", str(self.game()))
        self.assertEqual(0, code)
        self.assertIn("Video gốc     video.mp4 · 160×120 · 30 fps · 0:04.000", printed)
        self.assertIn("Kịch bản      script.json · 2 bước (1 chạy chậm, 1 dừng ngay)", printed)
        self.assertIn("     1       0:00.500   0:00.750   0.25× · 1000 ms   SEQUENCE      CROSS", printed)
        self.assertIn("video-interpolated.mp4 · chưa có — chạy build để tạo", printed)
        self.assertIn("✓ Kịch bản và video gốc không có lỗi — chạy build để tạo video upload.", printed)

    def test_build_writes_an_upload_video_that_check_then_finds_fresh_and_build_leaves_alone(self):
        folder = self.game()
        code, printed = run("build", str(folder))
        self.assertEqual(0, code, printed)
        self.assertRegex(printed, r"\n  █{20} 100% · xong sau 0:\d\d\n")  # outside a terminal, the closing line alone
        self.assertIn("✓ ĐẠT — upload video-interpolated.mp4 lên source_vid", printed)
        target = folder / "video-interpolated.mp4"
        written = target.stat().st_mtime_ns
        code, printed = run("build", str(folder))
        self.assertIn("video-interpolated.mp4 đã khớp kịch bản, không cần tạo lại.", printed)
        self.assertEqual(written, target.stat().st_mtime_ns)

    def test_the_upload_video_goes_stale_when_a_slow_phase_or_the_source_changes_but_not_otherwise(self):
        folder = self.game()
        run("build", str(folder))
        script = folder / "script.json"
        script.write_text(json.dumps([full_step(1, 500, 0.25, 1000, ("L1", "R1")), full_step(2, 1500, 0, 0)]))
        self.assertEqual("fresh", inspection.inspect(folder).upload)  # buttons are not in the video
        script.write_text(json.dumps([full_step(1, 500, 0.25, 2000), full_step(2, 1500, 0, 0)]))
        self.assertEqual("old_script", inspection.inspect(folder).upload)
        script.write_text(json.dumps(SCRIPT))
        shutil.copyfile(self.clips / "other.mp4", folder / "video.mp4")
        self.assertEqual("other_video", inspection.inspect(folder).upload)

    def test_an_upload_video_without_a_stamp_is_unknown_unless_it_is_the_source_for_a_script_with_no_slow_phase(self):
        folder = self.game()
        shutil.copyfile(self.clips / "other.mp4", folder / "video-interpolated.mp4")
        self.assertEqual("unknown", inspection.inspect(folder).upload)
        (folder / "script.json").write_text(json.dumps([full_step(1, 500, 0, 0)]))
        shutil.copyfile(folder / "video.mp4", folder / "video-interpolated.mp4")
        self.assertEqual("fresh", inspection.inspect(folder).upload)

    def test_missing_files_are_named_with_what_the_folder_holds(self):
        folder = self.game(video=None)
        (folder / "gameplay.mov").write_bytes(b"")
        code, printed = run("check", str(folder))
        self.assertEqual(1, code)
        self.assertIn("Thiếu video.mp4 (video gốc). Thư mục đang có: gameplay.mov, script.json.", printed)
        self.assertIn("✗ CHƯA ĐẠT — 1 lỗi.", printed)

    def test_a_video_the_tool_cannot_use_is_refused_in_the_ba_s_words(self):
        cases = {"vfr.mp4": "số khung hình/giây thay đổi (VFR)", "late.mp4": "bắt đầu ở 500 ms thay vì 0"}
        for video, expected in cases.items():
            with self.subTest(video=video):
                game = inspection.inspect(self.game(video, video, [full_step(1, 500, 0.25, 1000)]))
                self.assertTrue(any(expected in error for error in game.errors), game.errors)
                self.assertIsNone(game.upload)

    def test_an_unreadable_video_is_refused(self):
        folder = self.game(video=None)
        (folder / "video.mp4").write_bytes(b"not a video")
        game = inspection.inspect(folder)
        self.assertIn("video.mp4 không đọc được như một video MP4", game.errors[0])

    def test_a_portrait_video_is_a_warning(self):
        game = inspection.inspect(self.game(video="portrait.mp4"))
        self.assertEqual([], game.errors)
        self.assertIn("video.mp4 đang quay dọc (120×160)", game.warnings[0])

    def test_a_stop_past_the_end_of_the_video_is_the_app_s_error(self):
        game = inspection.inspect(self.game(script=[full_step(1, 3500, 1, 600)]))
        self.assertEqual(["Bước 1: mốc dừng (4100 ms) phải trước khi video kết thúc (4000 ms)."], game.errors)

    def test_an_unreadable_script_is_refused_with_that_error_alone(self):
        folder = self.game(script=None)
        (folder / "script.json").write_bytes("[]".encode("utf-16"))
        [error] = inspection.inspect(folder).errors
        self.assertIn("script.json không phải văn bản UTF-8", error)

    def test_a_non_finite_speed_is_reported_not_crashed_on(self):
        for literal in ("NaN", "Infinity", "-Infinity"):
            with self.subTest(literal=literal):
                text = json.dumps([full_step(1, 500, 0.25, 1000)]).replace("0.25", literal)
                code, printed = run("check", str(self.game(literal, script=text)))
                self.assertEqual(1, code, printed)
                self.assertIn(f"Bước 1: playbackSpeed ({literal}) phải bằng 0", printed)

    def test_a_script_saved_with_a_byte_order_mark_is_read(self):
        folder = self.game(script=None)
        (folder / "script.json").write_bytes(json.dumps(SCRIPT).encode("utf-8-sig"))
        self.assertEqual([], inspection.inspect(folder).errors)

    def test_a_failed_build_is_reported_with_the_message_for_a_developer(self):
        folder = self.game()
        with mock.patch.object(engine, "build", lambda *a, **k: sys.exit("check failed: 1 frames, expected 2")):
            code, printed = run("build", str(folder))
        self.assertEqual(1, code)
        self.assertIn("Tạo video thất bại. Gửi nguyên văn thông báo sau cho dev:\n    check failed", printed)

    def test_a_folder_of_games_a_file_inside_a_game_and_a_bare_id_all_name_games(self):
        self.game("b")
        self.game("a", script=[full_step(1, 500, 0.25, 1000), full_step(1, 2000, 0, 0)])
        (self.root / "notes").mkdir()
        code, printed = run("check", str(self.root))
        self.assertEqual(1, code)
        self.assertLess(printed.index("━━ a ━━"), printed.index("━━ b ━━"))
        self.assertNotIn("notes", printed)
        self.assertIn("2 game: 1 không lỗi, 1 có lỗi.", printed)
        self.assertEqual([self.root / "b"], cli.find_games([str(self.root / "b" / "script.json")]))
        (self.root / "demo-sources").mkdir()
        os.symlink(self.root / "b", self.root / "demo-sources" / "b")
        with mock.patch.object(engine, "ROOT", self.root):
            self.assertEqual([self.root / "demo-sources" / "b"], cli.find_games(["b"]))


if __name__ == "__main__":
    unittest.main()
