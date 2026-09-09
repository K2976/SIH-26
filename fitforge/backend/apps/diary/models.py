from django.conf import settings
from django.db import models

from apps.reference.models import Exercise


class DiaryEntry(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    client_uuid = models.UUIDField(db_index=True)
    occurred_at = models.DateTimeField()
    utc_offset = models.CharField(max_length=6)
    source = models.CharField(max_length=32)
    created_at = models.DateTimeField(auto_now_add=True)
    supersedes = models.ForeignKey("self", null=True, blank=True, on_delete=models.SET_NULL)

    class Meta:
        abstract = True
        unique_together = (("user", "client_uuid"),)


class PlannedSession(DiaryEntry):  # noqa: DJ008 -- preserve existing string representation
    class Status(models.TextChoices):
        PENDING = "pending", "Pending"
        COMPLETED = "completed", "Completed"
        SKIPPED = "skipped", "Skipped"
        MOVED = "moved", "Moved"

    date = models.DateField()
    focus = models.CharField(max_length=64, blank=True)
    planned_volume = models.JSONField(default=dict, blank=True)
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.PENDING)


class WorkoutSession(DiaryEntry):  # noqa: DJ008 -- preserve existing string representation
    planned_session = models.ForeignKey(
        PlannedSession, null=True, blank=True, on_delete=models.SET_NULL
    )
    duration_minutes = models.PositiveIntegerField(null=True, blank=True)
    notes = models.TextField(blank=True)


class PoseLandmarkSequence(DiaryEntry):  # noqa: DJ008 -- preserve existing string representation
    """One completed camera-tracked set's raw landmark sequence plus the
    deterministic per-rep labels Prompt A's state machine already computed
    (depth/lockout/symmetry/rejection reason) — training data for the future
    exercise + rep-quality classifier (ml/), never read by the engine or any
    `UserState` computation. See `fitforge/ml/README.md` for the training
    pipeline this feeds and its current sample-size gate.
    """

    exercise = models.ForeignKey(Exercise, to_field="slug", on_delete=models.PROTECT)
    camera_position = models.CharField(max_length=16)
    # "user_calibrated" | "default" — which CalibrationProfile (Prompt B) counted this set,
    # so a low-quality default-range set can be excluded from training later if needed.
    calibration_source = models.CharField(max_length=16)
    # JSON-encoded text, not JSONField: the Android client already serializes this once
    # for local Room storage, and reusing that same string for the wire payload avoids
    # a free-form JSONField producing an untyped `Any` in the generated Retrofit client.
    # [{"t_ms": int, "landmarks": {"LEFT_KNEE": [x, y, z, visibility], ...}}, ...]
    landmark_frames = models.TextField()
    # [{"start_ms", "end_ms", "outcome": "completed"|"rejected", "depth_reached",
    #   "lockout_reached", "symmetry_deviation_deg", "tempo_ms", "quality", "reason"}, ...]
    rep_labels = models.TextField(default="[]", blank=True)


class SetLog(DiaryEntry):  # noqa: DJ008 -- preserve existing string representation
    session = models.ForeignKey(WorkoutSession, on_delete=models.CASCADE, related_name="sets")
    exercise = models.ForeignKey(Exercise, to_field="slug", on_delete=models.PROTECT)
    set_index = models.PositiveIntegerField()
    reps = models.PositiveIntegerField()
    weight_kg = models.DecimalField(max_digits=6, decimal_places=2, null=True, blank=True)
    rpe = models.DecimalField(max_digits=3, decimal_places=1, null=True, blank=True)
    is_warmup = models.BooleanField(default=False)
    # Retain nullability for the existing database/API contract.
    rom_achieved = models.CharField(max_length=32, null=True, blank=True)  # noqa: DJ001
    tempo_ms = models.PositiveIntegerField(null=True, blank=True)


class MealLog(DiaryEntry):  # noqa: DJ008 -- preserve existing string representation
    pass


class MealItem(DiaryEntry):  # noqa: DJ008 -- preserve existing string representation
    log = models.ForeignKey(MealLog, on_delete=models.CASCADE, related_name="items")
    # Natural key for food, slug. We might not have a Food model yet, just store slug.
    food_slug = models.CharField(max_length=64)
    quantity = models.DecimalField(max_digits=8, decimal_places=2)
    # Macros snapshotted at log time
    calories = models.DecimalField(max_digits=8, decimal_places=2)
    protein_g = models.DecimalField(max_digits=8, decimal_places=2)
    carbs_g = models.DecimalField(max_digits=8, decimal_places=2)
    fat_g = models.DecimalField(max_digits=8, decimal_places=2)


class WeightLog(DiaryEntry):  # noqa: DJ008 -- preserve existing string representation
    weight_kg = models.DecimalField(max_digits=5, decimal_places=2)


class SleepLog(DiaryEntry):  # noqa: DJ008 -- preserve existing string representation
    bedtime = models.DateTimeField()
    wake = models.DateTimeField()
    quality = models.PositiveSmallIntegerField(
        choices=[(1, 1), (2, 2), (3, 3), (4, 4), (5, 5)], null=True, blank=True
    )


class CycleLog(DiaryEntry):  # noqa: DJ008 -- preserve existing string representation
    # Recognised `event` values: "period_start" | "period_end" | "spotting".
    # Left as a plain CharField (no `choices=`) rather than altering an
    # existing column's constraints — validated at the serializer/service
    # layer instead. See apps/engine/cycle.py for the values consumed there.
    date = models.DateField()
    event = models.CharField(max_length=32, blank=True)
    flow = models.CharField(max_length=32, blank=True)
    symptoms = models.JSONField(default=list, blank=True)
    energy_rating = models.PositiveSmallIntegerField(
        choices=[(1, 1), (2, 2), (3, 3), (4, 4), (5, 5)], null=True, blank=True
    )


class CycleProfile(models.Model):
    """Cycle-module settings, one row per user. Not a diary entry: it's a
    settings row (like UserProfile), not an append-only logged event.

    Kept in apps/diary alongside CycleLog since both belong to the cycle
    module (§10), not in apps/accounts — UserProfile only carries the
    boolean opt-in (`tracks_cycle`); everything past that opt-in question
    lives here.
    """

    class TrackingMode(models.TextChoices):
        NATURAL = "natural", "Natural"
        HORMONAL_CONTRACEPTION = "hormonal_contraception", "Hormonal contraception"
        PREGNANT = "pregnant", "Pregnant"
        POSTPARTUM = "postpartum", "Postpartum"
        PERIMENOPAUSE = "perimenopause", "Perimenopause"
        NOT_CYCLING = "not_cycling", "Not cycling"
        UNKNOWN = "unknown", "Unknown"

    user = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="cycle_profile"
    )
    tracking_mode = models.CharField(
        max_length=32, choices=TrackingMode.choices, default=TrackingMode.UNKNOWN
    )
    # Self-reported starting points; refined by observed history once enough
    # period-start events exist (see apps.engine.cycle.infer_typical_length).
    typical_length_days = models.PositiveSmallIntegerField(null=True, blank=True)
    typical_period_days = models.PositiveSmallIntegerField(null=True, blank=True)
    # Confidence in typical_length_days / typical_period_days as self-reported
    # values, distinct from the confidence attached to a derived phase or
    # forecast (those are computed fresh from history each time).
    confidence = models.FloatField(null=True, blank=True)

    def __str__(self):
        return f"CycleProfile<{self.user_id}:{self.tracking_mode}>"
