"""Mechanical smoke test only — proves the pipeline's code runs end to end
(model builds, dataset windowing shapes are correct, TFLite export works,
the sample-size gate actually blocks). This is NOT a training run and the
numbers here are NOT an accuracy claim: the "dataset" is a handful of
synthetic random arrays, not real motion. See README.md for why no real
training has happened yet.

Run: python test_pipeline.py
"""

import json
import os
import tempfile

import numpy as np

from dataset import LANDMARK_ORDER, NUM_FEATURES, WINDOW_LENGTH, build_exercise_dataset, build_rep_quality_dataset
from model import build_sequence_classifier, export_tflite
from train import MIN_SAMPLES_PER_CLASS, check_sample_size, train_and_evaluate


def _fake_sample(exercise_slug: str, num_frames: int, quality: str) -> dict:
    rng = np.random.default_rng(0)
    frames = [
        {
            "t_ms": i * 33,
            "landmarks": {name: rng.random(4).tolist() for name in LANDMARK_ORDER},
        }
        for i in range(num_frames)
    ]
    return {
        "exercise_slug": exercise_slug,
        "landmark_frames": frames,
        "rep_labels": [{"start_ms": 0, "end_ms": num_frames * 33, "outcome": "completed", "quality": quality}],
    }


def test_dataset_shapes():
    samples = [_fake_sample("bodyweight-squat", 50, "clean"), _fake_sample("push-up", 40, "shallow")]
    x, y, labels = build_exercise_dataset(samples)
    assert x.shape == (2, WINDOW_LENGTH, NUM_FEATURES), x.shape
    assert y.shape == (2,)
    assert labels == ["bodyweight-squat", "push-up"]

    x2, y2, quality_labels = build_rep_quality_dataset(samples)
    assert x2.shape == (2, WINDOW_LENGTH, NUM_FEATURES), x2.shape
    assert "clean" in quality_labels and "shallow" in quality_labels
    print("test_dataset_shapes: OK")


def test_sample_size_gate_blocks_small_data():
    y = np.array([0] * 5 + [1] * 3)
    blocker = check_sample_size(y, ["clean", "shallow"])
    assert blocker is not None, "the gate must refuse tiny datasets, not train on them"
    print("test_sample_size_gate_blocks_small_data: OK ->", blocker)


def test_model_trains_and_exports_to_tflite():
    """Synthetic-only: proves the Keras -> TFLite path this app depends on
    actually works with the pinned TF version, nothing about accuracy."""
    rng = np.random.default_rng(1)
    num_examples = MIN_SAMPLES_PER_CLASS * 2  # 2 balanced classes, right at the gate's floor
    x = rng.random((num_examples, WINDOW_LENGTH, NUM_FEATURES)).astype("float32")
    y = np.array([0] * MIN_SAMPLES_PER_CLASS + [1] * MIN_SAMPLES_PER_CLASS)

    blocker = check_sample_size(y, ["a", "b"])
    assert blocker is None, f"synthetic fixture should clear the gate: {blocker}"

    model = build_sequence_classifier(WINDOW_LENGTH, NUM_FEATURES, num_classes=2)
    model.fit(x, y, epochs=1, verbose=0)  # one epoch: mechanical check, not a real training run

    with tempfile.TemporaryDirectory() as tmp_dir:
        output_path = os.path.join(tmp_dir, "model.tflite")
        export_tflite(model, output_path)
        assert os.path.exists(output_path)
        assert os.path.getsize(output_path) > 0
    print("test_model_trains_and_exports_to_tflite: OK (mechanical only, not an accuracy result)")


def test_export_pose_sequences_json_shape_is_loadable():
    """The shape `export_pose_sequences` produces (see the Django management
    command) round-trips through dataset.py without special-casing."""
    samples = [_fake_sample("bodyweight-squat", 45, "asymmetric")]
    with tempfile.TemporaryDirectory() as tmp_dir:
        path = os.path.join(tmp_dir, "dataset.json")
        with open(path, "w") as f:
            json.dump(samples, f)
        from dataset import load_raw_samples

        loaded = load_raw_samples(path)
        x, y, _ = build_rep_quality_dataset(loaded)
        assert x.shape[0] == 1
    print("test_export_pose_sequences_json_shape_is_loadable: OK")


if __name__ == "__main__":
    test_dataset_shapes()
    test_sample_size_gate_blocks_small_data()
    test_export_pose_sequences_json_shape_is_loadable()
    test_model_trains_and_exports_to_tflite()
    print("\nAll smoke tests passed — this proves the pipeline runs, not that any model is trained or accurate.")
