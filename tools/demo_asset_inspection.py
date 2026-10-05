"""What demo-assets.py finds in one game's folder. Reads only: nothing here writes a file or prints.

The source video is checked for what interpolate-slow-segments.py needs, in the BA's words, before the tool itself
would refuse it in a developer's. The upload video is matched against the script through the stamp the tool writes
into it, so a script edited after its video was built is caught here instead of stuttering on a phone.
"""

import importlib.util
import subprocess
from dataclasses import dataclass, field
from decimal import Decimal
from fractions import Fraction
from pathlib import Path
from typing import List, Optional

import demo_script_advice as advice
import demo_script_rules as rules

_spec = importlib.util.spec_from_file_location("interpolate_slow_segments",
                                               Path(__file__).with_name("interpolate-slow-segments.py"))
engine = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(engine)

VIDEO_ENTRIES = "stream=r_frame_rate,avg_frame_rate,nb_frames,start_pts,time_base,bit_rate,width,height:format=duration"


@dataclass
class Video:
    fps: Fraction
    frame_count: int
    width: int
    height: int
    start: Fraction
    duration_ms: int
    size: int


@dataclass
class Game:
    folder: Path
    video: Optional[Video] = None
    script: Optional[rules.ScriptCheck] = None
    errors: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)
    upload: Optional[str] = None  # missing, fresh, old_script, other_video or unknown; None while the assets have errors


def inspect(folder: Path) -> Game:
    game = Game(folder)
    if not folder.is_dir():
        game.errors.append(f"Không tìm thấy thư mục {folder}.")
        return game
    source, script, target = (folder / name for name in (engine.SOURCE, engine.SCRIPT, engine.TARGET))
    present = ", ".join(sorted(p.name for p in folder.iterdir() if not p.name.startswith("."))) or "không có file nào"
    for path, what in ((source, "video gốc"), (script, "kịch bản")):
        if not path.is_file():
            game.errors.append(f"Thiếu {path.name} ({what}). Thư mục đang có: {present}. Đặt tên đúng: "
                               f"video gốc là {engine.SOURCE}, kịch bản là {engine.SCRIPT}.")
    game.video = read_video(source, game) if source.is_file() else None
    text = read_script(script, game) if script.is_file() else None
    if text is not None:
        game.script = rules.check_script(text, phone_duration_ms(game.video, target))
        game.errors += game.script.errors
        game.warnings += advice.advise(game.script)
    if game.errors or game.video is None or game.script is None:
        return game

    timings = [{"triggerTimeMs": s.trigger_ms, "playbackSpeed": s.speed, "slowDurationMs": s.slow_ms}
               for s in game.script.steps]
    segments = engine.merge(engine.slow_segments(timings, game.video.fps, game.video.frame_count))
    try:
        engine.h264_level(game.video.width, game.video.height,
                          game.video.fps * max((factor for _, _, factor in segments), default=1))
    except SystemExit:
        game.errors.append(f"{engine.SOURCE}: {game.video.width}×{game.video.height} quá lớn để điện thoại phát "
                           f"đoạn chạy chậm. Dùng video nhỏ hơn, ví dụ 1280×720.")
        return game
    game.upload = upload_status(source, target, segments)
    return game


def read_video(path: Path, game: Game) -> Optional[Video]:
    try:
        probed = engine.ffprobe(path, "v:0", VIDEO_ENTRIES)
        stream = probed["streams"][0]
        int(stream["bit_rate"])  # the tool encodes at the source's bitrate
        video = Video(Fraction(stream["r_frame_rate"]), int(stream["nb_frames"]), int(stream["width"]),
                      int(stream["height"]), int(stream["start_pts"]) * Fraction(stream["time_base"]),
                      to_ms(probed["format"]["duration"]), path.stat().st_size)
        constant = video.fps == Fraction(stream["avg_frame_rate"])
    except (subprocess.CalledProcessError, LookupError, ValueError, ZeroDivisionError, ArithmeticError):
        game.errors.append(f"{path.name} không đọc được như một video MP4 (file hỏng, chưa tải xong, hoặc không phải "
                           f"video). Xuất lại video dạng MP4 (H.264).")
        return None
    if not constant:
        game.errors.append(f"{path.name} có số khung hình/giây thay đổi (VFR). Xuất lại video với số khung hình cố "
                           f"định, ví dụ 30 hoặc 60 fps.")
    if abs(video.start) > engine.MAX_START_S:
        game.errors.append(f"{path.name} bắt đầu ở {float(video.start) * 1000:.0f} ms thay vì 0. "
                           f"Xuất lại video bắt đầu từ 0.")
    if video.height > video.width:
        game.warnings.append(f"{path.name} đang quay dọc ({video.width}×{video.height}). App phát video theo chiều "
                             f"ngang, nên video dọc sẽ nhỏ và có dải đen hai bên.")
    return video


def read_script(path: Path, game: Game) -> Optional[str]:
    try:
        return path.read_bytes().decode("utf-8-sig")
    except UnicodeDecodeError:
        game.errors.append(f"{path.name} không phải văn bản UTF-8. Mở bằng trình soạn thảo và lưu lại dạng UTF-8.")
        return None


def phone_duration_ms(video: Optional[Video], target: Path) -> Optional[int]:
    """The length the app checks stops against: the uploaded file's. Before there is one the source stands in, and the
    shorter is used, so a stop the phone would refuse is never passed here."""
    if video is None:
        return None
    if not target.is_file():
        return video.duration_ms
    try:
        return min(video.duration_ms, to_ms(engine.ffprobe(target, "v:0", "format=duration")["format"]["duration"]))
    except (OSError, subprocess.CalledProcessError, LookupError, ArithmeticError):
        return video.duration_ms  # an unreadable upload file: upload_status says so


def upload_status(source: Path, target: Path, segments) -> str:
    if not target.is_file():
        return "missing"
    try:
        stamp = engine.read_stamp(target)
    except subprocess.CalledProcessError:
        return "unknown"
    source_hash = engine.sha256(source)
    if stamp is None:  # made before stamps; a copy of the source is what the tool writes for a script with no slow phase
        return "fresh" if not segments and engine.sha256(target) == source_hash else "unknown"
    if stamp.get("source_sha256") != source_hash:
        return "other_video"
    return "fresh" if stamp.get("segments") == segments else "old_script"


def to_ms(seconds: str) -> int:
    """ffprobe's seconds as the app reads them: microseconds / 1000, truncated (VideoDurationReader)."""
    return int(Decimal(seconds) * 1000)
