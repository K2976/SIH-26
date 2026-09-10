"""Train the exercise or rep-quality classifier and export it as TensorFlow
Lite — but only once enough real collected data exists. See README.md: this
gate is load-bearing, not a formality. Running this script against too little
data prints why it refuses, and exits nonzero, instead of training anyway.

Usage:
    python train.py --dataset dataset.json --task exercise --output exercise.tflite
    python train.py --dataset dataset.json --task rep_quality --output rep_quality.tflite
"""

import argparse
import sys

import numpy as np

from dataset import build_exercise_dataset, build_rep_quality_dataset, load_raw_samples
from model import build_sequence_classifier, export_tflite

# Minimum labeled windows *per class* before a training run is trustworthy at
# all. Not derived from a formula — a deliberately conservative floor so a
# lucky split can't produce a misleadingly high held-out accuracy on what's
# really a handful of examples. Revisit once real data collection is underway.
MIN_SAMPLES_PER_CLASS = 30
TEST_FRACTION = 0.2
EPOCHS = 30
SEED = 42


def check_sample_size(y: np.ndarray, class_names: list[str]) -> str | None:
    """Returns a human-readable blocker message, or None if training may proceed."""
    if len(y) == 0:
        return f"0 samples collected for this task. Need at least {MIN_SAMPLES_PER_CLASS} per class."
    counts = {name: int(np.sum(y == i)) for i, name in enumerate(class_names)}
    short = {name: count for name, count in counts.items() if count < MIN_SAMPLES_PER_CLASS}
    if short:
        detail = ", ".join(f"{name}={count}" for name, count in counts.items())
        return (
            f"Not enough data to train honestly: {detail} "
            f"(need >= {MIN_SAMPLES_PER_CLASS} per class; short: {list(short)})."
        )
    return None


def train_and_evaluate(x: np.ndarray, y: np.ndarray, class_names: list[str]) -> tuple[object, float]:
    rng = np.random.default_rng(SEED)
    indices = rng.permutation(len(x))
    split = int(len(x) * (1 - TEST_FRACTION))
    train_idx, test_idx = indices[:split], indices[split:]

    model = build_sequence_classifier(x.shape[1], x.shape[2], len(class_names))
    model.fit(x[train_idx], y[train_idx], epochs=EPOCHS, verbose=0)
    _, accuracy = model.evaluate(x[test_idx], y[test_idx], verbose=0)
    return model, float(accuracy)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", required=True, help="Path to export_pose_sequences' JSON output.")
    parser.add_argument("--task", required=True, choices=["exercise", "rep_quality"])
    parser.add_argument("--output", required=True, help="Path to write the trained .tflite model.")
    args = parser.parse_args()

    samples = load_raw_samples(args.dataset)
    if args.task == "exercise":
        x, y, class_names = build_exercise_dataset(samples)
    else:
        x, y, class_names = build_rep_quality_dataset(samples)

    if len(class_names) < 2:
        print(f"BLOCKED: only {len(class_names)} class(es) present ({class_names}) — need at least 2.")
        return 1

    blocker = check_sample_size(y, class_names)
    if blocker is not None:
        print(f"BLOCKED: {blocker}")
        print("Not training. Collect more real tracked sets via the app, then re-export and retry.")
        return 1

    model, accuracy = train_and_evaluate(x, y, class_names)
    print(f"Held-out accuracy: {accuracy:.4f} (measured on {int(len(y) * TEST_FRACTION)} held-out examples)")
    export_tflite(model, args.output)
    print(f"Wrote {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
