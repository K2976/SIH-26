import uuid

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


class PlannedSession(DiaryEntry):
    class Status(models.TextChoices):
        PENDING = "pending", "Pending"
        COMPLETED = "completed", "Completed"
        SKIPPED = "skipped", "Skipped"
        MOVED = "moved", "Moved"

    date = models.DateField()
    focus = models.CharField(max_length=64, blank=True)
    planned_volume = models.JSONField(default=dict, blank=True)
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.PENDING)


class WorkoutSession(DiaryEntry):
    planned_session = models.ForeignKey(PlannedSession, null=True, blank=True, on_delete=models.SET_NULL)
    duration_minutes = models.PositiveIntegerField(null=True, blank=True)
    notes = models.TextField(blank=True)


class SetLog(DiaryEntry):
    session = models.ForeignKey(WorkoutSession, on_delete=models.CASCADE, related_name="sets")
    exercise = models.ForeignKey(Exercise, to_field="slug", on_delete=models.PROTECT)
    set_index = models.PositiveIntegerField()
    reps = models.PositiveIntegerField()
    weight_kg = models.DecimalField(max_digits=6, decimal_places=2, null=True, blank=True)
    rpe = models.DecimalField(max_digits=3, decimal_places=1, null=True, blank=True)
    is_warmup = models.BooleanField(default=False)
    rom_achieved = models.CharField(max_length=32, null=True, blank=True)
    tempo_ms = models.PositiveIntegerField(null=True, blank=True)


class MealLog(DiaryEntry):
    pass


class MealItem(DiaryEntry):
    log = models.ForeignKey(MealLog, on_delete=models.CASCADE, related_name="items")
    # Natural key for food, slug. We might not have a Food model yet, just store slug.
    food_slug = models.CharField(max_length=64)
    quantity = models.DecimalField(max_digits=8, decimal_places=2)
    # Macros snapshotted at log time
    calories = models.DecimalField(max_digits=8, decimal_places=2)
    protein_g = models.DecimalField(max_digits=8, decimal_places=2)
    carbs_g = models.DecimalField(max_digits=8, decimal_places=2)
    fat_g = models.DecimalField(max_digits=8, decimal_places=2)


class WeightLog(DiaryEntry):
    weight_kg = models.DecimalField(max_digits=5, decimal_places=2)


class SleepLog(DiaryEntry):
    bedtime = models.DateTimeField()
    wake = models.DateTimeField()
    quality = models.PositiveSmallIntegerField(
        choices=[(1, 1), (2, 2), (3, 3), (4, 4), (5, 5)], null=True, blank=True
    )


class CycleLog(DiaryEntry):
    date = models.DateField()
    event = models.CharField(max_length=32, blank=True)
    flow = models.CharField(max_length=32, blank=True)
    symptoms = models.JSONField(default=list, blank=True)
    energy_rating = models.PositiveSmallIntegerField(
        choices=[(1, 1), (2, 2), (3, 3), (4, 4), (5, 5)], null=True, blank=True
    )
