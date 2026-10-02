#!/usr/bin/env python3
"""Makes a demo's slow-motion phases play smoothly by motion-interpolating them.

A 30 fps video played at 0.25x shows 7.5 frames a second, which stutters. For every step that slows the
video, this inserts interpolated frames between the step's trigger and its stop point, so the slow phase
still shows the source frame rate on screen. Every source frame keeps its timestamp, and the audio is
copied untouched (the output is variable frame rate), so the triggers and stop points in script.json still
land on the same scene. A stop on a source frame's time (11500 ms at 30 fps) pauses on that source frame.

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
It always starts from the original, so running it twice is harmless. Needs ffmpeg and ffprobe on the PATH.
"""

import json
import math
import shutil
import subprocess
import sys
import tempfile
from fractions import Fraction
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

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


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    demo_id = sys.argv[1]
    source = ROOT / "demo-sources" / demo_id / "video.mp4"
    script = ROOT / "demo-sources" / demo_id / "script.json"
    target = ROOT / "demo-sources" / demo_id / "video-interpolated.mp4"
    for tool in ("ffmpeg", "ffprobe"):
        if shutil.which(tool) is None:
            sys.exit(f"{tool} is not on the PATH (brew install ffmpeg)")
    for path in (source, script):
        if not path.is_file():
            sys.exit(f"missing {path.relative_to(ROOT)}")

    fps, frame_count, bitrate, width, height = probe_video(source)
    segments = merge(slow_segments(json.loads(script.read_text()), fps, frame_count))
    if not segments:
        # Nothing slows down any more: the uploaded video must not keep frames for phases that are gone.
        shutil.copyfile(source, target)
        print(f"script.json has no slow phase; copied the original to {target.relative_to(ROOT)}")
        return

    level = h264_level(width, height, fps * max(factor for _, _, factor in segments))
    with tempfile.TemporaryDirectory() as work:
        parts = [interpolate(source, segment, fps, Path(work) / f"segment-{i}.nut") for i, segment in enumerate(segments)]
        output = Path(work) / "video.mp4"
        assemble(source, segments, parts, int(bitrate * BITRATE_MARGIN), level, Path(work), output)
        check(output, segments, fps, frame_count)
        shutil.move(str(output), target)

    print(f"wrote {target.relative_to(ROOT)} ({target.stat().st_size / 1e6:.1f} MB, "
          f"source {source.stat().st_size / 1e6:.1f} MB)")


def probe_video(path: Path):
    """The source's frame rate, frame count, video bitrate and size. Refuses anything but a constant-rate video starting at 0."""
    entries = "stream=r_frame_rate,avg_frame_rate,nb_frames,start_time,bit_rate,width,height"
    stream = ffprobe(path, "v:0", entries)["streams"][0]
    fps = Fraction(stream["r_frame_rate"])
    if fps != Fraction(stream["avg_frame_rate"]) or float(stream["start_time"]) != 0:
        sys.exit(f"{path.name} must be constant frame rate starting at 0 "
                 f"(r={stream['r_frame_rate']} avg={stream['avg_frame_rate']} start={stream['start_time']})")
    return fps, int(stream["nb_frames"]), int(stream["bit_rate"]), int(stream["width"]), int(stream["height"])


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


def interpolate(source: Path, segment, fps: Fraction, out: Path) -> Path:
    """Source frames [first, end) at factor x the frame rate, losslessly, at their original timestamps."""
    first, end, factor = segment
    # Two frames past the segment are decoded too: minterpolate interpolates towards frame `end`, and its
    # bidirectional search needs the one after that, or it drops the last interval at end of input.
    graph = (f"trim=start_frame={first}:end_frame={end + 2},"
             f"minterpolate=fps={fps * factor}:{MINTERPOLATE},"
             # Half an *output* frame before `end`: source frame `end` itself belongs to the base stream.
             f"select='lt(t\\,{float((end - Fraction(1, 2 * factor)) / fps)})'")
    run(["ffmpeg", "-copyts", "-i", str(source), "-an", "-vf", graph,
         "-fps_mode", "passthrough", "-c:v", "ffv1", str(out)])
    return out


def assemble(source: Path, segments, parts, bitrate: int, level: str, work: Path, out: Path) -> None:
    """The source's own frames outside every segment, interleaved by timestamp with the interpolated ones."""
    outside = "+".join(f"between(n\\,{first}\\,{end - 1})" for first, end, _ in segments)
    inputs = "".join(f"[{i + 1}:v]" for i in range(len(parts)))
    graph = f"[0:v]select='not({outside})'[base];[base]{inputs}interleave=nb_inputs={len(parts) + 1}[v]"
    command = ["ffmpeg", "-copyts", "-i", str(source)]
    for part in parts:
        command += ["-i", str(part)]
    command += ["-filter_complex", graph, "-map", "[v]", *X264, "-level:v", level, "-b:v", str(bitrate),
                "-passlogfile", str(work / "x264"), "-fps_mode", "passthrough"]
    run([*command, "-pass", "1", "-an", "-f", "null", "-"])
    run([*command, "-pass", "2", "-map", "0:a?", "-c:a", "copy",
         "-video_track_timescale", "15360", "-movflags", "+faststart", str(out)])


def check(out: Path, segments, fps: Fraction, frame_count: int) -> None:
    """Fails unless the frame count is exactly what the segments add and no source frame has moved."""
    times = [float(f["pts_time"]) for f in ffprobe(out, "v:0", "frame=pts_time")["frames"]]
    expected = frame_count + sum((end - first) * (factor - 1) for first, end, factor in segments)
    if len(times) != expected:
        sys.exit(f"check failed: {len(times)} frames, expected {expected}")
    if any(b <= a for a, b in zip(times, times[1:])):
        sys.exit("check failed: timestamps are not strictly increasing")
    grid = {round(t * fps) for t in times if abs(t * fps - round(t * fps)) < 0.01}
    if grid != set(range(frame_count)):
        sys.exit("check failed: a source frame is missing or has moved")
    for first, end, factor in segments:
        added = (end - first) * (factor - 1)
        print(f"  {float(first / fps) * 1000:8.0f}-{float(end / fps) * 1000:<8.0f}ms  {factor}x  +{added} frames")


def ffprobe(path: Path, stream: str, entries: str):
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", stream, "-show_entries", entries, "-of", "json", str(path)],
        check=True, capture_output=True, text=True,
    )
    return json.loads(result.stdout)


def run(command) -> None:
    result = subprocess.run([command[0], "-hide_banner", "-loglevel", "error", "-y", *command[1:]],
                            capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"{' '.join(command[:3])}... failed:\n{result.stderr}")


if __name__ == "__main__":
    main()
