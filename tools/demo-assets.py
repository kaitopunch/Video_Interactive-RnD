#!/usr/bin/env python3
"""Kiểm tra asset của game demo (video + kịch bản) và tạo video để upload lên CMS.

    python3 tools/demo-assets.py check <thư-mục-game> [<thư-mục-game> ...]
    python3 tools/demo-assets.py build <thư-mục-game> [<thư-mục-game> ...]

Mỗi thư mục game chứa:
    video.mp4      video gốc, quay ngang
    script.json    kịch bản, đúng nội dung sẽ dán vào custom_fields.json trên CMS

check   Kiểm tra kịch bản theo đúng luật của app, kiểm tra video gốc, và cho biết video-interpolated.mp4
        còn khớp kịch bản không. Không ghi file nào. Vài giây mỗi game.
build   Chạy check. Nếu không có lỗi, tạo video-interpolated.mp4 — file upload lên custom_fields.source_vid.
        Từ 1 phút tới khoảng 10 phút mỗi game, tuỳ độ dài video và số đoạn chạy chậm.
        Bỏ qua game có video-interpolated.mp4 đã khớp.

Đưa thư mục chứa nhiều game (ví dụ demo-sources) thì xử lý từng game bên trong.
Hướng dẫn đầy đủ: tools/README.md. Cần Python 3.9+ và ffmpeg.
"""

import shutil
import subprocess
import sys
import traceback
from pathlib import Path

import demo_asset_inspection as inspection
from demo_asset_inspection import engine
from demo_build_progress import ProgressLine

UPLOAD = {
    "missing": "chưa có — chạy build để tạo",
    "fresh": "khớp kịch bản và video gốc hiện tại",
    "old_script": "được tạo cho kịch bản cũ (đoạn chạy chậm đã đổi) — chạy build lại rồi upload lại",
    "other_video": "được tạo từ một video.mp4 khác — chạy build lại rồi upload lại",
    "unknown": "không rõ được tạo từ kịch bản nào (tạo bằng tool bản cũ) — chạy build lại cho chắc",
}

FFMPEG_MISSING = """Chưa cài ffmpeg (cần cả ffmpeg và ffprobe). Cài một lần rồi chạy lại:
    macOS:    brew install ffmpeg
    Windows:  winget install Gyan.FFmpeg   (rồi mở lại cửa sổ dòng lệnh)"""


def main(argv) -> int:
    if len(argv) < 2 or argv[0] not in ("check", "build"):
        print(__doc__)
        return 2
    if any(shutil.which(tool) is None for tool in ("ffmpeg", "ffprobe")):
        print(FFMPEG_MISSING)
        return 2
    games = find_games(argv[1:])
    failed = 0
    for folder in games:
        game = inspection.inspect(folder)
        show(game)
        if argv[0] == "build" and not game.errors:
            game = build(game)
        print(verdict(game))
        failed += bool(game.errors)
    if len(games) > 1:
        print(f"\n{len(games)} game: {len(games) - failed} không lỗi, {failed} có lỗi.")
    return 1 if failed else 0


def find_games(args):
    """Each argument as a game's folder; a folder holding several games stands for all of them."""
    games = []
    for arg in args:
        path = Path(arg).expanduser()
        if not path.exists() and (engine.ROOT / "demo-sources" / arg).is_dir():
            path = engine.ROOT / "demo-sources" / arg  # a bare id, as the dev tool takes
        if path.is_file():
            path = path.parent  # script.json or video.mp4 dragged in instead of its folder
        if path.is_dir() and not is_game(path):
            children = sorted(child for child in path.iterdir() if child.is_dir() and is_game(child))
            if children:
                games += children
                continue
        games.append(path)
    return games


def is_game(folder: Path) -> bool:
    return (folder / engine.SOURCE).is_file() or (folder / engine.SCRIPT).is_file()


