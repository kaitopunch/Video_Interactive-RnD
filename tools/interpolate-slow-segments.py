#!/usr/bin/env python3
"""Makes a demo's slow-motion phases play smoothly by motion-interpolating them.

A 30 fps video played at 0.25x shows 7.5 frames a second, which stutters. For every step that slows the
video, this inserts interpolated frames between the step's trigger and its stop point, so the slow phase
still shows the source frame rate on screen. Every source frame keeps its timestamp, and the audio is
copied untouched (the output is variable frame rate), so the triggers and stop points in script.json still
land on the same scene. A stop on a source frame's time (11500 ms at 30 fps) pauses on that source frame.
A source whose first frame is a little after 0 (B-frame delay, 33 ms on the BA's spiderman2) is moved to 0.

Only the slow phases are interpolated, not the whole video: the file the phone streams grows by a couple of
MB instead of several times over. At the slow speed the phone decodes what it did before (120 fps x 0.25 = 30 a second);
only when a step is completed early, or during the 0.3 s ramp, does it decode the rest of a 120 fps stretch
at up to 1x, for half a second at most.

    python3 tools/interpolate-slow-segments.py <demo-id>

reads   demo-sources/<demo-id>/video.mp4                the original, never uploaded
        demo-sources/<demo-id>/script.json              a copy of the game's custom_fields.json in the catalogue
writes  demo-sources/<demo-id>/video-interpolated.mp4   the file to upload as the game's source_vid

Run it again whenever triggerTimeMs, playbackSpeed or slowDurationMs change, and upload the result: the app
streams whatever source_vid points at, so a video built for an older script stutters in the new slow phases.
The output carries the slow phases and the source's hash it was built from, which demo-assets.py reads back to
tell the BA when it no longer matches. It always starts from the original, so running it twice is harmless.
Needs ffmpeg and ffprobe on the PATH. The BA runs it through `python3 tools/demo-assets.py build`, which checks
the script first.
"""

import hashlib
import itertools
import json
import math
import shutil
import subprocess
import sys
import tempfile
from fractions import Fraction
from pathlib import Path
from typing import NamedTuple

ROOT = Path(__file__).resolve().parent.parent
SOURCE, SCRIPT, TARGET = "video.mp4", "script.json", "video-interpolated.mp4"

# A first frame this close to 0 is moved to 0, the audio with it. Further off, the BA's scene times are suspect: the
# player they were read in may or may not have counted the gap.
MAX_START_S = 0.1

# The output's comment tag: what it was built from, read back by read_stamp().
STAMP_PREFIX = "ps-remote-slow-segments "

# Motion-compensated interpolation, bidirectional search, overlapped blocks: the best quality ffmpeg's own
# filter offers. Chosen over an AI interpolator on 2026-10-01; fast camera moves still show some warping.
MINTERPOLATE = "mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1"

# Re-encoding is unavoidable (frames are added). Two passes at the source's own bitrate, plus a margin for
# the added frames, keep the upload the size it was: a CRF encode of this already-compressed gameplay came out
# at twice the source's size even at CRF 20, and was still larger at CRF 27.
X264 = ["-c:v", "libx264", "-preset", "slow", "-profile:v", "high", "-pix_fmt", "yuv420p"]
BITRATE_MARGIN = 1.1

# The level is stated outright: left to itself x264 reads the variable frame rate as 6.2, which some phone
# decoders refuse. (level, macroblocks per second, macroblocks per frame), H.264 Table A-1; the lowest one
# that fits the frame size at the highest interpolated frame rate is used.
H264_LEVELS = [("3.1", 108000, 3600), ("3.2", 216000, 5120), ("4.0", 245760, 8192),
               ("4.2", 522240, 8704), ("5.1", 983040, 36864), ("5.2", 2073600, 36864)]

# How long each kind of work takes, for the progress bar: seconds per frame of a million pixels, timed on an M2 Pro on
# 2026-10-02 (spiderman's whole build; one late segment and the check of spiderman2). They predict 59 s for spiderman
# (61 s measured) and 455 s for spiderman2 (390 s).
# Only the ratios matter: the bar shows the share of the work done, and the time left follows from the time it took.
SEEK_COST = 0.0006          # a source frame decoded on the way to a slow phase: trim reads from frame 0
INTERPOLATE_COST = 0.09     # a frame minterpolate makes, on one core
PASS_COSTS = (0.0035, 0.0086)  # an output frame through x264's first pass, and through its second
CHECK_COST = 0.0015         # an output frame ffprobe decodes for check()


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    build(ROOT / "demo-sources" / sys.argv[1])


