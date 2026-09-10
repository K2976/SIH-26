"""Loads the JSON dataset produced by `manage.py export_pose_sequences` and
turns it into fixed-length numpy windows for `model.py`/`train.py`.

No Django, no database — this module only ever reads the exported JSON file,
so the training pipeline stays fully decoupled from the backend. See
`README.md` for the current sample-size gate this feeds into.

Windowing is a provisional, documented design choice, not a validated one:
real data may show different rep lengths or landmark-visibility patterns
than assumed here, and this should be revisited once it exists (see README).
"""

import json

import numpy as np

# Must match PoseLandmark's declaration order in the Android app
# (android/app/src/main/java/com/fitforge/app/pose/PoseTypes.kt) — the fixed
# feature-vector layout every frame is flattened into.
LANDMARK_ORDER = [
    "LEFT_SHOULDER",
    "RIGHT_SHOULDER",
    "LEFT_ELBOW",
    "RIGHT_ELBOW",
    "LEFT_WRIST",
    "RIGHT_WRIST",
    "LEFT_HIP",
    "RIGHT_HIP",
    "LEFT_KNEE",
    "RIGHT_KNEE",
    "LEFT_ANKLE",
    "RIGHT_ANKLE",
]
NUM_FEATURES = len(LANDMARK_ORDER) * 4  # x, y, z, visibility per landmark

# ~2s at 30fps. A fixed window is what a 1D-CNN needs; real reps vary in
# length, so shorter windows are zero-padded and longer ones truncated.
# Provisional — see module docstring.
WINDOW_LENGTH = 60

REP_QUALITY_CLASSES = ["clean", "shallow", "asymmetric", "partial_lockout"]


def load_raw_samples(path: str) -> list[dict]:
    with open(path) as f:
        return json.load(f)


def _frame_to_features(frame: dict) -> list[float]:
    """One frame's landmarks dict -> a fixed-order feature vector. A landmark
    absent from this frame (not detected) becomes all-zero, visibility
    included — zero visibility already means "not confidently seen"
    everywhere else in this codebase."""
    features: list[float] = []
    for name in LANDMARK_ORDER:
        features.extend(frame["landmarks"].get(name, [0.0, 0.0, 0.0, 0.0]))
    return features


def _window(frames: list[dict], start_ms: float, end_ms: float) -> np.ndarray:
    """Frames within [start_ms, end_ms), flattened and padded/truncated to WINDOW_LENGTH."""
    in_range = [f for f in frames if start_ms <= f["t_ms"] < end_ms]
    matrix = np.zeros((WINDOW_LENGTH, NUM_FEATURES), dtype=np.float32)
    for i, frame in enumerate(in_range[:WINDOW_LENGTH]):
        matrix[i] = _frame_to_features(frame)
    return matrix


def build_exercise_dataset(samples: list[dict]) -> tuple[np.ndarray, np.ndarray, list[str]]:
    """X: one window per set (from its first WINDOW_LENGTH frames), y: exercise label index."""
    labels = sorted({s["exercise_slug"] for s in samples})
    label_index = {label: i for i, label in enumerate(labels)}

    xs = []
    ys = []
    for sample in samples:
        frames = sample["landmark_frames"]
        if not frames:
            continue
        start_ms = frames[0]["t_ms"]
        end_ms = start_ms + WINDOW_LENGTH * 1000  # generous; _window itself caps frame count
        xs.append(_window(frames, start_ms, end_ms))
        ys.append(label_index[sample["exercise_slug"]])

    x_array = np.stack(xs) if xs else np.zeros((0, WINDOW_LENGTH, NUM_FEATURES), dtype=np.float32)
    return x_array, np.array(ys, dtype=np.int64), labels


def build_rep_quality_dataset(samples: list[dict]) -> tuple[np.ndarray, np.ndarray, list[str]]:
    """X: one window per completed rep, y: rep-quality label index.

    Only reps with a `quality` label (i.e. RepEvent.Completed — see
    `RepQualityLabel.kt`) are usable training examples; a rejected rep has no
    quality bucket to classify into.
    """
    xs = []
    ys = []
    for sample in samples:
        frames = sample["landmark_frames"]
        for rep in sample["rep_labels"]:
            quality = rep.get("quality")
            if quality is None:
                continue
            xs.append(_window(frames, rep["start_ms"], rep["end_ms"]))
            ys.append(REP_QUALITY_CLASSES.index(quality))

    x_array = np.stack(xs) if xs else np.zeros((0, WINDOW_LENGTH, NUM_FEATURES), dtype=np.float32)
    return x_array, np.array(ys, dtype=np.int64), REP_QUALITY_CLASSES
