from django.db import models, transaction


class ReferenceVersion(models.Model):
    """Single-row global counter. Bumped whenever any reference row changes."""

    value = models.PositiveIntegerField(default=0)

    def __str__(self):
        return f"ReferenceVersion<{self.value}>"

    @classmethod
    def bump(cls) -> int:
        with transaction.atomic():
            obj, _ = cls.objects.select_for_update().get_or_create(pk=1)
            obj.value += 1
            obj.save(update_fields=["value"])
            return obj.value

    @classmethod
    def current(cls) -> int:
        obj, _ = cls.objects.get_or_create(pk=1)
        return obj.value


class Exercise(models.Model):
    class MovementPattern(models.TextChoices):
        SQUAT = "squat", "Squat"
        HINGE = "hinge", "Hinge"
        LUNGE = "lunge", "Lunge"
        PUSH_HORIZONTAL = "push_horizontal", "Push (horizontal)"
        PUSH_VERTICAL = "push_vertical", "Push (vertical)"
        PULL_HORIZONTAL = "pull_horizontal", "Pull (horizontal)"
        PULL_VERTICAL = "pull_vertical", "Pull (vertical)"
        CARRY = "carry", "Carry"
        CORE = "core", "Core"
        ISOLATION = "isolation", "Isolation"
        PLYOMETRIC = "plyometric", "Plyometric"
        CARDIO = "cardio", "Cardio"

    class CameraPosition(models.TextChoices):
        FRONT = "front", "Front"
        SIDE = "side", "Side"
        FORTY_FIVE_DEGREE = "45_degree", "45 degree"

    # Natural key. Clients store this, never the numeric id.
    slug = models.SlugField(max_length=64, unique=True)

    # Locale-keyed as {"en": "...", "hi": "..."} so a new language is a data
    # write, never a migration. English is always present; readers fall back
    # to it via localized_name()/localized_instructions().
    name = models.JSONField()
    instructions = models.JSONField()

    primary_muscles = models.JSONField(default=list, blank=True)
    secondary_muscles = models.JSONField(default=list, blank=True)
    equipment = models.CharField(max_length=64, blank=True)
    movement_pattern = models.CharField(max_length=32, choices=MovementPattern.choices)
    form_cues = models.JSONField(default=list, blank=True)

    # Required — strength-session burn cannot be estimated without it.
    met_value = models.DecimalField(max_digits=3, decimal_places=1)

    is_bodyweight = models.BooleanField(default=False)
    pose_config = models.JSONField(null=True, blank=True)
    # null=True is deliberate: unset means "no required camera position",
    # distinct from any choice value.
    camera_position = models.CharField(  # noqa: DJ001
        max_length=16, choices=CameraPosition.choices, null=True, blank=True
    )

    # Never hard-deleted — set False instead. User history points at this row.
    is_active = models.BooleanField(default=True)

    # The version of the global ReferenceVersion counter as of this row's
    # last change. Delta sync is `updated_at_version > since`.
    updated_at_version = models.PositiveIntegerField(default=0, db_index=True)

    def __str__(self):
        return self.slug

    def save(self, *args, **kwargs):
        self.updated_at_version = ReferenceVersion.bump()
        super().save(*args, **kwargs)

    def localized(self, field_name: str, locale: str = "en"):
        value = getattr(self, field_name)
        return value.get(locale) or value.get("en")

    def localized_name(self, locale: str = "en"):
        return self.localized("name", locale)

    def localized_instructions(self, locale: str = "en"):
        return self.localized("instructions", locale)
