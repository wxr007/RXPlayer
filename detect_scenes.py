import argparse
import sys
from pathlib import Path

from scenedetect import open_video, SceneManager
from scenedetect.detectors import (
    ContentDetector,
    AdaptiveDetector,
    ThresholdDetector,
    HistogramDetector,
    HashDetector,
)


DETECTORS = {
    "content": ContentDetector,
    "adaptive": AdaptiveDetector,
    "threshold": ThresholdDetector,
    "histogram": HistogramDetector,
    "hash": HashDetector,
}


def format_timestamp(frame_timecode, fps: float) -> str:
    total_sec = frame_timecode.seconds
    h = int(total_sec // 3600)
    m = int((total_sec % 3600) // 60)
    s = int(total_sec % 60)
    frame = round(frame_timecode.frame_num % fps)
    return f"{h:02d}:{m:02d}:{s:02d}.{frame:02d}"


def detect_scenes(
    video_path: str,
    detector_name: str = "content",
    threshold: float | None = None,
    min_scene_len: float = 0.5,
):
    video = open_video(video_path)
    fps = float(video.frame_rate)

    detector_cls = DETECTORS[detector_name]
    kwargs = {"min_scene_len": min_scene_len}
    if threshold is not None:
        kwargs["threshold"] = threshold
    detector = detector_cls(**kwargs)

    scene_manager = SceneManager()
    scene_manager.add_detector(detector)
    scene_manager.detect_scenes(video, show_progress=True)

    scene_list = scene_manager.get_scene_list()
    return scene_list, fps


def main():
    parser = argparse.ArgumentParser(
        description="Detect scene changes in a video and output timestamps."
    )
    parser.add_argument("input", help="Path to input video file")
    parser.add_argument(
        "-o",
        "--output",
        default=None,
        help="Output file path (default: <input>_scenes.txt)",
    )
    parser.add_argument(
        "-d",
        "--detector",
        choices=list(DETECTORS.keys()),
        default="content",
        help="Detection algorithm (default: content)",
    )
    parser.add_argument(
        "-t",
        "--threshold",
        type=float,
        default=None,
        help="Detection threshold (default: detector-specific default)",
    )
    parser.add_argument(
        "--min-scene-len",
        type=float,
        default=0.5,
        help="Minimum scene length in seconds (default: 0.5)",
    )
    args = parser.parse_args()

    if not Path(args.input).exists():
        print(f"Error: file not found: {args.input}", file=sys.stderr)
        sys.exit(1)

    output = args.output or (Path(args.input).stem + "_scenes.txt")

    scene_list, fps = detect_scenes(
        args.input, args.detector, args.threshold, args.min_scene_len
    )

    lines = []
    lines.append(f"Video: {args.input}")
    lines.append(f"Detector: {args.detector}")
    lines.append(f"Frame rate: {fps:.3f}")
    lines.append(f"Total scenes: {len(scene_list)}")
    lines.append(f"{'Scene':<8}{'Start':<16}{'End':<16}{'Duration':<12}")
    lines.append("-" * 52)

    for i, (start, end) in enumerate(scene_list, 1):
        start_ts = format_timestamp(start, fps)
        end_ts = format_timestamp(end, fps)
        dur = end.seconds - start.seconds
        lines.append(f"{i:<8}{start_ts:<16}{end_ts:<16}{dur:<12.2f}")

    Path(output).write_text("\n".join(lines), encoding="utf-8")
    print(f"\nDetected {len(scene_list)} scenes. Output saved to: {output}")


if __name__ == "__main__":
    main()