def build(folder: Path, report=print, progress=None) -> None:
    """Writes folder/video-interpolated.mp4 from folder's video.mp4 and script.json. Exits, the previous output
    untouched, on anything wrong. [progress] is called with (done, total, stage) as each stage starts and, while
    x264 encodes, about twice a second: done and total in the units of stage_weights(), stage one of
    ("interpolate", i, segments), ("encode", 1 or 2, 2) and ("check", 1, 1)."""
    source, script, target = folder / SOURCE, folder / SCRIPT, folder / TARGET
    for tool in ("ffmpeg", "ffprobe"):
        if shutil.which(tool) is None:
            sys.exit(f"{tool} is not on the PATH (brew install ffmpeg)")
    for path in (source, script):
        if not path.is_file():
            sys.exit(f"missing {shown(path)}")

    fps, frame_count, bitrate, width, height, start = probe_video(source)
    segments = merge(slow_segments(json.loads(script.read_text(encoding="utf-8-sig")), fps, frame_count))
    if not segments:
        # Nothing slows down any more: the uploaded video must not keep frames for phases that are gone.
        shutil.copyfile(source, target)
        report(f"script.json has no slow phase; copied the original to {shown(target)}")
        return

    level = h264_level(width, height, fps * max(factor for _, _, factor in segments))
    weights = stage_weights(segments, frame_count, width * height)
    # Running sums, not sum(): from Python 3.12 that one compensates, and a stage's end missed the next one's start.
    starts = [0.0, *itertools.accumulate(weights)]
    stages = [("interpolate", i + 1, len(segments)) for i in range(len(segments))]
    stages += [("encode", 1, 2), ("encode", 2, 2), ("check", 1, 1)]
    frames = output_frames(segments, frame_count)

    def reached(stage: int, share: float = 0.0) -> None:
        if progress:
            progress(starts[stage] + weights[stage] * min(share, 1.0), starts[-1], stages[stage])

    encode = len(segments)
    with tempfile.TemporaryDirectory() as work:
        parts = []
        for i, segment in enumerate(segments):
            reached(i)
            parts.append(interpolate(source, segment, fps, start, Path(work) / f"segment-{i}.nut"))
        output = Path(work) / "video.mp4"
        reached(encode)
        assemble(source, start, segments, parts, int(bitrate * BITRATE_MARGIN), level, stamp(source, segments),
                 Path(work), output, lambda pass_number, done: reached(encode + pass_number - 1, done / frames))
        reached(encode + 2)
        check(output, segments, fps, frame_count)
        reached(encode + 2, 1.0)
        shutil.move(str(output), target)

    for first, end, factor in segments:
        report(f"  {float(first / fps) * 1000:8.0f}-{float(end / fps) * 1000:<8.0f}ms  {factor}x  "
               f"+{(end - first) * (factor - 1)} frames")
    report(f"wrote {shown(target)} ({target.stat().st_size / 1e6:.1f} MB, source {source.stat().st_size / 1e6:.1f} MB)")


def shown(path: Path) -> str:
    """Relative to the repo when inside it: the BA may keep a game's folder anywhere."""
    try:
        return str(path.resolve().relative_to(ROOT.resolve()))
    except ValueError:
        return str(path)


class Video(NamedTuple):
    fps: Fraction
    frame_count: int
    bitrate: int
    width: int
    height: int
    start: Fraction  # the first frame's time in seconds, exact: start_pts x time_base


def probe_video(path: Path) -> Video:
    """The source's frame rate, frame count, video bitrate, size and first timestamp. Refuses anything but a
    constant-rate video starting within MAX_START_S of 0."""
    entries = "stream=r_frame_rate,avg_frame_rate,nb_frames,start_time,start_pts,time_base,bit_rate,width,height"
    stream = ffprobe(path, "v:0", entries)["streams"][0]
    fps = Fraction(stream["r_frame_rate"])
    start = Fraction(int(stream["start_pts"])) * Fraction(stream["time_base"])
    if fps != Fraction(stream["avg_frame_rate"]) or abs(start) > MAX_START_S:
        sys.exit(f"{path.name} must be constant frame rate starting within {MAX_START_S} s of 0 "
                 f"(r={stream['r_frame_rate']} avg={stream['avg_frame_rate']} start={stream['start_time']})")
    return Video(fps, int(stream["nb_frames"]), int(stream["bit_rate"]), int(stream["width"]), int(stream["height"]),
                 start)


