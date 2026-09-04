from django.conf import settings
from django.db import models, transaction
from django.utils import timezone

from apps.accounts.constants import MAX_TARGET_RATE_KG_PER_WEEK, MIN_TARGET_RATE_KG_PER_WEEK


class UserProfile(models.Model):
    KNOWN = "known"
    NOT_ASKED = "not_asked"
    DECLINED = "declined"

    # Fields with three states: known (has a value), not_asked (null, never
    # offered to the user), declined (null, user was asked and skipped it).
    OPTIONAL_FIELDS = (
        "date_of_birth",
        "sex_at_birth",
        "height_cm",
        "waist_cm",
        "resting_hr",
        "tracks_cycle",
    )

    class SexAtBirth(models.TextChoices):
        MALE = "male", "Male"
        FEMALE = "female", "Female"
        INTERSEX = "intersex", "Intersex"

    class UnitPreference(models.TextChoices):
        METRIC = "metric", "Metric"
        IMPERIAL = "imperial", "Imperial"

    user = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="profile"
    )
    date_of_birth = models.DateField(null=True, blank=True)
    # null=True (not empty-string) is deliberate: the three-state contract
    # needs a real null for "no value", distinct from "" as a choice value.
    sex_at_birth = models.CharField(  # noqa: DJ001
        max_length=16, choices=SexAtBirth.choices, null=True, blank=True
    )
    height_cm = models.DecimalField(max_digits=5, decimal_places=1, null=True, blank=True)
    waist_cm = models.DecimalField(max_digits=5, decimal_places=1, null=True, blank=True)
    resting_hr = models.PositiveSmallIntegerField(null=True, blank=True)
    timezone = models.CharField(max_length=64, default="UTC")
    unit_preference = models.CharField(
        max_length=16, choices=UnitPreference.choices, default=UnitPreference.METRIC
    )
    # Never inferred from sex_at_birth — set only by a direct question.
    tracks_cycle = models.BooleanField(null=True, blank=True)
    declined_fields = models.JSONField(default=list, blank=True)
    onboarding_completed_at = models.DateTimeField(null=True, blank=True)

    def __str__(self):
        return f"Profile<{self.user_id}>"

    def field_state(self, field_name: str) -> str:
        """Report whether an optional field is known, not asked, or declined."""
        if field_name not in self.OPTIONAL_FIELDS:
            raise ValueError(f"{field_name!r} is not an optional field")
        if field_name in self.declined_fields:
            return self.DECLINED
        if getattr(self, field_name) is None:
            return self.NOT_ASKED
        return self.KNOWN


class GoalManager(models.Manager):
    def current(self, user):
        return self.filter(user=user, ended_at__isnull=True).order_by("-started_at").first()


class Goal(models.Model):
    class EnergyDirection(models.TextChoices):
        CUT = "cut", "Cut"
        MAINTAIN = "maintain", "Maintain"
        GAIN = "gain", "Gain"
        NOT_TRACKING = "not_tracking", "Not tracking"

    class TrainingFocus(models.TextChoices):
        STRENGTH = "strength", "Strength"
        HYPERTROPHY = "hypertrophy", "Hypertrophy"
        ENDURANCE = "endurance", "Endurance"
        GENERAL = "general", "General"

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="goals"
    )
    # Independent axes — never combine into one enum. maintain + hypertrophy
    # *is* recomp; it needs no third value.
    energy_direction = models.CharField(max_length=16, choices=EnergyDirection.choices)
    training_focus = models.CharField(max_length=16, choices=TrainingFocus.choices)
    target_rate_kg_per_week = models.DecimalField(
        max_digits=4, decimal_places=2, default=MIN_TARGET_RATE_KG_PER_WEEK
    )
    auto_adjust = models.BooleanField(default=True)
    started_at = models.DateTimeField(default=timezone.now)
    ended_at = models.DateTimeField(null=True, blank=True)

    objects = GoalManager()

    class Meta:
        indexes = [models.Index(fields=["user", "ended_at"])]

    def __str__(self):
        return f"Goal<{self.user_id}:{self.energy_direction}/{self.training_focus}>"


def current_goal(user):
    return Goal.objects.current(user)


def clamp_target_rate(value):
    """Clamp a requested rate to the safe range, returning (value, reason)."""
    if value < MIN_TARGET_RATE_KG_PER_WEEK:
        return (
            MIN_TARGET_RATE_KG_PER_WEEK,
            f"target_rate_kg_per_week below safe minimum "
            f"({MIN_TARGET_RATE_KG_PER_WEEK}); clamped up",
        )
    if value > MAX_TARGET_RATE_KG_PER_WEEK:
        return (
            MAX_TARGET_RATE_KG_PER_WEEK,
            f"target_rate_kg_per_week above safe maximum "
            f"({MAX_TARGET_RATE_KG_PER_WEEK}); clamped down",
        )
    return value, None


@transaction.atomic
def create_goal(*, user, energy_direction, training_focus, target_rate_kg_per_week, auto_adjust):
    """Close the user's current goal (if any) and open a new versioned row."""
    clamped_rate, reason = clamp_target_rate(target_rate_kg_per_week)
    Goal.objects.filter(user=user, ended_at__isnull=True).update(ended_at=timezone.now())
    goal = Goal.objects.create(
        user=user,
        energy_direction=energy_direction,
        training_focus=training_focus,
        target_rate_kg_per_week=clamped_rate,
        auto_adjust=auto_adjust,
    )
    goal.clamp_reason = reason
    return goal
