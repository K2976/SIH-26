# fitforge/ml — exercise + rep-quality classifier

## Status: blocked on data. Not trained. No accuracy claimed.

**Zero real landmark sequences have been collected as of this writing.** This
is a from-scratch dev repo with no production users yet — the data-collection
path (`PoseLandmarkSequence`, wired up in this same change) has never run
against a real person. Training a model now would mean training on nothing,
or on fabricated data, and reporting a resulting "accuracy" number would be
exactly the dishonest outcome the task creating this pipeline warned against.
So: **this pipeline exists and is verified to run mechanically (see
"What's actually been verified" below); no model has been trained, and none
ships with this change.**

Do not treat this gate as a formality. Do not train on a handful of samples
just to produce a number — `train.py`'s sample-size gate exists specifically
to stop that, and it's not a suggestion (see `MIN_SAMPLES_PER_CLASS` in
`train.py`).

## What this is

A pipeline that classifies, per completed camera-tracked set:

1. **Exercise type** (`bodyweight-squat` / `push-up` / `dumbbell-bicep-curl`)
   from the landmark sequence alone.
2. **Rep quality** (`clean` / `shallow` / `asymmetric` / `partial_lockout`,
   per rep) — a small 1D-CNN's *learned* approximation of the rule-based
   proxy label in `RepQualityLabel.kt`, trained on real motion instead of
   only the hand-tuned thresholds.

Both outputs are **additive** to Prompt A's deterministic count and
rejection reason — they never replace it, and there is no combined
`form_score`. See `CLAUDE.md`'s "Things that are deliberately absent."

## How data gets collected

Every completed camera-tracked set (saved or discarded) is captured on-device
as a `PoseLandmarkSequence` — the raw per-frame landmark positions plus the
deterministic per-rep labels Prompt A's state machine already computed
(depth/lockout/symmetry/rejection reason), tagged with the exercise already
selected in the UI. It syncs through the existing `/api/v1/sync/` pipeline
like every other diary row. Nothing here is shown to the user or read back by
the app — this is training-data capture only.

## Running the pipeline once enough data exists

```bash
# 1. From backend/, dump collected rows to a JSON file:
python manage.py export_pose_sequences --output dataset.json

# 2. From ml/, install deps (TensorFlow only — isolated from the Django
#    backend's own requirements, since this never runs as part of the
#    served app):
pip install -r requirements.txt

# 3. Train. Refuses and exits nonzero if there isn't enough data yet —
#    read the printed message rather than assuming it worked.
python train.py --dataset dataset.json --task exercise --output exercise.tflite
python train.py --dataset dataset.json --task rep_quality --output rep_quality.tflite
```

`train.py` prints the actual held-out accuracy it measured, or a `BLOCKED:`
message explaining exactly what's short, if there isn't enough data.

## What's actually been verified (mechanical, not a training claim)

`test_pipeline.py` is a **smoke test on synthetic random arrays**, not a
training run — it exists to prove the code path works with the pinned
TensorFlow version, nothing about model quality:

```bash
python test_pipeline.py
```

Run against a real TensorFlow 2.21.0 install in this environment. Confirmed:
dataset windowing produces correctly-shaped arrays from the same JSON shape
`export_pose_sequences` emits; the sample-size gate actually blocks a
too-small dataset instead of training on it; a model builds, trains for one
epoch, and exports to a valid non-empty `.tflite` file via a real
Keras 3 → SavedModel → `TFLiteConverter.from_saved_model` path (verified
empirically — Keras 3's default `TFLiteConverter.from_keras_model` path is
known to be unreliable, so this was checked against a real install rather
than assumed from documentation).

## Architecture

A small 1D-CNN over a fixed-length sliding window of per-frame joint-landmark
features (`model.py`) — a sequence model over pose landmarks, not a
raw-frame/video classifier, per the pose-based exercise-recognition
literature this follows. Depth/width are a reasonable small-model starting
point, not a tuned result. `WINDOW_LENGTH` (60 frames, ~2s at 30fps) and the
zero-padding-for-missing-landmarks choice in `dataset.py` are provisional —
real data may show different rep lengths or visibility patterns than assumed
here and this should be revisited once it exists, not treated as settled.

## On-device deployment: not built yet, deliberately

No Kotlin TFLite-interpreter integration exists in the Android app yet.
Wiring one in now, with no trained model to load, would be dead code with
nothing to exercise it — the construction-phase rule against partial/
placeholder implementations applies here. Once a real model is trained (with
a real, honestly-measured accuracy — see the gate above) and its accuracy is
judged good enough to ship, the next slice is: bundle the `.tflite` file as
an app asset (same `noCompress` treatment as the existing MediaPipe `.task`
models — see `DECISIONS.md` 2026-09-05), add a `TfliteRepClassifier` wrapper
in `feature/repcounter/`, and run it alongside the live tracker in
`RepCounterViewModel.handleCounting` — additive to the existing `RepEvent`
flow, never blocking or replacing it. That integration should be verified
for "no perceptible lag" on a real device before shipping, same as the
never-run device benchmark already flagged in `DECISIONS.md`.

## Known pre-production risk: payload size

`RepCounterViewModel` buffers every frame of a set and JSON-encodes the whole
sequence into one `landmark_frames` string, which then rides the batched
`/api/v1/sync/` POST alongside every other pending row. Django's
`DATA_UPLOAD_MAX_MEMORY_SIZE` defaults to 2.5MB and rejects the entire
request at parse time, before any view code runs. A single long set (~2 min
at 30fps × 12 landmarks) can approach that on its own, and because it's
batched, one oversized pose sequence can 400 the whole sync batch — not just
its own event — silently stalling all syncing. Not fixed here: unverified
without a device, and the obvious direction (downsample toward the ~60-frame
training window at capture time instead of storing raw 30fps) is itself a
design choice that should be made with real capture durations in hand, not
guessed at now.

## Sample-size gate

`MIN_SAMPLES_PER_CLASS = 30` in `train.py` — a deliberately conservative
floor, not derived from a formula. Its job is to stop a lucky train/test
split on a handful of examples from producing a misleadingly high held-out
accuracy. Revisit once real data collection is actually underway and there's
a real number of classes/examples to reason about.