def h264_level(width: int, height: int, peak_fps: Fraction) -> str:
    frame = math.ceil(width / 16) * math.ceil(height / 16)
    for level, per_second, per_frame in H264_LEVELS:
        if frame <= per_frame and frame * peak_fps <= per_second:
            return level
    sys.exit(f"{width}x{height} at {float(peak_fps):.0f} fps is beyond H.264 level 5.2: use a smaller source")


def slow_segments(steps, fps: Fraction, frame_count: int):
    """[first source frame, end frame exclusive, factor] per slowing step, from its trigger to its stop."""
    segments = []
    for step in steps:
        speed = float(step["playbackSpeed"])
        slow_ms = int(step["slowDurationMs"])
        if not 0 < speed < 1 or slow_ms <= 0:
            continue  # stops at once, or does not slow down: nothing to smooth
        trigger_ms = int(step["triggerTimeMs"])
        stop_ms = math.floor(trigger_ms + speed * slow_ms + 0.5)  # TutorialStep.stopPositionMs, half up
        first = math.floor(trigger_ms * fps / 1000)
        # Not past the stop: the picture held while the video waits is then a source frame, not an
        # invented one, whenever the stop falls on a frame's time. Two frames short of the end at most,
        # because interpolating towards frame `end` needs the one after it too.
        end = min(math.ceil(stop_ms * fps / 1000), frame_count - 2)
        factor = math.ceil(1 / speed - 1e-9)  # 0.25 -> 4x, 0.3 -> 4x, 0.5 -> 2x
        if end > first:  # a slow phase in the last two frames has nothing left to interpolate
            segments.append([first, end, factor])
    return sorted(segments)


def merge(segments):
    """Touching or overlapping segments become one, at the higher factor of the two."""
    merged = []
    for segment in segments:
        if merged and segment[0] <= merged[-1][1]:
            merged[-1][1] = max(merged[-1][1], segment[1])
            merged[-1][2] = max(merged[-1][2], segment[2])
        else:
            merged.append(list(segment))
    return merged


def output_frames(segments, frame_count: int) -> int:
    return frame_count + sum((end - first) * (factor - 1) for first, end, factor in segments)


def stage_weights(segments, frame_count: int, pixels: int):
    """Each stage's expected length in M2 Pro seconds: one per segment, then x264's two passes, then check()."""
    megapixels = pixels / 1e6
    # minterpolate makes frames up to `end + 2` (see interpolate()) before select drops the last of them.
    segment_costs = [megapixels * (SEEK_COST * first + INTERPOLATE_COST * (end + 2 - first) * factor)
                     for first, end, factor in segments]
    frames = output_frames(segments, frame_count)
    return segment_costs + [megapixels * cost * frames for cost in (*PASS_COSTS, CHECK_COST)]


def source_input(source: Path, start: Fraction):
    """The source as an ffmpeg input with its timestamps kept, and moved so its first frame is at 0. Moved here, on every
    stream alike, not with a setpts filter: the audio stays in sync, and a filtered last frame lost its duration and
    with it its place in the mp4's edit list."""
    offset = ["-itsoffset", f"{float(-start):.6f}"] if start else []
    return ["-copyts", *offset, "-i", str(source)]


def interpolate(source: Path, segment, fps: Fraction, start: Fraction, out: Path) -> Path:
    """Source frames [first, end) at factor x the frame rate, losslessly, at their original timestamps."""
    first, end, factor = segment
    # Two frames past the segment are decoded too: minterpolate interpolates towards frame `end`, and its
    # bidirectional search needs the one after that, or it drops the last interval at end of input.
    graph = (f"trim=start_frame={first}:end_frame={end + 2},"
             f"minterpolate=fps={fps * factor}:{MINTERPOLATE},"
             # Half an *output* frame before `end`: source frame `end` itself belongs to the base stream.
             f"select='lt(t\\,{float((end - Fraction(1, 2 * factor)) / fps)})'")
    run(["ffmpeg", *source_input(source, start), "-an", "-vf", graph,
         "-fps_mode", "passthrough", "-c:v", "ffv1", str(out)])
    return out


