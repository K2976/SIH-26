from django.conf import settings
from django.core.exceptions import ValidationError
from django.core.validators import RegexValidator
from django.db import models
from django.utils import timezone

USERNAME_REGEX = r"^[a-zA-Z0-9_]{3,32}$"
username_validator = RegexValidator(
    regex=USERNAME_REGEX,
    message=(
        "Username must be 3-32 characters long and contain only letters, numbers, and underscores."
    ),
)


class SocialProfile(models.Model):
    """Public user identity for social features.

    Independent of auth_user.username and auth_user.email to preserve
    backward compatibility with the existing website authentication.
    """

    user = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="social_profile",
    )
    username = models.CharField(
        max_length=32,
        unique=True,
        db_index=True,
        validators=[username_validator],
        help_text="Public unique @handle (e.g. iron_lifter99)",
    )
    display_name = models.CharField(max_length=64)
    bio = models.TextField(blank=True, default="")
    profile_image = models.ImageField(upload_to="profiles/", null=True, blank=True)
    is_private = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "social_profile"
        ordering = ["-created_at"]

    def __str__(self):
        return f"@{self.username} ({self.display_name})"


class CommunityVisibility(models.TextChoices):
    PUBLIC = "public", "Public"
    PRIVATE = "private", "Private"


class Community(models.Model):
    """Represents a fitness community or accountability group."""

    name = models.CharField(max_length=128)
    description = models.TextField(blank=True, default="")
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="owned_communities",
    )
    visibility = models.CharField(
        max_length=16,
        choices=CommunityVisibility.choices,
        default=CommunityVisibility.PUBLIC,
        db_index=True,
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "social_community"
        ordering = ["-created_at"]

    def __str__(self):
        return self.name


class CommunityRole(models.TextChoices):
    OWNER = "owner", "Owner"
    ADMIN = "admin", "Admin"
    MEMBER = "member", "Member"


class CommunityMembership(models.Model):
    """Represents membership and role of a user in a community."""

    community = models.ForeignKey(
        Community,
        on_delete=models.CASCADE,
        related_name="memberships",
    )
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="community_memberships",
    )
    role = models.CharField(
        max_length=16,
        choices=CommunityRole.choices,
        default=CommunityRole.MEMBER,
    )
    joined_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "social_communitymembership"
        constraints = [
            models.UniqueConstraint(
                fields=["community", "user"],
                name="unique_community_membership",
            ),
        ]
        ordering = ["-joined_at"]

    def __str__(self):
        return f"{self.user} in {self.community.name} ({self.role})"


class ChallengeType(models.TextChoices):
    WORKOUT_COUNT = "workout_count", "Workout Count"
    WORKOUT_MINUTES = "workout_minutes", "Workout Minutes"
    STEPS = "steps", "Steps"
    CALORIES_BURNED = "calories_burned", "Calories Burned"
    DISTANCE_KM = "distance_km", "Distance (km)"


class ChallengeStatus(models.TextChoices):
    UPCOMING = "upcoming", "Upcoming"
    ACTIVE = "active", "Active"
    COMPLETED = "completed", "Completed"
    CANCELLED = "cancelled", "Cancelled"


class Challenge(models.Model):
    """Represents a fitness challenge within a community."""

    community = models.ForeignKey(
        Community,
        on_delete=models.CASCADE,
        related_name="challenges",
    )
    title = models.CharField(max_length=128)
    description = models.TextField(blank=True, default="")
    challenge_type = models.CharField(
        max_length=32,
        choices=ChallengeType.choices,
        db_index=True,
    )
    target_value = models.DecimalField(max_digits=10, decimal_places=2)
    start_date = models.DateTimeField(db_index=True)
    end_date = models.DateTimeField(db_index=True)
    status = models.CharField(
        max_length=16,
        choices=ChallengeStatus.choices,
        default=ChallengeStatus.UPCOMING,
        db_index=True,
    )
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="created_challenges",
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "social_challenge"
        ordering = ["-start_date"]
        indexes = [
            models.Index(fields=["community", "status", "start_date"]),
        ]

    def __str__(self):
        return f"{self.title} ({self.community.name})"

    def save(self, *args, **kwargs):
        self.full_clean()
        super().save(*args, **kwargs)

    def clean(self):
        if self.end_date <= self.start_date:
            raise ValidationError("End date must be after start date.")
        if self.target_value <= 0:
            raise ValidationError("Target value must be positive.")

    @property
    def is_upcoming(self) -> bool:
        return self.start_date > timezone.now()

    @property
    def is_active(self) -> bool:
        now = timezone.now()
        return self.start_date <= now <= self.end_date

    @property
    def is_expired(self) -> bool:
        return self.end_date < timezone.now()

    @property
    def participants_count(self) -> int:
        return self.participants.count()

    def get_participant_count(self) -> int:
        return self.participants.count()

    def get_user_progress(self, user) -> dict:
        """Calculate user's progress from stored activity data."""
        from django.db.models import Sum

        from apps.diary.models import SetLog, WorkoutSession

        target_f = float(self.target_value)
        if not self.is_active and not self.is_expired:
            return {
                "current": 0.0,
                "target": target_f,
                "percentage": 0.0,
                "completed": False,
            }

        # Get user's workouts within challenge date range
        workout_sessions = WorkoutSession.objects.filter(
            user=user,
            occurred_at__gte=self.start_date,
            occurred_at__lte=self.end_date,
        )

        try:
            if self.challenge_type == ChallengeType.WORKOUT_COUNT:
                current = workout_sessions.count()

            elif self.challenge_type == ChallengeType.WORKOUT_MINUTES:
                current = (
                    workout_sessions.filter(duration_minutes__isnull=False).aggregate(
                        total=Sum("duration_minutes")
                    )["total"]
                    or 0
                )

            elif self.challenge_type == ChallengeType.STEPS:
                # Sum reps from cardio exercises
                current = (
                    SetLog.objects.filter(
                        session__user=user,
                        session__occurred_at__gte=self.start_date,
                        session__occurred_at__lte=self.end_date,
                        exercise__movement_pattern="cardio",
                    ).aggregate(total=Sum("reps"))["total"]
                    or 0
                )

            elif self.challenge_type == ChallengeType.CALORIES_BURNED:
                current = 0

            elif self.challenge_type == ChallengeType.DISTANCE_KM:
                current = 0

            else:
                current = 0
        except Exception:
            current = 0

        completed = float(current) >= target_f
        percentage = (
            min(100.0, round((float(current) / target_f) * 100.0, 1)) if target_f > 0 else 0.0
        )
        return {
            "current": float(current),
            "target": target_f,
            "percentage": percentage,
            "completed": completed,
        }


class ChallengeParticipant(models.Model):
    """Represents a user's participation in a challenge."""

    challenge = models.ForeignKey(
        Challenge,
        on_delete=models.CASCADE,
        related_name="participants",
    )
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="challenge_participations",
    )
    joined_at = models.DateTimeField(auto_now_add=True)
    completed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "social_challengeparticipant"
        constraints = [
            models.UniqueConstraint(
                fields=["challenge", "user"],
                name="unique_challenge_participation",
            ),
        ]
        ordering = ["-joined_at"]

    def __str__(self):
        return f"{self.user} in {self.challenge.title}"

    @property
    def progress(self) -> dict:
        prog = self.challenge.get_user_progress(self.user)
        if prog.get("completed") and not self.completed_at:
            self.mark_completed()
        return prog

    @property
    def is_completed(self) -> bool:
        return self.completed_at is not None

    def mark_completed(self):
        if not self.completed_at:
            self.completed_at = timezone.now()
            self.save(update_fields=["completed_at"])
