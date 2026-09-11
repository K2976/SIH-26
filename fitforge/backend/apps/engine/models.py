from django.conf import settings
from django.db import models


class DailyState(models.Model):
    """Cache of one local day's derived values (UserState's energy/training
    numbers, pinned to that day). Always reproducible from the diary tables
    via apps.engine.service — never a source of truth, never hand-edited.

    FloatField throughout: these are recomputed wholesale from the diary on
    every sync and by the nightly rebuild, never accumulated in place, so
    there's no rounding-drift risk that would call for Decimal.

    `partial_fields` names which of the nullable numeric fields below were
    computed over a window with at least one missing day, rather than adding
    a matching boolean column per field.
    """

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="daily_states"
    )
    local_date = models.DateField()
    computed_at = models.DateTimeField(auto_now=True)

    consumed_kcal = models.FloatField(null=True, blank=True)
    burned_kcal = models.FloatField(null=True, blank=True)
    maintenance_kcal = models.FloatField(null=True, blank=True)
    maintenance_source = models.CharField(max_length=16, null=True, blank=True)  # noqa: DJ001
    maintenance_confidence = models.FloatField(null=True, blank=True)
    target_kcal = models.FloatField(null=True, blank=True)
    week_balance_kcal = models.FloatField(null=True, blank=True)

    week_volume_planned_kg = models.FloatField(null=True, blank=True)
    week_volume_completed_kg = models.FloatField(null=True, blank=True)
    volume_by_muscle_group = models.JSONField(null=True, blank=True)
    adherence_7d = models.FloatField(null=True, blank=True)
    days_since_rest = models.IntegerField(null=True, blank=True)

    weight_trend_kg_per_week = models.FloatField(null=True, blank=True)
    weight_trend_confidence = models.FloatField(null=True, blank=True)

    missing_food_days_7d = models.PositiveSmallIntegerField()
    missing_weight_days_14d = models.PositiveSmallIntegerField()
    goal_recently_changed = models.BooleanField()
    insufficient_data_for_adaptation = models.BooleanField()
    energy_data_sufficiency = models.CharField(max_length=16)

    partial_fields = models.JSONField(default=list, blank=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["user", "local_date"], name="unique_daily_state_per_day"
            )
        ]
        indexes = [models.Index(fields=["user", "local_date"])]

    def __str__(self):
        return f"DailyState<{self.user_id}:{self.local_date}>"


class CyclePattern(models.Model):
    """Cache of one cycle-day's learned pattern, one row per user per
    cycle-day (day 1 = a logged period start). Recomputed nightly from
    CycleLog + DailyState history by apps.engine.cycle_service — always
    reproducible, never hand-edited. Mirrors DailyState's role for §10.

    This is display/forecast/pattern-analysis data only. Nothing here feeds
    a budget — see apps/engine/budgets.py's readiness_budget, which reads
    self_reported_energy / self_reported_symptoms directly off UserState,
    never this table.
    """

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="cycle_patterns"
    )
    cycle_day = models.PositiveSmallIntegerField()
    computed_at = models.DateTimeField(auto_now=True)

    avg_energy_rating = models.FloatField(null=True, blank=True)
    symptom_frequency = models.JSONField(default=dict, blank=True)
    avg_adherence = models.FloatField(null=True, blank=True)
    avg_completed_volume_kg = models.FloatField(null=True, blank=True)

    # How many distinct cycles contributed a data point to this cycle-day,
    # and the resulting confidence (scaled by sample_cycles, capped at 1.0).
    # Surfaced in the UI alongside every pattern claim (CLAUDE.md rule 2).
    sample_cycles = models.PositiveSmallIntegerField(default=0)
    confidence = models.FloatField(default=0.0)

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["user", "cycle_day"], name="unique_cycle_pattern_per_day"
            )
        ]
        indexes = [models.Index(fields=["user", "cycle_day"])]

    def __str__(self):
        return f"CyclePattern<{self.user_id}:day{self.cycle_day}>"