def assemble(source: Path, start: Fraction, segments, parts, bitrate: int, level: str, stamp_text: str, work: Path,
             out: Path, encoded=lambda pass_number, frames: None) -> None:
    """The source's own frames outside every segment, interleaved by timestamp with the interpolated ones. [encoded]
    is told how many frames each pass has written so far."""
    outside = "+".join(f"between(n\\,{first}\\,{end - 1})" for first, end, _ in segments)
    inputs = "".join(f"[{i + 1}:v]" for i in range(len(parts)))
    graph = f"[0:v]select='not({outside})'[base];[base]{inputs}interleave=nb_inputs={len(parts) + 1}[v]"
    command = ["ffmpeg", *source_input(source, start)]
    for part in parts:
        command += ["-i", str(part)]
    command += ["-filter_complex", graph, "-map", "[v]", *X264, "-level:v", level, "-b:v", str(bitrate),
                "-passlogfile", str(work / "x264"), "-fps_mode", "passthrough"]
    run([*command, "-pass", "1", "-an", "-f", "null", "-"], lambda frames: encoded(1, frames))
    run([*command, "-pass", "2", "-map", "0:a?", "-c:a", "copy", "-metadata", f"comment={stamp_text}",
         "-video_track_timescale", "15360", "-movflags", "+faststart", str(out)], lambda frames: encoded(2, frames))


def check(out: Path, segments, fps: Fraction, frame_count: int) -> None:
    """Fails unless the frame count is exactly what the segments add and no source frame has moved."""
    times = [float(f["pts_time"]) for f in ffprobe(out, "v:0", "frame=pts_time")["frames"]]
    expected = output_frames(segments, frame_count)
    if len(times) != expected:
        sys.exit(f"check failed: {len(times)} frames, expected {expected}")
    if any(b <= a for a, b in zip(times, times[1:])):
        sys.exit("check failed: timestamps are not strictly increasing")
    grid = {round(t * fps) for t in times if abs(t * fps - round(t * fps)) < 0.01}
    if grid != set(range(frame_count)):
        sys.exit("check failed: a source frame is missing or has moved")


def stamp(source: Path, segments) -> str:
    """What the output is built from: the source's hash and the merged slow segments."""
    return STAMP_PREFIX + json.dumps({"source_sha256": sha256(source), "segments": segments}, separators=(",", ":"))


def read_stamp(path: Path):
    """The {"source_sha256", "segments"} an output was built from, or None for one made before stamps (2026-10-02)."""
    comment = ffprobe(path, "v:0", "format_tags=comment").get("format", {}).get("tags", {}).get("comment", "")
    if not comment.startswith(STAMP_PREFIX):
        return None
    try:
        return json.loads(comment[len(STAMP_PREFIX):])
    except ValueError:
        return None


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def ffprobe(path: Path, stream: str, entries: str):
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", stream, "-show_entries", entries, "-of", "json", str(path)],
        check=True, capture_output=True, text=True,
    )
    return json.loads(result.stdout)


def run(command, frames=None) -> None:
    """Runs ffmpeg quietly, overwriting. [frames] is told the count of frames written so far, each time ffmpeg reports
    it: about twice a second while it writes, and not at all while it only decodes."""
    full = [command[0], "-hide_banner", "-loglevel", "error", "-nostats", "-progress", "pipe:1", "-y", *command[1:]]
    with tempfile.TemporaryFile() as stderr:  # a file, not a pipe: a pipe left unread can fill and stall ffmpeg
        with subprocess.Popen(full, stdout=subprocess.PIPE, stderr=stderr, text=True) as process:
            try:
                for line in process.stdout:
                    if frames and line.startswith("frame="):
                        frames(int(line[len("frame="):]))
            except BaseException:  # Ctrl+C reaches ffmpeg too, in a terminal; anything else would leave it encoding
                process.kill()
                raise
        if process.returncode != 0:
            stderr.seek(0)
            sys.exit(f"{' '.join(command[:3])}... failed:\n{stderr.read().decode(errors='replace')}")


if __name__ == "__main__":
    main()