def build(game):
    """Writes the upload video unless it already matches, then checks the folder again against what was written."""
    if game.upload == "fresh":
        print(f"{engine.TARGET} đã khớp kịch bản, không cần tạo lại.")
        return game
    print(f"Đang tạo {engine.TARGET} — từ 1 tới khoảng 10 phút tuỳ độ dài video, đừng đóng cửa sổ này.")
    try:
        with ProgressLine() as line:
            engine.build(game.folder, report=lambda text: None, progress=line.update)
    except SystemExit as failed:
        game.errors.append(f"Tạo video thất bại. Gửi nguyên văn thông báo sau cho dev:\n{failed.code}")
        print()
        show_item("✗", game.errors[-1])
        return game
    rebuilt = inspection.inspect(game.folder)
    show_upload(rebuilt)
    for error in rebuilt.errors:  # only the end-of-video rule can change: the new file's length is now the one read
        show_item("✗", error)
    return rebuilt


def show(game) -> None:
    print(f"\n━━ {game.folder.name} ━━  {game.folder}")
    video, script = game.video, game.script
    if video:
        shift = f" · bắt đầu ở {float(video.start) * 1000:.0f} ms, video upload dời về 0" if video.start else ""
        print(f"Video gốc     {engine.SOURCE} · {video.width}×{video.height} · {float(video.fps):.4g} fps · "
              f"{clock(video.duration_ms)} · {video.size / 1e6:.1f} MB{shift}")
    if script and script.elements:
        slow = sum(not step.stops_immediately for step in script.steps)
        broken = len(script.elements) - len(script.steps)
        print(f"Kịch bản      {engine.SCRIPT} · {len(script.elements)} bước ({slow} chạy chậm, "
              f"{len(script.steps) - slow} dừng ngay" + (f", {broken} bước sai cấu trúc)" if broken else ")"))
    if script and script.steps:
        print("\n  Bước  Hiện tutorial   Dừng chờ   Chạy chậm         Chế độ        Nút")
        for step in sorted(script.steps, key=lambda step: step.sequence):
            pace = "dừng ngay" if step.stops_immediately else f"{step.speed:g}× · {step.slow_ms} ms"
            print(f"  {step.sequence:>4}  {clock(step.trigger_ms):>13}  {clock(step.stop_ms):>9}   {pace:<16}  "
                  f"{step.mode:<12}  {' '.join(step.targets)}")
    for title, lines, mark in (("LỖI — app sẽ không phát game này", game.errors, "✗"),
                               ("CẢNH BÁO — app vẫn phát, nhưng nên xem lại", game.warnings, "!")):
        if lines:
            print(f"\n{title} ({len(lines)})")
            for line in lines:
                show_item(mark, line)
    show_upload(game)


def show_item(mark: str, line: str) -> None:
    print(f"  {mark} " + line.replace("\n", "\n    "))


def show_upload(game) -> None:
    if game.upload:
        target = game.folder / engine.TARGET
        size = f" · {target.stat().st_size / 1e6:.1f} MB" if target.is_file() else ""
        print(f"\nVideo upload  {engine.TARGET}{size} · {UPLOAD[game.upload]}")


def verdict(game) -> str:
    if game.errors:
        return f"\n✗ CHƯA ĐẠT — {len(game.errors)} lỗi. Sửa các lỗi trên rồi chạy lại."
    if game.upload == "fresh":
        return (f"\n✓ ĐẠT — upload {engine.TARGET} lên source_vid (link mới, đừng ghi đè link cũ), "
                f"và dán nội dung {engine.SCRIPT} vào custom_fields.json.")
    return "\n✓ Kịch bản và video gốc không có lỗi — chạy build để tạo video upload."


def clock(ms: int) -> str:
    if ms < 0:
        return f"{ms} ms"
    minutes, rest = divmod(ms, 60_000)
    return f"{minutes}:{rest / 1000:06.3f}"


def run(argv) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # Vietnamese text on any console
    try:
        return main(argv)
    except KeyboardInterrupt:
        print("\nĐã dừng.")
        return 130
    except (Exception, SystemExit) as unexpected:  # the BA gets a message to forward, never a bare traceback
        if isinstance(unexpected, SystemExit) and unexpected.code in (None, 0):
            return 0
        detail = unexpected.code if isinstance(unexpected, SystemExit) else traceback.format_exc()
        if isinstance(unexpected, subprocess.CalledProcessError):
            detail += f"\n{unexpected.stderr or ''}"
        print(f"\nLỗi không mong đợi. Gửi nguyên văn nội dung dưới đây cho dev:\n{detail}")
        return 3


if __name__ == "__main__":
    sys.exit(run(sys.argv[1:]))
